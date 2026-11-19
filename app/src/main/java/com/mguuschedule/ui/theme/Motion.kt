package com.mguuschedule.ui.theme

import androidx.activity.BackEventCompat
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
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
 * Единая система движения в стиле Material 3 Expressive и нативных приложений Google (Pixel / Android 15–16).
 *
 * - Spatial-пружины — для локальной геометрии (чипы, кнопки, карточки, индикаторы).
 * - Effects-пружины (damping = 1.0f) — для цвета и прозрачности без перелёта.
 * - Полноэкранная навигация и Predictive Back (`predictivePopEnterTransition` / `predictivePopExitTransition`
 *   в Navigation Compose 2.10+ с учётом стороны свайпа `swipeEdge`):
 *   - 100% непрозрачные поверхности (без cross-fade ghosting);
 *   - верхний экран с физическим скруглением углов устройства и лёгким масштабированием (`0.92f`)
 *     полностью уходит за границу экрана в направлении жеста (не «схлопывается в центр»);
 *   - нижний экран плавно выезжает из параллакса (`-24%`, `scale 0.94f -> 1.0f`), как в Pixel Settings.
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

    // 2. Переход вперёд на детальный экран (Pixel Settings / Android 15–16):
    // новый экран плавно въезжает справа, старый уходит в параллакс -24% + scale 0.94f.
    fun detailEnterTransition(): EnterTransition =
        slideInHorizontally(
            animationSpec = tween(durationMillis = 340, easing = EmphasizedDecelerateEasing),
            initialOffsetX = { fullWidth -> fullWidth }
        )

    fun detailBackgroundExitTransition(): ExitTransition =
        slideOutHorizontally(
            animationSpec = tween(durationMillis = 340, easing = EmphasizedDecelerateEasing),
            targetOffsetX = { fullWidth -> -(fullWidth * 0.24f).toInt() }
        ) + scaleOut(
            targetScale = 0.94f,
            transformOrigin = TransformOrigin.Center,
            animationSpec = tween(durationMillis = 340, easing = EmphasizedDecelerateEasing)
        )

    // 3. Возврат по кнопке «Назад» (TopBar / 3-кнопочная навигация)
    fun detailBackgroundPopEnterTransition(): EnterTransition =
        slideInHorizontally(
            animationSpec = tween(durationMillis = 320, easing = EmphasizedDecelerateEasing),
            initialOffsetX = { fullWidth -> -(fullWidth * 0.24f).toInt() }
        ) + scaleIn(
            initialScale = 0.94f,
            transformOrigin = TransformOrigin.Center,
            animationSpec = tween(durationMillis = 320, easing = EmphasizedDecelerateEasing)
        )

    fun detailPopExitTransition(): ExitTransition =
        slideOutHorizontally(
            animationSpec = tween(durationMillis = 320, easing = EmphasizedDecelerateEasing),
            targetOffsetX = { fullWidth -> fullWidth }
        ) + scaleOut(
            targetScale = 0.92f,
            transformOrigin = TransformOrigin.Center,
            animationSpec = tween(durationMillis = 320, easing = EmphasizedDecelerateEasing)
        )

    // 4. Нативный Predictive Back жест в NavHost 2.10+ с учётом края свайпа (EDGE_LEFT / EDGE_RIGHT).
    // Работает одинаково чисто и при медленном удержании жеста, и при быстром смахивании (fling):
    // закрываемый экран с реальным скруглением углов и тенью плавно уходит за край экрана
    // по направлению жеста, а предыдущий выезжает из параллакса (-24% / +24%) без ghosting.
    fun detailPredictivePopEnterTransition(swipeEdge: Int): EnterTransition {
        val direction = if (swipeEdge == BackEventCompat.EDGE_RIGHT) -1 else 1
        return slideInHorizontally(
            animationSpec = tween(durationMillis = 340, easing = FastOutSlowInEasing),
            initialOffsetX = { fullWidth -> -(direction * (fullWidth * 0.24f)).toInt() }
        ) + scaleIn(
            initialScale = 0.94f,
            transformOrigin = TransformOrigin.Center,
            animationSpec = tween(durationMillis = 340, easing = FastOutSlowInEasing)
        )
    }

    fun detailPredictivePopExitTransition(swipeEdge: Int): ExitTransition {
        val direction = if (swipeEdge == BackEventCompat.EDGE_RIGHT) -1 else 1
        return slideOutHorizontally(
            animationSpec = tween(durationMillis = 340, easing = FastOutSlowInEasing),
            targetOffsetX = { fullWidth -> direction * fullWidth }
        ) + scaleOut(
            targetScale = 0.92f,
            transformOrigin = TransformOrigin.Center,
            animationSpec = tween(durationMillis = 340, easing = FastOutSlowInEasing)
        )
    }
}
