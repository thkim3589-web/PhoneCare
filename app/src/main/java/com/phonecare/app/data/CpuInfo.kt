package com.phonecare.app.data

import android.content.Context
import android.os.Build
import android.os.PowerManager
import java.io.File

data class CoreFreq(val index: Int, val curKhz: Long, val maxKhz: Long) {
    val readable: Boolean get() = curKhz > 0 && maxKhz > 0
    val ratio: Float get() = if (readable) (curKhz.toFloat() / maxKhz).coerceIn(0f, 1f) else 0f
}

data class ThermalInfo(val label: String, val level: Int) // level 0=정상 ~ 4=위험

object CpuInfo {

    private val coreDirPattern = Regex("cpu\\d+")

    fun socName(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            Build.SOC_MODEL.isNotBlank() && Build.SOC_MODEL != Build.UNKNOWN
        ) {
            "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}"
        } else {
            Build.HARDWARE
        }

    /** 코어별 현재/최대 클럭. 읽을 수 없는 값은 -1 */
    fun cores(): List<CoreFreq> {
        val dirs = File("/sys/devices/system/cpu").listFiles { f -> f.isDirectory && coreDirPattern.matches(f.name) }
        if (dirs.isNullOrEmpty()) {
            return (0 until Runtime.getRuntime().availableProcessors()).map { CoreFreq(it, -1, -1) }
        }
        return dirs.map { it.name.removePrefix("cpu").toInt() }
            .sorted()
            .map { i ->
                val base = "/sys/devices/system/cpu/cpu$i/cpufreq"
                CoreFreq(i, readLong("$base/scaling_cur_freq"), readLong("$base/cpuinfo_max_freq"))
            }
    }

    /** 클럭 기준 부하 추정(%) — 실제 사용률은 안드로이드 8부터 앱에서 읽을 수 없음 */
    fun estimatedLoad(cores: List<CoreFreq>): Int? {
        val valid = cores.filter { it.readable }
        if (valid.isEmpty()) return null
        return (valid.map { it.ratio }.average() * 100).toInt()
    }

    fun thermal(context: Context): ThermalInfo? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val pm = context.getSystemService(PowerManager::class.java) ?: return null
        return when (pm.currentThermalStatus) {
            PowerManager.THERMAL_STATUS_NONE -> ThermalInfo("정상", 0)
            PowerManager.THERMAL_STATUS_LIGHT -> ThermalInfo("약간 따뜻함", 1)
            PowerManager.THERMAL_STATUS_MODERATE -> ThermalInfo("따뜻함 — 성능이 조금 제한될 수 있음", 2)
            PowerManager.THERMAL_STATUS_SEVERE -> ThermalInfo("뜨거움 — 성능 제한 중", 3)
            else -> ThermalInfo("매우 뜨거움 — 사용을 멈추고 식혀주세요", 4)
        }
    }

    private fun readLong(path: String): Long = try {
        File(path).readText().trim().toLong()
    } catch (e: Exception) {
        -1L
    }
}
