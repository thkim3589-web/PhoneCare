package com.phonecare.app.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.phonecare.app.data.BatteryInfo
import com.phonecare.app.data.DeviceInfo
import com.phonecare.app.data.MemoryInfoData
import com.phonecare.app.data.StorageInfoData
import com.phonecare.app.util.formatBytes
import kotlinx.coroutines.delay
import java.util.Locale

private data class Recommendation(
    val symbol: String,
    val tint: Color,
    val title: String,
    val desc: String,
    val tab: Int = -1,
)

@Composable
fun HomeScreen(resumeTick: Int, onNavigate: (Int) -> Unit) {
    val context = LocalContext.current
    var battery by remember { mutableStateOf<BatteryInfo?>(null) }
    var memory by remember { mutableStateOf<MemoryInfoData?>(null) }
    var storage by remember { mutableStateOf<StorageInfoData?>(null) }

    LaunchedEffect(resumeTick) {
        while (true) {
            battery = DeviceInfo.battery(context)
            memory = DeviceInfo.memory(context)
            storage = DeviceInfo.storage()
            // 주간 리포트용 기록 (내부에서 5~10분 간격으로만 저장)
            val b = battery
            val st = storage
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                if (b != null) com.phonecare.app.data.History.recordTemp(context, b.temperatureC)
                if (st != null) {
                    com.phonecare.app.data.History.recordSnapshot(
                        context, DeviceInfo.score(b, memory, st), (st.usedRatio * 100).toInt(),
                    )
                }
            }
            delay(5000)
        }
    }

    val score = DeviceInfo.score(battery, memory, storage)
    val brand = LocalBrand.current

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // ---------- 상단 ----------
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("폰케어", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            androidx.compose.material3.FilledTonalButton(onClick = { onNavigate(com.phonecare.app.NAV_GUIDE) }) {
                Text("📖 가이드")
            }
            androidx.compose.material3.IconButton(onClick = { onNavigate(com.phonecare.app.NAV_SETTINGS) }) {
                Text("⚙️", style = MaterialTheme.typography.titleLarge)
            }
        }

        // ---------- 히어로: 점수 ----------
        HeroScore(score, DeviceInfo.scoreItems(battery, memory, storage))

        // ---------- 지표 타일 2x2 ----------
        val s = storage
        val m = memory
        val b = battery
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(
                symbol = "💾", tint = brand.start, label = "저장공간",
                value = s?.let { "${(it.usedRatio * 100).toInt()}%" } ?: "-",
                sub = s?.let { "남은 ${formatBytes(it.free)}" } ?: "",
                progress = s?.usedRatio,
                modifier = Modifier.weight(1f),
                onClick = { onNavigate(2) },
            )
            val memRatio = if (m != null && m.total > 0) 1f - m.available.toFloat() / m.total else null
            StatTile(
                symbol = "🧠", tint = brand.end, label = "메모리",
                value = memRatio?.let { "${(it * 100).toInt()}%" } ?: "-",
                sub = m?.let { "여유 ${formatBytes(it.available)}" } ?: "",
                progress = memRatio,
                modifier = Modifier.weight(1f),
                onClick = { onNavigate(1) },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(
                symbol = "🔋", tint = ScoreGood, label = "배터리",
                value = b?.takeIf { it.level >= 0 }?.let { "${it.level}%" } ?: "-",
                sub = b?.let { if (it.charging) "충전 중 · ${it.plugged}" else "상태 ${it.health}" } ?: "",
                progress = b?.takeIf { it.level >= 0 }?.let { it.level / 100f },
                modifier = Modifier.weight(1f),
                onClick = { onNavigate(1) },
            )
            val temp = b?.temperatureC
            val tempColor = when {
                temp == null -> ScoreGood
                temp >= 42f -> ScoreBad
                temp >= 38f -> ScoreWarn
                else -> ScoreGood
            }
            StatTile(
                symbol = "🌡️", tint = tempColor, label = "온도",
                value = temp?.let { String.format(Locale.US, "%.1f°", it) } ?: "-",
                sub = when {
                    temp == null -> ""
                    temp >= 42f -> "뜨거움"
                    temp >= 38f -> "따뜻함"
                    else -> "정상"
                },
                progress = temp?.let { ((it - 20f) / 30f).coerceIn(0f, 1f) },
                modifier = Modifier.weight(1f),
                onClick = { onNavigate(1) },
            )
        }

        // ---------- 주간 리포트 바로가기 ----------
        var weekFreed by remember { mutableStateOf(0L) }
        LaunchedEffect(resumeTick) {
            weekFreed = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.phonecare.app.data.History.lastDays(context, 7).sumOf { it.freedBytes }.coerceAtLeast(0)
            }
        }
        PremiumCard(Modifier.fillMaxWidth(), onClick = { onNavigate(com.phonecare.app.NAV_REPORT) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge("📊", brand.end)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("주간 리포트", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (weekFreed > 0) "이번 주 ${formatBytes(weekFreed)} 확보 · 온도·점수·충전 습관" else "정리 기록·온도·점수·충전 습관 한눈에",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // ---------- 추천 조치 ----------
        val setupIncomplete = remember(resumeTick) { isSetupIncomplete(context) }
        var workFolders by remember { mutableStateOf(0) }
        LaunchedEffect(resumeTick) {
            val seen = context.getSharedPreferences("protect", android.content.Context.MODE_PRIVATE).getBoolean("suggest_seen", false)
            workFolders = if (seen || !com.phonecare.app.data.StorageScanner.hasAccess(context)) {
                0
            } else {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.phonecare.app.data.ProtectedFolders.suggestWorkFolders(context).size
                }
            }
        }
        var trashBytes by remember { mutableStateOf(0L) }
        LaunchedEffect(resumeTick) {
            trashBytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.phonecare.app.data.TrashManager.totalSize(context)
            }
        }
        val noWidget = remember(resumeTick) { !com.phonecare.app.widget.PhoneCareWidget.hasWidgets(context) }
        val recs = buildRecommendations(battery, storage, brand, setupIncomplete, noWidget, trashBytes, workFolders)
        PremiumCard(Modifier.fillMaxWidth()) {
            Text("추천 조치", style = MaterialTheme.typography.titleMedium)
            recs.forEachIndexed { i, rec ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                RecommendationRow(rec) { if (rec.tab >= 0) onNavigate(rec.tab) }
            }
        }

        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun HeroScore(score: Int, items: List<DeviceInfo.ScoreItem>) {
    val brand = LocalBrand.current
    var showDetail by remember { mutableStateOf(false) }
    val color = when {
        score >= 80 -> ScoreGood
        score >= 60 -> ScoreWarn
        else -> ScoreBad
    }
    val (label, message) = when {
        score >= 80 -> "아주 좋아요" to "휴대폰이 최적의 상태로 관리되고 있어요"
        score >= 60 -> "점검이 필요해요" to "아래 추천 조치를 따라 하면 더 쾌적해져요"
        else -> "관리가 필요해요" to "저장공간·발열을 먼저 확인해 주세요"
    }
    Box(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(
                Brush.linearGradient(
                    listOf(brand.start.copy(alpha = if (brand.isDark) 0.22f else 0.14f), brand.end.copy(alpha = if (brand.isDark) 0.18f else 0.10f)),
                ),
            )
            .clickable { showDetail = true }
            .padding(22.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScoreRing(score = score, color = color, modifier = Modifier.size(132.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AnimatedNumber(
                        value = score,
                        style = MaterialTheme.typography.headlineMedium.merge(NumberStyle).copy(fontSize = 40.sp),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text("/ 100", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(18.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("관리 점수", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(label, style = MaterialTheme.typography.titleLarge, color = color)
                Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("점수 근거 보기 ›", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
    }

    if (showDetail) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDetail = false },
            title = { Text("관리 점수 ${score}점 근거") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "100점에서 시작해 체감 속도에 영향을 주는 항목만 감점해요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    items.forEach { item ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(item.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                Text(
                                    if (item.penalty > 0) "-${item.penalty}점" else "감점 없음",
                                    fontWeight = FontWeight.Bold,
                                    color = if (item.penalty > 0) ScoreBad else ScoreGood,
                                )
                            }
                            Text(
                                "${item.state} · ${item.advice}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { showDetail = false }) { Text("확인") } },
        )
    }
}

@Composable
private fun RecommendationRow(rec: Recommendation, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = rec.tab >= 0) { onClick() }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(rec.symbol, rec.tint)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(rec.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(rec.desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (rec.tab >= 0) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun buildRecommendations(
    b: BatteryInfo?,
    s: StorageInfoData?,
    brand: BrandColors,
    setupIncomplete: Boolean,
    noWidget: Boolean,
    trashBytes: Long,
    workFolders: Int,
): List<Recommendation> {
    val list = mutableListOf<Recommendation>()
    if (setupIncomplete) {
        list.add(Recommendation("🔑", brand.start, "권한 설정 마저 하기", "일부 기능이 꺼져 있어요. 눌러서 한 번에 허용하세요", com.phonecare.app.NAV_SETUP))
    }
    if (s != null) {
        val pct = (s.usedRatio * 100).toInt()
        when {
            s.usedRatio > 0.9f -> list.add(Recommendation("🧹", ScoreBad, "저장공간 정리가 급해요", "$pct% 사용 중 · 속도 저하의 주원인이에요", 2))
            s.usedRatio > 0.8f -> list.add(Recommendation("🧹", ScoreWarn, "저장공간을 조금 비워주세요", "$pct% 사용 중", 2))
        }
    }
    if (workFolders > 0) {
        list.add(
            Recommendation(
                "💼", brand.start, "업무 폴더 ${workFolders}개를 찾았어요",
                "보호 폴더로 지정하면 정리할 때 실수로 지워지지 않아요", com.phonecare.app.NAV_PROTECT,
            ),
        )
    }
    if (trashBytes >= 100L * 1024 * 1024) {
        list.add(
            Recommendation(
                "🗑️", ScoreWarn, "휴지통 ${formatBytes(trashBytes)}",
                "지운 파일이 아직 공간을 차지해요. 비우면 바로 확보돼요", com.phonecare.app.NAV_TRASH,
            ),
        )
    }
    if (b != null) {
        if (b.temperatureC >= 40f) {
            list.add(Recommendation("🔥", ScoreBad, "발열이 높아요", String.format(Locale.US, "%.0f°C · 원인 앱을 확인해 보세요", b.temperatureC), 1))
        }
        if (!b.healthGood) {
            list.add(Recommendation("⚠️", ScoreBad, "배터리 상태: ${b.health}", "서비스센터 점검을 권장해요"))
        }
    }
    list.add(Recommendation("📦", brand.end, "안 쓰는 앱·앱 권한 점검", "오래 안 쓴 앱과 권한이 과한 앱을 확인해요", 3))
    list.add(Recommendation("⚡", brand.start, "최적화 체크리스트", "효과 있는 설정만 모았어요", 4))
    if (noWidget) {
        list.add(Recommendation("🧩", brand.end, "홈 화면에 위젯 추가", "앱을 열지 않아도 점수·배터리·온도를 바로 확인해요", com.phonecare.app.NAV_WIDGET))
    }
    return list
}
