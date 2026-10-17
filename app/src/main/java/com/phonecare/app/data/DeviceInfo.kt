package com.phonecare.app.data

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs

data class BatteryInfo(
    val level: Int,            // 0~100, 알 수 없으면 -1
    val temperatureC: Float,
    val health: String,
    val healthGood: Boolean,
    val charging: Boolean,
    val plugged: String,
)

data class MemoryInfoData(val total: Long, val available: Long, val lowMemory: Boolean)

data class StorageInfoData(val total: Long, val free: Long) {
    val used: Long get() = total - free
    val usedRatio: Float get() = if (total > 0) used.toFloat() / total else 0f
}

object DeviceInfo {

    fun battery(context: Context): BatteryInfo? {
        // ACTION_BATTERY_CHANGED는 "고정(sticky)" 방송이라 수신기 없이도 마지막 상태를 바로 읽을 수 있음
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return null
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val pct = if (level >= 0 && scale > 0) level * 100 / scale else -1
        val temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f
        val healthCode = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)
        val health = when (healthCode) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "양호"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "과열"
            BatteryManager.BATTERY_HEALTH_DEAD -> "수명 다함"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "과전압"
            BatteryManager.BATTERY_HEALTH_COLD -> "저온"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "이상 감지"
            else -> "알 수 없음"
        }
        val healthGood = healthCode == BatteryManager.BATTERY_HEALTH_GOOD ||
            healthCode == BatteryManager.BATTERY_HEALTH_UNKNOWN
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        val plugged = when (intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)) {
            BatteryManager.BATTERY_PLUGGED_AC -> "충전기"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "무선 충전"
            else -> "연결 안 됨"
        }
        return BatteryInfo(pct, temp, health, healthGood, charging, plugged)
    }

    fun memory(context: Context): MemoryInfoData {
        val am = context.getSystemService(ActivityManager::class.java)
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        return MemoryInfoData(mi.totalMem, mi.availMem, mi.lowMemory)
    }

    fun storage(): StorageInfoData {
        val stat = StatFs(Environment.getDataDirectory().path)
        return StorageInfoData(stat.totalBytes, stat.availableBytes)
    }

    /**
     * 0~100 점수. 실제로 체감 속도에 영향을 주는 항목 위주로 감점.
     * (저장공간이 거의 꽉 차면 느려지고, 고온이면 성능이 제한됨)
     */
    fun score(b: BatteryInfo?, m: MemoryInfoData?, s: StorageInfoData?): Int {
        var score = 100
        if (s != null && s.total > 0) {
            val used = s.usedRatio
            score -= when {
                used > 0.9f -> 35
                used > 0.8f -> 15
                used > 0.7f -> 5
                else -> 0
            }
        }
        if (m != null && m.total > 0) {
            val avail = m.available.toFloat() / m.total
            score -= when {
                m.lowMemory || avail < 0.1f -> 10
                avail < 0.2f -> 5
                else -> 0
            }
        }
        if (b != null) {
            score -= when {
                b.temperatureC >= 42f -> 20
                b.temperatureC >= 38f -> 8
                else -> 0
            }
            if (!b.healthGood) score -= 15
        }
        return score.coerceIn(0, 100)
    }
}
