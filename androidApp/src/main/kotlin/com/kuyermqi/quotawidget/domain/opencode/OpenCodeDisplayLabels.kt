package com.kuyermqi.quotawidget.domain.opencode

import com.kuyermqi.quotawidget.R
import com.kuyermqi.quotawidget.domain.OpenCodeUsageDisplayMode
import com.kuyermqi.quotawidget.domain.QuotaWindowKind

internal fun openCodeWindowLabelRes(
    kind: QuotaWindowKind,
    mode: OpenCodeUsageDisplayMode,
): Int = when (mode) {
    OpenCodeUsageDisplayMode.USED -> when (kind) {
        QuotaWindowKind.FIVE_HOUR -> R.string.opencode_window_rolling
        QuotaWindowKind.WEEKLY -> R.string.opencode_window_weekly
        QuotaWindowKind.MONTHLY -> R.string.opencode_window_monthly
        QuotaWindowKind.BALANCE -> R.string.opencode_window_rolling
    }
    OpenCodeUsageDisplayMode.REMAINING -> when (kind) {
        QuotaWindowKind.FIVE_HOUR -> R.string.opencode_window_rolling_remaining
        QuotaWindowKind.WEEKLY -> R.string.opencode_window_weekly_remaining
        QuotaWindowKind.MONTHLY -> R.string.opencode_window_monthly_remaining
        QuotaWindowKind.BALANCE -> R.string.opencode_window_rolling_remaining
    }
}
