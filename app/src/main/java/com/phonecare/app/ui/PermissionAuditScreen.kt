package com.phonecare.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.phonecare.app.data.AppPerms
import com.phonecare.app.data.AppUsage
import com.phonecare.app.data.PermGroup
import com.phonecare.app.data.PermissionAudit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun PermissionAuditScreen(resumeTick: Int) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<AppPerms>?>(null) }
    var filter by remember { mutableStateOf<PermGroup?>(null) }
    var staleOnly by remember { mutableStateOf(false) }
    val hasUsage = remember(resumeTick) { AppUsage.hasUsageAccess(context) }

    // 앱 정보에서 권한을 끄고 돌아오면 다시 불러옴
    LaunchedEffect(resumeTick) {
        apps = withContext(Dispatchers.IO) { PermissionAudit.load(context) }
    }

    val all = apps.orEmpty()
    val staleCount = all.count { it.isStale }
    val shown = all
        .filter { filter == null || filter in it.groups }
        .filter { !staleOnly || it.isStale }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { ScreenTitle("앱 권한 점검", "위치·카메라·마이크 같은 민감한 권한이 허용된 앱을 모아 봤어요") }

        if (apps == null) {
            item {
                Row(Modifier.padding(vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("앱 권한을 확인하는 중…")
                }
            }
            return@LazyColumn
        }

        // 요약 카드
        item {
            PremiumCard(Modifier.fillMaxWidth()) {
                SectionHeader(
                    "🔐", ScoreWarn, "권한이 허용된 앱",
                    "${all.size}개", MaterialTheme.colorScheme.onSurface,
                )
                if (hasUsage) {
                    Text(
                        if (staleCount > 0) {
                            "그중 ${staleCount}개는 ${PermissionAudit.STALE_DAYS}일 넘게 안 썼는데 권한을 가지고 있어요. 필요 없다면 권한을 끄거나 앱을 지우세요."
                        } else {
                            "오래 안 쓰면서 권한을 가진 앱은 없어요. 잘 관리되고 있어요!"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (staleCount > 0) ScoreBad else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (staleCount > 0) {
                        OutlinedButton(onClick = { staleOnly = !staleOnly }) {
                            Text(if (staleOnly) "전체 보기" else "안 쓰는 앱 ${staleCount}개만 보기")
                        }
                    }
                } else {
                    Text(
                        "사용 기록 접근을 허용하면 '안 쓰는데 권한을 가진 앱'을 따로 찾아드려요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = { AppUsage.openUsageAccessSettings(context) }) { Text("사용 기록 접근 허용") }
                }
            }
        }

        // 권한 종류 필터
        item {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text("전체") })
                PermGroup.entries.forEach { g ->
                    val n = all.count { g in it.groups }
                    if (n > 0) {
                        FilterChip(
                            selected = filter == g,
                            onClick = { filter = if (filter == g) null else g },
                            label = { Text("${g.symbol} ${g.label} $n") },
                        )
                    }
                }
            }
        }

        if (shown.isEmpty()) {
            item {
                Text("해당하는 앱이 없어요.", Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        items(shown, key = { it.packageName }) { app ->
            AppPermRow(app) { AppUsage.openAppInfo(context, app.packageName) }
        }

        item {
            Text(
                "'권한 관리'를 누르면 앱 정보 화면이 열려요. [권한]에서 필요 없는 권한을 '허용 안 함'으로 바꾸세요. " +
                    "'항상 위치'는 앱을 안 쓸 때도 위치를 볼 수 있어서 배터리와 개인정보에 가장 영향이 커요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun AppPermRow(app: AppPerms, onManage: () -> Unit) {
    PremiumCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val icon = app.icon
            if (icon != null) {
                Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(42.dp))
            } else {
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(app.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                app.unusedDays?.let { d ->
                    Text(
                        when {
                            d == 0L -> "오늘 사용"
                            d >= AppUsage.LOOKBACK_DAYS -> "${AppUsage.LOOKBACK_DAYS}일 넘게 사용 안 함"
                            else -> "마지막 사용 ${d}일 전"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (app.isStale) ScoreBad else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (app.isStale) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
            FilledTonalButton(onClick = onManage) { Text("권한 관리") }
        }
        Text(
            app.groups.joinToString("   ") { "${it.symbol} ${it.label}" },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
