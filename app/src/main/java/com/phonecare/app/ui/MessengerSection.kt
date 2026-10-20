package com.phonecare.app.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.phonecare.app.data.AppStorage
import com.phonecare.app.data.AppUsage
import com.phonecare.app.data.MessengerFiles
import com.phonecare.app.data.MsgFile
import com.phonecare.app.data.MsgKind
import com.phonecare.app.data.TrashManager
import com.phonecare.app.util.daysAgo
import com.phonecare.app.util.formatBytes
import com.phonecare.app.util.formatDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val ageOptions = listOf(0 to "전체 기간", 30 to "30일+", 90 to "90일+", 365 to "1년+")
private const val KAKAO_PKG = "com.kakao.talk"

@Composable
fun MessengerSection(protectedFolders: List<String>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var files by remember { mutableStateOf<List<MsgFile>?>(null) }
    var kakaoStorage by remember { mutableStateOf<AppStorage?>(null) }
    var appFilter by remember { mutableStateOf<String?>(null) }
    var kindFilter by remember { mutableStateOf<MsgKind?>(null) }
    var minAge by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var showConfirm by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    val kakaoInstalled = remember { MessengerFiles.isInstalled(context, KAKAO_PKG) }

    LaunchedEffect(protectedFolders) {
        files = null
        files = withContext(Dispatchers.IO) { MessengerFiles.scan(protectedFolders) }
        kakaoStorage = withContext(Dispatchers.IO) { MessengerFiles.appStorage(context, KAKAO_PKG) }
    }

    val all = files.orEmpty()
    val appsFound = all.map { it.app }.distinct()
    val visible = all.filter {
        (appFilter == null || it.app == appFilter) &&
            (kindFilter == null || it.kind == kindFilter) &&
            (minAge == 0 || daysAgo(it.modified) >= minAge)
    }
    val visiblePaths = visible.map { it.path }.toSet()
    val allVisibleSelected = visible.isNotEmpty() && visiblePaths.all { it in selected }
    val selectedFiles = all.filter { it.path in selected }
    val selectedBytes = selectedFiles.sumOf { it.size }

    Column(modifier) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 카카오톡 앱 자체 용량 + 캐시 정리 안내
            if (kakaoInstalled) {
                item {
                    PremiumCard(Modifier.fillMaxWidth()) {
                        SectionHeader(
                            "💬", ScoreWarn, "카카오톡 앱 데이터",
                            kakaoStorage?.let { formatBytes(it.total) }, MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            kakaoStorage?.let { "그중 캐시 ${formatBytes(it.cache)} · " }.orEmpty() +
                                "채팅방에서 본 사진·동영상은 카톡 안에 따로 쌓여서 다른 앱이 지울 수 없어요. " +
                                "카카오톡 [설정 ⚙ → 기타 → 저장공간 관리]에서 '캐시 데이터 삭제'를 누르세요. 대화 내용은 지워지지 않아요.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (kakaoStorage == null && !AppUsage.hasUsageAccess(context)) {
                            Text(
                                "사용 기록 접근을 허용하면 카카오톡이 차지하는 용량도 보여드려요.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                context.packageManager.getLaunchIntentForPackage(KAKAO_PKG)?.let { context.startActivity(it) }
                            }) { Text("카카오톡 열기") }
                            OutlinedButton(onClick = { AppUsage.openAppInfo(context, KAKAO_PKG) }) { Text("앱 정보") }
                        }
                    }
                }
            }

            item {
                Text(
                    "메신저로 받아서 휴대폰에 저장된 파일",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            if (files == null) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("메신저 폴더를 확인하는 중…")
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
                return@LazyColumn
            }

            if (all.isEmpty()) {
                item {
                    Text(
                        "카카오톡·텔레그램·라인·밴드로 받아서 저장된 파일이 없어요.",
                        Modifier.padding(vertical = 16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                return@LazyColumn
            }

            // 필터
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (appsFound.size > 1) {
                        ChipRow {
                            FilterChip(selected = appFilter == null, onClick = { appFilter = null }, label = { Text("모든 앱") })
                            appsFound.forEach { a ->
                                FilterChip(selected = appFilter == a, onClick = { appFilter = a }, label = { Text(a) })
                            }
                        }
                    }
                    ChipRow {
                        FilterChip(selected = kindFilter == null, onClick = { kindFilter = null }, label = { Text("모든 종류") })
                        MsgKind.entries.forEach { k ->
                            val n = all.count { it.kind == k && (appFilter == null || it.app == appFilter) }
                            if (n > 0) {
                                FilterChip(selected = kindFilter == k, onClick = { kindFilter = k }, label = { Text("${k.label} $n") })
                            }
                        }
                    }
                    ChipRow {
                        ageOptions.forEach { (d, label) ->
                            FilterChip(selected = minAge == d, onClick = { minAge = d }, label = { Text(label) })
                        }
                    }
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${visible.size}개 · ${formatBytes(visible.sumOf { it.size })}",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    if (visible.isNotEmpty()) {
                        TextButton(onClick = {
                            selected = if (allVisibleSelected) selected - visiblePaths else selected + visiblePaths
                        }) { Text(if (allVisibleSelected) "선택 해제" else "보이는 것 전체 선택") }
                    }
                }
            }

            items(visible, key = { it.path }) { f ->
                MsgFileRow(f, f.path in selected) {
                    selected = if (f.path in selected) selected - f.path else selected + f.path
                }
            }
        }

        if (selectedFiles.isNotEmpty()) {
            BottomActionBar(
                text = "선택한 ${selectedFiles.size}개 휴지통으로 (${formatBytes(selectedBytes)})",
                enabled = !working,
                onClick = { showConfirm = true },
            )
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("메신저 파일 정리") },
            text = {
                Text(
                    "${selectedFiles.size}개 파일(${formatBytes(selectedBytes)})을 휴지통으로 옮길까요?\n" +
                        "${TrashManager.KEEP_DAYS}일 동안 '휴지통'에서 복원할 수 있어요. 채팅방 대화 내용에는 영향이 없어요.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showConfirm = false
                    working = true
                    val paths = selectedFiles.map { it.path }
                    scope.launch {
                        val (moved, freed) = withContext(Dispatchers.IO) { TrashManager.moveToTrash(context, paths) }
                        files = files?.filterNot { it.path in moved }
                        selected = selected - moved
                        working = false
                        Toast.makeText(context, "${moved.size}개 휴지통으로 이동 · ${formatBytes(freed)} 확보", Toast.LENGTH_LONG).show()
                    }
                }) { Text("휴지통으로 이동") }
            },
            dismissButton = { TextButton(onClick = { showConfirm = false }) { Text("취소") } },
        )
    }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) { content() }
}

@Composable
private fun MsgFileRow(file: MsgFile, checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        FileThumbnail(file.path, Modifier.size(48.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            Text(
                "${formatBytes(file.size)} · ${formatDate(file.modified)} · ${file.app} ${file.kind.label}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Suppress("unused")
private val unusedHeight = Modifier.height(0.dp)
