package com.mguuschedule.ui.theme

import androidx.compose.animation.*
import androidx.compose.animation.core.*

object AppMotionScheme {
    // Размеренная пространственная пружина MD3 Expressive (чуть замедленная, без резких рывков)
    fun <T> expressiveSpatialSpec(): SpringSpec<T> = spring(
        dampingRatio = 0.84f,
        stiffness = 190f
    )

    fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = spring(
        dampingRatio = 0.82f,
        stiffness = 220f
    )

    fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = spring(
        dampingRatio = 0.86f,
        stiffness = 200f
    )

    fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = spring(
        dampingRatio = 0.88f,
        stiffness = 140f
    )

    fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = tween(
        durationMillis = 200,
        easing = FastOutSlowInEasing
    )

    fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = tween(
        durationMillis = 260,
        easing = FastOutSlowInEasing
    )

    // 1. Переход между главными вкладками BottomBar (Through-Fade без наложения и остаточных изображений!)
    fun tabEnterTransition(): EnterTransition =
        fadeIn(
            animationSpec = tween(
                durationMillis = 280,
                delayMillis = 85,
                easing = LinearOutSlowInEasing
            )
        ) + scaleIn(
            initialScale = 0.94f,
            animationSpec = spring(dampingRatio = 0.84f, stiffness = 210f)
        )

    fun tabExitTransition(): ExitTransition =
        fadeOut(
            animationSpec = tween(
                durationMillis = 110,
                easing = FastOutLinearInEasing
            )
        ) + scaleOut(
            targetScale = 0.96f,
            animationSpec = tween(durationMillis = 110, easing = FastOutLinearInEasing)
        )

    // 2. Переход вперед на детальный экран (Информация о паре, История уведомлений, Обучение)
    fun detailEnterTransition(): EnterTransition =
        slideInHorizontally(
            animationSpec = spring(dampingRatio = 0.85f, stiffness = 190f),
            initialOffsetX = { fullWidth -> (fullWidth * 0.28f).toInt() }
        ) + fadeIn(
            animationSpec = tween(durationMillis = 220, easing = LinearOutSlowInEasing)
        ) + scaleIn(
            initialScale = 0.95f,
            animationSpec = spring(dampingRatio = 0.85f, stiffness = 190f)
        )

    // Фоновый экран при открытии детального уходит чуть влево и вглубь, оставаясь плотным
    fun detailBackgroundExitTransition(): ExitTransition =
        slideOutHorizontally(
            animationSpec = spring(dampingRatio = 0.85f, stiffness = 190f),
            targetOffsetX = { fullWidth -> -(fullWidth * 0.14f).toInt() }
        ) + scaleOut(
            targetScale = 0.93f,
            animationSpec = spring(dampingRatio = 0.85f, stiffness = 190f)
        ) + fadeOut(
            targetAlpha = 0.88f,
            animationSpec = tween(durationMillis = 280)
        )

    // 3. Возврат назад (Pop / Predictive Back) — фоновый экран плавно возвращается на место
    fun detailBackgroundPopEnterTransition(): EnterTransition =
        slideInHorizontally(
            animationSpec = spring(dampingRatio = 0.85f, stiffness = 200f),
            initialOffsetX = { fullWidth -> -(fullWidth * 0.14f).toInt() }
        ) + scaleIn(
            initialScale = 0.93f,
            animationSpec = spring(dampingRatio = 0.85f, stiffness = 200f)
        ) + fadeIn(
            initialAlpha = 0.88f,
            animationSpec = tween(durationMillis = 240)
        )

    // Закрывающийся детальный экран уходит вправо с уменьшением масштаба
    fun detailPopExitTransition(): ExitTransition =
        slideOutHorizontally(
            animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
            targetOffsetX = { fullWidth -> (fullWidth * 0.32f).toInt() }
        ) + scaleOut(
            targetScale = 0.88f,
            animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing)
        ) + fadeOut(
            animationSpec = tween(durationMillis = 240, delayMillis = 60, easing = FastOutLinearInEasing)
        )
}