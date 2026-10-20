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

    private const val SAMSUNG_CARE = "com.samsung.android.lool"
    private const val PREFS = "memory_screen"
    private const val KEY = "preferred"

    /** 디바이스 케어가 설치돼 있는지 */
    fun hasDeviceCare(context: Context): Boolean = AppUsage.isInstalled(context, SAMSUNG_CARE)

    /** 이름으로 본 '메모리 화면'일 가능성 점수 (높을수록 유력) */
    private fun memoryScore(cls: String): Int {
        val n = cls.lowercase()
        if (n.contains("plus")) return 0 // RAM Plus(가상 메모리) 설정 화면 제외
        val parts = n.split('.', '$')
        return when {
            parts.any { it == "ram" } -> 3
            parts.any { it.startsWith("ram") && (it.endsWith("activity") || it.endsWith("ui")) } -> 3
            parts.any { it.startsWith("memory") } -> 2
            else -> 0
        }
    }

    /** 디바이스 케어 안에서 열 수 있는 화면 목록 (메모리 화면 후보가 앞쪽) */
    fun deviceCareScreens(context: Context): List<String> = try {
        val pi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                SAMSUNG_CARE,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_ACTIVITIES.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(SAMSUNG_CARE, PackageManager.GET_ACTIVITIES)
        }
        pi.activities.orEmpty()
            .filter { it.exported && it.enabled }
            .map { it.name }
            .distinct()
            .sortedWith(compareByDescending<String> { memoryScore(it) }.thenBy { it })
    } catch (e: Exception) {
        emptyList()
    }

    /** 화면 이름을 짧게 (패키지 경로 제거) */
    fun shortName(cls: String): String = cls.substringAfterLast('.')

    fun savePreferred(context: Context, cls: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, cls).apply()
    }

    fun openComponent(context: Context, cls: String): Boolean = try {
        context.startActivity(Intent().setClassName(SAMSUNG_CARE, cls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: Exception) {
        false
    }

    /**
     * 메모리 화면 열기: ① 사용자가 직접 골라 저장한 화면 → ② 이름상 메모리 화면 후보 →
     * ③ 디바이스 케어 첫 화면 → ④ 앱 목록. 메모리 화면을 못 찾았으면 false
     */
    fun openMemoryScreen(context: Context): Boolean {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        if (saved != null && openComponent(context, saved)) return true
        val candidates = deviceCareScreens(context).filter { memoryScore(it) > 0 }
        for (cls in candidates) {
            if (openComponent(context, cls)) return true
        }
        openDeviceCareHome(context)
        return false
    }

    fun openDeviceCareHome(context: Context) {
        val launch = context.packageManager.getLaunchIntentForPackage(SAMSUNG_CARE)
        if (launch != null) {
            try {
                context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (e: Exception) {
            }
        }
        if (openComponent(context, "com.samsung.android.sm.ui.dashboard.SmartManagerDashBoardActivity")) return
        context.openSettings(Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS))
    }

    fun openDeviceCare(context: Context) {
        openMemoryScreen(context)
    }
}
