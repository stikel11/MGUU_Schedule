package com.mguuschedule.ui.theme

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
    // Плавные пространственные пружины (без резких рывков)
    fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.84f, stiffness = 280f)

    fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.86f, stiffness = 200f)

    fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.88f, stiffness = 140f)

    // Плавные пружины для цвета и прозрачности
    fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> =
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 500f)

    fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> =
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 320f)

    fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> =
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 200f)
}