package com.phonecare.app.data

import android.content.Context
import com.phonecare.app.util.DAY_MS
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 하루치 기록 */
data class DayRecord(
    val date: String,          // yyyyMMdd
    val freedBytes: Long,      // 휴지통으로 보낸 용량 (복원하면 빠짐)
    val freedCount: Int,
    val tempMax: Float?,       // 그날 배터리 최고 온도
    val tempAvg: Float?,
    val score: Int?,           // 그날 마지막 관리 점수
    val storagePct: Int?,      // 그날 마지막 저장공간 사용률
    val hotChargeMin: Int,     // 40°C 넘는 상태로 충전한 시간(분)
    val fullPluggedMin: Int,   // 95% 넘은 채로 충전기에 꽂아둔 시간(분)
    val chargeCount: Int,      // 충전기 연결 횟수
)

/**
 * 주간 리포트용 기록 저장소 (폰 안의 파일 하나, 최근 60일만 보관).
 * 여러 곳(화면·배터리 감시 서비스)에서 쓰므로 동기화해서 접근.
 */
object History {

    private const val FILE = "history.json"
    private const val KEEP_DAYS = 60
    private val lock = Any()
    private val fmt get() = SimpleDateFormat("yyyyMMdd", Locale.US)

    private fun today() = fmt.format(Date())

    private fun file(c: Context) = File(c.filesDir, FILE)

    private fun read(c: Context): JSONObject = try {
        val f = file(c)
        if (f.exists()) JSONObject(f.readText()) else JSONObject()
    } catch (e: Exception) {
        JSONObject()
    }

    private fun write(c: Context, root: JSONObject) {
        val cutoff = fmt.format(Date(System.currentTimeMillis() - KEEP_DAYS * DAY_MS))
        val days = root.optJSONObject("days")
        days?.keys()?.asSequence()?.toList()?.filter { it < cutoff }?.forEach { days.remove(it) }
        try {
            file(c).writeText(root.toString())
        } catch (e: Exception) {
        }
    }

    /** 오늘 날짜 항목을 꺼내서 수정 */
    private fun updateToday(c: Context, block: (JSONObject, JSONObject) -> Unit) = synchronized(lock) {
        val root = read(c)
        val days = root.optJSONObject("days") ?: JSONObject().also { root.put("days", it) }
        val day = days.optJSONObject(today()) ?: JSONObject().also { days.put(today(), it) }
        block(root, day)
        write(c, root)
    }

    /** 휴지통으로 보냄(+) / 복원(-) */
    fun recordFreed(c: Context, bytes: Long, count: Int) = updateToday(c) { root, day ->
        day.put("freed", day.optLong("freed") + bytes)
        day.put("freedN", day.optInt("freedN") + count)
        root.put("totalFreed", (root.optLong("totalFreed") + bytes).coerceAtLeast(0))
    }

    private var lastTempAt = 0L

    /** 배터리 온도 (5분에 한 번까지만 기록) */
    fun recordTemp(c: Context, tempC: Float) {
        val now = System.currentTimeMillis()
        if (now - lastTempAt < 5 * 60 * 1000L) return
        lastTempAt = now
        updateToday(c) { _, day ->
            day.put("tMax", maxOf(day.optDouble("tMax", -100.0), tempC.toDouble()))
            day.put("tSum", day.optDouble("tSum", 0.0) + tempC)
            day.put("tN", day.optInt("tN") + 1)
        }
    }

    private var lastSnapAt = 0L

    /** 관리 점수·저장공간 (10분에 한 번까지만) */
    fun recordSnapshot(c: Context, score: Int, storagePct: Int) {
        val now = System.currentTimeMillis()
        if (now - lastSnapAt < 10 * 60 * 1000L) return
        lastSnapAt = now
        updateToday(c) { _, day ->
            day.put("score", score)
            day.put("storage", storagePct)
        }
    }

    /** 충전 습관: 분 단위 누적 (배터리 감시 서비스에서 호출) */
    fun addChargeMinutes(c: Context, hotMin: Int, fullMin: Int) {
        if (hotMin <= 0 && fullMin <= 0) return
        updateToday(c) { _, day ->
            day.put("hotChg", day.optInt("hotChg") + hotMin)
            day.put("fullPlug", day.optInt("fullPlug") + fullMin)
        }
    }

    fun addChargeSession(c: Context) = updateToday(c) { _, day ->
        day.put("chgN", day.optInt("chgN") + 1)
    }

    /** 최근 n일 (오래된 날 → 오늘 순). 기록이 없는 날도 빈 값으로 채움 */
    fun lastDays(c: Context, n: Int): List<DayRecord> = synchronized(lock) {
        val days = read(c).optJSONObject("days") ?: JSONObject()
        (n - 1 downTo 0).map { back ->
            val key = fmt.format(Date(System.currentTimeMillis() - back * DAY_MS))
            val d = days.optJSONObject(key)
            val tN = d?.optInt("tN") ?: 0
            DayRecord(
                date = key,
                freedBytes = d?.optLong("freed") ?: 0L,
                freedCount = d?.optInt("freedN") ?: 0,
                tempMax = d?.optDouble("tMax", Double.NaN)?.takeIf { !it.isNaN() && it > -50 }?.toFloat(),
                tempAvg = if (tN > 0) (d!!.optDouble("tSum") / tN).toFloat() else null,
                score = d?.takeIf { it.has("score") }?.optInt("score"),
                storagePct = d?.takeIf { it.has("storage") }?.optInt("storage"),
                hotChargeMin = d?.optInt("hotChg") ?: 0,
                fullPluggedMin = d?.optInt("fullPlug") ?: 0,
                chargeCount = d?.optInt("chgN") ?: 0,
            )
        }
    }

    /** 지금까지 폰케어로 확보한 총 용량 */
    fun totalFreed(c: Context): Long = synchronized(lock) { read(c).optLong("totalFreed") }
}
