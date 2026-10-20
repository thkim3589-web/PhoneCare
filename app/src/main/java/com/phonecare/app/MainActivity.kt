package com.phonecare.app

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.phonecare.app.data.TrashManager
import com.phonecare.app.service.GuardSettings
import com.phonecare.app.ui.AppsTab
import com.phonecare.app.ui.HomeScreen
import com.phonecare.app.ui.PerformanceScreen
import com.phonecare.app.ui.PhoneCareTheme
import com.phonecare.app.ui.SetupPrefs
import com.phonecare.app.ui.SetupScreen
import com.phonecare.app.ui.StorageScreen
import com.phonecare.app.ui.TipsScreen
import com.phonecare.app.ui.UpdateBanner
import com.phonecare.app.util.CrashLog
import com.phonecare.app.widget.PhoneCareWidget

/** 홈 화면 추천에서 "권한 설정"을 눌렀을 때 쓰는 특별 번호 */
const val NAV_SETUP = 99

/** 홈 화면 추천에서 "위젯 추가"를 눌렀을 때 쓰는 특별 번호 */
const val NAV_WIDGET = 98

class MainActivity : ComponentActivity() {

    // 설정 화면에서 돌아올 때마다 값이 바뀌어 각 화면이 권한/목록을 다시 확인하게 함
    private var resumeTick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashLog.install(this)
        enableEdgeToEdge()
        // 보관 기간(7일)이 지난 휴지통 파일 자동 삭제
        val appContext = applicationContext
        Thread {
            try {
                TrashManager.purgeExpired(appContext)
            } catch (e: Exception) {
            }
        }.start()
        // 충전·과열 알림이 켜져 있으면 감시 서비스 유지
        if (GuardSettings.isEnabled(this)) GuardSettings.start(this)
        setContent {
            PhoneCareTheme {
                MainApp(resumeTick)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeTick++
        PhoneCareWidget.updateAll(this, force = true)
    }
}

private data class TabItem(val label: String, val icon: ImageVector)

private val tabItems = listOf(
    TabItem("홈", Icons.Filled.Home),
    TabItem("성능", Icons.Filled.Build),
    TabItem("저장공간", Icons.Filled.Delete),
    TabItem("앱", Icons.AutoMirrored.Filled.List),
    TabItem("팁", Icons.Filled.CheckCircle),
)

@Composable
private fun MainApp(resumeTick: Int) {
    val context = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showSetup by rememberSaveable { mutableStateOf(!SetupPrefs.isDone(context)) }
    var crash by remember { mutableStateOf(CrashLog.read(context)) }

    if (showSetup) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .systemBarsPadding(),
        ) {
            SetupScreen(resumeTick) {
                SetupPrefs.setDone(context)
                showSetup = false
            }
        }
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 0.dp,
            ) {
                tabItems.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label, fontWeight = if (tab == index) FontWeight.Bold else FontWeight.Normal) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            UpdateBanner(resumeTick)
            Box(Modifier.weight(1f)) {
                when (tab) {
                    0 -> HomeScreen(resumeTick) { target ->
                        when (target) {
                            NAV_SETUP -> showSetup = true
                            NAV_WIDGET -> if (!PhoneCareWidget.requestPin(context)) {
                                Toast.makeText(
                                    context,
                                    "홈 화면 빈 곳을 길게 누르고 [위젯 → 폰케어]를 선택해 추가해 주세요",
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                            else -> tab = target
                        }
                    }
                    1 -> PerformanceScreen(resumeTick)
                    2 -> StorageScreen(resumeTick)
                    3 -> AppsTab(resumeTick)
                    else -> TipsScreen()
                }
            }
        }
    }

    // 지난번 비정상 종료 기록이 있으면 안내
    crash?.let { log ->
        AlertDialog(
            onDismissRequest = {
                CrashLog.clear(context)
                crash = null
            },
            title = { Text("앱이 비정상 종료됐었어요") },
            text = {
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    Text(
                        "불편을 드려 죄송해요. '오류 복사'를 눌러 내용을 Claude에게 보내주시면 빠르게 고칠 수 있어요.\n",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        log.take(1500),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    context.getSystemService(ClipboardManager::class.java)
                        ?.setPrimaryClip(ClipData.newPlainText("PhoneCare crash", log))
                    Toast.makeText(context, "오류 내용을 복사했어요. 채팅창에 붙여넣어 주세요", Toast.LENGTH_LONG).show()
                    CrashLog.clear(context)
                    crash = null
                }) { Text("오류 복사") }
            },
            dismissButton = {
                TextButton(onClick = {
                    CrashLog.clear(context)
                    crash = null
                }) { Text("닫기") }
            },
        )
    }
}
