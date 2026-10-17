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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.phonecare.app.data.AppItem
import com.phonecare.app.data.AppUsage
import com.phonecare.app.util.daysAgo
import com.phonecare.app.util.formatBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val filterOptions = listOf(30, 90, 180, 0) // 0 = 전체

@Composable
fun AppsScreen(resumeTick: Int) {
    val context = LocalContext.current
    var hasAccess by remember { mutableStateOf(AppUsage.hasUsageAccess(context)) }
    var apps by remember { mutableStateOf<List<AppItem>?>(null) }
    var loading by remember { mutableStateOf(false) }
    var minDays by rememberSaveable { mutableIntStateOf(30) }

    // 처음 열 때 불러오고, 앱 삭제 화면 등에서 돌아오면 지워진 앱을 목록에서 뺌
    LaunchedEffect(resumeTick) {
        hasAccess = AppUsage.hasUsageAccess(context)
        if (!hasAccess) return@LaunchedEffect
        val current = apps
        if (current == null) {
            loading = true
            apps = withContext(Dispatchers.IO) { AppUsage.loadApps(context) }
            loading = false
        } else {
            apps = withContext(Dispatchers.IO) {
                current.filter { AppUsage.isInstalled(context, it.packageName) }
            }
        }
    }

    val filtered = apps.orEmpty()
        .filter { minDays == 0 || it.unusedDays >= minDays }
        .sortedByDescending { it.unusedDays }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            ScreenTitle("안 쓰는 앱", "오래 안 쓴 앱을 지우면 저장공간과 배터리가 절약돼요")
        }

        if (!hasAccess) {
            item {
                PermissionCard(
                    title = "사용 기록 접근 권한이 필요해요",
                    description = "어떤 앱을 언제 마지막으로 썼는지 알려면 '사용 기록 접근' 권한이 필요해요. " +
                        "설정 목록에서 '폰케어'를 찾아 허용해 주세요. 이 정보는 휴대폰 밖으로 나가지 않아요.",
                    buttonText = "권한 허용하러 가기",
                ) { AppUsage.openUsageAccessSettings(context) }
            }
            return@LazyColumn
        }

        if (loading) {
            item {
                Row(Modifier.padding(vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("앱 목록을 불러오는 중…")
                }
            }
            return@LazyColumn
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                filterOptions.forEach { d ->
                    FilterChip(
                        selected = minDays == d,
                        onClick = { minDays = d },
                        label = { Text(if (d == 0) "전체" else "${d}일+") },
                    )
                }
            }
        }

        item {
            val total = filtered.filter { it.sizeBytes > 0 }.sumOf { it.sizeBytes }
            Text(
                "${filtered.size}개 앱 · 합계 ${formatBytes(total)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        if (filtered.isEmpty()) {
            item {
                Text(
                    "이 기간 동안 안 쓴 앱이 없어요.",
                    Modifier.padding(vertical = 16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        items(filtered, key = { it.packageName }) { app ->
            AppRow(
                app = app,
                onUninstall = { AppUsage.openUninstall(context, app.packageName) },
                onInfo = { AppUsage.openAppInfo(context, app.packageName) },
            )
        }
    }
}

private fun usageText(app: AppItem): String {
    val lookback = AppUsage.LOOKBACK_DAYS
    return when {
        app.neverUsed && daysAgo(app.installed) > lookback -> "${lookback}일 넘게 사용 안 함"
        app.neverUsed -> "설치 후 사용 기록 없음 (${app.unusedDays}일 전 설치)"
        app.unusedDays == 0L -> "오늘 사용"
        else -> "마지막 사용 ${app.unusedDays}일 전"
    }
}

@Composable
private fun AppRow(app: AppItem, onUninstall: () -> Unit, onInfo: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val icon = app.icon
                if (icon != null) {
                    Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(44.dp))
                } else {
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        app.label,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        usageText(app),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (app.unusedDays >= 90) ScoreBad else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (app.sizeBytes > 0) {
                        Text(
                            "용량 ${formatBytes(app.sizeBytes)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onInfo) { Text("앱 정보") }
                Spacer(Modifier.width(4.dp))
                FilledTonalButton(onClick = onUninstall) { Text("삭제") }
            }
        }
    }
}
