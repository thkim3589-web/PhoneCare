package com.phonecare.app.ui

import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.phonecare.app.BuildConfig
import com.phonecare.app.data.Changelog
import com.phonecare.app.data.TrashManager
import com.phonecare.app.data.Updater
import com.phonecare.app.service.WeeklyCheck
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** 설정: 화면 테마, 정리, 알림, 권한, 앱 정보 */
@Composable
fun SettingsScreen(onClose: () -> Unit, onOpenSetup: () -> Unit, onOpenGuide: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var keepDays by remember { mutableStateOf(TrashManager.keepDays(context)) }
    var checking by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.padding(start = 4.dp, end = 16.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로") }
            Text("설정", style = MaterialTheme.typography.titleLarge)
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // ---------- 화면 ----------
            SettingsGroup("🎨", "화면 테마") {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(
                        AppSettings.THEME_AUTO to "폰 설정 따라가기",
                        AppSettings.THEME_LIGHT to "라이트",
                        AppSettings.THEME_DARK to "다크",
                    ).forEach { (mode, label) ->
                        FilterChip(
                            selected = AppSettings.themeMode == mode,
                            onClick = { AppSettings.setTheme(context, mode) },
                            label = { Text(label) },
                        )
                    }
                }
            }

            // ---------- 정리 ----------
            SettingsGroup("🗑️", "휴지통 보관 기간") {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TrashManager.keepOptions.forEach { d ->
                        FilterChip(
                            selected = keepDays == d,
                            onClick = {
                                keepDays = d
                                TrashManager.setKeepDays(context, d)
                            },
                            label = { Text("${d}일") },
                        )
                    }
                }
                Hint("기간이 지난 파일은 앱을 열 때 자동으로 영구 삭제돼요.")
            }
            SettingsGroup("🖼️", "중복 사진 분석 기록") {
                Hint("한 번 분석한 사진은 기록해 두고 다음엔 새 사진만 분석해요. 결과가 이상하면 기록을 지우고 처음부터 다시 분석하세요.")
                OutlinedButton(onClick = {
                    File(context.filesDir, "photo_hash_cache.tsv").delete()
                    ScanState.dupGroups = null
                    Toast.makeText(context, "분석 기록을 지웠어요", Toast.LENGTH_SHORT).show()
                }) { Text("분석 기록 지우기") }
            }

            // ---------- 알림 ----------
            GuardCard()
            SettingsGroup("📊", "주간 점검 알림") {
                var weeklyOn by remember { mutableStateOf(WeeklyCheck.isEnabled(context)) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "매주 일요일 저녁, 정리할 파일과 주간 리포트를 알려줘요",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    androidx.compose.material3.Switch(
                        checked = weeklyOn,
                        onCheckedChange = {
                            weeklyOn = it
                            WeeklyCheck.setEnabled(context, it)
                        },
                    )
                }
            }

            // ---------- 권한 ----------
            SettingsGroup("🔑", "권한") {
                Hint("파일 접근·사용 기록·알림·앱 설치 권한 상태를 확인하고 다시 허용할 수 있어요.")
                OutlinedButton(onClick = onOpenSetup) { Text("권한 설정 다시 보기") }
            }

            // ---------- 앱 정보 ----------
            SettingsGroup("ℹ️", "앱 정보") {
                Text(
                    "폰케어 v${BuildConfig.VERSION_NAME}" +
                        if (BuildConfig.BUILD_NUMBER > 0) " (빌드 ${BuildConfig.BUILD_NUMBER})" else "",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        enabled = !checking,
                        onClick = {
                            checking = true
                            scope.launch {
                                Updater.resetThrottle()
                                val info = withContext(Dispatchers.IO) { Updater.check(context) }
                                UpdateState.info = info
                                checking = false
                                Toast.makeText(
                                    context,
                                    if (info != null) "새 버전이 있어요! 화면 위쪽 배너에서 업데이트하세요" else "최신 버전을 쓰고 있어요",
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        },
                    ) { Text(if (checking) "확인 중…" else "업데이트 확인") }
                    OutlinedButton(onClick = onOpenGuide) { Text("사용 가이드") }
                }
                OutlinedButton(onClick = { Changelog.share(context, Changelog.local(context).take(3)) }) {
                    Text("📤 폰케어 소개·업데이트 소식 공유")
                }
                Hint("🔒 파일·사진·사용 기록 분석은 모두 휴대폰 안에서만 이뤄지고 외부로 보내지 않아요. 인터넷은 새 버전 확인과 다운로드에만 써요.")
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SettingsGroup(symbol: String, title: String, content: @Composable () -> Unit) {
    PremiumCard(Modifier.fillMaxWidth()) {
        SectionHeader(symbol, LocalBrand.current.start, title)
        content()
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
