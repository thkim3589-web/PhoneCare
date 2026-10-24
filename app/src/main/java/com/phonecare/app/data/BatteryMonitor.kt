package com.phonecare.app.data

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import kotlin.math.abs

data class BatteryLive(
    val level: Int,
    val charging: Boolean,
    val plugged: String,
    val tempC: Float,
    val voltageMv: Int,
    val currentMa: Int?,        // 절대값(mA). 기기가 지원하지 않으면 null
    val powerW: Float?,         // 절대값(W)
    val health: String,
    val healthGood: Boolean,
    val technology: String,
    val cycleCount: Int?,       // 안드로이드 14+ 일부 기기
    val remainingMin: Long?,    // 충전 중: 완충까지 / 사용 중: 방전까지 (추정)
    val rawCurrent: Int,        // 기기가 보고한 원본 전류 값 (단위는 기기마다 다름)
    val rawCounter: Int,        // 기기가 보고한 원본 남은 용량 값
    val unitNote: String,       // 원본 값을 어떤 단위로 해석했는지
)

object BatteryMonitor {

    fun read(context: Context): BatteryLive? {
        val basic = DeviceInfo.battery(context) ?: return null
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
        val bm = context.getSystemService(BatteryManager::class.java)

        val voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
        val technology = intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "-"

        // 전류: 대부분 µA 단위지만 일부 기기는 mA로 보고함 → 값 크기로 판별
        val raw = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: Int.MIN_VALUE
        // 전류 단위: 대부분 µA, 일부 기기는 mA. 20,000을 넘는 값이 한 번이라도 나오면 µA 기기로 기억해서
        // 화면이 꺼진 상태처럼 아주 작은 전류도 잘못 읽지 않게 함
        val prefs = context.getSharedPreferences("battery_unit", Context.MODE_PRIVATE)
        if (raw != Int.MIN_VALUE && abs(raw) >= 20_000 && !prefs.getBoolean("micro", false)) {
            prefs.edit().putBoolean("micro", true).apply()
        }
        val micro = prefs.getBoolean("micro", false)
        val currentMa: Int? = when {
            raw == Int.MIN_VALUE || raw == 0 -> null
            micro -> abs(raw) / 1000
            else -> abs(raw)
        }
        val powerW = if (currentMa != null && voltage > 0) currentMa * voltage / 1_000_000f else null

        val cycle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            intent.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1).takeIf { it >= 0 }
        } else {
            null
        }

        val counter = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER) ?: Int.MIN_VALUE
        val remaining: Long? = if (basic.charging) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                bm?.computeChargeTimeRemaining()?.takeIf { it > 0 }?.let { it / 60_000 }
            } else {
                null
            }
        } else {
            // 남은 용량(µAh) ÷ 현재 소모 전류 → 남은 시간
            if (counter > 0 && currentMa != null && currentMa > 50) {
                val mah = if (counter > 100_000) counter / 1000 else counter
                (mah * 60L) / currentMa
            } else {
                null
            }
        }

        return BatteryLive(
            level = basic.level,
            charging = basic.charging,
            plugged = basic.plugged,
            tempC = basic.temperatureC,
            voltageMv = voltage,
            currentMa = currentMa,
            powerW = powerW,
            health = basic.health,
            healthGood = basic.healthGood,
            technology = technology,
            cycleCount = cycle,
            remainingMin = remaining,
            rawCurrent = raw,
            rawCounter = counter,
            unitNote = if (micro) "µA로 해석" else "mA로 해석 (µA 값이 아직 안 나옴)",
        )
    }
}
