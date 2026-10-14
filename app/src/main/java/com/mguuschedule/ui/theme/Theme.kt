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
import androidx.compose.runtime.DisposableEffect
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
        val applySystemBarsAppearance = {
            val context = view.context
            val activity = context as? android.app.Activity
                ?: (context as? android.content.ContextWrapper)?.baseContext as? android.app.Activity
            activity?.window?.let { window ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.isNavigationBarContrastEnforced = false
                }
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
            }
        }

        SideEffect {
            applySystemBarsAppearance()
        }

        DisposableEffect(view, darkTheme) {
            val listener = android.view.ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
                if (hasFocus) {
                    applySystemBarsAppearance()
                }
            }
            view.viewTreeObserver.addOnWindowFocusChangeListener(listener)
            onDispose {
                view.viewTreeObserver.removeOnWindowFocusChangeListener(listener)
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
                val primaryTone = rawLight.primary

                rawLight.copy(
                    background = androidx.compose.ui.graphics.lerp(Color.White, primaryTone, 0.055f),
                    surface = androidx.compose.ui.graphics.lerp(Color.White, primaryTone, 0.055f),
                    onBackground = rawLight.onSurface,
                    onSurface = rawLight.onSurface,
                    onSurfaceVariant = rawLight.onSurfaceVariant,

                    surfaceContainerLowest = Color.White,
                    surfaceContainerLow = Color.White,
                    surfaceContainer = androidx.compose.ui.graphics.lerp(Color.White, primaryTone, 0.05f),
                    surfaceVariant = Color.White,
                    surfaceContainerHigh = androidx.compose.ui.graphics.lerp(Color.White, primaryTone, 0.085f),
                    surfaceContainerHighest = androidx.compose.ui.graphics.lerp(Color.White, primaryTone, 0.13f),

                    primary = rawLight.primary,
                    onPrimary = rawLight.onPrimary,
                    primaryContainer = rawLight.primaryContainer,
                    onPrimaryContainer = rawLight.onPrimaryContainer,
                    secondary = rawLight.secondary,
                    onSecondary = rawLight.onSecondary,
                    secondaryContainer = rawLight.secondaryContainer,
                    onSecondaryContainer = rawLight.onSecondaryContainer,
                    tertiary = rawLight.primary,
                    onTertiary = rawLight.onPrimary,
                    tertiaryContainer = rawLight.primaryContainer,
                    onTertiaryContainer = rawLight.onPrimaryContainer,

                    outline = rawLight.outline,
                    outlineVariant = androidx.compose.ui.graphics.lerp(rawLight.onSurface, Color.White, 0.78f)
                )
            }
        }
        darkTheme -> DarkColorScheme
        else -> {
            val primaryTone = LightColorScheme.primary
            LightColorScheme.copy(
                background = androidx.compose.ui.graphics.lerp(Color.White, primaryTone, 0.055f),
                surface = androidx.compose.ui.graphics.lerp(Color.White, primaryTone, 0.055f),
                surfaceContainerLowest = Color.White,
                surfaceContainerLow = Color.White,
                surfaceContainer = androidx.compose.ui.graphics.lerp(Color.White, primaryTone, 0.05f),
                surfaceVariant = Color.White,
                surfaceContainerHigh = androidx.compose.ui.graphics.lerp(Color.White, primaryTone, 0.085f),
                surfaceContainerHighest = androidx.compose.ui.graphics.lerp(Color.White, primaryTone, 0.13f),
                outlineVariant = androidx.compose.ui.graphics.lerp(LightColorScheme.onSurface, Color.White, 0.78f)
            )
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}