package com.mguuschedule.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import com.mguuschedule.ui.theme.AppMotionScheme

/**
 * Анимированная затеняющая дымка статус-бара при скролле.
 */
@Composable
fun BoxScope.StatusBarScrim(
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    val isScrolled by remember { derivedStateOf { listState.canScrollBackward } }
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val scrimColor = MaterialTheme.colorScheme.surface

    AnimatedVisibility(
        visible = isScrolled,
        enter = fadeIn(AppMotionScheme.defaultEffectsSpec()),
        exit = fadeOut(AppMotionScheme.defaultEffectsSpec()),
        modifier = modifier.fillMaxWidth().align(Alignment.TopCenter)
    ) {
        Spacer(
            Modifier
                .fillMaxWidth()
                .height(statusBarHeight + 24.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            scrimColor.copy(alpha = 0.95f),
                            scrimColor.copy(alpha = 0.6f),
                            Color.Transparent
                        )
                    )
                )
        )
    }
}

/**
 * Легкая градиентная защита статус-бара с поддержкой матового стекла (Haze Backdrop Blur).
 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun TopScrimProtection(
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    color: Color = MaterialTheme.colorScheme.surface
) {
    val density = LocalDensity.current
    val statusBarHeight = WindowInsets.statusBars.getTop(density)
    val heightDp = with(density) { maxOf(statusBarHeight * 1.35f, 40.dp.toPx()).toDp() }

    if (hazeState != null) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(heightDp)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.Black, Color.Black.copy(alpha = 0.85f), Color.Transparent)
                        ),
                        blendMode = BlendMode.DstIn
                    )
                }
                .hazeEffect(
                    state = hazeState,
                    style = HazeMaterials.thin(containerColor = color)
                ) {
                    blurRadius = 24.dp
                    tints = listOf(HazeTint(color.copy(alpha = 0.55f)))
                    noiseFactor = 0.08f
                }
        )
    } else {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(heightDp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            color,
                            color.copy(alpha = 0.85f),
                            Color.Transparent
                        )
                    )
                )
        )
    }
}

/**
 * Динамическая градиентная защита Status Bar при скролле.
 */
@Composable
fun StatusBarScrollProtection(
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    TopScrimProtection(modifier = modifier)
}

/**
 * Переиспользуемый заголовок экрана, уходящий под блюр-полосу при скролле.
 */
@Composable
fun CollapsibleScreenTitle(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.padding(
            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp,
            bottom = 12.dp
        )
    )
}

/**
 * Переиспользуемая полоса матового стекла (Haze Blur) в зоне статус-бара.
 */
@Composable
fun StatusBarBlurOverlay(
    hazeState: HazeState,
    modifier: Modifier = Modifier
) {
    TopScrimProtection(
        modifier = modifier,
        hazeState = hazeState
    )
}
