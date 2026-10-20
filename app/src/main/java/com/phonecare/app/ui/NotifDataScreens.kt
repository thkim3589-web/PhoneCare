package com.phonecare.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.phonecare.app.data.AppUsage
import com.phonecare.app.data.DataEntry
import com.phonecare.app.data.DataUsage
import com.phonecare.app.data.NotifEntry
import com.phonecare.app.data.NotifSource
import com.phonecare.app.data.NotifStats
import com.phonecare.app.util.daysAgo
import com.phonecare.app.util.formatBytes
import com.phonecare.app.util.formatDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ======================== 알림 많은 앱 ========================

@Composable
fun NotifAppsScreen(resumeTick: Int) {
    val context = LocalContext.current
    var result by remember { mutableStateOf<Pair<List<NotifEntry>, NotifSource>?>(null) }
    var listenerOn by remember { mutableStateOf(NotifStats.isListenerEnabled(context)) }

    LaunchedEffect(resumeTick) {
        listenerOn = NotifStats.isListenerEnabled(context)
        result = withContext(Dispatchers.IO) { NotifStats.load(context) }
    }

    val entries = result?.first.orEmpty()
    val source = result?.second ?: NotifSource.NONE
    val total = entries.sumOf { it.count }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { ScreenTitle("알림 많은 앱", "알림이 잦은 앱은 배터리를 쓰고 집중을 깨요. 필요 없는 알림은 꺼두세요") }

        if (!listenerOn) {
            item {
                PermissionCard(
                    title = "알림 접근을 허용하면 정확하게 세어드려요",
                    description = "어느 앱이 알림을 보냈는지 '개수'만 세고, 알림 제목·내용은 읽거나 저장하지 않아요. " +
                        "목록에서 '폰케어'를 켜주세요. 켠 뒤부터 집계가 쌓여요.",
                    buttonText = "알림 접근 허용하기",
                ) { NotifStats.openListenerSettings(context) }
            }
        }

        if (result == null) {
            item { LoadingRow("알림 기록을 확인하는 중…") }
            return@LazyColumn
        }

        item {
            PremiumCard(Modifier.fillMaxWidth()) {
                SectionHeader("🔔", ScoreWarn, "최근 ${NotifStats.DAYS}일 알림", "${total}개", MaterialTheme.colorScheme.onSurface)
                val since = NotifStats.listenerSince(context)
                Text(
                    when {
                        source == NotifSource.LISTENER && since > 0 && daysAgo(since) < NotifStats.DAYS ->
                            "${formatDate(since)}부터 집계 중이에요. 며칠 지나면 더 정확해져요."
                        source == NotifSource.LISTENER -> "알림 접근으로 직접 센 결과예요."
                        source == NotifSource.USAGE -> "시스템 기록 기준이에요. 알림 접근을 켜면 더 정확해져요."
                        else -> "아직 집계된 알림이 없어요."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        val max = entries.firstOrNull()?.count ?: 1
        items(entries, key = { it.packageName }) { e ->
            val perDay = e.count.toFloat() / NotifStats.DAYS
            RankRow(
                icon = e.icon,
                label = e.label,
                detail = "${e.count}개 · 하루 평균 ${if (perDay >= 10) perDay.toInt().toString() else "%.1f".format(perDay)}개",
                ratio = e.count.toFloat() / max,
                barColor = if (perDay >= 20) ScoreBad else if (perDay >= 5) ScoreWarn else MaterialTheme.colorScheme.primary,
                buttonText = "알림 설정",
            ) { NotifStats.openAppNotificationSettings(context, e.packageName) }
        }

        item {
            Text(
                "'알림 설정'에서 앱 알림을 통째로 끄거나, 광고·이벤트 같은 일부 종류만 끌 수 있어요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
    }
}

// ======================== 데이터 많이 쓰는 앱 ========================

@Composable
fun DataUsageScreen(resumeTick: Int) {
    val context = LocalContext.current
    var wifi by rememberSaveable { mutableStateOf(false) }
    var hasAccess by remember { mutableStateOf(AppUsage.hasUsageAccess(context)) }
    var entries by remember { mutableStateOf<List<DataEntry>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(resumeTick, wifi) {
        hasAccess = AppUsage.hasUsageAccess(context)
        if (!hasAccess) return@LaunchedEffect
        loading = true
        val r = withContext(Dispatchers.IO) { DataUsage.load(context, wifi) }
        failed = r == null
        entries = r
        loading = false
    }

    val list = entries.orEmpty()
    val total = list.sumOf { it.total }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { ScreenTitle("데이터 사용량", "이번 달 앱별 데이터 사용 순위예요") }

        if (!hasAccess) {
            item {
                PermissionCard(
                    title = "사용 기록 접근이 필요해요",
                    description = "앱별 데이터 사용량은 '사용 기록 접근' 권한으로 확인할 수 있어요. 목록에서 '폰케어'를 켜주세요.",
                    buttonText = "권한 허용하러 가기",
                ) { AppUsage.openUsageAccessSettings(context) }
            }
            return@LazyColumn
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !wifi, onClick = { wifi = false }, label = { Text("📶 모바일 데이터") })
                FilterChip(selected = wifi, onClick = { wifi = true }, label = { Text("🛜 와이파이") })
            }
        }

        if (loading) {
            item { LoadingRow("사용량을 계산하는 중…") }
            return@LazyColumn
        }
        if (failed) {
            item {
                Text(
                    "이 기기에서는 데이터 사용량을 불러올 수 없어요.",
                    Modifier.padding(vertical = 16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@LazyColumn
        }

        item {
            PremiumCard(Modifier.fillMaxWidth()) {
                SectionHeader(
                    if (wifi) "🛜" else "📶", LocalBrand.current.end,
                    if (wifi) "이번 달 와이파이" else "이번 달 모바일 데이터",
                    formatBytes(total), MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "${formatDate(DataUsage.monthStart())}부터 오늘까지 · 받은 데이터와 보낸 데이터를 합친 값이에요",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (list.isEmpty()) {
            item {
                Text("이번 달 사용 기록이 없어요.", Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        val max = list.firstOrNull()?.total?.coerceAtLeast(1) ?: 1
        items(list.take(40), key = { it.key }) { e ->
            val pkg = e.packageName
            RankRow(
                icon = e.icon,
                label = e.label,
                detail = "${formatBytes(e.total)} · 받기 ${formatBytes(e.rx)} / 보내기 ${formatBytes(e.tx)}",
                ratio = e.total.toFloat() / max,
                barColor = LocalBrand.current.end,
                buttonText = if (pkg != null) "앱 정보" else null,
            ) { if (pkg != null) AppUsage.openAppInfo(context, pkg) }
        }

        if (!wifi && list.isNotEmpty()) {
            item {
                Text(
                    "모바일 데이터가 많은 앱은 [앱 정보 → 모바일 데이터]에서 '백그라운드 데이터 사용'을 끄면 절약돼요. " +
                        "동영상 앱은 앱 설정에서 '모바일 데이터에서 화질 낮추기'를 켜보세요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
    }
}

// ======================== 공통 ========================

@Composable
private fun LoadingRow(text: String) {
    Row(Modifier.padding(vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
        Spacer(Modifier.width(12.dp))
        Text(text)
    }
}

@Composable
private fun RankRow(
    icon: ImageBitmap?,
    label: String,
    detail: String,
    ratio: Float,
    barColor: Color,
    buttonText: String?,
    onClick: () -> Unit,
) {
    PremiumCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(40.dp))
            } else {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) { Text("⚙️") }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                SoftBar(progress = { ratio }, modifier = Modifier.fillMaxWidth().height(5.dp), color = barColor)
            }
            if (buttonText != null) {
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(onClick = onClick) { Text(buttonText) }
            }
        }
    }
}

@Suppress("unused")
@Composable
private fun OutlinedAction(text: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick) { Text(text) }
}
