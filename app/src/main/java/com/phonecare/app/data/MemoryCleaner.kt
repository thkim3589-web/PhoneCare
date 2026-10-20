package com.phonecare.app.data

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import com.phonecare.app.util.openSettings

object MemoryCleaner {

    /** 안드로이드 14부터는 다른 앱의 백그라운드 프로세스를 종료할 수 없음 */
    val canClean: Boolean get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE

    /** 백그라운드에 남아 있는 사용자 앱 프로세스를 정리하고, 요청한 앱 수를 돌려준다. */
    fun cleanBackground(context: Context): Int {
        val am = context.getSystemService(ActivityManager::class.java)
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val packages = pm.queryIntentActivities(launcherIntent, 0)
            .map { it.activityInfo.packageName }
            .distinct()
            .filter { it != context.packageName }
        var count = 0
        for (pkg in packages) {
            try {
                @Suppress("DEPRECATION")
                val ai = pm.getApplicationInfo(pkg, 0)
                if ((ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0) continue
                am.killBackgroundProcesses(pkg)
                count++
            } catch (e: PackageManager.NameNotFoundException) {
            } catch (e: SecurityException) {
            }
        }
        return count
    }

    /** 삼성 디바이스 케어 → 메모리 화면을 바로 연다. 안 되면 디바이스 케어 첫 화면 → 앱 목록 순으로 시도 */
    fun openMemoryScreen(context: Context) {
        val pkg = "com.samsung.android.lool"
        val candidates = listOf(
            "com.samsung.android.sm.ui.ram.RamActivity",
            "com.samsung.android.sm.ui.dashboard.SmartManagerDashBoardActivity",
        )
        for (cls in candidates) {
            try {
                context.startActivity(Intent().setClassName(pkg, cls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (e: Exception) {
            }
        }
        context.openSettings(Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS))
    }

    fun openDeviceCare(context: Context) = openMemoryScreen(context)
}
