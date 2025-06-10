package com.wyldsoft.notes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import com.wyldsoft.notes.pen.PenType

fun Modifier.noRippleClickable(
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier = composed {
    clickable(
        enabled = enabled,
        indication = null,
        interactionSource = remember { MutableInteractionSource() }
    ) {
        onClick()
    }
}

// Helper functions for pen profile management
fun getDefaultStrokeWidthForPenType(penType: PenType): Float {
    return when (penType) {
        PenType.BALLPEN -> 5f
        PenType.FOUNTAIN -> 8f
        PenType.MARKER -> 20f
        PenType.PENCIL -> 3f
        PenType.CHARCOAL -> 15f
        PenType.CHARCOAL_V2 -> 15f
        PenType.NEO_BRUSH -> 25f
        PenType.DASH -> 6f
    }
}

fun getMaxStrokeSizeForPenType(penType: PenType): Float {
    return when (penType) {
        PenType.BALLPEN -> 20f
        PenType.FOUNTAIN -> 30f
        PenType.MARKER -> 60f
        PenType.PENCIL -> 15f
        PenType.CHARCOAL -> 40f
        PenType.CHARCOAL_V2 -> 40f
        PenType.NEO_BRUSH -> 50f
        PenType.DASH -> 25f
    }
}

fun getColorName(color: androidx.compose.ui.graphics.Color): String {
    return when (color) {
        androidx.compose.ui.graphics.Color.Black -> "Black"
        androidx.compose.ui.graphics.Color.DarkGray -> "Dark Gray"
        androidx.compose.ui.graphics.Color.Gray -> "Gray"
        androidx.compose.ui.graphics.Color.LightGray -> "Light Gray"
        androidx.compose.ui.graphics.Color.Red -> "Red"
        androidx.compose.ui.graphics.Color.Blue -> "Blue"
        androidx.compose.ui.graphics.Color.Green -> "Green"
        androidx.compose.ui.graphics.Color(0xFF8B4513) -> "Brown"
        androidx.compose.ui.graphics.Color(0xFFFF69B4) -> "Pink"
        androidx.compose.ui.graphics.Color(0xFFFF8C00) -> "Orange"
        androidx.compose.ui.graphics.Color(0xFF800080) -> "Purple"
        androidx.compose.ui.graphics.Color(0xFF008080) -> "Teal"
        else -> "Custom"
    }
}