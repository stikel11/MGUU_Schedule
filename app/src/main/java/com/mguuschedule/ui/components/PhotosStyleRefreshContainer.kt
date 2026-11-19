package com.mguuschedule.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.circle
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath
import com.mguuschedule.ui.theme.AppMotionScheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private enum class RefreshStatePhase { Idle, Pulling, Refreshing, Done }

@Composable
fun PhotosStyleRefreshContainer(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    statusText: String = "Данные обновлены",
    isError: Boolean = false,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val statusBarTopPx = WindowInsets.statusBars.getTop(density).toFloat()
    val triggerPx = with(density) { 104.dp.toPx() }
    val maxPullPx = with(density) { 180.dp.toPx() }
    val activeHoldPx = with(density) { 92.dp.toPx() } + statusBarTopPx

    val offsetAnim = remember { Animatable(0f) }
    var phase by remember { mutableStateOf(RefreshStatePhase.Idle) }
    var wasRefreshing by remember { mutableStateOf(false) }

    val currentPhase by rememberUpdatedState(phase)
    val currentOnRefresh by rememberUpdatedState(onRefresh)

    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            wasRefreshing = true
            phase = RefreshStatePhase.Refreshing
            offsetAnim.animateTo(
                targetValue = activeHoldPx,
                animationSpec = AppMotionScheme.defaultSpatialSpec()
            )
        } else if (wasRefreshing) {
            wasRefreshing = false
            phase = RefreshStatePhase.Done
            offsetAnim.animateTo(
                targetValue = activeHoldPx,
                animationSpec = AppMotionScheme.defaultSpatialSpec()
            )
            delay(if (isError) 1400L else 850L)
            offsetAnim.animateTo(
                targetValue = 0f,
                animationSpec = AppMotionScheme.defaultSpatialSpec()
            )
            phase = RefreshStatePhase.Idle
        }
    }

    // ВАЖНО: remember БЕЗ ключей phase/isRefreshing, чтобы жест никогда не прерывался во время свайпа!
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y < 0f && offsetAnim.value > 0f && currentPhase == RefreshStatePhase.Pulling) {
                    val consumed = available.y.coerceAtLeast(-offsetAnim.value)
                    scope.launch { offsetAnim.snapTo(offsetAnim.value + consumed) }
                    return Offset(0f, consumed)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0f &&
                    currentPhase != RefreshStatePhase.Refreshing && currentPhase != RefreshStatePhase.Done
                ) {
                    if (currentPhase != RefreshStatePhase.Pulling) {
                        phase = RefreshStatePhase.Pulling
                    }
                    val damping = (1f - (offsetAnim.value / (maxPullPx * 1.6f))).coerceIn(0.20f, 0.55f)
                    val newOffset = (offsetAnim.value + available.y * damping).coerceAtMost(maxPullPx + statusBarTopPx)
                    scope.launch { offsetAnim.snapTo(newOffset) }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (currentPhase == RefreshStatePhase.Pulling) {
                    if (offsetAnim.value >= triggerPx) {
                        wasRefreshing = true
                        phase = RefreshStatePhase.Refreshing
                        scope.launch {
                            offsetAnim.animateTo(
                                targetValue = activeHoldPx,
                                animationSpec = AppMotionScheme.defaultSpatialSpec()
                            )
                        }
                        currentOnRefresh()
                    } else {
                        scope.launch {
                            offsetAnim.animateTo(
                                targetValue = 0f,
                                animationSpec = AppMotionScheme.defaultSpatialSpec()
                            )
                            phase = RefreshStatePhase.Idle
                        }
                    }
                    return Velocity(0f, available.y * 0.5f)
                }
                return Velocity.Zero
            }
        }
    }

    // Рекомпозиция контейнера срабатывает только при пересечении порога видимости (> 2px),
    // а не на каждый пиксель свайпа или кадр пружинной анимации
    val isPullVisible by remember { derivedStateOf { offsetAnim.value > 2f } }

    val backgroundColor = MaterialTheme.colorScheme.background
    // Выразительная подложка MD3 Expressive (secondaryContainer):
    // заметно и гармонично отличается от основного фона (background) и карточек (surfaceContainer)
    // как в светлой (Tone 90 с цветовым акцентом), так и в тёмной теме (Tone 30 с цветовым акцентом),
    // динамически подстраиваясь под палитру Material You.
    val recessedWellColor = MaterialTheme.colorScheme.secondaryContainer
    val donePillBgColor = if (isError) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val donePillIconColor = if (isError) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }
    val donePillTextColor = if (isError) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(if (offsetAnim.value > 2f) recessedWellColor else backgroundColor)
            }
            .nestedScroll(nestedScrollConnection)
    ) {
        // 1. Ниша под статус-баром с экспрессивным полигоном и капсулой статуса
        if (isPullVisible) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .layout { measurable, constraints ->
                        val currentOff = offsetAnim.value.coerceAtLeast(0f)
                        val progress = (currentOff / triggerPx).coerceIn(0f, 1f)
                        val topPadPx = (statusBarTopPx * progress).roundToInt()
                        val heightPx = currentOff.roundToInt()
                        val availableH = (heightPx - topPadPx).coerceAtLeast(0)
                        val placeable = measurable.measure(
                            constraints.copy(minHeight = availableH, maxHeight = availableH)
                        )
                        layout(constraints.maxWidth, heightPx) {
                            placeable.placeRelative(0, topPadPx)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = phase == RefreshStatePhase.Done,
                    transitionSpec = {
                        (scaleIn(AppMotionScheme.expressiveSpatialSpec(), initialScale = 0.86f) + fadeIn(AppMotionScheme.fastEffectsSpec()))
                            .togetherWith(scaleOut(AppMotionScheme.defaultSpatialSpec(), targetScale = 0.88f) + fadeOut(AppMotionScheme.fastEffectsSpec()))
                    },
                    label = "refresh_indicator_content"
                ) { isDone ->
                    if (isDone) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = donePillBgColor,
                            tonalElevation = 0.dp,
                            shadowElevation = 2.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (isError) Icons.Rounded.Warning else Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = donePillIconColor,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = statusText,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = donePillTextColor
                                )
                            }
                        }
                    } else {
                        GoogleExpressivePolygonIndicator(
                            pullProgressProvider = { (offsetAnim.value / triggerPx).coerceIn(0f, 1f) },
                            isSpinning = phase == RefreshStatePhase.Refreshing
                        )
                    }
                }
            }
        }

        // 2. Сдвигающийся вниз основной экран со скругленными верхними углами (/‾\)
        // Смещение, тень и скругление выполняются в Layout/Draw без рекомпозиции content()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(0, offsetAnim.value.roundToInt()) }
                .graphicsLayer {
                    val progress = (offsetAnim.value / triggerPx).coerceIn(0f, 1f)
                    if (progress > 0.01f) {
                        val radius = (28.dp * progress)
                        shape = RoundedCornerShape(topStart = radius, topEnd = radius)
                        shadowElevation = 6.dp.toPx() * progress
                        clip = true
                    } else {
                        shadowElevation = 0f
                        clip = false
                    }
                }
                .background(backgroundColor)
        ) {
            content()
        }
    }
}

@Composable
private fun GoogleExpressivePolygonIndicator(
    pullProgressProvider: () -> Float,
    isSpinning: Boolean
) {
    val shapes = remember {
        listOf(
            RoundedPolygon(numVertices = 5, rounding = CornerRounding(radius = 0.32f)),
            RoundedPolygon.star(numVerticesPerRadius = 8, innerRadius = 0.72f, rounding = CornerRounding(radius = 0.28f)),
            RoundedPolygon.star(numVerticesPerRadius = 4, innerRadius = 0.62f, rounding = CornerRounding(radius = 0.38f)),
            RoundedPolygon.circle(numVertices = 6)
        )
    }

    val morphs = remember(shapes) {
        shapes.indices.map { i ->
            Morph(shapes[i], shapes[(i + 1) % shapes.size])
        }
    }

    // Бесконечная анимация создается только во время активного обновления
    val continuousPhaseState: State<Float>?
    val rotationState: State<Float>?
    if (isSpinning) {
        val infiniteTransition = rememberInfiniteTransition(label = "m3_expressive_morph")
        continuousPhaseState = infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = morphs.size.toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2400, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "morph_phase"
        )
        rotationState = infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 3000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "rotation_phase"
        )
    } else {
        continuousPhaseState = null
        rotationState = null
    }

    val androidPath = remember { android.graphics.Path() }
    val composePath = remember(androidPath) { androidPath.asComposePath() }
    val matrix = remember { android.graphics.Matrix() }
    val color = MaterialTheme.colorScheme.primary

    Canvas(
        modifier = Modifier
            .size(44.dp)
            .graphicsLayer {
                val pullProgress = pullProgressProvider()
                val s = (0.35f + 0.65f * pullProgress).coerceIn(0f, 1f)
                scaleX = s
                scaleY = s
                rotationZ = if (isSpinning) (rotationState?.value ?: 0f) else pullProgress * 140f
                alpha = (pullProgress * 1.4f).coerceIn(0f, 1f)
            }
    ) {
        val activeMorphValue = if (isSpinning) {
            continuousPhaseState?.value ?: 0f
        } else {
            pullProgressProvider() * 1.5f
        }
        val morphIndex = activeMorphValue.toInt() % morphs.size
        val morphFraction = activeMorphValue - activeMorphValue.toInt()

        androidPath.rewind()
        morphs[morphIndex].toPath(progress = morphFraction, path = androidPath)
        matrix.reset()
        val radius = size.minDimension / 2f
        matrix.postScale(radius, radius)
        matrix.postTranslate(size.width / 2f, size.height / 2f)
        androidPath.transform(matrix)

        drawPath(
            path = composePath,
            color = color
        )
    }
}
