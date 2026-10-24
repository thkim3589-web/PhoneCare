package com.phonecare.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.phonecare.app.data.AppUsage
import com.phonecare.app.data.StorageScanner
import com.phonecare.app.data.Updater

object SetupPrefs {
    private const val PREFS = "setup"
    fun isDone(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("done", false)
    fun setDone(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("done", true).apply()
}

fun hasNotificationPermission(c: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else {
        NotificationManagerCompat.from(c).areNotificationsEnabled()
    }

/** 필요한 권한이 하나라도 빠졌는지 */
fun isSetupIncomplete(c: Context): Boolean = // (앱 설치 허용은 필수가 아니라 제외)
    !StorageScanner.hasAccess(c) || !AppUsage.hasUsageAccess(c) || !hasNotificationPermission(c)

/** 첫 실행 안내: 필요한 권한을 한 화면에서 차례로 허용 */
@Composable
fun SetupScreen(resumeTick: Int, onFinish: () -> Unit) {
    val context = LocalContext.current
    val brand = LocalBrand.current
    var storage by remember { mutableStateOf(StorageScanner.hasAccess(context)) }
    var usage by remember { mutableStateOf(AppUsage.hasUsageAccess(context)) }
    var notif by remember { mutableStateOf(hasNotificationPermission(context)) }
    var install by remember { mutableStateOf(com.phonecare.app.BuildConfig.STORE || Updater.canInstall(context)) }

    fun refresh() {
        storage = StorageScanner.hasAccess(context)
        usage = AppUsage.hasUsageAccess(context)
        notif = hasNotificationPermission(context)
        install = com.phonecare.app.BuildConfig.STORE || Updater.canInstall(context)
    }

    // 설정 화면에서 돌아오면 상태 갱신
    LaunchedEffect(resumeTick) { refresh() }

    val legacyStorage = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh() }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }

    // 스토어 버전은 '앱 설치 허용'이 필요 없음
    val permTotal = if (com.phonecare.app.BuildConfig.STORE) 3 else 4
    val grantedCount = listOf(storage, usage, notif).count { it } + if (!com.phonecare.app.BuildConfig.STORE && install) 1 else 0

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        IconBadge("📱", brand.start, 64.dp)
        Text("폰케어 시작하기", style = MaterialTheme.typography.headlineMedium)
        Text(
            "제대로 점검하려면 아래 권한이 필요해요. 필요한 것만 골라서 허용해도 되고, 나중에 홈 화면에서 다시 설정할 수 있어요.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "$grantedCount / $permTotal 허용됨",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        SoftBar(progress = { grantedCount / permTotal.toFloat() }, modifier = Modifier.fillMaxWidth().height(6.dp))

        PermRow(
            symbol = "💾", tint = brand.start, title = "모든 파일 접근",
            desc = "큰 파일·중복 사진을 찾고 정리하는 데 필요해요",
            granted = storage,
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                StorageScanner.openAllFilesSettings(context)
            } else {
                legacyStorage.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE))
            }
        }
        PermRow(
            symbol = "📊", tint = brand.end, title = "사용 기록 접근",
            desc = "안 쓰는 앱, 오늘 많이 쓴 앱을 알아내는 데 필요해요. 목록에서 '폰케어'를 켜주세요",
            granted = usage,
        ) { AppUsage.openUsageAccessSettings(context) }
        PermRow(
            symbol = "🛎️", tint = ScoreWarn, title = "알림",
            desc = "충전 완료·과열 알림을 보내는 데 필요해요",
            granted = notif,
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                AppUsage.openAppInfo(context, context.packageName)
            }
        }
        if (!com.phonecare.app.BuildConfig.STORE) {
            PermRow(
                symbol = "🚀", tint = ScoreGood, title = "앱 설치 허용",
                desc = "새 버전을 버튼 한 번으로 업데이트하는 데 필요해요",
                granted = install,
            ) { Updater.openInstallPermission(context) }
        }

        Text(
            "🔒 파일·사진·사용 기록 분석은 모두 휴대폰 안에서만 이뤄지고, 외부로 보내지 않아요.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onFinish, modifier = Modifier.fillMaxWidth().height(54.dp)) {
            Text(if (grantedCount == permTotal) "시작하기" else "이대로 시작하기", fontWeight = FontWeight.Bold)
        }
        if (grantedCount < permTotal) {
            TextButton(onClick = onFinish, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("나중에 할게요")
            }
        }
    }
}

@Composable
private fun PermRow(
    symbol: String,
    tint: Color,
    title: String,
    desc: String,
    granted: Boolean,
    onRequest: () -> Unit,
) {
    PremiumCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(symbol, tint)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(8.dp))
            if (granted) {
                Text("✓ 허용됨", color = ScoreGood, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
            } else {
                FilledTonalButton(onClick = onRequest) { Text("허용") }
            }
        }
    }
}
