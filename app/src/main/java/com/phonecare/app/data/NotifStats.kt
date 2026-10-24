package com.phonecare.app.data

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.toBitmap
import com.phonecare.app.util.DAY_MS
import com.phonecare.app.util.Period
import com.phonecare.app.util.openSettings
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class NotifEntry(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap?,
    val count: Int,
)

enum class NotifSource { LISTENER, USAGE, NONE }

/**
 * 앱별 알림 개수.
 * 1) '알림 접근'을 켜면 직접 집계 (가장 정확, 켠 뒤부터 쌓임)
 * 2) 아니면 시스템 사용 기록의 알림 이벤트 사용 (기기에 따라 제공 안 될 수 있음)
 * 알림 '내용'은 읽지 않고 어느 앱이 보냈는지만 센다.
 */
object NotifStats {

    /** 알림 접근으로 센 기록 보관 기간 */
    private const val KEEP_DAYS = 31
    private const val PREFS = "notif_stats"
    private const val KEY_DATA = "data"
    private const val KEY_SINCE = "since"
    private const val EVENT_NOTIFICATION_INTERRUPTION = 12
    private val lock = Any()
    private val dayFmt get() = SimpleDateFormat("yyyyMMdd", Locale.US)

    /** 알림 접근(개수 집계)은 스토어 버전에만 있음 (직접 설치 APK는 Play Protect 경고 때문에 뺌) */
    val listenerSupported: Boolean get() = com.phonecare.app.BuildConfig.STORE

    fun isListenerEnabled(c: Context): Boolean =
        listenerSupported && NotificationManagerCompat.getEnabledListenerPackages(c).contains(c.packageName)

    /** 휴대폰 설정의 '앱 알림' 목록 (삼성은 '가장 많이 보낸 순' 정렬 가능) */
    fun openAllAppNotificationSettings(c: Context) {
        val tries = listOf("android.settings.ALL_APPS_NOTIFICATION_SETTINGS", "android.settings.NOTIFICATION_SETTINGS")
        for (action in tries) {
            try {
                c.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (e: Exception) {
            }
        }
        c.openSettings(Intent(Settings.ACTION_SETTINGS))
    }

    fun openListenerSettings(c: Context) {
        c.openSettings(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
    }

    fun openAppNotificationSettings(c: Context, pkg: String) {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pkg)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:$pkg"))
        }
        c.openSettings(intent)
    }

    /** 알림 감시 서비스에서 호출: 오늘 날짜에 +1 */
    fun record(c: Context, pkg: String) = synchronized(lock) {
        val prefs = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val root = try {
            JSONObject(prefs.getString(KEY_DATA, "{}") ?: "{}")
        } catch (e: Exception) {
            JSONObject()
        }
        val today = dayFmt.format(Date())
        val day = root.optJSONObject(today) ?: JSONObject().also { root.put(today, it) }
        day.put(pkg, day.optInt(pkg, 0) + 1)
        // 오래된 날짜 정리
        val cutoff = dayFmt.format(Date(System.currentTimeMillis() - (KEEP_DAYS + 1) * DAY_MS))
        root.keys().asSequence().toList().filter { it < cutoff }.forEach { root.remove(it) }
        val edit = prefs.edit().putString(KEY_DATA, root.toString())
        if (!prefs.contains(KEY_SINCE)) edit.putLong(KEY_SINCE, System.currentTimeMillis())
        edit.apply()
    }

    /** 집계를 시작한 시각 (없으면 0) */
    fun listenerSince(c: Context): Long =
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_SINCE, 0L)

    private fun listenerCounts(c: Context, period: Period): Map<String, Int> = synchronized(lock) {
        val root = try {
            JSONObject(c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_DATA, "{}") ?: "{}")
        } catch (e: Exception) {
            return emptyMap()
        }
        val cutoff = dayFmt.format(Date(System.currentTimeMillis() - (period.days - 1) * DAY_MS))
        val out = HashMap<String, Int>()
        root.keys().forEach { day ->
            if (day < cutoff) return@forEach
            val o = root.optJSONObject(day) ?: return@forEach
            o.keys().forEach { pkg -> out[pkg] = (out[pkg] ?: 0) + o.optInt(pkg, 0) }
        }
        out
    }

    private fun usageEventCounts(c: Context, period: Period): Map<String, Int> {
        if (!AppUsage.hasUsageAccess(c)) return emptyMap()
        val out = HashMap<String, Int>()
        try {
            val now = System.currentTimeMillis()
            val events = c.getSystemService(UsageStatsManager::class.java).queryEvents(period.start(), now)
            val e = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(e)
                if (e.eventType == EVENT_NOTIFICATION_INTERRUPTION) {
                    val pkg = e.packageName ?: continue
                    out[pkg] = (out[pkg] ?: 0) + 1
                }
            }
        } catch (ex: Exception) {
        }
        return out
    }

    /** 알림 많은 앱 순위 (백그라운드에서 호출) */
    fun load(c: Context, period: Period = Period.WEEK, limit: Int = 30): Pair<List<NotifEntry>, NotifSource> {
        var source = NotifSource.NONE
        var counts: Map<String, Int> = emptyMap()
        if (isListenerEnabled(c)) {
            counts = listenerCounts(c, period)
            source = NotifSource.LISTENER
        }
        if (counts.isEmpty()) {
            val fromEvents = usageEventCounts(c, period)
            if (fromEvents.isNotEmpty()) {
                counts = fromEvents
                source = NotifSource.USAGE
            }
        }
        val pm = c.packageManager
        val entries = counts.entries
            .filter { it.key != c.packageName && it.value > 0 }
            .sortedByDescending { it.value }
            .mapNotNull { (pkg, n) ->
                try {
                    @Suppress("DEPRECATION")
                    val ai = pm.getApplicationInfo(pkg, 0)
                    val icon = try {
                        pm.getApplicationIcon(ai).toBitmap(96, 96).asImageBitmap()
                    } catch (e: Exception) {
                        null
                    }
                    NotifEntry(pkg, pm.getApplicationLabel(ai).toString(), icon, n)
                } catch (e: Exception) {
                    null
                }
            }
            .take(limit)
        return entries to source
    }
}
