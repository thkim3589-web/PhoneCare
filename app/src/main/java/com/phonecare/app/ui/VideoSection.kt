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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.phonecare.app.data.CompressResult
import com.phonecare.app.data.ProtectedFolders
import com.phonecare.app.data.TrashManager
import com.phonecare.app.data.VideoCompressor
import com.phonecare.app.data.VideoItem
import com.phonecare.app.data.VideoPreset
import com.phonecare.app.util.formatBytes
import com.phonecare.app.util.formatDate
import com.phonecare.app.util.openFilePreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 압축 진행 상태 (화면을 옮겨도 유지) */
private object VideoJobState {
    var running by mutableStateOf(false)
    var current by mutableStateOf<String?>(null)
    var progress by mutableStateOf(0f)
    var index by mutableStateOf(0)
    var total by mutableStateOf(0)
    var job: Job? = null
}

@Composable
fun VideoSection(protectedFolders: List<String>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val view = LocalView.current
    var preset by remember { mutableStateOf(VideoPreset.HIGH) }
    var videos by remember { mutableStateOf<List<VideoItem>?>(null) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var showConfirm by remember { mutableStateOf(false) }
    var reload by remember { mutableStateOf(0) }

    LaunchedEffect(preset, protectedFolders, reload, VideoJobState.running) {
        if (VideoJobState.running) return@LaunchedEffect
        videos = null
        videos = withContext(Dispatchers.IO) { VideoCompressor.findCandidates(context, protectedFolders, preset) }
        selected = selected.filter { p -> videos.orEmpty().any { it.path == p } }.toSet()
    }

    // 압축하는 동안 화면이 꺼지지 않게
    DisposableEffect(VideoJobState.running) {
        view.keepScreenOn = VideoJobState.running
        onDispose { view.keepScreenOn = false }
    }

    val list = videos.orEmpty()
    val chosen = list.filter { it.path in selected }
    val expectedSave = chosen.sumOf { it.size - VideoCompressor.estimate(it, preset) }

    Column(modifier) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                PremiumCard(Modifier.fillMaxWidth()) {
                    SectionHeader("🎬", LocalBrand.current.end, "큰 동영상 압축")
                    Text(
                        "화질을 조금 낮춰 동영상 용량을 줄여요. 휴대폰 화면에서는 차이가 거의 안 보여요. " +
                            "원본은 휴지통에 ${TrashManager.keepDays(context)}일 보관돼서 마음에 안 들면 복원할 수 있어요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        VideoPreset.entries.forEach { p ->
                            FilterChip(
                                selected = preset == p,
                                enabled = !VideoJobState.running,
                                onClick = { preset = p },
                                label = { Text(p.label) },
                            )
                        }
                    }
                    if (VideoJobState.running) {
                        Text(
                            "압축 중 ${VideoJobState.index}/${VideoJobState.total} · ${VideoJobState.current ?: ""}",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        SoftBar(progress = { VideoJobState.progress }, modifier = Modifier.fillMaxWidth().height(8.dp))
                        Text(
                            "${(VideoJobState.progress * 100).toInt()}% · 압축하는 동안 폰케어를 켜두세요 (화면은 자동으로 켜져 있어요)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedButton(onClick = { VideoJobState.job?.cancel() }) { Text("압축 중단") }
                    }
                }
            }

            if (videos == null && !VideoJobState.running) {
                item { Text("동영상을 찾는 중…", Modifier.padding(vertical = 12.dp)) }
            } else if (list.isEmpty() && !VideoJobState.running) {
                item {
                    Text(
                        "줄일 수 있는 큰 동영상(50MB 이상)이 없어요.",
                        Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (!VideoJobState.running) {
                item {
                    Text(
                        "${list.size}개 · 모두 압축하면 약 ${formatBytes(list.sumOf { it.size - VideoCompressor.estimate(it, preset) })} 절약",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
                items(list, key = { it.path }) { v ->
                    VideoRow(v, preset, v.path in selected) {
                        selected = if (v.path in selected) selected - v.path else selected + v.path
                    }
                }
            }
        }

        if (chosen.isNotEmpty() && !VideoJobState.running) {
            BottomActionBar(
                text = "선택한 ${chosen.size}개 압축 (약 ${formatBytes(expectedSave)} 절약)",
                enabled = true,
                color = MaterialTheme.colorScheme.primary,
                onClick = { showConfirm = true },
            )
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("동영상 압축") },
            text = {
                Text(
                    "${chosen.size}개 동영상을 '${preset.label}'로 압축할까요?\n\n" +
                        "• 1분짜리 동영상은 보통 20~60초 걸려요\n" +
                        "• 원본은 휴지통으로 가고, 압축본이 원래 자리에 저장돼요\n" +
                        "• 압축하는 동안 폰케어를 켜두세요",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showConfirm = false
                    val queue = chosen
                    VideoJobState.running = true
                    VideoJobState.total = queue.size
                    VideoJobState.job = ScanState.scope.launch {
                        var saved = 0L
                        var done = 0
                        var lastMsg: String? = null
                        try {
                            for ((i, v) in queue.withIndex()) {
                                VideoJobState.index = i + 1
                                VideoJobState.current = v.name
                                VideoJobState.progress = 0f
                                when (val r = VideoCompressor.compress(context, v, preset) { VideoJobState.progress = it }) {
                                    is CompressResult.Done -> {
                                        saved += r.oldSize - r.newSize
                                        done++
                                    }
                                    is CompressResult.NotSmaller -> lastMsg = r.reason
                                    is CompressResult.Failed -> lastMsg = r.reason
                                }
                            }
                        } finally {
                            VideoJobState.running = false
                            VideoJobState.current = null
                            selected = emptySet()
                            reload++
                            val msg = "${done}개 압축 완료 · ${formatBytes(saved)} 절약" + (lastMsg?.let { "\n$it" } ?: "")
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        }
                    }
                }) { Text("압축 시작") }
            },
            dismissButton = { TextButton(onClick = { showConfirm = false }) { Text("취소") } },
        )
    }
}

@Composable
private fun VideoRow(v: VideoItem, preset: VideoPreset, checked: Boolean, onToggle: () -> Unit) {
    val context = LocalContext.current
    val est = VideoCompressor.estimate(v, preset)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        FileThumbnail(v.path, Modifier.size(56.dp).clickable { context.openFilePreview(v.path) })
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(v.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            Text(
                "${formatBytes(v.size)} → 약 ${formatBytes(est)} · ${durationText(v.durationMs)} · ${v.shortSide}p",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                formatDate(v.modified) + " · " + ProtectedFolders.display(v.path.substringBeforeLast('/')),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun durationText(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "${s / 3600}시간 ${(s % 3600) / 60}분" else "${s / 60}분 ${s % 60}초"
}
