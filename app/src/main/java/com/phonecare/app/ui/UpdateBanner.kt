package com.phonecare.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.phonecare.app.data.Changelog
import com.phonecare.app.data.UpdateInfo
import com.phonecare.app.data.Updater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 새 버전 정보 (설정 화면의 '업데이트 확인'과 배너가 함께 씀) */
object UpdateState {
    var info by mutableStateOf<UpdateInfo?>(null)
}

/** 화면 위쪽에 뜨는 "새 버전" 배너. 앱을 열거나 돌아올 때마다 확인 */
@Composable
fun UpdateBanner(resumeTick: Int) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val brand = LocalBrand.current
    var info by UpdateState::info
    var dismissedTag by remember { mutableStateOf<String?>(null) }
    var upcoming by remember { mutableStateOf<List<com.phonecare.app.data.ReleaseNote>?>(null) }

    LaunchedEffect(resumeTick) {
        info = withContext(Dispatchers.IO) { Updater.check(context) }
    }

    val current = info
    AnimatedVisibility(visible = current != null && current.tag != dismissedTag) {
        if (current == null) return@AnimatedVisibility
        Column(
            Modifier
                .padding(start = 16.dp, end = 16.dp, top = 8.dp)
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(Brush.linearGradient(listOf(brand.start.copy(alpha = 0.25f), brand.end.copy(alpha = 0.22f))))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge("🚀", brand.start, 36.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("새 버전이 있어요", style = MaterialTheme.typography.titleMedium)
                    Text(
                        current.name.ifBlank { current.tag },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { dismissedTag = current.tag }) { Text("나중에") }
                TextButton(onClick = {
                    scope.launch {
                        val mine = com.phonecare.app.BuildConfig.VERSION_CODE
                        upcoming = withContext(Dispatchers.IO) { Changelog.remote() }.filter { it.code > mine }
                    }
                }) { Text("업데이트 내용") }
                Spacer(Modifier.weight(1f))
                Button(onClick = {
                    if (Updater.openDownload(context, current)) {
                        Toast.makeText(
                            context,
                            "다운로드가 끝나면 알림(또는 내 파일 → 다운로드)의 PhoneCare.apk를 눌러 설치하세요",
                            Toast.LENGTH_LONG,
                        ).show()
                    } else {
                        context.getSystemService(ClipboardManager::class.java)
                            ?.setPrimaryClip(ClipData.newPlainText("PhoneCare", Updater.LATEST_APK_URL))
                        Toast.makeText(context, "인터넷 창을 열 수 없어 다운로드 주소를 복사했어요", Toast.LENGTH_LONG).show()
                    }
                }) { Text("지금 업데이트") }
            }
        }
    }

    upcoming?.let { notes -> UpcomingNotesDialog(notes) { upcoming = null } }
}
