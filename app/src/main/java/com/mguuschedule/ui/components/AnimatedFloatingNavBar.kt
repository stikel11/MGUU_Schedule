package com.mguuschedule.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mguuschedule.ui.navigation.Screen
import com.mguuschedule.ui.theme.AppMotionScheme
import com.mguuschedule.util.rememberHapticFeedback

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
    val density = LocalDensity.current

    val itemWidths = remember { mutableStateListOf<Dp>().apply { repeat(items.size) { add(0.dp) } } }
    val itemOffsets = remember { mutableStateListOf<Dp>().apply { repeat(items.size) { add(0.dp) } } }
    
    val selectedIndex = items.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)
    
    val pillAnimationSpec = AppMotionScheme.defaultSpatialSpec<Dp>()

    val pillOffset by animateDpAsState(
        targetValue = itemOffsets.getOrElse(selectedIndex) { 0.dp },
        animationSpec = pillAnimationSpec,
        label = "pillOffset"
    )
    
    val pillWidth by animateDpAsState(
        targetValue = itemWidths.getOrElse(selectedIndex) { 0.dp },
        animationSpec = pillAnimationSpec,
        label = "pillWidth"
    )

    Surface(
        modifier = modifier.height(52.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 3.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
    ) {
        Box(modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
            // Анимированный индикатор активного раздела
            Box(
                modifier = Modifier
                    .offset(x = pillOffset)
                    .align(Alignment.CenterStart)
                    .width(pillWidth)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
            )

            Row(
                modifier = Modifier.fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEachIndexed { index, screen ->
                    val isSelected = items.indexOfFirst { it.route == currentRoute } == index
                    
                    val contentColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = AppMotionScheme.fastEffectsSpec(),
                        label = "contentColor"
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxHeight()
                            .onGloballyPositioned { coordinates ->
                                with(density) {
                                    val newWidth = coordinates.size.width.toDp()
                                    val newOffset = coordinates.positionInParent().x.toDp()
                                    if (itemWidths[index] != newWidth) itemWidths[index] = newWidth
                                    if (itemOffsets[index] != newOffset) itemOffsets[index] = newOffset
                                }
                            }
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
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
