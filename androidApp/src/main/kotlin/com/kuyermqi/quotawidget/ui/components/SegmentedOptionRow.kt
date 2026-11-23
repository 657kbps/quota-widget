package com.kuyermqi.quotawidget.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kuyermqi.quotawidget.R

private val ConnectedGap = 2.dp
private val OuterPillCorner = 24.dp
private val InnerCorner = 8.dp
private val FlowUnselectedCorner = 10.dp

/**
 * Borderless Material 3 connected button group for mutually exclusive options.
 * Selected items morph to a full pill with primary fill; unselected items share
 * tonal containers with small inner corners.
 */
@Composable
fun <T> SegmentedOptionRow(
    options: List<T>,
    selectedOption: T,
    labelOf: (T) -> String,
    onOptionSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    showIcon: Boolean = true,
    minHeight: Dp = 40.dp,
    contentPadding: PaddingValues = if (showIcon) {
        PaddingValues(horizontal = 12.dp, vertical = 6.dp)
    } else {
        PaddingValues(horizontal = 8.dp, vertical = 6.dp)
    },
) {
    if (options.isEmpty()) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(ConnectedGap),
    ) {
        options.forEachIndexed { index, option ->
            val selected = option == selectedOption
            val startCorner by animateDpAsState(
                targetValue = if (selected || index == 0) OuterPillCorner else InnerCorner,
                label = "segmentStartCorner",
            )
            val endCorner by animateDpAsState(
                targetValue = if (selected || index == options.lastIndex) OuterPillCorner else InnerCorner,
                label = "segmentEndCorner",
            )
            ConnectedToggleItem(
                label = labelOf(option),
                selected = selected,
                enabled = enabled,
                showIcon = showIcon,
                minHeight = minHeight,
                contentPadding = contentPadding,
                shape = RoundedCornerShape(
                    topStart = startCorner,
                    bottomStart = startCorner,
                    topEnd = endCorner,
                    bottomEnd = endCorner,
                ),
                onClick = { if (!selected) onOptionSelected(option) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * Borderless Material 3 wrapping toggle group for longer option sets that cannot
 * comfortably fit in a single equal-width connected row.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> FlowOptionGroup(
    options: List<T>,
    selectedOption: T,
    labelOf: (T) -> String,
    onOptionSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    showIcon: Boolean = true,
    minHeight: Dp = 40.dp,
) {
    if (options.isEmpty()) return
    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.forEach { option ->
            val selected = option == selectedOption
            val corner by animateDpAsState(
                targetValue = if (selected) OuterPillCorner else FlowUnselectedCorner,
                label = "flowOptionCorner",
            )
            ConnectedToggleItem(
                label = labelOf(option),
                selected = selected,
                enabled = enabled,
                showIcon = showIcon,
                minHeight = minHeight,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                shape = RoundedCornerShape(corner),
                onClick = { if (!selected) onOptionSelected(option) },
            )
        }
    }
}

@Composable
private fun ConnectedToggleItem(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    showIcon: Boolean,
    minHeight: Dp,
    contentPadding: PaddingValues,
    shape: RoundedCornerShape,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val targetContainer = when {
        !enabled && selected -> scheme.onSurface.copy(alpha = 0.12f)
        !enabled -> scheme.surfaceContainerHighest.copy(alpha = 0.5f)
        selected -> scheme.primary
        else -> scheme.secondaryContainer
    }
    val targetContent = when {
        !enabled -> scheme.onSurface.copy(alpha = 0.38f)
        selected -> scheme.onPrimary
        else -> scheme.onSecondaryContainer
    }
    val containerColor by animateColorAsState(
        targetValue = targetContainer,
        label = "toggleContainer",
    )
    val contentColor by animateColorAsState(
        targetValue = targetContent,
        label = "toggleContent",
    )

    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides minHeight) {
        Surface(
            onClick = onClick,
            enabled = enabled,
            shape = shape,
            color = containerColor,
            contentColor = contentColor,
            modifier = modifier
                .heightIn(min = minHeight)
                .semantics {
                    role = Role.RadioButton
                    this.selected = selected
                },
        ) {
            Row(
                modifier = Modifier.padding(contentPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                AnimatedVisibility(visible = showIcon && selected) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(R.drawable.ic_check),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                }
                Text(
                    text = label,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}
