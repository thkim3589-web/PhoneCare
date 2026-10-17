package com.phonecare.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.phonecare.app.data.TrashManager
import com.phonecare.app.service.GuardSettings
import com.phonecare.app.ui.AppsScreen
import com.phonecare.app.ui.HomeScreen
import com.phonecare.app.ui.PerformanceScreen
import com.phonecare.app.ui.PhoneCareTheme
import com.phonecare.app.ui.StorageScreen
import com.phonecare.app.ui.TipsScreen
import com.phonecare.app.ui.UpdateBanner

class MainActivity : ComponentActivity() {

    // 설정 화면에서 돌아올 때마다 값이 바뀌어 각 화면이 권한/목록을 다시 확인하게 함
    private var resumeTick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
    var tab by rememberSaveable { mutableIntStateOf(0) }

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
                    0 -> HomeScreen(resumeTick) { tab = it }
                    1 -> PerformanceScreen(resumeTick)
                    2 -> StorageScreen(resumeTick)
                    3 -> AppsScreen(resumeTick)
                    else -> TipsScreen()
                }
            }
        }
    }
}
