package com.mguuschedule.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed

class HapticManager(vibrator: Vibrator) {
    private val vibratorRef = vibrator

    fun lightTick() {
        val effect = VibrationEffect.startComposition()
            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.35f)
            .compose()
        vibratorRef.vibrate(effect)
    }

    fun click() {
        val effect = VibrationEffect.startComposition()
            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.5f)
            .compose()
        vibratorRef.vibrate(effect)
    }

    fun success() {
        val effect = VibrationEffect.startComposition()
            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.6f)
            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.3f, 50)
            .compose()
        vibratorRef.vibrate(effect)
    }

    companion object {
        @Volatile
        private var INSTANCE: HapticManager? = null

        fun getInstance(context: Context): HapticManager {
            return INSTANCE ?: synchronized(this) {
                val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                    vibratorManager.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                }
                INSTANCE ?: HapticManager(vibrator).also { INSTANCE = it }
            }
        }
    }
}

fun Modifier.hapticClickable(
    enabled: Boolean = true,
    onClickLabel: String? = null,
    onClick: () -> Unit
): Modifier = composed {
    val context = androidx.compose.ui.platform.LocalContext.current
    val hapticManager = remember { HapticManager.getInstance(context) }
    
    this.clickable(
        enabled = enabled,
        onClickLabel = onClickLabel,
        interactionSource = remember { MutableInteractionSource() },
        indication = androidx.compose.foundation.LocalIndication.current
    ) {
        hapticManager.click()
        onClick()
    }
}
