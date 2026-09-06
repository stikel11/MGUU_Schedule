package com.mguuschedule.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Material 3 Expressive Shape Scale for MGUU Schedule.
 * Establishes visual hierarchy:
 * - Extra Small (8dp): Compact tags, timeline dividers, chips
 * - Small (12dp): Text fields, input controls, small badges
 * - Medium (16dp - 20dp): Content cards, list items, course accordions
 * - Large (24dp): Settings section containers, detail cards
 * - Extra Large (28dp): Hero cards, bottom sheet top corners, search container
 */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)
