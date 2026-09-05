package com.mguuschedule.util

import android.content.Context
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView

/**
 * Pixel 9 Pro calibrated Semantic Haptic Manager.
 * Uses standard Android Platform HapticFeedbackConstants and LocalHapticFeedback.
 * Automatically respects system vibration settings and accessibility preferences.
 */
class HapticManager private constructor() {

    fun lightTick(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

    fun click(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    fun selection(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_FREQUENT_TICK)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

    fun toggleOn(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            view.performHapticFeedback(HapticFeedbackConstants.TOGGLE_ON)
        } else {
            selection(view)
        }
    }

    fun toggleOff(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            view.performHapticFeedback(HapticFeedbackConstants.TOGGLE_OFF)
        } else {
            selection(view)
        }
    }

    fun success(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

    fun error(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.REJECT)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

    fun longPress(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    fun gestureThreshold(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            view.performHapticFeedback(HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE)
        } else {
            lightTick(view)
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: HapticManager? = null

        fun getInstance(context: Context): HapticManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: HapticManager().also { INSTANCE = it }
            }
        }
    }
}

/**
 * Composable helper to easily access semantic haptics tied to current View.
 */
@Composable
fun rememberHapticFeedback(): SemanticHapticFeedback {
    val view = LocalView.current
    val composeHaptic = LocalHapticFeedback.current
    val manager = remember(view.context) { HapticManager.getInstance(view.context) }
    
    return remember(view, composeHaptic, manager) {
        SemanticHapticFeedback(view, composeHaptic, manager)
    }
}

class SemanticHapticFeedback(
    private val view: View,
    private val composeHaptic: HapticFeedback,
    private val manager: HapticManager
) {
    fun lightTick() {
        manager.lightTick(view)
    }

    fun click() {
        manager.click(view)
    }

    fun selection() {
        manager.selection(view)
    }

    fun toggleOn() {
        manager.toggleOn(view)
    }

    fun toggleOff() {
        manager.toggleOff(view)
    }

    fun toggle(enabled: Boolean) {
        if (enabled) toggleOn() else toggleOff()
    }

    fun success() {
        manager.success(view)
    }

    fun error() {
        manager.error(view)
    }

    fun longPress() {
        composeHaptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    fun gestureThreshold() {
        manager.gestureThreshold(view)
    }
}

fun Modifier.hapticClickable(
    enabled: Boolean = true,
    onClickLabel: String? = null,
    onClick: () -> Unit
): Modifier = composed {
    val haptic = rememberHapticFeedback()
    
    this.clickable(
        enabled = enabled,
        onClickLabel = onClickLabel,
        interactionSource = remember { MutableInteractionSource() },
        indication = LocalIndication.current
    ) {
        haptic.click()
        onClick()
    }
}
