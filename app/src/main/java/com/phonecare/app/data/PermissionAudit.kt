package com.phonecare.app.data

import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.phonecare.app.util.DAY_MS
import com.phonecare.app.util.daysAgo

enum class PermGroup(val label: String, val symbol: String, val perms: List<String>) {
    BG_LOCATION("항상 위치", "🛰️", listOf("android.permission.ACCESS_BACKGROUND_LOCATION")),
    LOCATION("위치", "📍", listOf("android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION")),
    CAMERA("카메라", "📷", listOf("android.permission.CAMERA")),
    MIC("마이크", "🎙️", listOf("android.permission.RECORD_AUDIO")),
    CONTACTS("연락처", "👥", listOf("android.permission.READ_CONTACTS")),
    SMS("문자", "💬", listOf("android.permission.READ_SMS", "android.permission.RECEIVE_SMS")),
    CALL_LOG("통화 기록", "📞", listOf("android.permission.READ_CALL_LOG")),
    MEDIA(
        "사진·파일", "🖼️",
        listOf(
            "android.permission.READ_MEDIA_IMAGES",
            "android.permission.READ_MEDIA_VIDEO",
            "android.permission.READ_EXTERNAL_STORAGE",
        ),
    ),
}

data class AppPerms(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap?,
    val groups: List<PermGroup>,
    val unusedDays: Long?, // 사용 기록 권한이 없으면 null
) {
    val isStale: Boolean get() = (unusedDays ?: 0) >= PermissionAudit.STALE_DAYS
}

/** 민감한 권한이 "허용된" 앱 목록 */
object PermissionAudit {

    const val STALE_DAYS = 30L

    fun load(context: Context): List<AppPerms> {
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(launcherIntent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(launcherIntent, 0)
        }
        val packages = resolved.map { it.activityInfo.packageName }.distinct().filter { it != context.packageName }

        val lastUsed: Map<String, UsageStats>? = if (AppUsage.hasUsageAccess(context)) {
            try {
                val now = System.currentTimeMillis()
                context.getSystemService(UsageStatsManager::class.java)
                    .queryAndAggregateUsageStats(now - AppUsage.LOOKBACK_DAYS * DAY_MS, now)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }

        return packages.mapNotNull { pkg ->
            try {
                val pi: PackageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS)
                }
                val ai = pi.applicationInfo ?: return@mapNotNull null
                val isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                    (ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
                if (isSystem) return@mapNotNull null

                val requested = pi.requestedPermissions ?: return@mapNotNull null
                val flags = pi.requestedPermissionsFlags ?: return@mapNotNull null
                val granted = HashSet<String>()
                for (i in requested.indices) {
                    if (i < flags.size && (flags[i] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0) {
                        granted.add(requested[i])
                    }
                }
                var groups = PermGroup.entries.filter { g -> g.perms.any { it in granted } }
                // "항상 위치"가 있으면 일반 "위치"는 중복 표시하지 않음
                if (PermGroup.BG_LOCATION in groups) groups = groups - PermGroup.LOCATION
                if (groups.isEmpty()) return@mapNotNull null

                val unused: Long? = lastUsed?.let { stats ->
                    val t = stats[pkg]?.lastTimeUsed ?: 0L
                    if (t > 0 && t >= pi.firstInstallTime) daysAgo(t) else daysAgo(pi.firstInstallTime)
                }
                val icon = try {
                    pm.getApplicationIcon(ai).toBitmap(96, 96).asImageBitmap()
                } catch (e: Exception) {
                    null
                }
                AppPerms(pkg, pm.getApplicationLabel(ai).toString(), icon, groups, unused)
            } catch (e: Exception) {
                null
            }
        }.sortedWith(
            compareByDescending<AppPerms> { it.isStale }
                .thenByDescending { it.groups.size }
                .thenBy { it.label },
        )
    }
}
