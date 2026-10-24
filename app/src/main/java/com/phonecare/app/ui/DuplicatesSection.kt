package com.phonecare.app.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.phonecare.app.data.GroupType
import com.phonecare.app.data.PhotoDuplicates
import com.phonecare.app.data.PhotoGroup
import com.phonecare.app.data.PhotoItem
import com.phonecare.app.data.ProtectedFolders
import com.phonecare.app.data.TrashManager
import com.phonecare.app.util.formatBytes
import com.phonecare.app.util.formatDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DuplicatesSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var scanning by ScanState::dupScanning
    var progress by ScanState::dupProgress
    var groups by ScanState::dupGroups
    var selected by ScanState::dupSelected
    var showConfirm by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }

    fun startScan() {
        scanning = true
        progress = 0 to 0
        ScanState.dupJob = ScanState.scope.launch {
            try {
                val protectedFolders = ProtectedFolders.list(context)
                val found = withContext(Dispatchers.IO) {
                    PhotoDuplicates.scan(context, protectedFolders, isActive = { isActive }) { d, t -> progress = d to t }
                }
                if (isActive) {
                    groups = found
                    // 기본 선택: 각 묶음에서 추천 1장을 뺀 나머지
                    selected = found.flatMap { g -> g.removable.map { it.path } }.toSet()
                }
            } finally {
                scanning = false
            }
        }
    }

    val all = groups.orEmpty().flatMap { it.photos }
    val selectedItems = all.filter { it.path in selected }.distinctBy { it.path }
    val selectedBytes = selectedItems.sumOf { it.size }

    Column(modifier) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                PremiumCard(Modifier.fillMaxWidth()) {
                    SectionHeader("🖼️", LocalBrand.current.end, "중복·비슷한 사진")
                    Text(
                        "똑같은 사진, 연속 촬영으로 비슷하게 찍힌 사진을 찾아 가장 좋은 1장만 남겨요. " +
                            "분석은 폰 안에서만 이뤄지고, 지운 사진은 휴지통에 ${TrashManager.keepDays(context)}일간 보관돼요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (scanning) {
                        val (d, t) = progress
                        Text(if (t > 0) "사진 분석 중… $d / $t" else "사진 목록 불러오는 중…")
                        SoftBar(
                            progress = { if (t > 0) d.toFloat() / t else 0f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp),
                        )
                        OutlinedButton(onClick = { ScanState.dupJob?.cancel() }) { Text("분석 중단") }
                    } else {
                        Button(onClick = { startScan() }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                            Text(if (groups == null) "사진 분석 시작" else "다시 분석")
                        }
                    }
                }
            }

            val current = groups
            if (current != null && !scanning) {
                if (current.isEmpty()) {
                    item {
                        Text(
                            "중복되거나 비슷한 사진을 찾지 못했어요.",
                            Modifier.padding(vertical = 16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    item {
                        val exact = current.count { it.type == GroupType.EXACT }
                        val similar = current.size - exact
                        Text(
                            "완전 중복 ${exact}묶음 · 비슷한 사진 ${similar}묶음 · 최대 ${formatBytes(current.sumOf { g -> g.removable.sumOf { it.size } })} 확보 가능",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    items(current, key = { it.id }) { group ->
                        GroupCard(
                            group = group,
                            selected = selected,
                            onToggle = { path ->
                                selected = if (path in selected) selected - path else selected + path
                            },
                        )
                    }
                }
            }
        }

        if (selectedItems.isNotEmpty()) {
            BottomActionBar(
                text = "선택한 ${selectedItems.size}장 휴지통으로 (${formatBytes(selectedBytes)})",
                enabled = !working,
                onClick = { showConfirm = true },
            )
        }
    }

    if (showConfirm) {
        // 묶음의 사진을 전부 고르면 경고
        val wholeGroups = groups.orEmpty().count { g -> g.photos.all { it.path in selected } }
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("사진 정리") },
            text = {
                Text(
                    "${selectedItems.size}장(${formatBytes(selectedBytes)})을 휴지통으로 옮길까요?\n" +
                        "${TrashManager.keepDays(context)}일 안에는 '휴지통'에서 복원할 수 있어요." +
                        if (wholeGroups > 0) "\n\n⚠ ${wholeGroups}개 묶음은 사진을 전부 선택했어요. 한 장도 남지 않아요." else "",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showConfirm = false
                    working = true
                    val paths = selectedItems.map { it.path }
                    ScanState.scope.launch {
                        val (moved, freed) = withContext(Dispatchers.IO) { TrashManager.moveToTrash(context, paths) }
                        ScanState.removePaths(moved)
                        working = false
                        Toast.makeText(context, "${moved.size}장 휴지통으로 이동 · ${formatBytes(freed)} 확보", Toast.LENGTH_LONG).show()
                    }
                }) { Text("휴지통으로 이동") }
            },
            dismissButton = { TextButton(onClick = { showConfirm = false }) { Text("취소") } },
        )
    }
}

@Composable
private fun GroupCard(group: PhotoGroup, selected: Set<String>, onToggle: (String) -> Unit) {
    PremiumCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${group.type.label} ${group.photos.size}장",
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                formatDate(group.keep.taken),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(group.photos, key = { it.path }) { photo ->
                PhotoTile(
                    photo = photo,
                    isKeep = photo == group.keep,
                    checked = photo.path in selected,
                    onClick = { onToggle(photo.path) },
                )
            }
        }
    }
}

@Composable
private fun PhotoTile(photo: PhotoItem, isKeep: Boolean, checked: Boolean, onClick: () -> Unit) {
    val danger = MaterialTheme.colorScheme.error
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(104.dp)
                .clickable { onClick() }
                .then(
                    if (checked) Modifier.border(3.dp, danger, RoundedCornerShape(12.dp)) else Modifier,
                ),
        ) {
            FileThumbnail(photo.path, Modifier.fillMaxSize())
            // 선택 표시
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(22.dp)
                    .background(if (checked) danger else Color.Black.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (checked) Text("✓", color = Color.White, style = MaterialTheme.typography.labelMedium)
            }
            if (isKeep) {
                Text(
                    "남기기 추천",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(5.dp)
                        .background(ScoreGood, RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        Text(
            formatBytes(photo.size),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** 화면 아래 고정 실행 버튼 */
@Composable
fun BottomActionBar(text: String, enabled: Boolean, color: Color = MaterialTheme.colorScheme.error, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = color),
        ) { Text(text, fontWeight = FontWeight.Bold) }
    }
}
