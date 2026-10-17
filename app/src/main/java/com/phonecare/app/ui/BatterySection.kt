package com.phonecare.app.ui

import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.core.content.ContextCompat
import com.phonecare.app.service.GuardSettings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.phonecare.app.data.AppUsage
import com.phonecare.app.data.BatteryLive
import com.phonecare.app.data.BatteryMonitor
import com.phonecare.app.data.CpuInfo
import com.phonecare.app.data.UsageItem
import com.phonecare.app.util.openSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale

private const val HISTORY_SIZE = 90 // 2초 간격 → 약 3분

@Composable
fun BatterySection(resumeTick: Int) {
    val context = LocalContext.current
    var live by remember { mutableStateOf<BatteryLive?>(null) }
    var tempHistory by remember { mutableStateOf(listOf<Float>()) }
    var powerHistory by remember { mutableStateOf(listOf<Float>()) }
    var hasUsage by remember { mutableStateOf(AppUsage.hasUsageAccess(context)) }
    var topApps by remember { mutableStateOf<List<UsageItem>?>(null) }
    var thermalLevel by remember { mutableStateOf(0) }

    // 배터리 실시간 측정 (2초)
    LaunchedEffect(resumeTick) {
        while (true) {
            val b = BatteryMonitor.read(context)
            live = b
            if (b != null) {
                tempHistory = (tempHistory + b.tempC).takeLast(HISTORY_SIZE)
                b.powerW?.let { p -> powerHistory = (powerHistory + p).takeLast(HISTORY_SIZE) }
            }
            thermalLevel = CpuInfo.thermal(context)?.level ?: 0
            delay(2000)
        }
    }
    // 오늘 많이 쓴 앱 (1분마다 갱신)
    LaunchedEffect(resumeTick) {
        hasUsage = AppUsage.hasUsageAccess(context)
        while (hasUsage) {
            topApps = withContext(Dispatchers.IO) { AppUsage.loadTodayUsage(context) }
            delay(60_000)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GuardCard()
        val b = live
        if (b == null) {
            Text("배터리 정보를 읽을 수 없어요.")
        } else {
            // ----- 실시간 상태 -----
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("배터리 ${b.level}%", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            if (b.charging) "충전 중 (${b.plugged})" else "사용 중",
                            color = if (b.charging) ScoreGood else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    SoftBar(
                        progress = { (b.level / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp),
                        color = when {
                            b.level <= 15 -> ScoreBad
                            b.level <= 30 -> ScoreWarn
                            else -> ScoreGood
                        },
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                    b.remainingMin?.let { m ->
                        Text(
                            (if (b.charging) "완충까지 약 " else "현재 사용량 기준 약 ") + formatMinutes(m) +
                                (if (b.charging) "" else " 사용 가능"),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    InfoGrid(
                        listOf(
                            "온도" to String.format(Locale.US, "%.1f°C", b.tempC),
                            (if (b.charging) "충전 전력" else "소모 전력") to
                                (b.powerW?.let { String.format(Locale.US, "%.2f W", it) } ?: "지원 안 함"),
                            "전류" to (b.currentMa?.let { "$it mA" } ?: "지원 안 함"),
                            "전압" to String.format(Locale.US, "%.2f V", b.voltageMv / 1000f),
                            "상태" to b.health,
                            "충전 사이클" to (b.cycleCount?.let { "${it}회" } ?: "확인 불가"),
                        ),
                    )
                }
            }

            // ----- 발열 진단 -----
            val diagnosis = heatDiagnosis(b, thermalLevel)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("발열 진단", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(diagnosis.first, color = diagnosis.second, fontWeight = FontWeight.Bold)
                    Text("온도 변화 (최근 약 3분)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Sparkline(tempHistory, color = if (b.tempC >= 40f) ScoreBad else ScoreWarn, unit = "°C")
                    if (powerHistory.isNotEmpty()) {
                        Text(
                            if (b.charging) "충전 전력 변화" else "소모 전력 변화",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Sparkline(powerHistory, color = MaterialTheme.colorScheme.primary, unit = "W")
                    }
                }
            }
        }

        // ----- 오늘 많이 쓴 앱 -----
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("오늘 많이 쓴 앱", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "화면 사용 시간이 길수록 배터리 소모와 발열이 커요. 게임·동영상·지도·카메라 앱은 '고부하'로 표시돼요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!hasUsage) {
                    OutlinedButton(onClick = { AppUsage.openUsageAccessSettings(context) }) {
                        Text("사용 기록 접근 허용하기")
                    }
                } else {
                    val apps = topApps
                    when {
                        apps == null -> Text("불러오는 중…")
                        apps.isEmpty() -> Text("오늘 사용 기록이 아직 없어요.")
                        else -> {
                            val max = apps.first().foregroundMs.coerceAtLeast(1)
                            apps.forEach { UsageRow(it, it.foregroundMs.toFloat() / max) }
                        }
                    }
                }
                OutlinedButton(
                    onClick = { context.openSettings(Intent(Intent.ACTION_POWER_USAGE_SUMMARY)) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("시스템 배터리 사용량(앱별 %) 보기")
                }
            }
        }
    }
}

@Composable
private fun InfoGrid(items: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { (label, value) ->
                    Column(Modifier.weight(1f)) {
                        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun UsageRow(app: UsageItem, ratio: Float) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val icon = app.icon
        if (icon != null) {
            Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(36.dp))
        } else {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    app.label,
                    Modifier.weight(1f, fill = false),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (app.heavyLabel != null) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "고부하·${app.heavyLabel}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(ScoreWarn)
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    formatMinutes(app.foregroundMs / 60_000),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SoftBar(
                progress = { ratio.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp),
                color = if (app.heavy) ScoreWarn else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
    }
}

/** 간단한 실시간 선 그래프 */
@Composable
private fun Sparkline(values: List<Float>, color: Color, unit: String) {
    if (values.size < 2) {
        Text("측정 중…", style = MaterialTheme.typography.bodySmall)
        return
    }
    val min = values.min()
    val max = values.max()
    val track = MaterialTheme.colorScheme.surfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(
            Modifier
                .weight(1f)
                .height(56.dp),
        ) {
            val range = (max - min).takeIf { it > 0.0001f } ?: 1f
            val stepX = size.width / (HISTORY_SIZE - 1)
            val offsetX = size.width - stepX * (values.size - 1)
            drawLine(track, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 2f)
            val path = Path()
            values.forEachIndexed { i, v ->
                val x = offsetX + i * stepX
                val y = size.height - ((v - min) / range) * (size.height - 8f) - 4f
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color, style = Stroke(width = 5f, cap = StrokeCap.Round))
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(String.format(Locale.US, "%.1f%s", max, unit), style = MaterialTheme.typography.labelSmall)
            Text(String.format(Locale.US, "%.1f%s", min, unit), style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun heatDiagnosis(b: BatteryLive, thermalLevel: Int): Pair<String, Color> = when {
    b.tempC >= 45f || thermalLevel >= 4 ->
        "매우 뜨거워요! 사용을 멈추고 충전기를 분리해 식혀주세요." to ScoreBad
    b.tempC >= 40f || thermalLevel >= 3 ->
        (if (b.charging) "충전 중 발열이 커요. 충전 중엔 게임·영상 사용을 줄이세요." else
            "발열이 커요. 아래 '고부하' 앱 사용을 잠시 줄여보세요.") to ScoreBad
    b.tempC >= 37f || thermalLevel >= 2 ->
        (if (b.charging) "충전 중이라 조금 따뜻해요. 정상 범위예요." else "조금 따뜻해요. 무거운 앱을 쓰는 중일 수 있어요.") to ScoreWarn
    else -> "정상 온도예요." to ScoreGood
}

private fun formatMinutes(min: Long): String = when {
    min < 1 -> "1분 미만"
    min < 60 -> "${min}분"
    else -> "${min / 60}시간 ${min % 60}분"
}

/** 충전·과열 알림 켜기/끄기 + 기준 설정 */
@Composable
private fun GuardCard() {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(GuardSettings.isEnabled(context)) }
    var chargeLimit by remember { mutableStateOf(GuardSettings.chargeLimit(context)) }
    var heatLimit by remember { mutableStateOf(GuardSettings.heatLimit(context)) }

    fun turnOn() {
        GuardSettings.setEnabled(context, true)
        GuardSettings.start(context)
        enabled = true
    }

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            turnOn()
        } else {
            Toast.makeText(context, "알림 권한이 있어야 충전·과열 알림을 보낼 수 있어요", Toast.LENGTH_LONG).show()
        }
    }

    PremiumCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge("🛎️", ScoreWarn, 34.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("충전·과열 알림", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (enabled) "앱을 꺼도 계속 지켜봐요" else "꺼져 있어요",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = { on ->
                    if (on) {
                        val needAsk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                            PackageManager.PERMISSION_GRANTED
                        if (needAsk) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else turnOn()
                    } else {
                        GuardSettings.setEnabled(context, false)
                        GuardSettings.stop(context)
                        enabled = false
                    }
                },
            )
        }
        if (enabled) {
            Text("충전 한도", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GuardSettings.chargeOptions.forEach { v ->
                    FilterChip(
                        selected = chargeLimit == v,
                        onClick = {
                            chargeLimit = v
                            GuardSettings.setChargeLimit(context, v)
                        },
                        label = { Text("$v%") },
                    )
                }
            }
            Text("과열 기준", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GuardSettings.heatOptions.forEach { v ->
                    FilterChip(
                        selected = heatLimit == v,
                        onClick = {
                            heatLimit = v
                            GuardSettings.setHeatLimit(context, v)
                        },
                        label = { Text("$v°C") },
                    )
                }
            }
            Text(
                "배터리는 80% 근처에서 멈추면 수명이 오래가요. 알림이 늦게 오거나 멈추면 아래 버튼으로 '배터리 최적화 제외'를 해주세요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = {
                try {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:${context.packageName}"),
                        ),
                    )
                } catch (e: Exception) {
                    context.openSettings(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }
            }) { Text("배터리 최적화 제외하기") }
        }
    }
}
