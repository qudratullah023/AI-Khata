package com.aikhata.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition

@Composable
fun FuturisticBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.045f)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val minSide = size.minDimension
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF27D9B1).copy(alpha = 0.13f), Color.Transparent),
                    center = Offset(size.width * 0.92f, size.height * 0.02f),
                    radius = minSide * 0.78f
                ),
                radius = minSide * 0.78f,
                center = Offset(size.width * 0.92f, size.height * 0.02f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF5C73FF).copy(alpha = 0.10f), Color.Transparent),
                    center = Offset(size.width * 0.02f, size.height * 0.62f),
                    radius = minSide * 0.72f
                ),
                radius = minSide * 0.72f,
                center = Offset(size.width * 0.02f, size.height * 0.62f)
            )
            val step = 34.dp.toPx()
            var x = 0f
            while (x < size.width) {
                drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 0.7f)
                x += step
            }
            var y = 0f
            while (y < size.height) {
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 0.7f)
                y += step
            }
        }
        content()
    }
}

@Composable
fun ScreenEntrance(
    modifier: Modifier = Modifier,
    delayMillis: Int = 0,
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (delayMillis > 0) delay(delayMillis.toLong())
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(420, easing = FastOutSlowInEasing)) +
            slideInVertically(
                animationSpec = tween(480, easing = FastOutSlowInEasing),
                initialOffsetY = { it / 14 }
            )
    ) {
        content()
    }
}

@Composable
fun NeonPanel(
    modifier: Modifier = Modifier,
    radius: Dp = 22.dp,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(radius)
    Surface(
        modifier = modifier
            .clip(shape)
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.34f),
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.18f),
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.22f)
                    )
                ),
                shape = shape
            ),
        shape = shape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        tonalElevation = 2.dp,
        shadowElevation = 5.dp
    ) {
        content()
    }
}

@Composable
fun GlowPill(
    label: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary
) {
    val pulse = rememberInfiniteTransition(label = "glow-pill-pulse")
    val dotAlpha by pulse.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1050, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow-dot-alpha"
    )
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(accent.copy(alpha = 0.11f))
            .border(1.dp, accent.copy(alpha = 0.28f), CircleShape)
            .padding(horizontal = 11.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Box(
            Modifier
                .size(6.dp)
                .alpha(dotAlpha)
                .background(accent, CircleShape)
        )
        Text(label, color = accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun AnimatedValue(
    value: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    size: Int = 24
) {
    AnimatedContent(
        targetState = value,
        modifier = modifier,
        transitionSpec = {
            fadeIn(tween(220)) togetherWith fadeOut(tween(160))
        },
        label = "animated-value"
    ) { target ->
        Text(target, color = color, fontSize = size.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ProgressGlow(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 7.dp
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 110f),
        label = "progress-glow"
    )
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            Modifier
                .fillMaxWidth(animated)
                .height(height)
                .clip(CircleShape)
                .background(
                    Brush.horizontalGradient(
                        listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)
                    )
                )
        )
    }
}

@Composable
fun Eyebrow(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text.uppercase(),
        modifier = modifier,
        color = MaterialTheme.colorScheme.primary,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.5.sp
    )
}
