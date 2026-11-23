package com.kuyermqi.quotawidget.domain.usage

import android.content.res.Resources
import com.kuyermqi.quotawidget.R
import com.kuyermqi.quotawidget.domain.QuotaWindowKind

/** Base window label shared by the overview widget and the settings preview. */
fun usageWindowLabelRes(kind: QuotaWindowKind): Int = when (kind) {
    QuotaWindowKind.FIVE_HOUR -> R.string.usage_window_rolling
    QuotaWindowKind.WEEKLY -> R.string.usage_window_weekly
    QuotaWindowKind.MONTHLY -> R.string.usage_window_monthly
    QuotaWindowKind.TOKEN -> R.string.usage_window_token
    QuotaWindowKind.BALANCE -> R.string.usage_window_rolling
}

/**
 * Row label shared by the overview widget and its settings preview. The used/remaining
 * mode is intentionally omitted so the narrow widget rows do not overflow.
 */
fun overviewWindowLabel(
    resources: Resources,
    kind: QuotaWindowKind,
): String = resources.getString(usageWindowLabelRes(kind))
