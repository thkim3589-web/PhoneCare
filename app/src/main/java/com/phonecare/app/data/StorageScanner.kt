package com.phonecare.app.data

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.phonecare.app.util.daysAgo
import com.phonecare.app.util.openSettings
import java.io.File

enum class FileCategory(val title: String, val desc: String) {
    APK("설치 파일 (APK)", "앱 설치가 끝났다면 필요 없는 파일이에요"),
    SCREENSHOT("스크린샷", "쌓여 있는 화면 캡처 이미지"),
    OLD_DOWNLOAD("오래된 다운로드", "받은 지 90일이 넘은 다운로드 파일"),
    LARGE("큰 파일", "100MB 이상인 파일 (동영상 등) — 꼭 확인 후 삭제하세요"),
}

data class FoundFile(
    val path: String,
    val name: String,
    val folder: String,
    val size: Long,
    val modified: Long,
    val category: FileCategory,
)

object StorageScanner {

    private const val LARGE_FILE_BYTES = 100L * 1024 * 1024
    private const val OLD_DOWNLOAD_DAYS = 90

    fun hasAccess(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }

    /** Android 11 이상: "모든 파일 접근 허용" 설정 화면 열기 */
    fun openAllFilesSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val appIntent = Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:${context.packageName}"),
            )
            try {
                context.startActivity(appIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: Exception) {
                context.openSettings(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        }
    }

    /** 내장 저장소를 훑어서 정리 후보 파일을 찾는다. (시간이 걸리므로 백그라운드에서 호출) */
    fun scan(
        context: Context,
        protectedFolders: List<String> = emptyList(),
        isActive: () -> Boolean = { true },
        onProgress: (Int) -> Unit,
    ): List<FoundFile> {
        // 1) 빠른 검사: 안드로이드가 이미 만들어 둔 파일 목록(미디어 색인) 사용 → 수 초 안에 끝남
        scanIndex(context, protectedFolders, isActive, onProgress)?.let { return it }
        // 2) 색인을 못 쓰면 폴더를 직접 훑음 (느림)
        return scanWalk(protectedFolders, isActive, onProgress)
    }

    private fun categorize(path: String, name: String, size: Long, modified: Long, downloadsPath: String): FileCategory? {
        val lowerPath = path.lowercase()
        return when {
            name.lowercase().endsWith(".apk") -> FileCategory.APK
            lowerPath.contains("/screenshots/") -> FileCategory.SCREENSHOT
            path.startsWith(downloadsPath) && daysAgo(modified) > OLD_DOWNLOAD_DAYS -> FileCategory.OLD_DOWNLOAD
            size >= LARGE_FILE_BYTES -> FileCategory.LARGE
            else -> null
        }
    }

    @Suppress("DEPRECATION")
    private fun scanIndex(
        context: Context,
        protectedFolders: List<String>,
        isActive: () -> Boolean,
        onProgress: (Int) -> Unit,
    ): List<FoundFile>? {
        val root = Environment.getExternalStorageDirectory().absolutePath
        val androidDir = "$root/Android/"
        val downloadsPath = File(root, Environment.DIRECTORY_DOWNLOADS).absolutePath + "/"
        val result = ArrayList<FoundFile>()
        var count = 0
        try {
            val uri = android.provider.MediaStore.Files.getContentUri("external")
            val cols = arrayOf(
                android.provider.MediaStore.Files.FileColumns.DATA,
                android.provider.MediaStore.Files.FileColumns.SIZE,
                android.provider.MediaStore.Files.FileColumns.DATE_MODIFIED,
            )
            context.contentResolver.query(uri, cols, "${cols[1]} > 0", null, null)?.use { c ->
                if (c.count == 0) return null
                val iData = c.getColumnIndexOrThrow(cols[0])
                val iSize = c.getColumnIndexOrThrow(cols[1])
                val iMod = c.getColumnIndexOrThrow(cols[2])
                while (c.moveToNext()) {
                    if (!isActive()) return emptyList()
                    count++
                    if (count % 500 == 0) onProgress(count)
                    val path = c.getString(iData) ?: continue
                    if (!path.startsWith(root) || path.startsWith(androidDir) || path.contains("/.")) continue
                    if (ProtectedFolders.isProtected(path, protectedFolders)) continue
                    val name = path.substringAfterLast('/')
                    val size = c.getLong(iSize)
                    val modified = c.getLong(iMod) * 1000
                    val category = categorize(path, name, size, modified, downloadsPath) ?: continue
                    if (!File(path).exists()) continue // 색인에만 남은 지워진 파일 제외
                    val folder = path.substringBeforeLast('/', "").removePrefix(root).ifEmpty { "/" }
                    result.add(FoundFile(path, name, folder, size, modified, category))
                }
            } ?: return null
        } catch (e: Exception) {
            return null
        }
        onProgress(count)
        return result.sortedByDescending { it.size }
    }

    private fun scanWalk(
        protectedFolders: List<String>,
        isActive: () -> Boolean,
        onProgress: (Int) -> Unit,
    ): List<FoundFile> {
        val root = Environment.getExternalStorageDirectory()
        val rootPath = root.absolutePath
        val downloadsPath = File(root, Environment.DIRECTORY_DOWNLOADS).absolutePath + "/"
        val result = ArrayList<FoundFile>()
        var count = 0

        root.walkTopDown()
            .onEnter { dir ->
                // 중단 요청 시 더 들어가지 않음 / 다른 앱 전용 폴더(Android/)와 숨김 폴더는 건너뜀
                isActive() &&
                    !(dir.name == "Android" && dir.parentFile?.absolutePath == rootPath) &&
                    !(dir.name.startsWith(".") && dir.absolutePath != rootPath) &&
                    !ProtectedFolders.isProtected(dir.absolutePath, protectedFolders)
            }
            .forEach { file ->
                if (!file.isFile || !isActive()) return@forEach
                count++
                if (count % 200 == 0) onProgress(count)

                val path = file.absolutePath
                val name = file.name
                val size = file.length()
                val modified = file.lastModified()
                val category = categorize(path, name, size, modified, downloadsPath) ?: return@forEach

                val folder = (file.parentFile?.absolutePath ?: "")
                    .removePrefix(rootPath).ifEmpty { "/" }
                result.add(FoundFile(path, name, folder, size, modified, category))
            }
        onProgress(count)
        return result.sortedByDescending { it.size }
    }

    /** 선택한 파일을 삭제하고, 실제로 삭제된 경로와 확보한 용량을 돌려준다. */
    fun delete(context: Context, files: List<FoundFile>): Pair<Set<String>, Long> {
        val deleted = HashSet<String>()
        var freed = 0L
        for (f in files) {
            val file = File(f.path)
            try {
                if (file.exists() && file.delete()) {
                    deleted.add(f.path)
                    freed += f.size
                }
            } catch (ignored: SecurityException) {
            }
        }
        // 갤러리 등에서 지운 파일이 사라지도록 미디어 목록 갱신
        if (deleted.isNotEmpty()) {
            MediaScannerConnection.scanFile(context, deleted.toTypedArray(), null, null)
        }
        return deleted to freed
    }
}
