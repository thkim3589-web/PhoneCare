package com.phonecare.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.phonecare.app.BuildConfig
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(val tag: String, val name: String, val apkUrl: String, val publishedAt: Long)

/**
 * GitHub Releases에 새 APK가 올라왔는지 확인하고, 내려받아 설치 화면을 연다.
 * 새 버전 판단: 최신 릴리스 번호(build-N)가 이 앱의 빌드 번호보다 크면 새 버전.
 * GitHub API 대신 '최신 릴리스' 주소의 이동 위치만 확인해서 횟수 제한에 걸리지 않음.
 */
object Updater {

    private const val REPO = "https://github.com/thkim3589-web/PhoneCare"
    private const val LATEST_PAGE = "$REPO/releases/latest"

    /** 너무 자주 확인하지 않도록 30분 간격 */
    private const val CHECK_INTERVAL_MS = 30 * 60 * 1000L
    private var lastCheck = 0L
    private var lastResult: UpdateInfo? = null

    /** 최신 릴리스 태그 (예: build-15). 실패하면 null */
    private fun latestTag(): String? {
        val conn = (URL(LATEST_PAGE).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            instanceFollowRedirects = false
        }
        return try {
            conn.responseCode
            conn.getHeaderField("Location")?.substringAfter("/releases/tag/", "")?.takeIf { it.isNotBlank() }
        } finally {
            conn.disconnect()
        }
    }

    /** 다음 확인 때 30분 간격을 무시하고 바로 확인 */
    fun resetThrottle() {
        lastCheck = 0L
    }

    /** 새 버전이 있으면 정보를, 없으면 null (네트워크 실패도 null) */
    fun check(@Suppress("UNUSED_PARAMETER") context: Context): UpdateInfo? {
        if (BuildConfig.STORE) return null // 스토어 버전은 플레이 스토어가 업데이트
        val now = System.currentTimeMillis()
        if (now - lastCheck < CHECK_INTERVAL_MS) return lastResult
        return try {
            val tag = latestTag() ?: return lastResult
            lastCheck = now
            val latest = tag.removePrefix("build-").toIntOrNull()
            val mine = BuildConfig.BUILD_NUMBER
            lastResult = if (latest != null && mine > 0 && latest > mine) {
                UpdateInfo(tag, "폰케어 빌드 $latest", "$REPO/releases/download/$tag/PhoneCare.apk", 0L)
            } else {
                null
            }
            lastResult
        } catch (e: Exception) {
            lastResult
        }
    }

    /** APK 다운로드 (진행률 0~1 콜백). 성공하면 파일을 돌려준다. */
    fun download(context: Context, info: UpdateInfo, onProgress: (Float) -> Unit): File? {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val out = File(dir, "PhoneCare-${info.tag}.apk")
        return try {
            var url = URL(info.apkUrl)
            var conn: HttpURLConnection
            var redirects = 0
            // GitHub 다운로드는 다른 주소로 넘겨주므로 직접 따라감
            while (true) {
                conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 30000
                    instanceFollowRedirects = false
                }
                val code = conn.responseCode
                if (code in 300..399 && redirects < 5) {
                    val location = conn.getHeaderField("Location") ?: break
                    conn.disconnect()
                    url = URL(url, location)
                    redirects++
                    continue
                }
                break
            }
            val total = conn.contentLengthLong
            conn.inputStream.use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        output.write(buf, 0, n)
                        done += n
                        if (total > 0) onProgress(done.toFloat() / total)
                    }
                }
            }
            conn.disconnect()
            out
        } catch (e: Exception) {
            out.delete()
            null
        }
    }

    /** "출처를 알 수 없는 앱 설치" 허용 여부 */
    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
        }
    }

    /** 시스템 설치 화면 열기 */
    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** 내려받은 APK가 지금 설치된 앱과 같은 키로 서명됐는지 (확인할 수 없으면 true) */
    fun sameSigner(context: Context, apk: File): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return true
        return try {
            val pm = context.packageManager
            val flag = android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES
            val archive = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageArchiveInfo(apk.path, android.content.pm.PackageManager.PackageInfoFlags.of(flag.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(apk.path, flag)
            }
            val installed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(flag.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, flag)
            }
            val a = archive?.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet()
            val b = installed.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet()
            if (a.isNullOrEmpty() || b.isNullOrEmpty()) true else a == b
        } catch (e: Exception) {
            true
        }
    }

    /** 앱을 지워도 남도록 '다운로드' 폴더에 복사. 성공하면 저장 경로 */
    fun copyToDownloads(apk: File): String? = try {
        val dir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
        dir.mkdirs()
        val out = File(dir, "PhoneCare-새버전.apk")
        apk.copyTo(out, overwrite = true)
        out.absolutePath
    } catch (e: Exception) {
        null
    }

    const val LATEST_APK_URL = "https://github.com/thkim3589-web/PhoneCare/releases/latest/download/PhoneCare.apk"
}
