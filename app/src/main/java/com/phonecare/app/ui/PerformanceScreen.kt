package com.phonecare.app.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.phonecare.app.data.CoreFreq
import com.phonecare.app.data.CpuInfo
import com.phonecare.app.data.DeviceInfo
import com.phonecare.app.data.MemoryCleaner
import com.phonecare.app.data.MemoryInfoData
import com.phonecare.app.data.ThermalInfo
import com.phonecare.app.util.formatBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun PerformanceScreen(resumeTick: Int) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val soc = remember { CpuInfo.socName() }

    var cores by remember { mutableStateOf<List<CoreFreq>>(emptyList()) }
    var thermal by remember { mutableStateOf<ThermalInfo?>(null) }
    var memory by remember { mutableStateOf<MemoryInfoData?>(null) }
    var cleaning by remember { mutableStateOf(false) }
    var showGuide by remember { mutableStateOf(false) }
    var section by rememberSaveable { mutableIntStateOf(0) }

    // 2초마다 갱신
    LaunchedEffect(resumeTick) {
        while (true) {
            cores = withContext(Dispatchers.IO) { CpuInfo.cores() }
            thermal = CpuInfo.thermal(context)
            memory = DeviceInfo.memory(context)
            delay(2000)
        }
    }

    val load = CpuInfo.estimatedLoad(cores)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenTitle("성능", "CPU·메모리·배터리 상태를 실시간으로 보여줘요")

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("CPU·메모리", "배터리·발열").forEachIndexed { i, label ->
                FilterChip(
                    selected = section == i,
                    onClick = { section = i },
                    label = { Text(label) },
                )
            }
        }

        if (section == 1) {
            BatterySection(resumeTick)
        } else {

        // ---------- CPU ----------
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("CPU", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        if (load != null) "부하 약 $load%" else "-",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    "$soc · ${cores.size}코어",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (load != null) {
                    SoftBar(
                        progress = { load / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        color = levelColor(load / 100f),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }

                thermal?.let { t ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("발열 상태", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            t.label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                t.level >= 3 -> ScoreBad
                                t.level >= 2 -> ScoreWarn
                                else -> ScoreGood
                            },
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))
                if (cores.any { it.readable }) {
                    cores.forEach { c -> CoreRow(c) }
                } else if (cores.isNotEmpty()) {
                    Text(
                        "이 기기는 코어별 클럭 정보를 앱에 공개하지 않아요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "안드로이드 8부터 앱이 CPU 사용률을 직접 읽을 수 없어서, 코어 클럭(현재/최대)으로 부하를 추정해요. " +
                        "발열로 '성능 제한 중'이 뜨면 폰이 일부러 속도를 낮추고 있는 상태예요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ---------- 메모리 ----------
        memory?.let { m ->
            val usedRatio = if (m.total > 0) 1f - m.available.toFloat() / m.total else 0f
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("메모리 (RAM)", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "${(usedRatio * 100).toInt()}% 사용",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    SoftBar(
                        progress = { usedRatio.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        color = levelColor(usedRatio),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                    Text(
                        "사용 가능 ${formatBytes(m.available)} / 전체 ${formatBytes(m.total)}" +
                            if (m.lowMemory) "\n⚠ 메모리 부족 상태예요" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (m.lowMemory) ScoreBad else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = {
                            if (!MemoryCleaner.canClean) {
                                showGuide = true
                            } else {
                            cleaning = true
                            scope.launch {
                                val before = DeviceInfo.memory(context).available
                                val count = withContext(Dispatchers.IO) { MemoryCleaner.cleanBackground(context) }
                                delay(1500) // 시스템이 메모리를 회수할 시간
                                val after = DeviceInfo.memory(context)
                                memory = after
                                cleaning = false
                                val gained = after.available - before
                                val msg = if (gained > 0) {
                                    "${count}개 앱 정리 · 여유 메모리 ${formatBytes(gained)} 늘었어요"
                                } else {
                                    "${count}개 앱 정리 완료 · 이미 여유가 충분했어요"
                                }
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            }
                            }
                        },
                        enabled = !cleaning,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (cleaning) "정리 중…" else "메모리 정리")
                    }
                    Text(
                        "안드로이드는 남는 메모리를 캐시로 써서 앱을 빨리 다시 열어요. 사용률이 높아도 정상이고, " +
                            "버벅일 때만 정리하는 게 좋아요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        }

        Spacer(Modifier.height(8.dp))
    }

    if (showGuide) {
        AlertDialog(
            onDismissRequest = { showGuide = false },
            title = { Text("메모리 정리 안내") },
            text = {
                Text(
                    "안드로이드 14부터는 보안 때문에 다른 앱을 종료하는 기능이 막혀 있어서, 어떤 앱도 메모리를 직접 정리할 수 없어요.\n\n" +
                        "대신 효과 있는 방법:\n" +
                        "• 최근 앱 화면(□ 버튼) → '모두 닫기'\n" +
                        "• 삼성 '디바이스 케어' → '지금 최적화'\n" +
                        "• 그래도 느리면 재부팅",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showGuide = false
                    MemoryCleaner.openDeviceCare(context)
                }) { Text("디바이스 케어 열기") }
            },
            dismissButton = {
                TextButton(onClick = { showGuide = false }) { Text("닫기") }
            },
        )
    }
}

@Composable
private fun CoreRow(core: CoreFreq) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("코어 ${core.index}", Modifier.width(56.dp), style = MaterialTheme.typography.bodySmall)
        if (core.readable) {
            SoftBar(
                progress = { core.ratio },
                modifier = Modifier
                    .weight(1f)
                    .height(6.dp),
                color = levelColor(core.ratio),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                String.format(Locale.US, "%.2f / %.2f GHz", core.curKhz / 1_000_000f, core.maxKhz / 1_000_000f),
                Modifier.width(110.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text("꺼짐 / 확인 불가", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun levelColor(ratio: Float): Color = when {
    ratio > 0.9f -> ScoreBad
    ratio > 0.75f -> ScoreWarn
    else -> MaterialTheme.colorScheme.primary
}
