package com.mguuschedule.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Четырехкомпонентный кортеж параметров геометрии фигуры для морфинга.
 */
private data class MorphGeometry(
    val corners1: Float,
    val amp1: Float,
    val corners2: Float,
    val amp2: Float
)

/**
 * Самостоятельная геометрическая фигура Material 3 Expressive (Expressive Morphing Shape).
 * Является единственным и неделимым визуальным объектом индикатора обновления.
 * Не содержит никаких круглых контейнеров, круговых дуг, спиннеров или икон внутри круга.
 */
@Composable
fun ExpressiveMorphingShape(
    morphProgress: Float,
    rotationDegrees: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    fillColor: Color = MaterialTheme.colorScheme.primaryContainer
) {
    Canvas(
        modifier = modifier.graphicsLayer { rotationZ = rotationDegrees }
    ) {
        val sizePx = size.minDimension
        val center = Offset(sizePx / 2f, sizePx / 2f)
        val baseRadius = sizePx * 0.36f

        // Непрерывный геометрический морфинг между формами Material 3 Expressive:
        // Фаза 0..1: 4-pointed Soft Burst -> Squircle -> 8-pointed Flower -> Pill/Oval -> 4-pointed Soft Burst
        val phase = (morphProgress * 3f) % 3f
        val geom = when {
            phase < 1f -> {
                val t = phase
                MorphGeometry(
                    corners1 = 4f,
                    amp1 = 0.28f * (1f - t) + 0.08f * t,
                    corners2 = 8f,
                    amp2 = 0.0f
                )
            }
            phase < 2f -> {
                val t = phase - 1f
                MorphGeometry(
                    corners1 = 4f * (1f - t) + 8f * t,
                    amp1 = 0.08f * (1f - t) + 0.22f * t,
                    corners2 = 4f,
                    amp2 = 0.0f
                )
            }
            else -> {
                val t = phase - 2f
                MorphGeometry(
                    corners1 = 8f * (1f - t) + 2f * t,
                    amp1 = 0.22f * (1f - t) + 0.30f * t,
                    corners2 = 4f,
                    amp2 = 0.12f * t
                )
            }
        }

        val path = Path()
        val numSteps = 120
        for (i in 0..numSteps) {
            val theta = (i.toFloat() / numSteps) * 2f * Math.PI.toFloat()
            val r = baseRadius * (1f + geom.amp1 * cos(geom.corners1 * theta) + geom.amp2 * cos(geom.corners2 * theta))
            val x = center.x + r * cos(theta)
            val y = center.y + r * sin(theta)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()

        // 1. Отрисовка заливки анимированнойExpressive фигуры
        drawPath(
            path = path,
            color = fillColor
        )

        // 2. Отрисовка контура фигуры
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = sizePx * 0.07f, cap = StrokeCap.Round)
        )
    }
}

/**
 * Удобный компонент Expressive Loading Indicator на основе автономной фигуры ExpressiveMorphingShape
 * для применения в центральных состояниях загрузки списка (без контейнера круга).
 */
@Composable
fun ExpressiveLoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    val infiniteTransition = rememberInfiniteTransition(label = "expressiveLoadingLoop")
    val morphProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "loadingMorph"
    )
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "loadingRotation"
    )

    ExpressiveMorphingShape(
        morphProgress = morphProgress,
        rotationDegrees = rotation,
        modifier = modifier,
        color = color,
        fillColor = color.copy(alpha = 0.2f)
    )
}

/**
 * Индикатор Pull-to-Refresh на основе самостоятельной фигуры Material 3 Expressive Morphing Shape.
 * Полностью исключает устаревшую визуальную модель (круглые контейнеры, дуги progress ring, галки).
 *
 * Семантика состояний:
 * 1. IDLE: Скрыт (alpha = 0, scale = 0).
 * 2. DRAG (оттягивание): Фигура появляется, пропорционально меняет геометрию и поворот от distanceFraction.
 *    Сетевой запрос НЕ вызывается.
 * 3. THRESHOLD: Забоксирована готовность к отпусканию. Сетевой запрос НЕ вызывается.
 * 4. REFRESHING: Бесконечная анимированная морфинг-фигура в динамическом вращении.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpressiveRefreshIndicator(
    state: PullToRefreshState,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier
) {
    val fraction = state.distanceFraction.coerceIn(0f, 1.5f)
    val isVisible = fraction > 0.05f || isRefreshing

    if (isVisible) {
        val infiniteTransition = rememberInfiniteTransition(label = "expressiveLoop")

        // Бесконечный морфинг геометрической фигуры во время обновления
        val refreshingMorphProgress by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2400, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "refreshingMorph"
        )

        // Бесконечное вращение фигуры во время обновления
        val refreshingRotation by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1600, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "refreshingRotation"
        )

        val alpha by animateFloatAsState(
            targetValue = if (isRefreshing) 1f else (fraction * 1.5f).coerceIn(0f, 1f),
            animationSpec = tween(durationMillis = 180),
            label = "indicatorAlpha"
        )

        val scale by animateFloatAsState(
            targetValue = if (isRefreshing) 1f else (0.4f + fraction * 0.6f).coerceIn(0.4f, 1.1f),
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            label = "indicatorScale"
        )

        val morphProgress = if (isRefreshing) {
            refreshingMorphProgress
        } else {
            (fraction * 0.5f) % 1f
        }

        val rotation = if (isRefreshing) {
            refreshingRotation
        } else {
            fraction * 180f
        }

        val color = if (fraction >= 1.0f || isRefreshing) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }

        val fillColor = if (fraction >= 1.0f || isRefreshing) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        }

        Box(
            modifier = modifier
                .padding(top = 12.dp)
                .graphicsLayer {
                    this.alpha = alpha
                    this.scaleX = scale
                    this.scaleY = scale
                },
            contentAlignment = Alignment.Center
        ) {
            ExpressiveMorphingShape(
                morphProgress = morphProgress,
                rotationDegrees = rotation,
                modifier = Modifier.size(44.dp),
                color = color,
                fillColor = fillColor
            )
        }
    }
}
