package com.phonecare.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.phonecare.app.BuildConfig
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(val tag: String, val name: String, val apkUrl: String, val publishedAt: Long)

/**
 * GitHub Releases에 새 APK가 올라왔는지 확인하고, 인터넷 창에서 받도록 연다.
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

    /**
     * 새 버전 받기: 인터넷 창(브라우저)에서 APK를 내려받게 한다.
     * 앱이 직접 APK를 받아 설치하면 Play 프로텍트가 '유해 앱'으로 의심하므로,
     * 다운로드·설치는 브라우저와 시스템 설치 화면에 맡긴다.
     */
    fun openDownload(context: Context, info: UpdateInfo?): Boolean = try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(info?.apkUrl ?: LATEST_APK_URL))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    } catch (e: Exception) {
        false
    }

    const val LATEST_APK_URL = "https://github.com/thkim3589-web/PhoneCare/releases/latest/download/PhoneCare.apk"
}
