package com.mguuschedule.ui.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.graphics.TransformOrigin

/**
 * Единая система движения в стиле Material 3 Expressive (Pixel / Android 16+).
 *
 * Базируется на официальных пружинных токенах Google ExpressiveMotionTokens:
 * - Spatial-пружины (с лёгким живым откликом) — для геометрии, размеров, смещений, масштаба.
 * - Effects-пружины (критически демпфированные, damping = 1.0f) — для цвета и прозрачности без перелёта.
 * - Pop-переходы NavHost настроены под 1:1 скраббинг SeekableTransitionState (Predictive Back).
 */
object AppMotionScheme {

    val StandardEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val EmphasizedDecelerateEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerateEasing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    // --- Официальные MD3 Expressive Spatial Springs ---
    fun <T> fastSpatialSpec(): SpringSpec<T> = spring(
        dampingRatio = 0.6f,
        stiffness = 800f
    )

    fun <T> defaultSpatialSpec(): SpringSpec<T> = spring(
        dampingRatio = 0.8f,
        stiffness = 380f
    )

    fun <T> slowSpatialSpec(): SpringSpec<T> = spring(
        dampingRatio = 0.8f,
        stiffness = 200f
    )

    fun <T> expressiveSpatialSpec(): SpringSpec<T> = spring(
        dampingRatio = 0.75f,
        stiffness = 380f
    )

    // --- Официальные MD3 Expressive Effects Springs (без перелёта) ---
    fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = spring(
        dampingRatio = 1.0f,
        stiffness = 3800f
    )

    fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = spring(
        dampingRatio = 1.0f,
        stiffness = 1600f
    )

    fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = spring(
        dampingRatio = 1.0f,
        stiffness = 800f
    )

    // 1. Переключение верхнеуровневых вкладок BottomBar (MD3E Fade Through)
    fun tabEnterTransition(): EnterTransition =
        fadeIn(
            animationSpec = tween(
                durationMillis = 210,
                delayMillis = 55,
                easing = EmphasizedDecelerateEasing
            )
        ) + scaleIn(
            initialScale = 0.96f,
            animationSpec = defaultSpatialSpec()
        )

    fun tabExitTransition(): ExitTransition =
        fadeOut(
            animationSpec = tween(
                durationMillis = 90,
                easing = EmphasizedAccelerateEasing
            )
        )

    // 2. Переход вперёд на детальный экран (Shared Axis X + глубина MD3E)
    fun detailEnterTransition(): EnterTransition =
        slideInHorizontally(
            animationSpec = defaultSpatialSpec(),
            initialOffsetX = { fullWidth -> (fullWidth * 0.18f).toInt() }
        ) + fadeIn(
            animationSpec = tween(durationMillis = 200, easing = EmphasizedDecelerateEasing)
        ) + scaleIn(
            initialScale = 0.96f,
            animationSpec = defaultSpatialSpec()
        )

    fun detailBackgroundExitTransition(): ExitTransition =
        slideOutHorizontally(
            animationSpec = defaultSpatialSpec(),
            targetOffsetX = { fullWidth -> -(fullWidth * 0.08f).toInt() }
        ) + scaleOut(
            targetScale = 0.95f,
            animationSpec = defaultSpatialSpec()
        ) + fadeOut(
            targetAlpha = 0.76f,
            animationSpec = tween(durationMillis = 260, easing = StandardEasing)
        )

    // 3. Нативный Predictive Back / Pop (скраббится 1:1 через NavHost SeekableTransitionState)
    fun detailBackgroundPopEnterTransition(): EnterTransition =
        slideInHorizontally(
            animationSpec = tween(durationMillis = 300, easing = StandardEasing),
            initialOffsetX = { fullWidth -> -(fullWidth * 0.08f).toInt() }
        ) + scaleIn(
            initialScale = 0.94f,
            animationSpec = tween(durationMillis = 300, easing = StandardEasing)
        ) + fadeIn(
            initialAlpha = 0.76f,
            animationSpec = tween(durationMillis = 220, easing = StandardEasing)
        )

    fun detailPopExitTransition(): ExitTransition =
        slideOutHorizontally(
            animationSpec = tween(durationMillis = 300, easing = StandardEasing),
            targetOffsetX = { fullWidth -> (fullWidth * 0.16f).toInt() }
        ) + scaleOut(
            targetScale = 0.88f,
            transformOrigin = TransformOrigin(0.5f, 0.5f),
            animationSpec = tween(durationMillis = 300, easing = StandardEasing)
        ) + fadeOut(
            animationSpec = tween(
                durationMillis = 180,
                delayMillis = 100,
                easing = EmphasizedAccelerateEasing
            )
        )
}
