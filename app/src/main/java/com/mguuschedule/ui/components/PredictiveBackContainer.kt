package com.mguuschedule.ui.components

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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mguuschedule.ui.theme.AppMotionScheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Единый контейнер с поддержкой предиктивного жеста «Назад» в стиле Pixel / MD3 Expressive
 * для вложенных подэкранов и полноэкранных диалогов вне NavHost.
 *
 * - Читает прогресс свайпа внутри Draw-фазы (graphicsLayer / drawBehind) без рекомпозиций на кадр.
 * - При отмене жеста плавно возвращается на MD3E Spatial-пружине.
 * - При завершении жеста плавно доводит уход без мерцания.
 */
@Composable
fun PredictiveBackContainer(
    enabled: Boolean = true,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    backdropColor: Color = Color.Unspecified,
    maxScaleReduction: Float = 0.12f,
    maxHorizontalShift: Dp = 24.dp,
    maxCornerRadius: Dp = 28.dp,
    content: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()
    val progressAnim = remember { Animatable(0f) }
    var swipeEdge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }
    var startTouchY by remember { mutableFloatStateOf(0f) }
    var touchDeltaY by remember { mutableFloatStateOf(0f) }

    PredictiveBackHandler(enabled = enabled) { progressFlow ->
        try {
            var isFirst = true
            progressFlow.collect { event ->
                if (isFirst) {
                    startTouchY = event.touchY
                    isFirst = false
                }
                swipeEdge = event.swipeEdge
                touchDeltaY = (event.touchY - startTouchY) * 0.06f
                progressAnim.snapTo(event.progress.coerceIn(0f, 1f))
            }
            progressAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 150, easing = AppMotionScheme.StandardEasing)
            )
            onBack()
        } catch (_: CancellationException) {
            scope.launch {
                progressAnim.animateTo(
                    targetValue = 0f,
                    animationSpec = AppMotionScheme.defaultSpatialSpec()
                )
                touchDeltaY = 0f
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (backdropColor != Color.Unspecified) {
                    Modifier.drawBehind {
                        val p = progressAnim.value.coerceIn(0f, 1f)
                        drawRect(backdropColor.copy(alpha = backdropColor.alpha * (1f - p)))
                    }
                } else Modifier
            )
            .graphicsLayer {
                val p = AppMotionScheme.StandardEasing.transform(progressAnim.value.coerceIn(0f, 1f))
                val scale = 1f - (p * maxScaleReduction)
                val direction = if (swipeEdge == BackEventCompat.EDGE_LEFT) 1f else -1f
                scaleX = scale
                scaleY = scale
                translationX = direction * maxHorizontalShift.toPx() * p
                translationY = touchDeltaY * p
                alpha = 1f - (p * 0.22f).coerceAtMost(0.22f)
                clip = p > 0.005f
                shape = if (clip) RoundedCornerShape((maxCornerRadius.value * p).dp) else RectangleShape
            }
    ) {
        content()
    }
}
