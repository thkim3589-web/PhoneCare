package com.phonecare.app.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.phonecare.app.util.openSettings

private data class Tip(
    val id: String,
    val title: String,
    val desc: String,
    val buttonText: String? = null,
    val intentAction: String? = null,
)

private val tips = listOf(
    Tip(
        "storage", "저장공간 15% 이상 비워두기",
        "저장공간이 꽉 차면 체감 속도가 가장 크게 떨어져요. '저장공간' 탭에서 정리하거나 사진은 클라우드로 옮기세요.",
        "저장공간 설정", Settings.ACTION_INTERNAL_STORAGE_SETTINGS,
    ),
    Tip(
        "animation", "애니메이션 배율 0.5x로 줄이기",
        "화면 전환이 눈에 띄게 빨라져요. 먼저 [설정 → 휴대전화 정보 → 소프트웨어 정보 → 빌드번호]를 7번 눌러 개발자 옵션을 켠 뒤, " +
            "개발자 옵션에서 '창·전환·Animator 애니메이션 배율'을 모두 0.5x로 바꾸세요.",
        "개발자 옵션 열기", Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS,
    ),
    Tip(
        "unused", "안 쓰는 앱 삭제하기",
        "안 쓰는 앱도 백그라운드에서 업데이트·알림·동기화를 하며 자원을 써요. '앱' 탭에서 확인하세요.",
    ),
    Tip(
        "cache", "용량 큰 앱의 캐시 지우기",
        "메신저·유튜브·SNS 앱은 캐시가 몇 GB씩 쌓여요. [앱 → 해당 앱 → 저장공간 → 캐시 삭제]를 누르세요. (데이터 삭제와 달리 로그인·대화는 유지돼요)",
        "앱 목록 열기", Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS,
    ),
    Tip(
        "update", "시스템·앱 최신 업데이트",
        "업데이트에는 성능 개선과 배터리 최적화가 포함되는 경우가 많아요. [설정 → 소프트웨어 업데이트]와 Play 스토어 업데이트를 확인하세요.",
        "설정 열기", Settings.ACTION_SETTINGS,
    ),
    Tip(
        "battery", "배터리 절약·백그라운드 제한 사용",
        "자주 안 쓰는 앱은 백그라운드 사용을 제한하면 배터리와 발열이 줄어요. (삼성: 배터리 → 백그라운드 사용 제한)",
        "배터리 설정", Intent.ACTION_POWER_USAGE_SUMMARY,
    ),
    Tip(
        "widget", "라이브 배경화면·위젯 줄이기",
        "움직이는 배경화면과 실시간 위젯은 계속 화면을 그리고 데이터를 받아와서 배터리와 성능을 써요.",
        "디스플레이 설정", Settings.ACTION_DISPLAY_SETTINGS,
    ),
    Tip(
        "sync", "불필요한 계정 동기화 끄기",
        "쓰지 않는 계정이나 앱의 자동 동기화를 끄면 백그라운드 작업이 줄어요.",
        "동기화 설정", Settings.ACTION_SYNC_SETTINGS,
    ),
    Tip(
        "restart", "일주일에 한 번 재부팅",
        "오래 켜두면 쌓이는 임시 상태가 초기화돼요. 삼성은 [디바이스 케어 → 자동 최적화]로 자동 재시작을 예약할 수 있어요.",
    ),
    Tip(
        "charge", "충전 습관 관리",
        "배터리는 20~80% 사이를 유지하고, 충전 중 게임처럼 발열이 심한 사용은 피하세요. 지원 기종이면 '배터리 보호(85%)'를 켜세요.",
    ),
)

private const val PREFS = "tips"
private const val KEY_DONE = "done"

private fun loadDone(context: Context): Set<String> =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(KEY_DONE, emptySet())?.toSet() ?: emptySet()

private fun saveDone(context: Context, done: Set<String>) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet(KEY_DONE, done).apply()
}

@Composable
fun TipsScreen() {
    val context = LocalContext.current
    var done by remember { mutableStateOf(loadDone(context)) }

    fun toggle(id: String) {
        done = if (id in done) done - id else done + id
        saveDone(context, done)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            ScreenTitle("최적화 체크리스트", "실제로 효과 있는 설정만 모았어요. 해본 항목은 체크하세요")
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "${done.size} / ${tips.size} 완료",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                SoftBar(
                    progress = { done.size.toFloat() / tips.size },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
        }
        items(tips, key = { it.id }) { tip ->
            val checked = tip.id in done
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 12.dp)) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { toggle(tip.id) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = checked, onCheckedChange = { toggle(tip.id) })
                        Text(
                            tip.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            textDecoration = if (checked) TextDecoration.LineThrough else null,
                            color = if (checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Column(Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            tip.desc,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (tip.buttonText != null && tip.intentAction != null) {
                            OutlinedButton(onClick = { context.openSettings(Intent(tip.intentAction)) }) {
                                Text(tip.buttonText)
                            }
                        }
                    }
                }
            }
        }
    }
}
