package com.phonecare.app.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.os.Environment
import com.phonecare.app.util.daysAgo
import java.io.File

/**
 * 정크 종류
 * toTrash = true면 휴지통으로(복원 가능), false면 바로 삭제(다시 생기는 캐시나 내용 없는 것)
 */
enum class JunkKind(val title: String, val desc: String, val toTrash: Boolean, val preselect: Boolean) {
    THUMB("썸네일 캐시", "갤러리 미리보기용 작은 사진이에요. 지워도 필요하면 다시 만들어져요 · 바로 삭제", false, true),
    TEMP("임시·기록 파일", ".tmp·.log 파일과 받다 만 다운로드 파일 · 휴지통으로", true, true),
    EMPTY("빈 폴더·빈 파일", "아무 내용도 없는 폴더와 0바이트 파일 · 바로 삭제", false, true),
    LEFTOVER("지운 앱이 남긴 폴더", "지금 설치돼 있지 않은 앱 이름의 폴더예요. 내용을 확인하고 골라주세요 · 휴지통으로", true, false),
}

data class JunkItem(
    val path: String,
    val name: String,
    val size: Long,
    val fileCount: Int,
    val isDir: Boolean,
    val kind: JunkKind,
) {
    val folder: String
        get() = path.substringBeforeLast('/', "").removePrefix(Environment.getExternalStorageDirectory().absolutePath).ifEmpty { "/" }
}

object JunkCleaner {

    private val tempExt = setOf("tmp", "temp", "log")
    private val partialExt = setOf("crdownload", "part", "partial", "download")

    // 안드로이드 기본 폴더는 비어 있어도 지우지 않음
    private val standardTop = setOf(
        "android", "dcim", "pictures", "download", "documents", "music", "movies", "alarms",
        "ringtones", "notifications", "podcasts", "audiobooks", "recordings",
    )
    private val standardSub = setOf("dcim/camera", "dcim/screenshots", "pictures/screenshots")

    // 시스템·제조사 앱은 '설치 안 됨'으로 잘못 보일 수 있어서 남은 폴더 후보에서 제외
    private val systemPrefixes = listOf(
        "android", "com.android.", "com.google.", "com.samsung.", "com.sec.", "com.qualcomm.",
        "com.skt.", "com.sktelecom.", "com.kt.", "com.lguplus.", "com.facebook.", "com.microsoft.",
    )
    private val packageLike = Regex("^[a-z][a-z0-9_]*(\\.[a-z0-9_]+){2,}$")

    private fun root(): File = Environment.getExternalStorageDirectory()

    /** 정크 찾기 (시간이 걸리므로 백그라운드에서 호출) */
    fun scan(
        context: Context,
        protectedFolders: List<String>,
        isActive: () -> Boolean = { true },
        onProgress: (Int) -> Unit = {},
    ): List<JunkItem> {
        val root = root()
        val rootPath = root.absolutePath
        val trashPath = File(root, ".PhoneCareTrash").absolutePath
        val out = ArrayList<JunkItem>()
        var count = 0

        root.walkTopDown()
            .onEnter { dir ->
                if (!isActive()) return@onEnter false
                val path = dir.absolutePath
                if (path == rootPath) return@onEnter true
                if (path == trashPath || ProtectedFolders.isProtected(path, protectedFolders)) return@onEnter false
                val rel = path.removePrefix("$rootPath/").lowercase()
                if (rel == "android" || rel.startsWith("android/")) return@onEnter false
                if (dir.name == ".thumbnails") {
                    val (size, n) = treeSize(dir)
                    if (n > 0) out.add(JunkItem(path, "${dir.parentFile?.name ?: ""}/.thumbnails", size, n, true, JunkKind.THUMB))
                    return@onEnter false
                }
                if (dir.name.startsWith(".")) return@onEnter false // 다른 앱의 숨김 데이터는 건드리지 않음
                val isStandard = rel in standardTop || rel in standardSub
                if (!isStandard && isTreeEmpty(dir)) {
                    out.add(JunkItem(path, dir.name, 0, 0, true, JunkKind.EMPTY))
                    return@onEnter false
                }
                true
            }
            .forEach { f ->
                if (!f.isFile || !isActive()) return@forEach
                count++
                if (count % 300 == 0) onProgress(count)
                val name = f.name
                if (name.startsWith(".")) return@forEach // .nomedia 같은 표시 파일은 그대로
                val ext = name.substringAfterLast('.', "").lowercase()
                val size = f.length()
                val old = daysAgo(f.lastModified()) >= 1 // 지금 쓰고 있을 수 있는 파일은 제외
                val kind = when {
                    size == 0L && old -> JunkKind.EMPTY
                    ext in tempExt && old -> JunkKind.TEMP
                    ext in partialExt && old -> JunkKind.TEMP
                    else -> null
                } ?: return@forEach
                out.add(JunkItem(f.absolutePath, name, size, 1, false, kind))
            }

        if (isActive()) out.addAll(findLeftovers(context, root, protectedFolders))
        onProgress(count)
        return out.sortedByDescending { it.size }
    }

    /** 설치돼 있지 않은 앱 이름(패키지 이름)의 폴더: 최상위 폴더와 Android/media 아래 */
    private fun findLeftovers(context: Context, root: File, protectedFolders: List<String>): List<JunkItem> {
        val pm = context.packageManager
        val launcherApps: Set<String> = try {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                .map { it.activityInfo.packageName }.toSet()
        } catch (e: Exception) {
            return emptyList()
        }
        fun installed(pkg: String): Boolean {
            if (pkg in launcherApps) return true
            return try {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(pkg, 0)
                true
            } catch (e: PackageManager.NameNotFoundException) {
                false
            } catch (e: Exception) {
                true // 확인할 수 없으면 안전하게 '설치됨'으로 봄
            }
        }
        val dirs = (root.listFiles().orEmpty().toList() + File(root, "Android/media").listFiles().orEmpty().toList())
            .filter { it.isDirectory }
        val out = ArrayList<JunkItem>()
        for (d in dirs) {
            val name = d.name
            if (!packageLike.matches(name)) continue
            if (name == context.packageName || systemPrefixes.any { name == it.trimEnd('.') || name.startsWith(it) }) continue
            if (ProtectedFolders.isProtected(d.absolutePath, protectedFolders)) continue
            if (installed(name)) continue
            val (size, n) = treeSize(d)
            if (n == 0) continue // 빈 폴더는 '빈 폴더'에서 처리
            out.add(JunkItem(d.absolutePath, name, size, n, true, JunkKind.LEFTOVER))
        }
        return out
    }

    /** 폴더 안 파일 총 용량과 개수 */
    private fun treeSize(dir: File): Pair<Long, Int> {
        var size = 0L
        var n = 0
        dir.walkTopDown().forEach {
            if (it.isFile) {
                size += it.length()
                n++
            }
        }
        return size to n
    }

    /** 폴더 안(하위 포함)에 파일이 하나도 없고 숨김 항목도 없는지 */
    private fun isTreeEmpty(dir: File, depth: Int = 0): Boolean {
        if (depth > 8) return false
        val children = dir.listFiles() ?: return false
        for (c in children) {
            if (c.isFile || c.name.startsWith(".")) return false
            if (!isTreeEmpty(c, depth + 1)) return false
        }
        return true
    }

    /**
     * 선택한 정크 정리. 돌려주는 값: 정리된 항목 경로, 확보한 용량, 휴지통으로 간 개수
     */
    fun clean(context: Context, items: List<JunkItem>): Triple<Set<String>, Long, Int> {
        val done = HashSet<String>()
        var freed = 0L
        var trashed = 0
        var directCount = 0
        var directBytes = 0L
        val scanPaths = ArrayList<String>()

        for (item in items) {
            val f = File(item.path)
            if (!f.exists()) {
                done.add(item.path)
                continue
            }
            if (item.kind.toTrash) {
                // 폴더면 안의 파일을 하나씩 휴지통으로 (복원하면 원래 자리로 돌아감)
                val paths = if (item.isDir) f.walkTopDown().filter { it.isFile }.map { it.absolutePath }.toList() else listOf(item.path)
                val (moved, bytes) = TrashManager.moveToTrash(context, paths)
                freed += bytes
                trashed += moved.size
                if (item.isDir) {
                    deleteEmptyDirs(f)
                    if (!f.exists() || moved.size == paths.size) done.add(item.path)
                } else if (item.path in moved) {
                    done.add(item.path)
                }
            } else {
                val ok = try {
                    if (item.kind == JunkKind.THUMB) {
                        // 썸네일 폴더 자체는 남기고 안의 파일만 지움
                        f.listFiles().orEmpty().forEach { it.deleteRecursively() }
                        true
                    } else if (item.isDir) {
                        isTreeEmpty(f) && f.deleteRecursively()
                    } else {
                        f.delete()
                    }
                } catch (e: SecurityException) {
                    false
                }
                if (ok) {
                    done.add(item.path)
                    freed += item.size
                    directBytes += item.size
                    directCount += maxOf(item.fileCount, 1)
                    if (!item.isDir) scanPaths.add(item.path)
                }
            }
        }
        if (scanPaths.isNotEmpty()) MediaScannerConnection.scanFile(context, scanPaths.toTypedArray(), null, null)
        // 휴지통 이동분은 TrashManager가 이미 기록함 → 바로 지운 것만 기록
        if (directCount > 0) History.recordFreed(context, directBytes, directCount)
        return Triple(done, freed, trashed)
    }

    /** 파일을 다 옮긴 뒤 남은 빈 하위 폴더 정리 */
    private fun deleteEmptyDirs(dir: File) {
        dir.walkBottomUp().forEach { if (it.isDirectory && it.listFiles().isNullOrEmpty()) it.delete() }
    }
}
