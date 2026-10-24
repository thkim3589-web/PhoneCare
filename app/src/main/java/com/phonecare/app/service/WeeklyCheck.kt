package com.phonecare.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.phonecare.app.MainActivity
import com.phonecare.app.R
import com.phonecare.app.data.History
import com.phonecare.app.data.StorageScanner
import com.phonecare.app.data.TrashManager
import com.phonecare.app.util.formatBytes
import java.util.Calendar
import java.util.concurrent.TimeUnit

/** 주간 점검 알림 설정 + 예약 */
object WeeklyCheck {
    private const val PREFS = "weekly"
    private const val WORK = "weekly_check"
    const val EXTRA_OPEN = "open"
    const val OPEN_REPORT = "report"

    fun isEnabled(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("enabled", true)

    fun setEnabled(c: Context, on: Boolean) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("enabled", on).apply()
        if (on) schedule(c, replace = true) else WorkManager.getInstance(c).cancelUniqueWork(WORK)
    }

    /** 매주 일요일 저녁 7시쯤 실행되도록 예약 (이미 예약돼 있으면 그대로 둠) */
    fun schedule(c: Context, replace: Boolean = false) {
        if (!isEnabled(c)) return
        val now = Calendar.getInstance()
        val next = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
            set(Calendar.HOUR_OF_DAY, 19)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            if (!after(now)) add(Calendar.WEEK_OF_YEAR, 1)
        }
        val delay = next.timeInMillis - now.timeInMillis
        val req = PeriodicWorkRequestBuilder<WeeklyCheckWorker>(7, TimeUnit.DAYS)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        try {
            WorkManager.getInstance(c).enqueueUniquePeriodicWork(
                WORK,
                if (replace) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP,
                req,
            )
        } catch (e: Exception) {
        }
    }
}

/** 일주일에 한 번: 정리할 파일 용량과 이번 주 요약을 알림으로 */
class WeeklyCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val c = applicationContext
        if (!WeeklyCheck.isEnabled(c)) return Result.success()
        val cleanable = if (StorageScanner.hasAccess(c)) {
            try {
                StorageScanner.scan(c, com.phonecare.app.data.ProtectedFolders.list(c)) { }.sumOf { it.size }
            } catch (e: Exception) {
                0L
            }
        } else {
            0L
        }
        val trash = try {
            TrashManager.totalSize(c)
        } catch (e: Exception) {
            0L
        }
        val weekFreed = History.lastDays(c, 7).sumOf { it.freedBytes }.coerceAtLeast(0)

        val title = if (cleanable >= 200L * 1024 * 1024) {
            "🧹 정리할 파일 ${formatBytes(cleanable)}이 있어요"
        } else {
            "📊 폰케어 주간 리포트가 준비됐어요"
        }
        val body = buildString {
            append("이번 주 확보 ${formatBytes(weekFreed)}")
            if (trash >= 100L * 1024 * 1024) append(" · 휴지통 ${formatBytes(trash)}")
            append(" · 눌러서 리포트 보기")
        }
        notify(c, title, body)
        return Result.success()
    }

    private fun notify(c: Context, title: String, body: String) {
        val nm = c.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel("weekly", "주간 점검", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "일주일에 한 번 정리할 파일과 주간 리포트를 알려줘요"
                },
            )
        }
        val open = PendingIntent.getActivity(
            c, 20,
            Intent(c, MainActivity::class.java)
                .putExtra(WeeklyCheck.EXTRA_OPEN, WeeklyCheck.OPEN_REPORT)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(c, "weekly")
            .setSmallIcon(R.drawable.ic_stat_battery)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        try {
            nm.notify(1004, n)
        } catch (e: SecurityException) {
        }
    }
}
