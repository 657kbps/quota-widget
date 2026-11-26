package com.kuyermqi.quotawidget.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.layout.Column
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.kuyermqi.quotawidget.R
import com.kuyermqi.quotawidget.domain.WidgetDisplayState
import com.kuyermqi.quotawidget.ui.widgetErrorText

private const val STANDARD_STATUS_HORIZONTAL_RESERVE_DP = 28f
private const val STANDARD_STATUS_VERTICAL_RESERVE_DP = 72f
private const val STANDARD_STATUS_MIN_WIDTH_DP = 48f
private const val STANDARD_STATUS_MIN_HEIGHT_DP = 36f
private const val STANDARD_STATUS_MIN_SP = 12f
private const val STANDARD_STATUS_MAX_SP = 26f
private const val STANDARD_STATUS_MAX_LINES = 3

private const val COMPACT_STATUS_HORIZONTAL_RESERVE_DP = 64f
private const val COMPACT_STATUS_VERTICAL_RESERVE_DP = 16f
private const val COMPACT_STATUS_MIN_WIDTH_DP = 48f
private const val COMPACT_STATUS_MIN_HEIGHT_DP = 32f
private const val COMPACT_STATUS_MIN_SP = 11f
private const val COMPACT_ERROR_MIN_SP = 10f
private const val COMPACT_STATUS_MAX_SP = 22f
private const val COMPACT_STATUS_MAX_LINES = 2
private const val COMPACT_ERROR_MAX_LINES = 3

@Composable
internal fun widgetStatusTitle(state: WidgetDisplayState): String = when (state) {
    WidgetDisplayState.NotConfigured -> contextString(R.string.widget_not_configured)
    WidgetDisplayState.Loading -> contextString(R.string.widget_loading_balance)
    WidgetDisplayState.NeedsReauth -> contextString(R.string.widget_needs_reauth)
    is WidgetDisplayState.Error -> widgetErrorText(state.kind, widgetContext())
    is WidgetDisplayState.Success -> ""
}

@Suppress("FunctionNaming")
@Composable
internal fun WidgetStatusBlock(
    state: WidgetDisplayState,
    openApp: Action,
    horizontalReserve: Dp = STANDARD_STATUS_HORIZONTAL_RESERVE_DP.dp,
    verticalReserve: Dp = STANDARD_STATUS_VERTICAL_RESERVE_DP.dp,
) {
    val size = LocalSize.current
    val title = widgetStatusTitle(state)
    val titleSize = fittedMultiLineTextSize(
        text = title,
        bounds = TextFitBounds(
            maxWidth = (size.width - horizontalReserve).coerceAtLeast(STANDARD_STATUS_MIN_WIDTH_DP.dp),
            maxHeight = (size.height - verticalReserve).coerceAtLeast(STANDARD_STATUS_MIN_HEIGHT_DP.dp),
            minSp = STANDARD_STATUS_MIN_SP,
            maxSp = STANDARD_STATUS_MAX_SP,
        ),
        maxLines = STANDARD_STATUS_MAX_LINES,
    )
    BalanceBlock(
        title = title,
        subtitle = null,
        titleSize = titleSize,
        openApp = openApp,
        maxLines = STANDARD_STATUS_MAX_LINES,
    )
}

@Suppress("FunctionNaming")
@Composable
internal fun CompactStatusTitle(
    state: WidgetDisplayState,
    openApp: Action,
    modifier: GlanceModifier = GlanceModifier,
) {
    val size = LocalSize.current
    val title = widgetStatusTitle(state)
    val isError = state is WidgetDisplayState.Error
    val maxLines = if (isError) COMPACT_ERROR_MAX_LINES else COMPACT_STATUS_MAX_LINES
    val minSp = if (isError) COMPACT_ERROR_MIN_SP else COMPACT_STATUS_MIN_SP
    val titleSize = fittedMultiLineTextSize(
        text = title,
        bounds = TextFitBounds(
            maxWidth = (size.width - COMPACT_STATUS_HORIZONTAL_RESERVE_DP.dp)
                .coerceAtLeast(COMPACT_STATUS_MIN_WIDTH_DP.dp),
            maxHeight = (size.height - COMPACT_STATUS_VERTICAL_RESERVE_DP.dp)
                .coerceAtLeast(COMPACT_STATUS_MIN_HEIGHT_DP.dp),
            minSp = minSp,
            maxSp = COMPACT_STATUS_MAX_SP,
        ),
        maxLines = maxLines,
    )
    Column(modifier = modifier.clickableNoRipple(openApp)) {
        Text(
            text = title,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = titleSize,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = maxLines,
            modifier = GlanceModifier.clickableNoRipple(openApp),
        )
    }
}
