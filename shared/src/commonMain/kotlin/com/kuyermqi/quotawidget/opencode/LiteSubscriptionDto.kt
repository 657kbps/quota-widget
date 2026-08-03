package com.kuyermqi.quotawidget.opencode

import com.kuyermqi.quotawidget.domain.QuotaWindow
import com.kuyermqi.quotawidget.domain.QuotaWindowKind

data class LiteUsageDto(
    val status: String? = null,
    val usagePercent: Double,
    val resetInSec: Long? = null,
)

data class LiteSubscriptionDto(
    val rollingUsage: LiteUsageDto? = null,
    val weeklyUsage: LiteUsageDto? = null,
    val monthlyUsage: LiteUsageDto? = null,
) {
    fun toWindows(): List<QuotaWindow> = buildList {
        rollingUsage?.let {
            add(
                QuotaWindow(
                    kind = QuotaWindowKind.FIVE_HOUR,
                    usedPercent = it.usagePercent,
                    resetInSec = it.resetInSec,
                ),
            )
        }
        weeklyUsage?.let {
            add(
                QuotaWindow(
                    kind = QuotaWindowKind.WEEKLY,
                    usedPercent = it.usagePercent,
                    resetInSec = it.resetInSec,
                ),
            )
        }
        monthlyUsage?.let {
            add(
                QuotaWindow(
                    kind = QuotaWindowKind.MONTHLY,
                    usedPercent = it.usagePercent,
                    resetInSec = it.resetInSec,
                ),
            )
        }
    }
}
