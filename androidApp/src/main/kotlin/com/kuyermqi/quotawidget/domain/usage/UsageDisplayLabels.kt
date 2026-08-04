package com.kuyermqi.quotawidget.domain.usage

import com.kuyermqi.quotawidget.R
import com.kuyermqi.quotawidget.domain.QuotaWindowKind
import com.kuyermqi.quotawidget.domain.UsageDisplayMode

fun usageWindowLabelRes(
    kind: QuotaWindowKind,
    mode: UsageDisplayMode,
): Int = when (mode) {
    UsageDisplayMode.USED -> when (kind) {
        QuotaWindowKind.FIVE_HOUR -> R.string.usage_window_rolling
        QuotaWindowKind.WEEKLY -> R.string.usage_window_weekly
        QuotaWindowKind.MONTHLY -> R.string.usage_window_monthly
        QuotaWindowKind.BALANCE -> R.string.usage_window_rolling
    }
    UsageDisplayMode.REMAINING -> when (kind) {
        QuotaWindowKind.FIVE_HOUR -> R.string.usage_window_rolling_remaining
        QuotaWindowKind.WEEKLY -> R.string.usage_window_weekly_remaining
        QuotaWindowKind.MONTHLY -> R.string.usage_window_monthly_remaining
        QuotaWindowKind.BALANCE -> R.string.usage_window_rolling_remaining
    }
}
