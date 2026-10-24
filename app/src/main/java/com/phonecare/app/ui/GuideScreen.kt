package com.phonecare.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.phonecare.app.BuildConfig
import com.phonecare.app.data.Changelog
import com.phonecare.app.data.ReleaseNote

private data class GuideSection(
    val symbol: String,
    val title: String,
    val summary: String,
    val steps: List<String>,
)

private val guideSections = listOf(
    GuideSection(
        "🚀", "처음 시작하기", "권한 4가지만 허용하면 모든 기능을 쓸 수 있어요",
        listOf(
            "💾 모든 파일 접근 — 큰 파일·중복 사진·메신저 파일을 찾고 정리할 때 필요해요.",
            "📊 사용 기록 접근 — 안 쓰는 앱, 많이 쓴 앱, 데이터 사용량을 볼 때 필요해요. 목록에서 '폰케어'를 켜주세요.",
            "🛎️ 알림 — 충전 완료·과열 알림을 받을 때 필요해요.",
            "🚀 앱 설치 허용 — 새 버전을 버튼 한 번으로 업데이트할 때 필요해요.",
            "빠진 권한이 있으면 홈의 추천 조치에 '권한 설정 마저 하기'가 떠요. 누르면 한 번에 설정할 수 있어요.",
            "🔒 모든 분석은 휴대폰 안에서만 이뤄지고 외부로 보내지 않아요.",
        ),
    ),
    GuideSection(
        "🏠", "홈", "관리 점수와 휴대폰 상태를 한눈에",
        listOf(
            "관리 점수(0~100)는 저장공간 여유, 메모리, 배터리 온도·상태를 합쳐서 계산해요. 80점 이상이면 아주 좋은 상태예요.",
            "저장공간·메모리·배터리·온도 타일을 누르면 해당 화면으로 바로 이동해요.",
            "'추천 조치'에는 지금 하면 좋은 일이 나와요. 눌러서 바로 처리하세요.",
            "홈 화면 위젯: 추천 조치의 '홈 화면에 위젯 추가'를 누르거나, 홈 화면 빈 곳을 길게 눌러 [위젯 → 폰케어]를 고르세요.",
        ),
    ),
    GuideSection(
        "⚙️", "성능 — CPU·메모리", "실시간 CPU 상태와 메모리 정리",
        listOf(
            "CPU 부하는 코어별 속도(클럭)로 추정한 값이에요. 안드로이드 정책상 정확한 사용률은 앱이 읽을 수 없어요.",
            "'발열 상태'가 '성능 제한 중'이면 폰이 뜨거워서 일부러 속도를 낮추는 중이에요. 잠시 쉬게 해주세요.",
            "메모리 정리 버튼 → 삼성 디바이스 케어 메모리 화면이 열려요 → '지금 정리'를 누르세요.",
            "다른 화면이 열리면 버튼 아래 '직접 고르기'에서 '지금 정리'가 있는 화면을 골라 저장하세요.",
            "메모리 사용률이 높아도 정상이에요. 안드로이드는 남는 메모리로 앱을 빨리 여는 데 써요. 버벅일 때만 정리하세요.",
        ),
    ),
    GuideSection(
        "🔋", "성능 — 배터리·발열", "충전·과열 알림과 실시간 배터리 정보",
        listOf(
            "성능 탭 위쪽에서 '배터리·발열'을 누르세요.",
            "충전·과열 알림 스위치를 켜면 앱을 꺼도 계속 지켜봐요. 충전 한도(기본 80%)와 과열 기준(기본 42°C)을 고를 수 있어요.",
            "알림이 늦거나 멈추면 '배터리 최적화 제외하기'를 눌러주세요.",
            "충전·소모 전력, 전류, 온도, 남은 시간, 최근 3분 온도 그래프를 실시간으로 보여줘요.",
            "'오늘 많이 쓴 앱'에서 게임·동영상처럼 발열이 큰 앱은 '고부하'로 표시돼요.",
        ),
    ),
    GuideSection(
        "💾", "저장공간 정리", "지운 파일은 휴지통에 7일 보관돼요",
        listOf(
            "정리 후보: 설치 파일(APK), 스크린샷, 90일 지난 다운로드, 100MB 넘는 큰 파일을 찾아줘요. 정렬 순서(큰 순·오래된 순 등)를 바꿀 수 있어요.",
            "중복 사진: 똑같은 사진과 연속 촬영한 비슷한 사진을 묶어요. '남기기 추천' 1장을 빼고 자동 선택돼요. 사진을 눌러 선택을 바꿀 수 있어요.",
            "💬 메신저: 카톡·텔레그램 등으로 받은 파일을 앱·종류·기간별로 골라 정리해요. 카톡 안의 캐시는 카톡 [설정 → 기타 → 저장공간 관리]에서 지워요.",
            "휴지통: 지운 파일은 7일 동안 보관돼요. 골라서 원래 자리로 복원하거나 영구 삭제할 수 있어요.",
            "🛡️ 보호 폴더: '관리'를 눌러 업무 사진 같은 폴더를 지정하면 검사에서 완전히 빠져요.",
        ),
    ),
    GuideSection(
        "📱", "앱 관리", "안 쓰는 앱·권한·알림·데이터",
        listOf(
            "📦 안 쓰는 앱: 30·90·180일 넘게 안 쓴 앱과 용량을 보여줘요. '삭제'로 바로 지울 수 있어요.",
            "🔐 권한: 위치·카메라·마이크 같은 민감한 권한을 가진 앱을 모아요. 오래 안 썼는데 권한이 있는 앱은 빨간색으로 표시돼요.",
            "🔔 알림: 알림을 많이 보내는 앱 순위예요. '알림 접근'을 켜면 정확해져요 (알림 내용은 읽지 않고 개수만 세요).",
            "📶 데이터: 이번 달 앱별 모바일·와이파이 사용량이에요.",
        ),
    ),
    GuideSection(
        "✅", "팁", "효과 있는 최적화 설정 체크리스트",
        listOf(
            "애니메이션 배율 줄이기, 캐시 지우기, 백그라운드 제한 등 실제로 효과 있는 설정만 모았어요.",
            "각 항목의 버튼을 누르면 해당 설정 화면이 바로 열려요. 해본 항목은 체크해 두세요.",
        ),
    ),
    GuideSection(
        "🔄", "업데이트", "새 버전은 버튼 한 번으로",
        listOf(
            "새 버전이 나오면 앱 위쪽에 '🚀 새 버전이 있어요' 배너가 떠요. '업데이트 내용'으로 바뀐 점을 먼저 볼 수 있어요.",
            "'지금 업데이트' → 설치 화면에서 '업데이트'를 누르면 끝이에요.",
            "업데이트 후 처음 열면 바뀐 내용을 알려줘요. '공유'로 가족·동료에게 보낼 수 있어요.",
        ),
    ),
    GuideSection(
        "❓", "자주 묻는 질문", "설치 경고, 메모리 정리, 파일 복구 등",
        listOf(
            "Q. 설치할 때 '무시하고 설치' 경고가 떠요 → 플레이 스토어 밖에서 받은 앱이라 구글이 띄우는 경고예요. 폰케어는 안전해요.",
            "Q. 메모리를 폰케어가 직접 정리하지 않는 이유는? → 안드로이드 14부터 시스템 앱만 다른 앱을 종료할 수 있어요. 그래서 삼성 메모리 화면을 열어드려요.",
            "Q. 실수로 지운 파일은? → 저장공간 탭 → 휴지통에서 7일 안에 복원할 수 있어요.",
            "Q. 알림 접근을 켜면 내 알림 내용을 보나요? → 아니요. 어느 앱이 보냈는지 개수만 세요.",
            "Q. 앱이 갑자기 꺼졌어요 → 다시 열면 '오류 복사' 버튼이 나와요. 그 내용을 개발자에게 보내주세요.",
        ),
    ),
)

/** 사용 가이드 + 업데이트 내역 화면 */
@Composable
fun GuideScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(0) }
    var opened by remember { mutableStateOf(setOf(0)) }
    val notes = remember { Changelog.local(context) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.padding(start = 4.dp, end = 16.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로") }
            Text("폰케어 가이드", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Text(
                "v${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = tab == 0, onClick = { tab = 0 }, label = { Text("📖 사용 방법") })
            FilterChip(selected = tab == 1, onClick = { tab = 1 }, label = { Text("📝 업데이트 내역") })
        }

        if (tab == 0) {
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexedCompat(guideSections) { i, sec ->
                    val isOpen = i in opened
                    PremiumCard(
                        Modifier
                            .fillMaxWidth()
                            .animateContentSize(),
                        onClick = { opened = if (isOpen) opened - i else opened + i },
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(sec.symbol, LocalBrand.current.start)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(sec.title, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    sec.summary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Icon(
                                if (isOpen) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                contentDescription = null,
                            )
                        }
                        if (isOpen) {
                            sec.steps.forEachIndexed { n, step ->
                                Row {
                                    Text(
                                        "${n + 1}",
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.width(22.dp),
                                    )
                                    Text(step, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    OutlinedButton(onClick = { Changelog.share(context, notes.take(3)) }, modifier = Modifier.fillMaxWidth()) {
                        Text("📤 최근 업데이트 소식 공유하기")
                    }
                }
                items(notes, key = { it.code }) { note -> ReleaseNoteCard(note, isCurrent = note.code == BuildConfig.VERSION_CODE) }
            }
        }
    }
}

@Composable
private fun ReleaseNoteCard(note: ReleaseNote, isCurrent: Boolean) {
    PremiumCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("v${note.version}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (isCurrent) {
                Text(
                    "현재 버전  ",
                    style = MaterialTheme.typography.labelMedium,
                    color = ScoreGood,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(note.date, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        note.items.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
    }
}

/** 업데이트 직후 한 번 보여주는 "새로 바뀐 점" */
@Composable
fun WhatsNewDialog(notes: List<ReleaseNote>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val title = notes.firstOrNull()?.let { "폰케어 ${it.version} 업데이트 내용" } ?: "업데이트 내용"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                notes.forEach { n ->
                    if (notes.size > 1) {
                        Text("v${n.version}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    n.items.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("확인") } },
        dismissButton = {
            TextButton(onClick = { Changelog.share(context, notes) }) { Text("공유") }
        },
    )
}

/** 업데이트 배너에서 "업데이트 내용" 눌렀을 때 */
@Composable
fun UpcomingNotesDialog(notes: List<ReleaseNote>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("새 버전에서 바뀌는 점") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (notes.isEmpty()) {
                    Text("내용을 불러오지 못했어요. 업데이트 후 가이드의 '업데이트 내역'에서 볼 수 있어요.")
                }
                notes.forEach { n ->
                    Text("v${n.version}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    n.items.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
}

/** LazyColumn에서 순번과 함께 그리기 */
private fun <T> androidx.compose.foundation.lazy.LazyListScope.itemsIndexedCompat(
    list: List<T>,
    content: @Composable (Int, T) -> Unit,
) {
    list.forEachIndexed { i, t -> item(key = i) { content(i, t) } }
}

@Suppress("unused")
private val unusedColor = Color.Unspecified

@Suppress("unused")
private fun Modifier.clickableNoop() = this.clickable { }
