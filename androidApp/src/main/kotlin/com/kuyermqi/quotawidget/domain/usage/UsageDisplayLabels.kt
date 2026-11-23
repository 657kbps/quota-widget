package com.kuyermqi.quotawidget.domain.usage

import com.kuyermqi.quotawidget.R
import com.kuyermqi.quotawidget.domain.QuotaWindowKind
import com.kuyermqi.quotawidget.domain.UsageDisplayMode

/** Base window label shared by the overview widget and the settings preview. */
fun usageWindowLabelRes(kind: QuotaWindowKind): Int = when (kind) {
    QuotaWindowKind.FIVE_HOUR -> R.string.usage_window_rolling
    QuotaWindowKind.WEEKLY -> R.string.usage_window_weekly
    QuotaWindowKind.MONTHLY -> R.string.usage_window_monthly
    QuotaWindowKind.TOKEN -> R.string.usage_window_token
    QuotaWindowKind.BALANCE -> R.string.usage_window_rolling
}

/**
 * Suffix that tells which number the bar shows, e.g. "滚动用量" / "滚动余量".
 * Compose it with [R.string.usage_window_label_with_mode].
 */
fun usageWindowModeLabelRes(mode: UsageDisplayMode): Int = when (mode) {
    UsageDisplayMode.USED -> R.string.usage_display_used
    UsageDisplayMode.REMAINING -> R.string.usage_display_remaining
}
