package com.phonecare.app.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.phonecare.app.data.AppUsage
import com.phonecare.app.data.DayRecord
import com.phonecare.app.data.History
import com.phonecare.app.data.UsageItem
import com.phonecare.app.service.GuardSettings
import com.phonecare.app.util.formatBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

private val dayKeyFmt get() = SimpleDateFormat("yyyyMMdd", Locale.US)
private val weekdayFmt get() = SimpleDateFormat("E", Locale.KOREA)
private val monthDayFmt get() = SimpleDateFormat("M/d", Locale.KOREA)

private fun weekday(key: String): String = try {
    weekdayFmt.format(dayKeyFmt.parse(key)!!)
} catch (e: Exception) {
    ""
}

private fun monthDay(key: String): String = try {
    monthDayFmt.format(dayKeyFmt.parse(key)!!)
} catch (e: Exception) {
    key
}

private data class ReportData(val days: List<DayRecord>, val totalFreed: Long, val apps: List<UsageItem>)

/** 지난 7일 요약 */
@Composable
fun ReportScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val brand = LocalBrand.current
    val data by produceState<ReportData?>(null) {
        value = withContext(Dispatchers.IO) {
            ReportData(
                History.lastDays(context, 7),
                History.totalFreed(context),
                if (AppUsage.hasUsageAccess(context)) AppUsage.loadUsage(context, 7, 5) else emptyList(),
            )
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(start = 4.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로") }
            Column(Modifier.weight(1f)) {
                Text("주간 리포트", style = MaterialTheme.typography.titleLarge)
                data?.days?.let {
                    Text(
                        "${monthDay(it.first().date)} ~ ${monthDay(it.last().date)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        val d = data
        if (d == null) {
            Text("기록을 불러오는 중…", Modifier.padding(16.dp))
            return@Column
        }
        val weekFreed = d.days.sumOf { it.freedBytes }.coerceAtLeast(0)
        val temps = d.days.mapNotNull { it.tempAvg }
        val maxTemp = d.days.mapNotNull { it.tempMax }.maxOrNull()
        val scores = d.days.mapNotNull { it.score }
        val hasAnything = weekFreed > 0 || temps.isNotEmpty() || scores.isNotEmpty()

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!hasAnything) {
                PremiumCard(Modifier.fillMaxWidth()) {
                    Text("아직 기록이 적어요", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "폰케어를 며칠 쓰면 정리한 용량, 온도, 점수 변화가 여기에 쌓여요. 충전·과열 알림을 켜두면 앱을 안 열어도 기록돼요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // 요약 타일
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    "🧹", brand.start, "이번 주 확보", formatBytes(weekFreed),
                    "누적 ${formatBytes(d.totalFreed)}", null, Modifier.weight(1f),
                )
                StatTile(
                    "🌡️", if ((maxTemp ?: 0f) >= 42f) ScoreBad else ScoreWarn, "평균 온도",
                    if (temps.isEmpty()) "-" else String.format(Locale.US, "%.1f°", temps.average()),
                    maxTemp?.let { String.format(Locale.US, "최고 %.1f°", it) } ?: "기록 없음", null, Modifier.weight(1f),
                )
            }
            if (scores.isNotEmpty()) {
                val change = scores.last() - scores.first()
                PremiumCard(Modifier.fillMaxWidth()) {
                    SectionHeader(
                        "📈", brand.end, "관리 점수", "${scores.last()}점",
                        when {
                            change > 0 -> ScoreGood
                            change < 0 -> ScoreBad
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                    )
                    Text(
                        when {
                            scores.size < 2 -> "기록이 쌓이면 점수 변화를 보여드려요."
                            change > 0 -> "이번 주 ${change}점 올랐어요. 잘 관리하고 계세요! 👍"
                            change < 0 -> "이번 주 ${-change}점 내려갔어요. 홈의 '점수 근거 보기'에서 원인을 확인하세요."
                            else -> "이번 주 점수가 그대로예요."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    DayBars(d.days, { it.score?.toFloat() ?: 0f }, 100f, brand.end) { it.score?.toString() ?: "" }
                }
            }

            // 요일별 정리량
            PremiumCard(Modifier.fillMaxWidth()) {
                SectionHeader("🗑️", brand.start, "요일별 정리한 용량")
                val maxFreed = d.days.maxOf { it.freedBytes }.coerceAtLeast(1).toFloat()
                DayBars(d.days, { it.freedBytes.coerceAtLeast(0).toFloat() }, maxFreed, brand.start) {
                    if (it.freedBytes > 0) formatBytes(it.freedBytes).replace(" ", "") else ""
                }
            }

            // 온도
            if (temps.isNotEmpty()) {
                PremiumCard(Modifier.fillMaxWidth()) {
                    SectionHeader("🌡️", ScoreWarn, "요일별 최고 온도")
                    DayBars(d.days, { (it.tempMax ?: 0f) - 20f }, 30f, ScoreWarn) {
                        it.tempMax?.let { t -> String.format(Locale.US, "%.0f°", t) } ?: ""
                    }
                    if ((maxTemp ?: 0f) >= 42f) {
                        Text(
                            "42°C를 넘은 날이 있어요. 충전 중 게임·영상은 피하고 두꺼운 케이스를 빼보세요.",
                            style = MaterialTheme.typography.bodySmall,
                            color = ScoreBad,
                        )
                    }
                }
            }

            // 충전 습관
            PremiumCard(Modifier.fillMaxWidth()) {
                SectionHeader("🔋", ScoreGood, "충전 습관")
                if (!GuardSettings.isEnabled(context)) {
                    Text(
                        "성능 → 배터리·발열에서 '충전·과열 알림'을 켜면 충전 습관도 기록돼요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    val sessions = d.days.sumOf { it.chargeCount }
                    val hot = d.days.sumOf { it.hotChargeMin }
                    val full = d.days.sumOf { it.fullPluggedMin }
                    HabitRow("충전 횟수", "${sessions}회", null)
                    HabitRow("뜨거운 채로 충전 (40°C+)", minutesText(hot), if (hot >= 30) ScoreBad else null)
                    HabitRow("95% 넘은 채로 꽂아둔 시간", minutesText(full), if (full >= 300) ScoreWarn else null)
                    Text(
                        chargeAdvice(hot, full),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // 많이 쓴 앱
            if (d.apps.isNotEmpty()) {
                PremiumCard(Modifier.fillMaxWidth()) {
                    SectionHeader("📱", brand.end, "이번 주 많이 쓴 앱")
                    d.apps.forEach { app ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            app.icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(28.dp)) }
                            Spacer(Modifier.width(10.dp))
                            Text(app.label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(minutesText((app.foregroundMs / 60_000).toInt()), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            OutlinedButton(onClick = { shareReport(context, d, weekFreed, temps, maxTemp, scores) }, modifier = Modifier.fillMaxWidth()) {
                Text("📤 리포트 공유하기")
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** 요일별 막대 그래프 */
@Composable
private fun DayBars(
    days: List<DayRecord>,
    value: (DayRecord) -> Float,
    max: Float,
    color: Color,
    label: (DayRecord) -> String,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(130.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        days.forEach { day ->
            val ratio = (value(day) / max).coerceIn(0f, 1f)
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Text(label(day), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .fillMaxHeight(ratio.coerceAtLeast(0.02f))
                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                        .background(if (ratio > 0f) color else MaterialTheme.colorScheme.surfaceVariant),
                )
                Text(
                    weekday(day.date),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun HabitRow(label: String, value: String, warn: Color?) {
    Row {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, fontWeight = FontWeight.Bold, color = warn ?: MaterialTheme.colorScheme.onSurface)
    }
}

private fun minutesText(min: Int): String = when {
    min < 60 -> "${min}분"
    else -> "${min / 60}시간 ${min % 60}분"
}

private fun chargeAdvice(hotMin: Int, fullMin: Int): String = when {
    hotMin >= 30 -> "뜨거운 채로 충전한 시간이 길어요. 충전 중에는 무거운 앱을 쓰지 말고, 서늘한 곳에서 충전하세요."
    fullMin >= 300 -> "다 찬 뒤에도 오래 꽂아둔 편이에요. 충전 한도 알림을 80~85%로 맞추면 배터리 수명이 길어져요."
    else -> "좋은 충전 습관이에요. 지금처럼 유지하세요!"
}

private fun shareReport(
    context: Context,
    d: ReportData,
    weekFreed: Long,
    temps: List<Float>,
    maxTemp: Float?,
    scores: List<Int>,
) {
    val text = buildString {
        append("📊 폰케어 주간 리포트 (${monthDay(d.days.first().date)} ~ ${monthDay(d.days.last().date)})\n\n")
        append("🧹 이번 주 확보: ${formatBytes(weekFreed)} (누적 ${formatBytes(d.totalFreed)})\n")
        if (temps.isNotEmpty()) {
            append(String.format(Locale.US, "🌡️ 평균 온도 %.1f°C · 최고 %.1f°C\n", temps.average(), maxTemp ?: 0f))
        }
        if (scores.isNotEmpty()) append("📈 관리 점수 ${scores.last()}점\n")
        if (d.apps.isNotEmpty()) {
            append("📱 많이 쓴 앱: ")
            append(d.apps.take(3).joinToString(", ") { it.label })
            append('\n')
        }
    }
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, "리포트 공유").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

@Suppress("unused")
private val unusedState = mutableStateOf(0)

@Suppress("unused")
@Composable
private fun unusedRemember() = remember { 0 }
