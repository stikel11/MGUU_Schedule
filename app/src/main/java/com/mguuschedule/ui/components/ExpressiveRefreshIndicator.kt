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

private const val TWO_PI = (2.0 * Math.PI).toFloat()

/**
 * Самостоятельная геометрическая фигура Material 3 Expressive (Expressive Morphing Shape).
 * Принимает провайдеры лямбдами, чтобы чтение анимационных состояний происходило в Draw-фазе
 * без рекомпозиций на каждый кадр, и переиспользует Path через rewind().
 */
@Composable
fun ExpressiveMorphingShape(
    morphProgressProvider: () -> Float,
    rotationDegreesProvider: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    fillColor: Color = MaterialTheme.colorScheme.primaryContainer
) {
    val path = remember { Path() }

    Canvas(
        modifier = modifier.graphicsLayer { rotationZ = rotationDegreesProvider() }
    ) {
        val sizePx = size.minDimension
        val center = Offset(sizePx / 2f, sizePx / 2f)
        val baseRadius = sizePx * 0.36f

        val phase = (morphProgressProvider() * 3f) % 3f
        val corners1: Float
        val amp1: Float
        val corners2: Float
        val amp2: Float
        when {
            phase < 1f -> {
                val t = phase
                corners1 = 4f
                amp1 = 0.28f * (1f - t) + 0.08f * t
                corners2 = 8f
                amp2 = 0.0f
            }
            phase < 2f -> {
                val t = phase - 1f
                corners1 = 4f * (1f - t) + 8f * t
                amp1 = 0.08f * (1f - t) + 0.22f * t
                corners2 = 4f
                amp2 = 0.0f
            }
            else -> {
                val t = phase - 2f
                corners1 = 8f * (1f - t) + 2f * t
                amp1 = 0.22f * (1f - t) + 0.30f * t
                corners2 = 4f
                amp2 = 0.12f * t
            }
        }

        path.rewind()
        val numSteps = 120
        val stepAngle = TWO_PI / numSteps
        for (i in 0..numSteps) {
            val theta = i * stepAngle
            val r = baseRadius * (1f + amp1 * cos(corners1 * theta) + amp2 * cos(corners2 * theta))
            val x = center.x + r * cos(theta)
            val y = center.y + r * sin(theta)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()

        drawPath(
            path = path,
            color = fillColor
        )

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
    val morphProgressState = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "loadingMorph"
    )
    val rotationState = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "loadingRotation"
    )

    ExpressiveMorphingShape(
        morphProgressProvider = { morphProgressState.value },
        rotationDegreesProvider = { rotationState.value },
        modifier = modifier,
        color = color,
        fillColor = color.copy(alpha = 0.2f)
    )
}

/**
 * Индикатор Pull-to-Refresh на основе самостоятельной фигуры Material 3 Expressive Morphing Shape.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpressiveRefreshIndicator(
    state: PullToRefreshState,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier
) {
    val isVisible by remember(state, isRefreshing) {
        derivedStateOf { isRefreshing || state.distanceFraction > 0.05f }
    }

    if (isVisible) {
        val isThresholdReached by remember(state, isRefreshing) {
            derivedStateOf { isRefreshing || state.distanceFraction >= 1.0f }
        }

        val refreshingMorphState: State<Float>?
        val refreshingRotationState: State<Float>?
        if (isRefreshing) {
            val infiniteTransition = rememberInfiniteTransition(label = "expressiveLoop")
            refreshingMorphState = infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 2400, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "refreshingMorph"
            )
            refreshingRotationState = infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 1600, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "refreshingRotation"
            )
        } else {
            refreshingMorphState = null
            refreshingRotationState = null
        }

        val color = if (isThresholdReached) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }

        val fillColor = if (isThresholdReached) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        }

        Box(
            modifier = modifier
                .padding(top = 12.dp)
                .graphicsLayer {
                    val fraction = state.distanceFraction.coerceIn(0f, 1.5f)
                    alpha = if (isRefreshing) 1f else (fraction * 1.5f).coerceIn(0f, 1f)
                    val s = if (isRefreshing) 1f else (0.4f + fraction * 0.6f).coerceIn(0.4f, 1.1f)
                    scaleX = s
                    scaleY = s
                },
            contentAlignment = Alignment.Center
        ) {
            ExpressiveMorphingShape(
                morphProgressProvider = {
                    if (isRefreshing) {
                        refreshingMorphState?.value ?: 0f
                    } else {
                        (state.distanceFraction.coerceIn(0f, 1.5f) * 0.5f) % 1f
                    }
                },
                rotationDegreesProvider = {
                    if (isRefreshing) {
                        refreshingRotationState?.value ?: 0f
                    } else {
                        state.distanceFraction.coerceIn(0f, 1.5f) * 180f
                    }
                },
                modifier = Modifier.size(44.dp),
                color = color,
                fillColor = fillColor
            )
        }
    }
}
