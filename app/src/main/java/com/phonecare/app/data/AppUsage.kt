package com.phonecare.app.data

import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.phonecare.app.util.DAY_MS
import com.phonecare.app.util.daysAgo
import com.phonecare.app.util.openSettings
import java.util.Calendar

data class AppItem(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap?,
    val lastUsed: Long,      // 0이면 조회 기간 동안 사용 기록 없음
    val installed: Long,
    val sizeBytes: Long,     // -1이면 알 수 없음
) {
    val neverUsed: Boolean get() = lastUsed <= 0L || lastUsed < installed
    val unusedDays: Long get() = if (neverUsed) daysAgo(installed) else daysAgo(lastUsed)
}

data class UsageItem(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap?,
    val foregroundMs: Long,
    val heavy: Boolean,       // 게임·동영상 등 고부하 앱
    val heavyLabel: String?,
)

object AppUsage {

    const val LOOKBACK_DAYS = 180

    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName,
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName,
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun openUsageAccessSettings(context: Context) {
        context.openSettings(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    }

    fun openUninstall(context: Context, packageName: String) {
        val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName"))
        context.openSettings(intent)
    }

    fun openAppInfo(context: Context, packageName: String) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
        context.openSettings(intent)
    }

    fun isInstalled(context: Context, packageName: String): Boolean = try {
        @Suppress("DEPRECATION")
        context.packageManager.getApplicationInfo(packageName, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    /** 사용자가 설치한 앱 목록 + 마지막 사용일 + 용량 (백그라운드에서 호출) */
    fun loadApps(context: Context): List<AppItem> {
        val pm = context.packageManager
        val usm = context.getSystemService(UsageStatsManager::class.java)
        val ssm = context.getSystemService(StorageStatsManager::class.java)
        val now = System.currentTimeMillis()
        val stats: Map<String, UsageStats> = try {
            usm.queryAndAggregateUsageStats(now - LOOKBACK_DAYS * DAY_MS, now)
        } catch (e: Exception) {
            emptyMap()
        }

        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(launcherIntent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(launcherIntent, 0)
        }
        val packages = resolved.map { it.activityInfo.packageName }
            .distinct()
            .filter { it != context.packageName }

        val iconSize = 96
        return packages.mapNotNull { pkg ->
            try {
                @Suppress("DEPRECATION")
                val ai = pm.getApplicationInfo(pkg, 0)
                val isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                    (ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
                if (isSystem) return@mapNotNull null

                @Suppress("DEPRECATION")
                val pi = pm.getPackageInfo(pkg, 0)
                val size = try {
                    val s = ssm.queryStatsForPackage(ai.storageUuid, pkg, Process.myUserHandle())
                    s.appBytes + s.dataBytes
                } catch (e: Exception) {
                    -1L
                }
                val icon = try {
                    pm.getApplicationIcon(ai).toBitmap(iconSize, iconSize).asImageBitmap()
                } catch (e: Exception) {
                    null
                }
                AppItem(
                    packageName = pkg,
                    label = pm.getApplicationLabel(ai).toString(),
                    icon = icon,
                    lastUsed = stats[pkg]?.lastTimeUsed ?: 0L,
                    installed = pi.firstInstallTime,
                    sizeBytes = size,
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    /** 오늘 0시부터 지금까지 앱별 화면 사용 시간 (긴 순) — 백그라운드에서 호출 */
    /** 기간별 화면 사용 시간 (오늘은 정확한 이벤트 집계, 7일 이상은 시스템 합계 사용) */
    fun loadUsage(context: Context, days: Int, limit: Int = 10): List<UsageItem> {
        if (days <= 1) return loadTodayUsage(context, limit)
        val usm = context.getSystemService(UsageStatsManager::class.java)
        val now = System.currentTimeMillis()
        val totals = try {
            usm.queryAndAggregateUsageStats(now - days * DAY_MS, now)
                .mapValues { it.value.totalTimeInForeground }
        } catch (e: Exception) {
            return emptyList()
        }
        return toUsageItems(context, totals, limit)
    }

    fun loadTodayUsage(context: Context, limit: Int = 10): List<UsageItem> {
        val usm = context.getSystemService(UsageStatsManager::class.java)
        val pm = context.packageManager
        val now = System.currentTimeMillis()
        val start = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val totals = HashMap<String, Long>()
        val resumedAt = HashMap<String, Long>()
        try {
            val events = usm.queryEvents(start, now)
            val e = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(e)
                val pkg = e.packageName ?: continue
                when (e.eventType) {
                    1 -> resumedAt[pkg] = e.timeStamp  // ACTIVITY_RESUMED
                    2, 23 -> {                          // ACTIVITY_PAUSED, ACTIVITY_STOPPED
                        val t = resumedAt.remove(pkg)
                        if (t != null) totals[pkg] = (totals[pkg] ?: 0L) + (e.timeStamp - t)
                    }
                }
            }
            // 지금 화면에 떠 있는 앱
            for ((pkg, t) in resumedAt) totals[pkg] = (totals[pkg] ?: 0L) + (now - t)
        } catch (ex: Exception) {
            return emptyList()
        }

        return toUsageItems(context, totals, limit)
    }

    private fun toUsageItems(context: Context, totals: Map<String, Long>, limit: Int): List<UsageItem> {
        val pm = context.packageManager
        return totals.entries
            .filter { it.key != context.packageName && it.value > 60_000 }
            .sortedByDescending { it.value }
            .mapNotNull { (pkg, ms) ->
                try {
                    @Suppress("DEPRECATION")
                    val ai = pm.getApplicationInfo(pkg, 0)
                    // 런처·시스템UI처럼 사용자가 "쓴" 게 아닌 시스템 앱 제외
                    if (pm.getLaunchIntentForPackage(pkg) == null) return@mapNotNull null
                    val heavyLabel = when (ai.category) {
                        ApplicationInfo.CATEGORY_GAME -> "게임"
                        ApplicationInfo.CATEGORY_VIDEO -> "동영상"
                        ApplicationInfo.CATEGORY_MAPS -> "지도·GPS"
                        ApplicationInfo.CATEGORY_IMAGE -> "카메라·사진"
                        else -> null
                    }
                    val icon = try {
                        pm.getApplicationIcon(ai).toBitmap(96, 96).asImageBitmap()
                    } catch (ex: Exception) {
                        null
                    }
                    UsageItem(pkg, pm.getApplicationLabel(ai).toString(), icon, ms, heavyLabel != null, heavyLabel)
                } catch (ex: Exception) {
                    null
                }
            }
            .take(limit)
    }
}
