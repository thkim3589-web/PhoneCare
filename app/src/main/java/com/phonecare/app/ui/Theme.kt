package com.phonecare.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------- 브랜드 팔레트 ----------
private val Mint = Color(0xFF2DE1C2)
private val MintDeep = Color(0xFF0E9F8A)
private val Azure = Color(0xFF5B8CFF)
private val AzureDeep = Color(0xFF3466E8)

/** 다크 프리미엄: 깊은 남흑색 + 네온 민트 */
private val DarkColors = darkColorScheme(
    primary = Mint,
    onPrimary = Color(0xFF00241E),
    primaryContainer = Color(0xFF0D3B35),
    onPrimaryContainer = Color(0xFFB5FFF0),
    secondary = Azure,
    onSecondary = Color(0xFF001A4D),
    background = Color(0xFF080B10),
    onBackground = Color(0xFFE8EDF3),
    surface = Color(0xFF080B10),
    onSurface = Color(0xFFE8EDF3),
    onSurfaceVariant = Color(0xFF8C98A8),
    surfaceVariant = Color(0xFF1E2733),
    surfaceContainerLowest = Color(0xFF080B10),
    surfaceContainerLow = Color(0xFF0F141B),
    surfaceContainer = Color(0xFF121922),
    surfaceContainerHigh = Color(0xFF151D27),
    surfaceContainerHighest = Color(0xFF141C26), // 카드 배경
    outline = Color(0xFF2A3542),
    outlineVariant = Color(0xFF1F2833),
    error = Color(0xFFFF5C6C),
)

/** 라이트 클린: 밝은 회백색 + 딥 민트 */
private val LightColors = lightColorScheme(
    primary = MintDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD5F7F0),
    onPrimaryContainer = Color(0xFF00201B),
    secondary = AzureDeep,
    onSecondary = Color.White,
    background = Color(0xFFF3F5F8),
    onBackground = Color(0xFF111827),
    surface = Color(0xFFF3F5F8),
    onSurface = Color(0xFF111827),
    onSurfaceVariant = Color(0xFF6B7280),
    surfaceVariant = Color(0xFFE6EAF0),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF8F9FB),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color.White,        // 카드 배경
    outline = Color(0xFFD9DEE5),
    outlineVariant = Color(0xFFE8EBF0),
    error = Color(0xFFE5484D),
)

// 상태 색 (다크/라이트 공통으로 잘 보이는 톤)
val ScoreGood = Color(0xFF22C55E)
val ScoreWarn = Color(0xFFF59E0B)
val ScoreBad = Color(0xFFEF4444)

/** 그라데이션 등에 쓰는 브랜드 색 묶음 */
data class BrandColors(val start: Color, val end: Color, val glow: Color, val isDark: Boolean)

val LocalBrand = staticCompositionLocalOf { BrandColors(MintDeep, AzureDeep, MintDeep, false) }

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(22.dp),   // 카드
    large = RoundedCornerShape(28.dp),    // 다이얼로그
    extraLarge = RoundedCornerShape(32.dp),
)

private val AppTypography = Typography().run {
    copy(
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, letterSpacing = (-0.5).sp),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold),
        bodySmall = bodySmall.copy(lineHeight = 18.sp),
    )
}

/** 큰 숫자 표시용 */
val NumberStyle = TextStyle(fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp)

@Composable
fun PhoneCareTheme(content: @Composable () -> Unit) {
    val dark = when (AppSettings.themeMode) {
        AppSettings.THEME_LIGHT -> false
        AppSettings.THEME_DARK -> true
        else -> isSystemInDarkTheme()
    }
    val brand = if (dark) {
        BrandColors(Mint, Azure, Mint.copy(alpha = 0.35f), true)
    } else {
        BrandColors(MintDeep, AzureDeep, MintDeep.copy(alpha = 0.18f), false)
    }
    CompositionLocalProvider(LocalBrand provides brand) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            shapes = AppShapes,
            typography = AppTypography,
            content = content,
        )
    }
}
