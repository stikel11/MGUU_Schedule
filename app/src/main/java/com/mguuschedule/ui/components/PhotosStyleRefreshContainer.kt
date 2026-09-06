package com.mguuschedule.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Контейнер Pull-to-refresh в стиле Google Фото:
 * Контент уезжает вниз с плавно скругленными верхними углами,
 * открывая сверху акцентную панель с M3 Expressive индикатором и статусом.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotosStyleRefreshContainer(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    statusRowHeight: Dp = 56.dp,
    content: @Composable () -> Unit,
) {
    val state = rememberPullToRefreshState()
    val haptics = LocalHapticFeedback.current

    val statusInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val totalPanelHeight = statusInset + statusRowHeight

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

    // Показываем плашку "Готово" ещё ~1.2с после завершения обновления
    var showDonePanel by remember { mutableStateOf(false) }
    var wasRefreshing by remember { mutableStateOf(isRefreshing) }
    LaunchedEffect(isRefreshing) {
        if (wasRefreshing && !isRefreshing) {
            showDonePanel = true
            delay(1200)
            showDonePanel = false
        }
        wasRefreshing = isRefreshing
    }

    val rawFraction = state.distanceFraction
    val contentOffset by animateDpAsState(
        targetValue = when {
            isRefreshing || showDonePanel -> totalPanelHeight
            rawFraction <= 1f -> totalPanelHeight * rawFraction
            else -> totalPanelHeight + (totalPanelHeight.value * 0.15f * (rawFraction - 1f))
                .dp.coerceAtMost(24.dp)
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "photos_refresh_offset",
    )

    Box(modifier = modifier.fillMaxSize()) {

        // Неподвижная акцентная плашка — уходит под статус-бар, строка статуса прижата к нижнему краю
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(totalPanelHeight)
                .align(Alignment.TopCenter)
                .background(MaterialTheme.colorScheme.primaryContainer),
        ) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(statusRowHeight),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnimatedContent(
                    targetState = isRefreshing to showDonePanel,
                    label = "refresh_status",
                ) { (refreshing, done) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        when {
                            refreshing -> {
                                ExpressiveLoadingIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                                Text(
                                    "Обновление…",
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            done -> {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                                Text(
                                    "Готово",
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            else -> {
                                val pulled = state.distanceFraction.coerceIn(0f, 1f)
                                Text(
                                    text = if (pulled >= 1f) "Отпустите для обновления"
                                           else "Потяните для обновления",
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }

        // Основной контент — уезжает вниз с плавно скругленными верхними углами
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pullToRefresh(
                    state = state,
                    isRefreshing = isRefreshing,
                    onRefresh = onRefresh,
                )
                .graphicsLayer { translationY = contentOffset.toPx() }
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(MaterialTheme.colorScheme.background),
        ) {
            content()
        }
    }
}
