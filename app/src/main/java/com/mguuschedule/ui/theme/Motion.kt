package com.mguuschedule.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring

/**
 * Centralized Material 3 Expressive Motion Scheme for MGUU Schedule.
 * Provides unified, standardized motion specs adhering to M3 Expressive guidelines:
 * - Spatial Motion Specs: Position, size, bounds, displacement, sliding, expansion
 * - Effects Motion Specs: Color, alpha, opacity, subtle non-spatial state feedback
 */
object AppMotionScheme {
    // Spatial Specs (Position, bounds, size, displacement) - MD3 Expressive Springs
    fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = spring(
        dampingRatio = 0.78f,
        stiffness = 380f
    )

    fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = spring(
        dampingRatio = 0.75f,
        stiffness = 600f
    )

    fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = spring(
        dampingRatio = 0.82f,
        stiffness = 260f
    )

    // Effects Specs (Color, alpha, opacity) - MD3 Expressive Springs without overshoot
    fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 800f
    )

    fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 1200f
    )

    fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 400f
    )
}


