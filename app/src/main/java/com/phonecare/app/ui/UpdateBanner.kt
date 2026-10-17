package com.phonecare.app.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.phonecare.app.data.UpdateInfo
import com.phonecare.app.data.Updater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** 화면 위쪽에 뜨는 "새 버전" 배너. 앱을 열거나 돌아올 때마다 확인 */
@Composable
fun UpdateBanner(resumeTick: Int) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val brand = LocalBrand.current
    var info by remember { mutableStateOf<UpdateInfo?>(null) }
    var dismissedTag by remember { mutableStateOf<String?>(null) }
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var readyApk by remember { mutableStateOf<File?>(null) }

    LaunchedEffect(resumeTick) {
        if (!downloading) info = withContext(Dispatchers.IO) { Updater.check(context) }
    }

    fun installNow(apk: File) {
        if (Updater.canInstall(context)) {
            Updater.install(context, apk)
        } else {
            Toast.makeText(context, "'이 출처 허용'을 켠 뒤 돌아와서 다시 눌러주세요", Toast.LENGTH_LONG).show()
            Updater.openInstallPermission(context)
        }
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
            if (downloading) {
                Text("다운로드 중… ${(progress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                SoftBar(progress = { progress }, modifier = Modifier.fillMaxWidth().height(8.dp))
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { dismissedTag = current.tag }) { Text("나중에") }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = {
                        val apk = readyApk
                        if (apk != null && apk.exists()) {
                            installNow(apk)
                        } else {
                            downloading = true
                            progress = 0f
                            scope.launch {
                                val file = withContext(Dispatchers.IO) {
                                    Updater.download(context, current) { p -> progress = p }
                                }
                                downloading = false
                                if (file == null) {
                                    Toast.makeText(context, "다운로드에 실패했어요. 인터넷 연결을 확인해 주세요", Toast.LENGTH_LONG).show()
                                } else {
                                    readyApk = file
                                    installNow(file)
                                }
                            }
                        }
                    }) { Text(if (readyApk != null) "설치하기" else "지금 업데이트") }
                }
            }
        }
    }
}
