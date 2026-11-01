package com.phonecare.app.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.phonecare.app.data.JunkCleaner
import com.phonecare.app.data.JunkItem
import com.phonecare.app.data.JunkKind
import com.phonecare.app.data.TrashManager
import com.phonecare.app.util.agoText
import com.phonecare.app.util.formatBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 정크 파일: 썸네일 캐시, 임시·기록 파일, 빈 폴더·파일, 지운 앱이 남긴 폴더 */
@Composable
fun JunkSection(protectedFolders: List<String>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var scanning by ScanState::junkScanning
    var scannedCount by ScanState::junkCount
    var results by ScanState::junk
    var selected by remember { mutableStateOf(setOf<String>()) }
    var expanded by remember { mutableStateOf(setOf<JunkKind>()) }
    var showConfirm by remember { mutableStateOf(false) }
    var cleaning by remember { mutableStateOf(false) }

    fun startScan() {
        scanning = true
        scannedCount = 0
        selected = emptySet()
        ScanState.junkJob = ScanState.scope.launch {
            try {
                val found = withContext(Dispatchers.IO) {
                    JunkCleaner.scan(context, protectedFolders, isActive = { isActive }) { n -> scannedCount = n }
                }
                if (isActive) {
                    results = found
                    ScanState.junkAt = System.currentTimeMillis()
                    // 안전한 종류는 미리 선택, '지운 앱이 남긴 폴더'는 직접 골라야 함
                    selected = found.filter { it.kind.preselect }.map { it.path }.toSet()
                }
            } finally {
                scanning = false
            }
        }
    }

    val list = results.orEmpty()
    val chosen = list.filter { it.path in selected }
    val chosenBytes = chosen.sumOf { it.size }

    Column(modifier) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                if (scanning) {
                    PremiumCard(Modifier.fillMaxWidth()) {
                        Text("정크 파일 찾는 중… ($scannedCount 개 확인)")
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(
                            "폴더를 하나하나 확인해서 1~2분 걸릴 수 있어요. 다른 화면에 다녀와도 계속돼요.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedButton(onClick = { ScanState.junkJob?.cancel() }) { Text("검사 중단") }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Button(onClick = { startScan() }, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                            Text(if (results == null) "정크 찾기" else "다시 찾기")
                        }
                        Text(
                            if (results != null && ScanState.junkAt > 0) {
                                "마지막 검사: ${agoText(ScanState.junkAt)} · 보호 폴더는 제외돼요"
                            } else {
                                "필요 없는 찌꺼기 파일만 찾아요. 사진·문서 같은 내 파일은 건드리지 않아요."
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            val current = results
            if (current != null && !scanning) {
                if (current.isEmpty()) {
                    item {
                        Text(
                            "정크 파일이 없어요. 깨끗해요! ✨",
                            Modifier.padding(vertical = 24.dp),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                } else {
                    item {
                        Text(
                            "${current.size}개 항목 · ${formatBytes(current.sumOf { it.size })}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                JunkKind.entries.forEach { kind ->
                    val items = current.filter { it.kind == kind }
                    if (items.isEmpty()) return@forEach
                    val allSelected = items.all { it.path in selected }
                    val isOpen = kind in expanded
                    item(key = "junk_${kind.name}") {
                        PremiumCard(Modifier.fillMaxWidth(), onClick = { expanded = if (isOpen) expanded - kind else expanded + kind }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(kind.title, style = MaterialTheme.typography.titleMedium)
                                    val files = items.sumOf { it.fileCount }
                                    Text(
                                        "${items.size}개" + (if (files > items.size) " (파일 ${files}개)" else "") + " · ${formatBytes(items.sumOf { it.size })}",
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(kind.desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                TextButton(onClick = {
                                    val paths = items.map { it.path }.toSet()
                                    selected = if (allSelected) selected - paths else selected + paths
                                }) { Text(if (allSelected) "선택 해제" else "전체 선택") }
                                Icon(
                                    if (isOpen) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                    contentDescription = if (isOpen) "접기" else "펼치기",
                                )
                            }
                        }
                    }
                    if (isOpen) {
                        items(items, key = { "j_" + it.path }) { j ->
                            JunkRow(j, j.path in selected) {
                                selected = if (j.path in selected) selected - j.path else selected + j.path
                            }
                        }
                    }
                }
            }
        }

        if (chosen.isNotEmpty()) {
            BottomActionBar(
                text = "선택한 ${chosen.size}개 정리" + if (chosenBytes > 0) " (${formatBytes(chosenBytes)})" else "",
                enabled = !cleaning,
                onClick = { showConfirm = true },
            )
        }
    }

    if (showConfirm) {
        val toTrash = chosen.count { it.kind.toTrash }
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("정크 정리") },
            text = {
                Text(
                    buildString {
                        append("${chosen.size}개 항목(${formatBytes(chosenBytes)})을 정리할까요?\n\n")
                        append("• 썸네일 캐시·빈 폴더·빈 파일은 바로 지워요 (다시 생기거나 내용이 없는 것이에요)\n")
                        if (toTrash > 0) {
                            append("• 임시 파일·남은 앱 폴더는 휴지통으로 옮겨서 ${TrashManager.keepDays(context)}일 동안 복원할 수 있어요")
                        }
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showConfirm = false
                    cleaning = true
                    val targets = chosen
                    ScanState.scope.launch {
                        val (done, freed, trashed) = withContext(Dispatchers.IO) { JunkCleaner.clean(context, targets) }
                        ScanState.removePaths(done)
                        selected = selected - done
                        cleaning = false
                        val failed = targets.size - done.size
                        val msg = buildString {
                            append("${done.size}개 정리 · ${formatBytes(freed)} 확보")
                            if (trashed > 0) append(" (휴지통 ${trashed}개)")
                            if (failed > 0) append(" · ${failed}개 실패")
                        }
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    }
                }) { Text("정리하기") }
            },
            dismissButton = { TextButton(onClick = { showConfirm = false }) { Text("취소") } },
        )
    }
}

@Composable
private fun JunkRow(item: JunkItem, checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Column(Modifier.weight(1f)) {
            Text(
                (if (item.isDir) "📁 " else "📄 ") + item.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                buildString {
                    append(formatBytes(item.size))
                    if (item.isDir && item.fileCount > 0) append(" · 파일 ${item.fileCount}개")
                    append(" · ${item.folder}")
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
