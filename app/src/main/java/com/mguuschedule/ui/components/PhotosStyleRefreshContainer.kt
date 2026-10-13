package com.mguuschedule.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin

enum class RefreshPhase {
    Pulling, Refreshing, Done
}

/**
 * Контейнер Pull-to-refresh в стиле Google Фото (Material 3 Expressive):
 * Физическое сдвигание контента с логарифмическим сопротивлением,
 * открытие чистого фона и появление крупного залитого морфинг-индикатора.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotosStyleRefreshContainer(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") statusRowHeight: Dp = 56.dp, // Сохранено для совместимости сигнатуры, хотя используются фиксированные отступы
    content: @Composable () -> Unit,
) {
    val state = rememberPullToRefreshState()
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current

    var currentPhase by remember { mutableStateOf(RefreshPhase.Pulling) }
    var wasRefreshing by remember { mutableStateOf(isRefreshing) }

    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            currentPhase = RefreshPhase.Refreshing
        } else if (wasRefreshing) {
            currentPhase = RefreshPhase.Done
            delay(650)
            currentPhase = RefreshPhase.Pulling
        } else {
            currentPhase = RefreshPhase.Pulling
        }
        wasRefreshing = isRefreshing
    }

    // Легкая тактильная отдача при пересечении порога скролла
    var hasVibratedThreshold by remember { mutableStateOf(false) }
    LaunchedEffect(state.distanceFraction) {
        if (state.distanceFraction >= 1f && !hasVibratedThreshold) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            hasVibratedThreshold = true
        } else if (state.distanceFraction < 1f) {
            hasVibratedThreshold = false
        }
    }

    val thresholdPx = with(density) { 90.dp.toPx() }
    val targetOffsetPx = when (currentPhase) {
        RefreshPhase.Refreshing, RefreshPhase.Done -> thresholdPx
        RefreshPhase.Pulling -> {
            val fraction = state.distanceFraction
            // Логарифмическое сопротивление (rubber-band effect)
            thresholdPx * ln(1f + fraction)
        }
    }

    val offsetSpringSpec = if (currentPhase == RefreshPhase.Pulling && !isRefreshing) {
        spring<Float>(dampingRatio = 0.82f, stiffness = 400f)
    } else {
        spring<Float>(dampingRatio = 0.78f, stiffness = 380f)
    }

    val animatedOffsetPx by animateFloatAsState(
        targetValue = targetOffsetPx,
        animationSpec = offsetSpringSpec,
        label = "content_offset"
    )

    Box(modifier = modifier.fillMaxSize()) {
        
        // Область индикатора (прозрачная, лежит под контентом, отступ под статус-бар)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(with(density) { animatedOffsetPx.toDp() })
                .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = currentPhase,
                transitionSpec = {
                    if (targetState == RefreshPhase.Done) {
                        (scaleIn(spring(stiffness = 500f)) + fadeIn()) togetherWith 
                        (scaleOut(spring(stiffness = 500f)) + fadeOut())
                    } else {
                        fadeIn() togetherWith fadeOut()
                    }
                },
                label = "refresh_indicator"
            ) { phase ->
                when (phase) {
                    RefreshPhase.Pulling, RefreshPhase.Refreshing -> {
                        val rawScale = if (phase == RefreshPhase.Refreshing) 1f else {
                            val frac = state.distanceFraction
                            // Упругий овершут при натяжении
                            if (frac < 1f) frac else 1f + 0.2f * (frac - 1f)
                        }
                        MorphingPolygonIndicator(
                            modifier = Modifier.size(44.dp),
                            progress = rawScale.coerceIn(0f, 1.2f)
                        )
                    }
                    RefreshPhase.Done -> {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            tonalElevation = 2.dp,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Обновлено",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }

        // Основной контент — уезжает вниз, открывая чистый фон экрана
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = animatedOffsetPx }
                .pullToRefresh(
                    state = state,
                    isRefreshing = isRefreshing,
                    onRefresh = onRefresh
                )
                .background(Color.Transparent)
        ) {
            content()
        }
    }
}

/**
 * MD3 Expressive: Крупный залитый бесконечно вращающийся полигон, плавно меняющий форму.
 */
@Composable
fun MorphingPolygonIndicator(
    modifier: Modifier = Modifier,
    progress: Float = 1f,
    color: Color = MaterialTheme.colorScheme.primaryContainer
) {
    val infiniteTransition = rememberInfiniteTransition(label = "morphing_polygon")
    
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(3500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "morph_time"
    )
    
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(7000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "morph_rotation"
    )

    Canvas(
        modifier = modifier
            .scale(progress)
            .graphicsLayer { rotationZ = rotation }
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val baseRadius = size.width / 2f * 0.75f 
        
        // Фигуры: Pair(лепестки, амплитуда)
        val shapes = listOf(
            Pair(5, 0.15f), // Мягкий 5-угольник
            Pair(4, 0.20f), // 4-листник "cookie"
            Pair(7, 0.12f), // Мягкая 7-лучевая звезда
            Pair(0, 0.00f)  // Круг
        )
        
        val progressTime = (time / (PI * 2)) * shapes.size
        val index1 = progressTime.toInt() % shapes.size
        val index2 = (index1 + 1) % shapes.size
        val fraction = (progressTime - progressTime.toInt()).toFloat()
        
        // Плавное перетекание (smoothstep)
        val smoothFraction = fraction * fraction * (3f - 2f * fraction)
        
        val path = Path()
        val steps = 120
        for (i in 0..steps) {
            val angle = (i.toFloat() / steps) * (PI * 2).toFloat()
            
            val l1 = shapes[index1].first
            val a1 = shapes[index1].second
            val r1 = baseRadius * (1f + a1 * cos(l1 * angle))
            
            val l2 = shapes[index2].first
            val a2 = shapes[index2].second
            val r2 = baseRadius * (1f + a2 * cos(l2 * angle))
            
            val r = r1 + (r2 - r1) * smoothFraction
            
            val x = center.x + r * cos(angle)
            val y = center.y + r * sin(angle)
            
            if (i == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }
        path.close()
        
        drawPath(path = path, color = color)
    }
}
