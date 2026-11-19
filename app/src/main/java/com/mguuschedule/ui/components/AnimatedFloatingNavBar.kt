package com.mguuschedule.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.mguuschedule.ui.navigation.Screen
import com.mguuschedule.ui.theme.AppMotionScheme
import com.mguuschedule.util.rememberHapticFeedback
import kotlin.math.roundToInt

private val PillAnimationSpec = AppMotionScheme.expressiveSpatialSpec<Float>()

private val TabScaleAnimationSpec = AppMotionScheme.fastSpatialSpec<Float>()

/**
 * Компактный плавающий бар навигации (по принципу Google Photos).
 * Показывает иконку + подпись только для ВЫБРАННОГО раздела.
 * Для невыбранных разделов отображается только текстовая подпись.
 */
@Composable
fun AnimatedFloatingNavBar(
    items: List<Screen>,
    currentRoute: String?,
    onItemClick: (Screen) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = rememberHapticFeedback()

    val itemWidthsPx = remember(items.size) { FloatArray(items.size) }
    val itemOffsetsPx = remember(items.size) { FloatArray(items.size) }

    val selectedIndex = remember(items, currentRoute) {
        items.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)
    }

    var targetOffsetPx by remember { mutableFloatStateOf(0f) }
    var targetWidthPx by remember { mutableFloatStateOf(0f) }

    // Обновляем целевую позицию индикатора при смене активной вкладки
    SideEffect {
        if (selectedIndex in itemOffsetsPx.indices) {
            val cachedOffset = itemOffsetsPx[selectedIndex]
            val cachedWidth = itemWidthsPx[selectedIndex]
            if (cachedWidth > 0f) {
                if (targetOffsetPx != cachedOffset) targetOffsetPx = cachedOffset
                if (targetWidthPx != cachedWidth) targetWidthPx = cachedWidth
            }
        }
    }

    val pillOffsetPx by animateFloatAsState(
        targetValue = targetOffsetPx,
        animationSpec = PillAnimationSpec,
        label = "pillOffset"
    )

    val pillWidthPx by animateFloatAsState(
        targetValue = targetWidthPx,
        animationSpec = PillAnimationSpec,
        label = "pillWidth"
    )

    Surface(
        modifier = modifier.height(56.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 3.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    ) {
        Box(modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
            // Анимированный индикатор активного раздела (offset и width отложены в Layout-фазу)
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset { IntOffset(x = pillOffsetPx.roundToInt(), y = 0) }
                    .layout { measurable, constraints ->
                        val w = pillWidthPx.roundToInt().coerceAtLeast(0)
                        val placeable = measurable.measure(
                            constraints.copy(minWidth = w, maxWidth = w)
                        )
                        layout(placeable.width, placeable.height) {
                            placeable.placeRelative(0, 0)
                        }
                    }
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
            )

            Row(
                modifier = Modifier.fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEachIndexed { index, screen ->
                    val isSelected = selectedIndex == index

                    val contentColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = AppMotionScheme.fastEffectsSpec(),
                        label = "contentColor"
                    )

                    val interactionSource = remember { MutableInteractionSource() }
                    val isPressed by interactionSource.collectIsPressedAsState()
                    val tabScale by animateFloatAsState(
                        targetValue = if (isPressed) 0.93f else 1f,
                        animationSpec = TabScaleAnimationSpec,
                        label = "tabScale"
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxHeight()
                            .graphicsLayer {
                                scaleX = tabScale
                                scaleY = tabScale
                            }
                            .onPlaced { coordinates ->
                                val newWidth = coordinates.size.width.toFloat()
                                val newOffset = coordinates.positionInParent().x
                                itemWidthsPx[index] = newWidth
                                itemOffsetsPx[index] = newOffset
                                if (index == selectedIndex) {
                                    if (targetWidthPx != newWidth) targetWidthPx = newWidth
                                    if (targetOffsetPx != newOffset) targetOffsetPx = newOffset
                                }
                            }
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null
                            ) {
                                if (!isSelected) {
                                    haptic.selection()
                                } else {
                                    haptic.lightTick()
                                }
                                onItemClick(screen)
                            }
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        // Иконка отображается ТОЛЬКО у выбранного раздела
                        AnimatedVisibility(
                            visible = isSelected,
                            enter = expandHorizontally(AppMotionScheme.defaultSpatialSpec()) + fadeIn(AppMotionScheme.fastEffectsSpec()),
                            exit = shrinkHorizontally(AppMotionScheme.defaultSpatialSpec()) + fadeOut(AppMotionScheme.fastEffectsSpec())
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = screen.icon,
                                    contentDescription = screen.title,
                                    tint = contentColor,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                        }

                        Text(
                            text = screen.title,
                            color = contentColor,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }

                    if (index < items.size - 1) {
                        Spacer(modifier = Modifier.width(2.dp))
                    }
                }
            }
        }
    }
}
