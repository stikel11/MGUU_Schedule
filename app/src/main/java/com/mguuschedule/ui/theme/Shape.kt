package com.mguuschedule.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Material 3 Expressive Shape Scale for MGUU Schedule.
 * Establishes visual hierarchy:
 * - Extra Small (8dp): Compact tags, timeline dividers, code/log wells
 * - Small (12dp): Small badges, compact inner items
 * - Medium (16dp): Text fields, nested Bento tiles, inner cards
 * - Large (24dp): Primary cards, grouped list outer corners
 * - Extra Large (28dp): Hero cards, dialogs, bottom sheets
 */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/**
 * Единые экспрессивные формы MD3E (включая сегментированные списки в стиле Pixel Settings).
 */
object ExpressiveShapes {
    val Card: Shape = RoundedCornerShape(24.dp)
    val HeroCard: Shape = RoundedCornerShape(28.dp)
    val InnerCard: Shape = RoundedCornerShape(16.dp)
    val InputField: Shape = RoundedCornerShape(16.dp)
    val SearchBar: Shape = CircleShape
    val BottomSheet: Shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val Dialog: Shape = RoundedCornerShape(28.dp)

    val SegmentedSingle: Shape = RoundedCornerShape(24.dp)
    val SegmentedTop: Shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 6.dp, bottomEnd = 6.dp)
    val SegmentedMiddle: Shape = RoundedCornerShape(6.dp)
    val SegmentedBottom: Shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 24.dp, bottomEnd = 24.dp)

    fun segmentedItem(index: Int, total: Int): Shape {
        if (total <= 1) return SegmentedSingle
        return when (index) {
            0 -> SegmentedTop
            total - 1 -> SegmentedBottom
            else -> SegmentedMiddle
        }
    }
}
