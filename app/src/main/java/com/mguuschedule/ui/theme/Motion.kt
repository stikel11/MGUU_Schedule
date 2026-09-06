package com.mguuschedule.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * Centralized Material 3 Expressive Motion Scheme for MGUU Schedule.
 * Provides unified, standardized motion specs adhering to M3 Expressive guidelines:
 * - Spatial Motion Specs: Position, size, bounds, displacement, sliding, expansion
 * - Effects Motion Specs: Color, alpha, opacity, subtle non-spatial state feedback
 */
object AppMotionScheme {
    // Standard M3 Easing curves
    val ExpressiveSpatialEasing: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
    val StandardSpatialEasing: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    val StandardEffectsEasing: Easing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)

    // Spatial Specs (Position, bounds, size, displacement)
    fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessLow
    )

    fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessVeryLow
    )

    // Effects Specs (Color, alpha, opacity, subtle visual state transitions - NO spatial springs)
    fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = tween(
        durationMillis = 250,
        easing = StandardEffectsEasing
    )

    fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = tween(
        durationMillis = 150,
        easing = StandardEffectsEasing
    )

    fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = tween(
        durationMillis = 400,
        easing = StandardEffectsEasing
    )
}


