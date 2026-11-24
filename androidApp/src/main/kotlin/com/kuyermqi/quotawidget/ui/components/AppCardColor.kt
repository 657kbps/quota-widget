package com.kuyermqi.quotawidget.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** Card container color for a page with a surfaceContainerLow background. */
@Composable
internal fun appCardColor(): Color {
    val scheme = MaterialTheme.colorScheme
    return if (scheme.surface.luminance() < 0.5f) {
        scheme.surfaceContainer
    } else {
        scheme.surfaceContainerLowest
    }
}
