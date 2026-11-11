package com.kuyermqi.quotawidget.ui.usage

import android.content.res.Resources
import com.kuyermqi.quotawidget.R
import com.kuyermqi.quotawidget.domain.QuotaWindow
import com.kuyermqi.quotawidget.domain.UsageDisplayMode
import com.kuyermqi.quotawidget.domain.UsageWindowKind
import com.kuyermqi.quotawidget.domain.formatUsageForWindow
import com.kuyermqi.quotawidget.domain.resolveCodexUsageSummaryWindowKind

/**
 * Formats a home-list usage summary for the **exact** [windowKind].
 * OpenCode Go uses this — no cross-window fallback.
 */
fun formatUsageWindowSummary(
    resources: Resources,
    windows: List<QuotaWindow>,
    windowKind: UsageWindowKind,
    usageDisplayMode: UsageDisplayMode,
    fallback: String,
): String {
    val percent = formatUsageForWindow(windows, windowKind, usageDisplayMode)
        ?: return fallback
    return resources.getString(
        usageSummaryStringRes(windowKind, usageDisplayMode),
        percent,
    )
}

/**
 * Home-list summary that falls back to the smallest available window when
 * [windowKind] has no data (e.g. a plan that only reports weekly).
 */
fun formatUsageWindowSummaryWithFallback(
    resources: Resources,
    windows: List<QuotaWindow>,
    windowKind: UsageWindowKind,
    usageDisplayMode: UsageDisplayMode,
    fallback: String,
): String {
    val effectiveKind = resolveCodexUsageSummaryWindowKind(windows, windowKind)
        ?: return fallback
    val percent = formatUsageForWindow(windows, effectiveKind, usageDisplayMode)
        ?: return fallback
    return resources.getString(
        usageSummaryStringRes(effectiveKind, usageDisplayMode),
        percent,
    )
}

/** Codex-specific name kept for the home list; behavior is [formatUsageWindowSummaryWithFallback]. */
fun formatCodexUsageWindowSummary(
    resources: Resources,
    windows: List<QuotaWindow>,
    windowKind: UsageWindowKind,
    usageDisplayMode: UsageDisplayMode,
    fallback: String,
): String = formatUsageWindowSummaryWithFallback(
    resources = resources,
    windows = windows,
    windowKind = windowKind,
    usageDisplayMode = usageDisplayMode,
    fallback = fallback,
)

private fun usageSummaryStringRes(
    kind: UsageWindowKind,
    mode: UsageDisplayMode,
): Int = when (mode) {
    UsageDisplayMode.USED -> when (kind) {
        UsageWindowKind.ROLLING -> R.string.usage_used_rolling
        UsageWindowKind.WEEKLY -> R.string.usage_used_weekly
        UsageWindowKind.MONTHLY -> R.string.usage_used_monthly
        UsageWindowKind.TOKEN -> R.string.usage_used_token
    }
    UsageDisplayMode.REMAINING -> when (kind) {
        UsageWindowKind.ROLLING -> R.string.usage_remaining_rolling
        UsageWindowKind.WEEKLY -> R.string.usage_remaining_weekly
        UsageWindowKind.MONTHLY -> R.string.usage_remaining_monthly
        UsageWindowKind.TOKEN -> R.string.usage_remaining_token
    }
}
