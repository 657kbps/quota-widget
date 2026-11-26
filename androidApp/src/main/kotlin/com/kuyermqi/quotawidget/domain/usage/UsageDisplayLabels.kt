package com.kuyermqi.quotawidget.domain.usage

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import com.kuyermqi.quotawidget.R
import com.kuyermqi.quotawidget.domain.QuotaWindowKind
import com.kuyermqi.quotawidget.domain.UsageLabels

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

private class ResourcesUsageLabels(resources: Resources) : UsageLabels {
    override val rolling: String = resources.getString(R.string.usage_window_rolling)
    override val weekly: String = resources.getString(R.string.usage_window_weekly)
    override val monthly: String = resources.getString(R.string.usage_window_monthly)
}

@Volatile
private var cachedLabels: Pair<Configuration, UsageLabels>? = null

/**
 * Window labels of [resources] in their current locale, reused until the configuration
 * (and therefore the app language) changes.
 */
fun usageLabels(resources: Resources): UsageLabels {
    val configuration = Configuration(resources.configuration)
    cachedLabels?.let { (cachedConfiguration, labels) ->
        if (cachedConfiguration == configuration) return labels
    }
    return ResourcesUsageLabels(resources).also { cachedLabels = configuration to it }
}

fun usageLabels(context: Context): UsageLabels = usageLabels(context.resources)
