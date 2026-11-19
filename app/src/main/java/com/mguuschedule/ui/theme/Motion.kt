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
 * Единая система движения в стиле Material 3 Expressive и нативных приложений Google (Android 15/16).
 *
 * - Spatial-пружины — для локальной геометрии (чипы, кнопки, карточки, индикаторы).
 * - Effects-пружины (damping = 1.0f) — для цвета и прозрачности без перелёта.
 * - Полноэкранная навигация и Predictive Back соответствуют официальным спецификациям Android SystemUI:
 *   - PredictiveBackEasing = CubicBezierEasing(0.1f, 0.1f, 0f, 1f) (официальный SystemUI-интерполятор)
 *   - StandardDecelerateEasing = CubicBezierEasing(0f, 0f, 0f, 1f) (интерполятор прогресса жеста)
 *   - Вперёд и назад экраны остаются 100% непрозрачными (без cross-fade ghosting) и полностью
 *     уходят за край экрана при завершении перехода (без «телепортации» в последнем кадре).
 *   - Скругление углов и тень верхнего экрана выполняются на GPU в фазе draw (DetailDestinationContainer).
 */
object AppMotionScheme {

    val StandardEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val StandardDecelerateEasing = CubicBezierEasing(0f, 0f, 0f, 1f)
    val PredictiveBackEasing = CubicBezierEasing(0.1f, 0.1f, 0f, 1f)
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

    // 1. Переключение верхнеуровневых вкладок BottomBar (официальный MD3 Fade Through:
    // старая вкладка полностью исчезает за 90 мс, новая появляется после 90 мс — без пересечения и ghosting)
    fun tabEnterTransition(): EnterTransition =
        fadeIn(
            animationSpec = tween(
                durationMillis = 210,
                delayMillis = 90,
                easing = EmphasizedDecelerateEasing
            )
        ) + scaleIn(
            initialScale = 0.96f,
            animationSpec = tween(
                durationMillis = 210,
                delayMillis = 90,
                easing = EmphasizedDecelerateEasing
            )
        )

    fun tabExitTransition(): ExitTransition =
        fadeOut(
            animationSpec = tween(
                durationMillis = 90,
                easing = EmphasizedAccelerateEasing
            )
        )

    // 2. Переход вперёд на детальный экран (как в Android 15/16 Settings / Pixel:
    // непрозрачный слайд без перелёта на правом крае и мягкий параллакс нижнего экрана)
    fun detailEnterTransition(): EnterTransition =
        slideInHorizontally(
            animationSpec = tween(durationMillis = 320, easing = EmphasizedDecelerateEasing),
            initialOffsetX = { fullWidth -> fullWidth }
        )

    fun detailBackgroundExitTransition(): ExitTransition =
        slideOutHorizontally(
            animationSpec = tween(durationMillis = 320, easing = EmphasizedDecelerateEasing),
            targetOffsetX = { fullWidth -> -(fullWidth * 0.22f).toInt() }
        ) + scaleOut(
            targetScale = 0.96f,
            transformOrigin = TransformOrigin.Center,
            animationSpec = tween(durationMillis = 320, easing = EmphasizedDecelerateEasing)
        )

    // 3. Нативный Predictive Back / Pop (скраббится 1:1 через NavHost SeekableTransitionState).
    // Оба слоя остаются 100% непрозрачными (без alpha-ghosting), при коммите верхний экран
    // полностью уходит за границу экрана, а нижний плавно возвращается из параллакса.
    fun detailBackgroundPopEnterTransition(): EnterTransition =
        slideInHorizontally(
            animationSpec = tween(durationMillis = 300, easing = PredictiveBackEasing),
            initialOffsetX = { fullWidth -> -(fullWidth * 0.22f).toInt() }
        ) + scaleIn(
            initialScale = 0.96f,
            transformOrigin = TransformOrigin.Center,
            animationSpec = tween(durationMillis = 300, easing = PredictiveBackEasing)
        )

    fun detailPopExitTransition(): ExitTransition =
        slideOutHorizontally(
            animationSpec = tween(durationMillis = 300, easing = PredictiveBackEasing),
            targetOffsetX = { fullWidth -> fullWidth }
        ) + scaleOut(
            targetScale = 0.90f,
            transformOrigin = TransformOrigin.Center,
            animationSpec = tween(durationMillis = 300, easing = PredictiveBackEasing)
        )
}
