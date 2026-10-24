package com.phonecare.app.ui

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.phonecare.app.data.FileCategory
import com.phonecare.app.data.FoundFile
import com.phonecare.app.data.ProtectedFolders
import com.phonecare.app.data.StorageScanner
import com.phonecare.app.data.TrashManager
import com.phonecare.app.util.formatBytes
import com.phonecare.app.util.agoText
import com.phonecare.app.util.formatDate
import com.phonecare.app.util.openFilePreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** 파일 목록 정렬 기준 */
private enum class FileSort(val label: String) {
    SIZE("큰 순"),
    OLDEST("오래된 순"),
    NEWEST("최신 순"),
    NAME("이름 순");

    fun apply(files: List<FoundFile>): List<FoundFile> = when (this) {
        SIZE -> files.sortedByDescending { it.size }
        OLDEST -> files.sortedBy { it.modified }
        NEWEST -> files.sortedByDescending { it.modified }
        NAME -> files.sortedBy { it.name.lowercase() }
    }

    companion object {
        private const val PREFS = "storage"
        private const val KEY = "file_sort"

        fun load(context: Context): FileSort {
            val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            return entries.firstOrNull { it.name == saved } ?: SIZE
        }

        fun save(context: Context, sort: FileSort) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, sort.name).apply()
        }
    }
}

private val sectionLabels = listOf("정리 후보", "중복 사진", "💬 메신저", "휴지통")

/** 보호 추천 폴더 (있는 것만 보여줌) */
private val suggestedFolders = listOf(
    "DCIM/Camera" to "카메라 원본",
    "Documents" to "문서",
    "Pictures" to "저장한 사진",
    "Download" to "다운로드",
)

@Composable
fun StorageScreen(resumeTick: Int) {
    val context = LocalContext.current
    var hasAccess by remember { mutableStateOf(StorageScanner.hasAccess(context)) }
    var section by ScanState::storageSection
    var protectedList by remember { mutableStateOf(ProtectedFolders.list(context)) }
    var showProtect by remember { mutableStateOf(false) }
    var workSuggestions by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(resumeTick) { hasAccess = StorageScanner.hasAccess(context) }
    // 홈에서 '업무 폴더 보호하기'를 눌러 들어온 경우 바로 보호 폴더 창 열기
    LaunchedEffect(ScanState.openProtectDialog) {
        if (ScanState.openProtectDialog) {
            ScanState.openProtectDialog = false
            showProtect = true
        }
    }
    LaunchedEffect(showProtect, protectedList) {
        if (showProtect && hasAccess) {
            workSuggestions = withContext(Dispatchers.IO) { ProtectedFolders.suggestWorkFolders(context) }
        }
    }

    val legacyPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { hasAccess = StorageScanner.hasAccess(context) }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val path = ProtectedFolders.pathFromTreeUri(uri)
            if (path == null) {
                Toast.makeText(context, "내장 저장소 안의 폴더만 지정할 수 있어요 (최상위 제외)", Toast.LENGTH_LONG).show()
            } else {
                ProtectedFolders.add(context, path)
                protectedList = ProtectedFolders.list(context)
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ScreenTitle("저장공간 정리", "필요 없는 파일을 찾아 안전하게 정리해요")
            if (hasAccess) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    sectionLabels.forEachIndexed { i, label ->
                        FilterChip(selected = section == i, onClick = { section = i }, label = { Text(label) })
                    }
                }
                // 보호 폴더 바로가기
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { showProtect = true }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconBadge("🛡️", ScoreGood, 30.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (protectedList.isEmpty()) "보호 폴더 없음 · 업무 사진 폴더를 지정해 두세요" else "보호 폴더 ${protectedList.size}개 · 검사에서 제외돼요",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text("관리", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        if (!hasAccess) {
            Box(Modifier.padding(16.dp)) {
                PermissionCard(
                    title = "파일 접근 권한이 필요해요",
                    description = "휴대폰 안의 큰 파일, 오래된 다운로드, 중복 사진을 찾으려면 " +
                        "'모든 파일 접근' 권한을 허용해 주세요. 파일은 내가 선택한 것만 정리돼요.",
                    buttonText = "권한 허용하러 가기",
                ) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        StorageScanner.openAllFilesSettings(context)
                    } else {
                        legacyPermissionLauncher.launch(
                            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE),
                        )
                    }
                }
            }
        } else {
            when (section) {
                0 -> CleanupSection(protectedList, Modifier.weight(1f))
                1 -> DuplicatesSection(Modifier.weight(1f))
                2 -> MessengerSection(protectedList, Modifier.weight(1f))
                else -> TrashSection(resumeTick, Modifier.weight(1f))
            }
        }
    }

    if (showProtect) {
        ProtectedFoldersDialog(
            folders = protectedList,
            workSuggestions = workSuggestions,
            onAddPicker = { folderPicker.launch(null) },
            onAddPath = { path ->
                ProtectedFolders.add(context, path)
                protectedList = ProtectedFolders.list(context)
            },
            onRemove = { path ->
                ProtectedFolders.remove(context, path)
                protectedList = ProtectedFolders.list(context)
            },
            onDismiss = { showProtect = false },
        )
    }
}

@Composable
private fun ProtectedFoldersDialog(
    folders: List<String>,
    workSuggestions: List<String>,
    onAddPicker: () -> Unit,
    onAddPath: (String) -> Unit,
    onRemove: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val root = Environment.getExternalStorageDirectory().absolutePath
    val suggestions = suggestedFolders
        .map { (rel, label) -> "$root/$rel" to label }
        .filter { (path, _) -> path !in folders && File(path).isDirectory }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("보호 폴더") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "여기 지정한 폴더(하위 폴더 포함)는 정리 후보·중복 사진 검사에서 완전히 빠져요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (folders.isEmpty()) {
                    Text("아직 지정한 폴더가 없어요.", style = MaterialTheme.typography.bodyMedium)
                }
                folders.forEach { path ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🛡️  " + ProtectedFolders.display(path), Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        TextButton(onClick = { onRemove(path) }) { Text("해제", color = MaterialTheme.colorScheme.error) }
                    }
                }
                if (workSuggestions.isNotEmpty()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text("💼 업무 폴더로 보여요", style = MaterialTheme.typography.labelLarge)
                    workSuggestions.forEach { path ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                ProtectedFolders.display(path),
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            TextButton(onClick = { onAddPath(path) }) { Text("보호") }
                        }
                    }
                }
                if (suggestions.isNotEmpty()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text("빠른 추가", style = MaterialTheme.typography.labelLarge)
                    suggestions.forEach { (path, label) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(label, style = MaterialTheme.typography.bodyMedium)
                                Text(ProtectedFolders.display(path), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = { onAddPath(path) }) { Text("추가") }
                        }
                    }
                }
                OutlinedButton(onClick = onAddPicker, modifier = Modifier.fillMaxWidth()) { Text("＋ 다른 폴더 선택") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("완료") } },
    )
}

/** 정리 후보 (APK, 스크린샷, 오래된 다운로드, 큰 파일) */
@Composable
private fun CleanupSection(protectedFolders: List<String>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var sortOrder by remember { mutableStateOf(FileSort.load(context)) }
    // 결과·진행 상태는 화면 밖(ScanState)에 보관 → 다른 화면에 다녀와도 유지
    var scanning by ScanState::cleanupScanning
    var scannedCount by ScanState::cleanupCount
    var results by ScanState::cleanup
    var selected by remember { mutableStateOf(setOf<String>()) }
    var expanded by remember { mutableStateOf(setOf<FileCategory>()) }
    var showConfirm by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    fun startScan() {
        scanning = true
        scannedCount = 0
        selected = emptySet()
        ScanState.cleanupJob = ScanState.scope.launch {
            try {
                val found = withContext(Dispatchers.IO) {
                    StorageScanner.scan(context, protectedFolders, isActive = { isActive }) { n -> scannedCount = n }
                }
                if (isActive) {
                    results = found
                    ScanState.cleanupAt = System.currentTimeMillis()
                }
            } finally {
                scanning = false
            }
        }
    }

    val list = results.orEmpty()
    val selectedFiles = list.filter { it.path in selected }
    val selectedBytes = selectedFiles.sumOf { it.size }

    Column(modifier) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                if (scanning) {
                    PremiumCard(Modifier.fillMaxWidth()) {
                        Text("파일 검사 중… ($scannedCount 개 확인)")
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(
                            "다른 화면에 다녀와도 검사는 계속돼요.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedButton(onClick = { ScanState.cleanupJob?.cancel() }) { Text("검사 중단") }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Button(onClick = { startScan() }, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                            Text(if (results == null) "검사 시작" else "다시 검사")
                        }
                        if (results != null && ScanState.cleanupAt > 0) {
                            Text(
                                "마지막 검사: ${agoText(ScanState.cleanupAt)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            val current = results
            if (current != null && !scanning) {
                if (current.isEmpty()) {
                    item {
                        Text(
                            "정리할 파일을 찾지 못했어요. 잘 관리되고 있어요!",
                            Modifier.padding(vertical = 24.dp),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                } else {
                    item {
                        Text(
                            "총 ${current.size}개 · ${formatBytes(current.sumOf { it.size })} 정리 가능",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    item {
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            FileSort.entries.forEach { s ->
                                FilterChip(
                                    selected = sortOrder == s,
                                    onClick = {
                                        sortOrder = s
                                        FileSort.save(context, s)
                                    },
                                    label = { Text(s.label) },
                                )
                            }
                        }
                    }
                }

                FileCategory.entries.forEach { cat ->
                    val files = sortOrder.apply(current.filter { it.category == cat })
                    if (files.isEmpty()) return@forEach
                    val allSelected = files.all { it.path in selected }
                    val isOpen = cat in expanded

                    item(key = "header_${cat.name}") {
                        CategoryHeader(
                            category = cat,
                            count = files.size,
                            totalBytes = files.sumOf { it.size },
                            allSelected = allSelected,
                            isOpen = isOpen,
                            onToggleOpen = { expanded = if (isOpen) expanded - cat else expanded + cat },
                            onToggleAll = {
                                val paths = files.map { it.path }.toSet()
                                selected = if (allSelected) selected - paths else selected + paths
                            },
                        )
                    }
                    if (isOpen) {
                        items(files, key = { it.path }) { f ->
                            FileRow(
                                file = f,
                                checked = f.path in selected,
                                onToggle = { selected = if (f.path in selected) selected - f.path else selected + f.path },
                            )
                        }
                    }
                }
            }
        }

        if (selectedFiles.isNotEmpty()) {
            BottomActionBar(
                text = "선택한 ${selectedFiles.size}개 휴지통으로 (${formatBytes(selectedBytes)})",
                enabled = !deleting,
                onClick = { showConfirm = true },
            )
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("파일 정리") },
            text = {
                Text(
                    "${selectedFiles.size}개 파일(${formatBytes(selectedBytes)})을 휴지통으로 옮길까요?\n" +
                        "${TrashManager.keepDays(context)}일 동안 '휴지통'에서 복원할 수 있고, 그 뒤 자동으로 삭제돼요.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showConfirm = false
                    deleting = true
                    val paths = selectedFiles.map { it.path }
                    ScanState.scope.launch {
                        val (moved, freed) = withContext(Dispatchers.IO) { TrashManager.moveToTrash(context, paths) }
                        ScanState.removePaths(moved)
                        selected = selected - moved
                        deleting = false
                        val failed = paths.size - moved.size
                        val msg = buildString {
                            append("${moved.size}개 휴지통으로 이동 · ${formatBytes(freed)} 확보")
                            if (failed > 0) append(" (${failed}개 실패)")
                        }
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    }
                }) { Text("휴지통으로 이동") }
            },
            dismissButton = { TextButton(onClick = { showConfirm = false }) { Text("취소") } },
        )
    }
}

@Composable
private fun CategoryHeader(
    category: FileCategory,
    count: Int,
    totalBytes: Long,
    allSelected: Boolean,
    isOpen: Boolean,
    onToggleOpen: () -> Unit,
    onToggleAll: () -> Unit,
) {
    PremiumCard(Modifier.fillMaxWidth(), onClick = onToggleOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(category.title, style = MaterialTheme.typography.titleMedium)
                Text("${count}개 · ${formatBytes(totalBytes)}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Text(category.desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onToggleAll) { Text(if (allSelected) "선택 해제" else "전체 선택") }
            Icon(
                if (isOpen) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = if (isOpen) "접기" else "펼치기",
            )
        }
    }
}

@Composable
private fun FileRow(file: FoundFile, checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        val ctx = LocalContext.current
        FileThumbnail(file.path, Modifier.size(44.dp).clickable { ctx.openFilePreview(file.path) })
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            Text(
                "${formatBytes(file.size)} · ${formatDate(file.modified)} · ${file.folder}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
