package com.phonecare.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 화면 제목 */
@Composable
fun ScreenTitle(title: String, subtitle: String? = null) {
    Column(Modifier.padding(top = 8.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 고급 카드: 다크는 얇은 테두리, 라이트는 부드러운 그림자 */
@Composable
fun PremiumCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val brand = LocalBrand.current
    val shape = MaterialTheme.shapes.medium
    val base = if (brand.isDark) {
        modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
    } else {
        modifier.shadow(10.dp, shape, ambientColor = Color(0x14000000), spotColor = Color(0x1A000000))
    }
    Column(
        base
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

/** 아이콘 대신 쓰는 둥근 색 배지 (이모지/글자) */
@Composable
fun IconBadge(symbol: String, tint: Color, size: Dp = 38.dp) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(tint.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, fontSize = (size.value * 0.45f).sp)
    }
}

/** 카드 제목 줄: 배지 + 제목 + 오른쪽 값 */
@Composable
fun SectionHeader(symbol: String, tint: Color, title: String, trailing: String? = null, trailingColor: Color? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconBadge(symbol, tint, 34.dp)
        Spacer(Modifier.width(10.dp))
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        if (trailing != null) {
            Text(
                trailing,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = trailingColor ?: MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** 둥근 그라데이션 진행 막대 (값이 바뀔 때 부드럽게 움직임) */
@Composable
fun SoftBar(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
) {
    val animated by animateFloatAsState(
        targetValue = progress().coerceIn(0f, 1f),
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "bar",
    )
    Box(
        modifier
            .clip(CircleShape)
            .background(trackColor),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(animated)
                .clip(CircleShape)
                .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.65f), color))),
        )
    }
}

/** 그라데이션 점수 링 (처음 열 때 차오르는 애니메이션) */
@Composable
fun ScoreRing(
    score: Int,
    color: Color,
    modifier: Modifier = Modifier,
    stroke: Dp = 14.dp,
    center: @Composable BoxScope.() -> Unit,
) {
    val sweep = remember { Animatable(0f) }
    LaunchedEffect(score) {
        sweep.animateTo(score / 100f, tween(1100, easing = FastOutSlowInEasing))
    }
    val track = MaterialTheme.colorScheme.surfaceVariant
    val brand = LocalBrand.current
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSizeCompat()) {
            val strokePx = stroke.toPx()
            val inset = strokePx / 2
            val arcSize = Size(size.width - strokePx, size.height - strokePx)
            val topLeft = Offset(inset, inset)
            drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(strokePx))
            rotate(-90f) {
                drawArc(
                    brush = Brush.sweepGradient(listOf(brand.end, color, brand.start, brand.end)),
                    startAngle = 0f,
                    sweepAngle = 360f * sweep.value,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(strokePx, cap = StrokeCap.Round),
                )
            }
        }
        center()
    }
}

/** BoxScope 밖에서도 쓸 수 있게 크기를 부모에 맞춤 */
private fun Modifier.matchParentSizeCompat(): Modifier = this.fillMaxWidth().fillMaxHeight()

/** 숫자가 바뀔 때 굴러가듯 변하는 텍스트 */
@Composable
fun AnimatedNumber(value: Int, suffix: String = "", style: androidx.compose.ui.text.TextStyle, color: Color) {
    val animated by animateIntAsState(value, tween(800), label = "num")
    Text("$animated$suffix", style = style, color = color)
}

/** 2열 그리드용 지표 타일 */
@Composable
fun StatTile(
    symbol: String,
    tint: Color,
    label: String,
    value: String,
    sub: String,
    progress: Float?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    PremiumCard(modifier, onClick) {
        IconBadge(symbol, tint)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge.merge(NumberStyle))
        }
        if (progress != null) {
            SoftBar(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = tint,
            )
        }
        Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

/** 권한 요청 카드 */
@Composable
fun PermissionCard(title: String, description: String, buttonText: String, onClick: () -> Unit) {
    val brand = LocalBrand.current
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier
                .background(Brush.linearGradient(listOf(brand.start.copy(alpha = 0.18f), brand.end.copy(alpha = 0.14f))))
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconBadge("🔐", brand.start, 44.dp)
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onClick, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text(buttonText) }
        }
    }
}
