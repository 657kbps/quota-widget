package com.kuyermqi.quotawidget.widget.usage

import androidx.compose.runtime.Composable
import com.kuyermqi.quotawidget.R
import com.kuyermqi.quotawidget.widget.ResetMoment
import com.kuyermqi.quotawidget.widget.WidgetDateFormatter
import com.kuyermqi.quotawidget.widget.contextString
import com.kuyermqi.quotawidget.widget.widgetContext

/**
 * Absolute reset wording: `重置于 14:50`, `重置于明天 13:42`,
 * `重置于 10 月 12 日 14:00`.
 */
@Composable
internal fun resetAtText(resetAtEpochMs: Long, nowEpochMs: Long): String =
    when (val moment = WidgetDateFormatter.resetMoment(widgetContext(), resetAtEpochMs, nowEpochMs)) {
        is ResetMoment.Today -> contextString(R.string.widget_resets_at, moment.time)
        is ResetMoment.Tomorrow ->
            contextString(R.string.widget_resets_at_tomorrow, moment.time)
        is ResetMoment.Later ->
            contextString(R.string.widget_resets_at_date, moment.date, moment.time)
    }
