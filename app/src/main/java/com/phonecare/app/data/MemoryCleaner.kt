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

    /** 삼성 디바이스 케어가 있으면 열고, 없으면 앱 목록 설정을 연다. */
    fun openDeviceCare(context: Context) {
        val samsung = Intent().setClassName(
            "com.samsung.android.lool",
            "com.samsung.android.sm.ui.dashboard.SmartManagerDashBoardActivity",
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(samsung)
        } catch (e: Exception) {
            context.openSettings(Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS))
        }
    }
}
