package com.phonecare.app.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.phonecare.app.data.TrashItem
import com.phonecare.app.data.TrashManager
import com.phonecare.app.util.formatBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun TrashSection(resumeTick: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<TrashItem>?>(null) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var confirmDelete by remember { mutableStateOf<Set<String>?>(null) }
    var working by remember { mutableStateOf(false) }

    suspend fun reload() {
        items = withContext(Dispatchers.IO) { TrashManager.list(context) }
        selected = selected.filter { id -> items.orEmpty().any { it.id == id } }.toSet()
    }

    LaunchedEffect(resumeTick) { reload() }

    val list = items.orEmpty()
    val total = list.sumOf { it.size }

    Column(modifier) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                PremiumCard(Modifier.fillMaxWidth()) {
                    SectionHeader("🗑️", ScoreWarn, "휴지통", formatBytes(total), MaterialTheme.colorScheme.onSurface)
                    Text(
                        "폰케어에서 지운 파일은 여기에 ${TrashManager.KEEP_DAYS}일간 보관된 뒤 자동으로 영구 삭제돼요. " +
                            "실수로 지웠다면 골라서 복원하세요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (list.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    selected = if (selected.size == list.size) emptySet() else list.map { it.id }.toSet()
                                },
                                modifier = Modifier.weight(1f),
                            ) { Text(if (selected.size == list.size) "선택 해제" else "전체 선택") }
                            OutlinedButton(
                                onClick = { confirmDelete = list.map { it.id }.toSet() },
                                modifier = Modifier.weight(1f),
                            ) { Text("휴지통 비우기", color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
            if (items != null && list.isEmpty()) {
                item {
                    Text(
                        "휴지통이 비어 있어요.",
                        Modifier.padding(vertical = 16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(list, key = { it.id }) { item ->
                TrashRow(
                    item = item,
                    checked = item.id in selected,
                    onToggle = { selected = if (item.id in selected) selected - item.id else selected + item.id },
                )
            }
        }

        if (selected.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = { confirmDelete = selected },
                    enabled = !working,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                ) { Text("영구 삭제", color = MaterialTheme.colorScheme.error) }
                Button(
                    onClick = {
                        working = true
                        val ids = selected
                        scope.launch {
                            val n = withContext(Dispatchers.IO) { TrashManager.restore(context, ids) }
                            reload()
                            working = false
                            Toast.makeText(context, "${n}개 파일을 원래 자리로 복원했어요", Toast.LENGTH_LONG).show()
                        }
                    },
                    enabled = !working,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(),
                ) { Text("${selected.size}개 복원", fontWeight = FontWeight.Bold) }
            }
        }
    }

    confirmDelete?.let { ids ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("영구 삭제") },
            text = { Text("${ids.size}개 파일을 영구 삭제할까요? 이 작업은 되돌릴 수 없어요.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    working = true
                    scope.launch {
                        val freed = withContext(Dispatchers.IO) { TrashManager.deleteForever(context, ids) }
                        reload()
                        working = false
                        Toast.makeText(context, "영구 삭제 완료 · ${formatBytes(freed)} 확보", Toast.LENGTH_LONG).show()
                    }
                }) { Text("영구 삭제", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("취소") } },
        )
    }
}

@Composable
private fun TrashRow(item: TrashItem, checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        FileThumbnail(item.trashPath, Modifier.size(48.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            Text(
                "${formatBytes(item.size)} · ${item.originalFolder}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            if (item.daysLeft == 0L) "오늘 삭제" else "${item.daysLeft}일 남음",
            style = MaterialTheme.typography.labelSmall,
            color = if (item.daysLeft <= 1) ScoreBad else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
