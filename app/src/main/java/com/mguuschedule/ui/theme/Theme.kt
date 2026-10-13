package com.mguuschedule.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = OnPrimaryDark,
    primaryContainer = PrimaryContainerDark,
    onPrimaryContainer = OnPrimaryContainerDark,
    secondary = SecondaryDark,
    onSecondary = OnSecondaryDark,
    secondaryContainer = SecondaryContainerDark,
    onSecondaryContainer = OnSecondaryContainerDark,
    tertiary = TertiaryDark,
    onTertiary = OnTertiaryDark,
    tertiaryContainer = TertiaryContainerDark,
    onTertiaryContainer = OnTertiaryContainerDark,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    surfaceDim = SurfaceDimDark,
    surfaceBright = SurfaceBrightDark,
    surfaceContainerLowest = SurfaceContainerLowestDark,
    surfaceContainerLow = SurfaceContainerLowDark,
    surfaceContainer = SurfaceContainerDark,
    surfaceContainerHigh = SurfaceContainerHighDark,
    surfaceContainerHighest = SurfaceContainerHighestDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryLight,
    onPrimary = OnPrimaryLight,
    primaryContainer = PrimaryContainerLight,
    onPrimaryContainer = OnPrimaryContainerLight,
    secondary = SecondaryLight,
    onSecondary = OnSecondaryLight,
    secondaryContainer = SecondaryContainerLight,
    onSecondaryContainer = OnSecondaryContainerLight,
    tertiary = TertiaryLight,
    onTertiary = OnTertiaryLight,
    tertiaryContainer = TertiaryContainerLight,
    onTertiaryContainer = OnTertiaryContainerLight,
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    surfaceDim = SurfaceDimLight,
    surfaceBright = SurfaceBrightLight,
    surfaceContainerLowest = SurfaceContainerLowestLight,
    surfaceContainerLow = SurfaceContainerLowLight,
    surfaceContainer = SurfaceContainerLight,
    surfaceContainerHigh = SurfaceContainerHighLight,
    surfaceContainerHighest = SurfaceContainerHighestLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight
)

@Composable
fun MGUUScheduleTheme(
    themeMode: Int = 0, // 0: System, 1: Light, 2: Dark
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        val activity = view.context.findActivity()
        if (activity != null) {
            SideEffect {
                val window = activity.window
                // Полный сброс цвета баров для правильного Edge-to-Edge
                window.statusBarColor = android.graphics.Color.TRANSPARENT
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.isNavigationBarContrastEnforced = false
                }
                
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) {
                dynamicDarkColorScheme(context)
            } else {
                val rawLight = dynamicLightColorScheme(context)

                rawLight.copy(
                    // Фон экрана — родной воздушный Tone 95/96 из HCT (без грязных серых примесей):
                    background = rawLight.surfaceContainerLow,
                    surface = rawLight.surfaceContainerLow,
                    onBackground = rawLight.onSurface,
                    onSurface = rawLight.onSurface,
                    onSurfaceVariant = rawLight.onSurfaceVariant,

                    // Все основные карточки, кнопки шапки и плавающий бар — ЧИСТО БЕЛЫЕ (Tone 100):
                    surfaceContainerLowest = Color.White,
                    surfaceContainerLow = Color.White,
                    surfaceContainer = Color.White,
                    surfaceVariant = Color.White,
                    surfaceContainerHigh = Color.White,

                    // Вложенные чипы внутри белых карточек (Ауд. 419к, Модуль 1/2) — легкий, чистый Tone 94 из HCT:
                    surfaceContainerHighest = rawLight.surfaceContainer,

                    // Акцентные контейнеры — БЕРЕМ НАПРЯМУЮ ИЗ HCT (Tone 90, высокая Chroma, никакой грязи от lerp!):
                    primary = rawLight.primary,
                    onPrimary = rawLight.onPrimary,
                    primaryContainer = rawLight.primaryContainer,
                    onPrimaryContainer = rawLight.onPrimaryContainer,
                    secondary = rawLight.secondary,
                    onSecondary = rawLight.onSecondary,
                    secondaryContainer = rawLight.secondaryContainer,
                    onSecondaryContainer = rawLight.onSecondaryContainer,

                    // Убираем кислотный разброс третьего цвета, приводя его к единой гамме с primaryContainer:
                    tertiary = rawLight.primary,
                    onTertiary = rawLight.onPrimary,
                    tertiaryContainer = rawLight.primaryContainer,
                    onTertiaryContainer = rawLight.onPrimaryContainer,

                    // Мягкие, почти невидимые границы, чтобы убрать темную серую рамку вокруг белых карточек:
                    outline = rawLight.outline,
                    outlineVariant = rawLight.surfaceContainerHighest.copy(alpha = 0.5f)
                )
            }
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}