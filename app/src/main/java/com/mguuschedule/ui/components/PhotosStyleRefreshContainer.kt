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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
                animationSpec = spring(dampingRatio = 0.85f, stiffness = 240f)
            )
        } else if (wasRefreshing) {
            wasRefreshing = false
            phase = RefreshStatePhase.Done
            offsetAnim.animateTo(
                targetValue = activeHoldPx,
                animationSpec = spring(dampingRatio = 0.85f, stiffness = 260f)
            )
            delay(if (isError) 1400L else 850L)
            offsetAnim.animateTo(
                targetValue = 0f,
                animationSpec = spring(dampingRatio = 0.88f, stiffness = 220f)
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
                                animationSpec = spring(dampingRatio = 0.85f, stiffness = 240f)
                            )
                        }
                        currentOnRefresh()
                    } else {
                        scope.launch {
                            offsetAnim.animateTo(
                                targetValue = 0f,
                                animationSpec = spring(dampingRatio = 0.88f, stiffness = 240f)
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

    val currentOffset = offsetAnim.value
    val pullProgress = (currentOffset / triggerPx).coerceIn(0f, 1f)
    val contentTopCornerRadius = (28.dp * pullProgress).coerceIn(0.dp, 28.dp)

    val recessedWellColor = androidx.compose.ui.graphics.lerp(
        MaterialTheme.colorScheme.background,
        MaterialTheme.colorScheme.primary,
        0.09f
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                if (currentOffset > 2f) recessedWellColor
                else MaterialTheme.colorScheme.background
            )
            .nestedScroll(nestedScrollConnection)
    ) {
        // 1. Ниша под статус-баром с экспрессивным полигоном и капсулой статуса
        if (currentOffset > 2f) {
            val blockHeightDp = with(density) { currentOffset.toDp() }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(blockHeightDp)
                    .padding(top = with(density) { (statusBarTopPx * pullProgress).toDp() }),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = phase == RefreshStatePhase.Done,
                    transitionSpec = {
                        (scaleIn(spring(0.82f, 300f)) + fadeIn(tween(180)))
                            .togetherWith(scaleOut(spring(0.85f, 300f)) + fadeOut(tween(120)))
                    },
                    label = "refresh_indicator_content"
                ) { isDone ->
                    if (isDone) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (isError) Icons.Rounded.Warning else Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = statusText,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    } else {
                        GoogleExpressivePolygonIndicator(
                            pullProgress = pullProgress,
                            isSpinning = phase == RefreshStatePhase.Refreshing
                        )
                    }
                }
            }
        }

        // 2. Сдвигающийся вниз основной экран со скругленными верхними углами (/‾\)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(0, currentOffset.roundToInt()) }
                .clip(
                    RoundedCornerShape(
                        topStart = contentTopCornerRadius,
                        topEnd = contentTopCornerRadius
                    )
                )
                .background(MaterialTheme.colorScheme.background)
        ) {
            content()
        }
    }
}

@Composable
private fun GoogleExpressivePolygonIndicator(
    pullProgress: Float,
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

    val infiniteTransition = rememberInfiniteTransition(label = "m3_expressive_morph")
    val continuousPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = morphs.size.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "morph_phase"
    )
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation_phase"
    )

    val activeMorphValue = if (isSpinning) continuousPhase else (pullProgress * 1.5f)
    val morphIndex = activeMorphValue.toInt() % morphs.size
    val morphFraction = activeMorphValue - activeMorphValue.toInt()

    val androidPath = remember { android.graphics.Path() }
    val matrix = remember { android.graphics.Matrix() }
    val color = MaterialTheme.colorScheme.primary
    val scale = (0.35f + 0.65f * pullProgress).coerceIn(0f, 1f)

    Canvas(
        modifier = Modifier
            .size(44.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                rotationZ = if (isSpinning) rotation else pullProgress * 140f
                alpha = (pullProgress * 1.4f).coerceIn(0f, 1f)
            }
    ) {
        androidPath.rewind()
        morphs[morphIndex].toPath(progress = morphFraction, path = androidPath)
        matrix.reset()
        val radius = size.minDimension / 2f
        matrix.postScale(radius, radius)
        matrix.postTranslate(size.width / 2f, size.height / 2f)
        androidPath.transform(matrix)

        drawPath(
            path = androidPath.asComposePath(),
            color = color
        )
    }
}