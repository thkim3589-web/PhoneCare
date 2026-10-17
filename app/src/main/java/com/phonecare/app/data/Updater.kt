package com.phonecare.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

data class UpdateInfo(val tag: String, val name: String, val apkUrl: String, val publishedAt: Long)

/**
 * GitHub Releases에 새 APK가 올라왔는지 확인하고, 내려받아 설치 화면을 연다.
 * 새 버전 판단: 릴리스 게시 시각이 이 앱을 마지막으로 설치/업데이트한 시각보다 늦으면 새 버전.
 */
object Updater {

    private const val LATEST_URL = "https://api.github.com/repos/thkim3589-web/PhoneCare/releases/latest"

    /** 설치 직후 같은 빌드를 다시 새 버전으로 보지 않도록 여유 시간 */
    private const val MARGIN_MS = 2 * 60 * 1000L

    /** 새 버전이 있으면 정보를, 없으면 null (네트워크 실패도 null) */
    fun check(context: Context): UpdateInfo? = try {
        val conn = (URL(LATEST_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()
        val json = JSONObject(body)
        val assets = json.getJSONArray("assets")
        var apkUrl: String? = null
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.getString("name").endsWith(".apk")) apkUrl = a.getString("browser_download_url")
        }
        val published = Instant.parse(json.getString("published_at")).toEpochMilli()
        @Suppress("DEPRECATION")
        val installedAt = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
        if (apkUrl != null && published > installedAt + MARGIN_MS) {
            UpdateInfo(json.getString("tag_name"), json.optString("name", ""), apkUrl, published)
        } else {
            null
        }
    } catch (e: Exception) {
        null
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
}
