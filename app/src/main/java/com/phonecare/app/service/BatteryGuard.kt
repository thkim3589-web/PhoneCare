package com.phonecare.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.phonecare.app.MainActivity
import com.phonecare.app.R
import com.phonecare.app.data.History
import com.phonecare.app.widget.PhoneCareWidget
import java.util.Locale

/** 충전·과열 알림 설정 */
object GuardSettings {
    private const val PREFS = "guard"

    val chargeOptions = listOf(80, 85, 90, 100)
    val heatOptions = listOf(40, 42, 45)

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(c: Context) = prefs(c).getBoolean("enabled", false)
    fun setEnabled(c: Context, v: Boolean) = prefs(c).edit().putBoolean("enabled", v).apply()

    fun chargeLimit(c: Context) = prefs(c).getInt("charge", 80)
    fun setChargeLimit(c: Context, v: Int) = prefs(c).edit().putInt("charge", v).apply()

    fun heatLimit(c: Context) = prefs(c).getInt("heat", 42)
    fun setHeatLimit(c: Context, v: Int) = prefs(c).edit().putInt("heat", v).apply()

    fun start(c: Context) {
        try {
            ContextCompat.startForegroundService(c, Intent(c, BatteryGuardService::class.java))
        } catch (e: Exception) {
        }
    }

    fun stop(c: Context) {
        c.stopService(Intent(c, BatteryGuardService::class.java))
    }
}

/** 배터리 상태를 지켜보다가 충전 한도·과열 시 알림을 보내는 서비스 */
class BatteryGuardService : Service() {

    companion object {
        private const val CH_STATUS = "guard_status"
        private const val CH_ALERT = "guard_alert"
        private const val ID_STATUS = 1001
        private const val ID_CHARGE = 1002
        private const val ID_HEAT = 1003
        private const val HEAT_COOLDOWN_MS = 10 * 60 * 1000L
    }

    private var registered = false
    private var chargeAlerted = false
    private var lastHeatAlert = 0L
    private var lastStatusText = ""
    private var lastHandleAt = 0L
    private var wasPlugged: Boolean? = null
    private var pendingHotMs = 0L
    private var pendingFullMs = 0L

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = handle(intent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        try {
            ServiceCompat.startForeground(this, ID_STATUS, statusNotification("배터리를 지켜보는 중"), type)
        } catch (e: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!registered) {
            ContextCompat.registerReceiver(
                this, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_EXPORTED,
            )?.let { handle(it) }
            registered = true
        }
        return START_STICKY
    }

    override fun onDestroy() {
        if (registered) {
            try {
                unregisterReceiver(receiver)
            } catch (e: Exception) {
            }
            registered = false
        }
        super.onDestroy()
    }

    private fun handle(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val pct = if (level >= 0 && scale > 0) level * 100 / scale else return
        val temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        val charging = plugged || status == BatteryManager.BATTERY_STATUS_CHARGING

        // ----- 주간 리포트용 기록: 온도, 충전 습관 -----
        val nowMs = System.currentTimeMillis()
        val elapsed = if (lastHandleAt > 0) (nowMs - lastHandleAt).coerceIn(0, 10 * 60 * 1000L) else 0L
        lastHandleAt = nowMs
        if (wasPlugged == false && plugged) History.addChargeSession(this)
        wasPlugged = plugged
        if (charging && temp >= 40f) pendingHotMs += elapsed // 뜨거운 채로 충전
        if (plugged && pct >= 95) pendingFullMs += elapsed   // 거의 다 찼는데 계속 꽂혀 있음
        if (pendingHotMs >= 60_000 || pendingFullMs >= 60_000) {
            History.addChargeMinutes(this, (pendingHotMs / 60_000).toInt(), (pendingFullMs / 60_000).toInt())
            pendingHotMs %= 60_000
            pendingFullMs %= 60_000
        }
        History.recordTemp(this, temp)

        // 상태 알림 갱신 (값이 바뀔 때만)
        val text = String.format(Locale.KOREA, "배터리 %d%% · %.1f°C%s", pct, temp, if (charging) " · 충전 중" else "")
        if (text != lastStatusText) {
            lastStatusText = text
            notify(ID_STATUS, statusNotification(text))
        }

        // 홈 화면 위젯도 함께 갱신 (1분에 한 번까지)
        PhoneCareWidget.updateAll(this)

        // 충전 한도 알림: 충전할 때마다 한 번
        val limit = GuardSettings.chargeLimit(this)
        if (!charging) {
            chargeAlerted = false
        } else if (!chargeAlerted && pct >= limit) {
            chargeAlerted = true
            val msg = if (limit >= 100) "완충됐어요. 충전기를 분리해 주세요." else "${limit}%까지 충전됐어요. 지금 분리하면 배터리 수명이 길어져요."
            notify(ID_CHARGE, alertNotification("🔋 충전 완료 ($pct%)", msg))
        }

        // 과열 알림: 10분에 한 번
        val heat = GuardSettings.heatLimit(this)
        val now = System.currentTimeMillis()
        if (temp >= heat && now - lastHeatAlert > HEAT_COOLDOWN_MS) {
            lastHeatAlert = now
            val msg = if (charging) {
                "충전 중 발열이 커요. 충전 중에는 게임·영상 사용을 멈추고, 두꺼운 케이스를 빼보세요."
            } else {
                "무거운 앱 사용을 잠시 멈추고 서늘한 곳에서 식혀주세요."
            }
            notify(ID_HEAT, alertNotification(String.format(Locale.KOREA, "🔥 배터리 온도 %.1f°C", temp), msg))
        }
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun statusNotification(text: String): Notification =
        NotificationCompat.Builder(this, CH_STATUS)
            .setSmallIcon(R.drawable.ic_stat_battery)
            .setContentTitle("폰케어 배터리 지킴이")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(openAppIntent())
            .build()

    private fun alertNotification(title: String, text: String): Notification =
        NotificationCompat.Builder(this, CH_ALERT)
            .setSmallIcon(R.drawable.ic_stat_battery)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openAppIntent())
            .build()

    private fun notify(id: Int, n: Notification) {
        try {
            getSystemService(NotificationManager::class.java).notify(id, n)
        } catch (e: SecurityException) {
            // 알림 권한이 꺼져 있음
        }
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_STATUS, "배터리 지킴이 상태", NotificationManager.IMPORTANCE_MIN).apply {
                description = "백그라운드 감시 중임을 표시하는 조용한 알림"
                setShowBadge(false)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_ALERT, "충전·과열 알림", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "충전 한도 도달, 배터리 과열 알림"
            },
        )
    }
}

/** 재부팅·앱 업데이트 후 자동으로 다시 시작 */
class GuardBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        if (GuardSettings.isEnabled(context)) GuardSettings.start(context)
    }
}
