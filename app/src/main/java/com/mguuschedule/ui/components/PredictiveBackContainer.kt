package com.mguuschedule.ui.components

import android.os.Build
import android.view.RoundedCorner
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mguuschedule.ui.theme.AppMotionScheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Определяет реальный радиус скругления углов экрана устройства (API 31+, Pixel/Android 12+)
 * с безопасным фолбэком 28.dp для остальных устройств и эмуляторов.
 */
@Composable
fun rememberDeviceCornerRadius(fallback: Dp = 28.dp): Dp {
    val view = LocalView.current
    val density = LocalDensity.current
    return remember(view, density, fallback) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val radiusPx = view.rootWindowInsets
                ?.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)
                ?.radius
                ?.takeIf { it > 0 }
            if (radiusPx != null) {
                with(density) { radiusPx.toDp() }.coerceIn(20.dp, 44.dp)
            } else {
                fallback
            }
        } else {
            fallback
        }
    }
}

/**
 * Контейнер с поддержкой предиктивного жеста «Назад» по официальным гайдлайнам Google
 * (Predictive Back Design Spec: `STANDARD_DECELERATE`, масштаб 90%, отступ 8.dp от края,
 * сдвиг по X/Y от края свайпа, аппаратное скругление под углы экрана, плавный уход при коммите).
 *
 * Предназначен для вложенных подэкранов и полноэкранных диалогов вне NavHost.
 */
@Composable
fun PredictiveBackContainer(
    enabled: Boolean = true,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    backdropColor: Color = Color.Unspecified,
    maxScaleReduction: Float = 0.10f,
    maxHorizontalShift: Dp? = null,
    maxCornerRadius: Dp? = null,
    maxShadowElevation: Dp = 14.dp,
    content: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    val deviceCornerRadius = rememberDeviceCornerRadius()
    val effectiveCornerRadius = maxCornerRadius ?: deviceCornerRadius

    // Официальная формула Google: X shift = (screenWidthDp / 20) - 8dp (оставляет 8dp от края при scale 0.9)
    val computedMaxShiftX = remember(windowInfo, density, maxHorizontalShift) {
        maxHorizontalShift ?: run {
            val widthDp = with(density) { windowInfo.containerSize.width.toDp().value }
            ((widthDp / 20f) - 8f).coerceIn(16f, 56f).dp
        }
    }
    val computedMaxShiftY = remember(windowInfo, density) {
        val heightDp = with(density) { windowInfo.containerSize.height.toDp().value }
        ((heightDp / 20f) - 8f).coerceIn(12f, 44f).dp
    }

    val progressAnim = remember { Animatable(0f) }
    val commitExitAnim = remember { Animatable(0f) }
    var swipeEdge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }
    var startTouchY by remember { mutableFloatStateOf(0f) }
    var normalizedDeltaY by remember { mutableFloatStateOf(0f) }

    androidx.compose.runtime.LaunchedEffect(enabled) {
        if (enabled) {
            progressAnim.snapTo(0f)
            commitExitAnim.snapTo(0f)
            normalizedDeltaY = 0f
        }
    }

    PredictiveBackHandler(enabled = enabled) { progressFlow ->
        try {
            var isFirst = true
            val screenHeightPx = windowInfo.containerSize.height.coerceAtLeast(1).toFloat()
            progressFlow.collect { event ->
                if (isFirst) {
                    startTouchY = event.touchY
                    isFirst = false
                }
                swipeEdge = event.swipeEdge
                val rawDelta = ((event.touchY - startTouchY) / (screenHeightPx * 0.5f)).coerceIn(-1f, 1f)
                normalizedDeltaY = rawDelta
                progressAnim.snapTo(event.progress.coerceIn(0f, 1f))
            }
            // Коммит жеста: плавный уход непрозрачной поверхности за край экрана в сторону свайпа
            commitExitAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 220, easing = AppMotionScheme.EmphasizedDecelerateEasing)
            )
            onBack()
        } catch (_: CancellationException) {
            scope.launch {
                progressAnim.animateTo(
                    targetValue = 0f,
                    animationSpec = AppMotionScheme.defaultSpatialSpec()
                )
                normalizedDeltaY = 0f
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (backdropColor != Color.Unspecified) {
                    Modifier.drawBehind {
                        val p = AppMotionScheme.StandardDecelerateEasing.transform(progressAnim.value.coerceIn(0f, 1f))
                        val exit = commitExitAnim.value.coerceIn(0f, 1f)
                        val backdropAlpha = backdropColor.alpha * (1f - (p * 0.65f)) * (1f - exit)
                        if (backdropAlpha > 0.001f) {
                            drawRect(backdropColor.copy(alpha = backdropAlpha))
                        }
                    }
                } else Modifier
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val rawP = progressAnim.value.coerceIn(0f, 1f)
                    val p = AppMotionScheme.StandardDecelerateEasing.transform(rawP)
                    val exit = commitExitAnim.value.coerceIn(0f, 1f)

                    val scale = (1f - (p * maxScaleReduction)) * (1f - (exit * 0.02f))
                    val direction = if (swipeEdge == BackEventCompat.EDGE_LEFT) 1f else -1f
                    val previewShiftX = direction * computedMaxShiftX.toPx() * p
                    val commitSlideX = direction * size.width * exit

                    scaleX = scale
                    scaleY = scale
                    translationX = previewShiftX + commitSlideX
                    translationY = normalizedDeltaY * computedMaxShiftY.toPx() * p

                    val cornerFraction = (p + exit).coerceIn(0f, 1f)
                    val radiusPx = (effectiveCornerRadius.toPx() * cornerFraction)
                    val isRounded = radiusPx > 1f
                    shadowElevation = if (isRounded) maxShadowElevation.toPx() * cornerFraction * (1f - exit) else 0f
                    clip = isRounded
                    shape = if (isRounded) RoundedCornerShape(radiusPx) else RectangleShape
                }
        ) {
            content()
        }
    }
}
