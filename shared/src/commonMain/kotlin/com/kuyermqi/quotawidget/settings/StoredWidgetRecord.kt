package com.kuyermqi.quotawidget.settings

import com.kuyermqi.quotawidget.domain.CurrencyPreference
import com.kuyermqi.quotawidget.domain.QuotaSnapshot
import com.kuyermqi.quotawidget.domain.QuotaWindow
import com.kuyermqi.quotawidget.domain.QuotaWindowKind
import com.kuyermqi.quotawidget.domain.WidgetDisplayState
import com.kuyermqi.quotawidget.domain.decodeQuotaWindows
import com.kuyermqi.quotawidget.domain.formatBalance
import com.kuyermqi.quotawidget.platform.PlatformIds
import com.kuyermqi.quotawidget.platform.PlatformRegistry

/** Widget status values as persisted in settings. */
internal object StoredWidgetStatus {
    const val NOT_CONFIGURED = "not_configured"
    const val LOADING = "loading"
    const val SUCCESS = "success"
    const val ERROR = "error"
    const val NEEDS_REAUTH = "needs_reauth"
}

private const val DEFAULT_WIDGET_ERROR = "刷新失败"

/**
 * Widget status plus the last stored payload of one platform, already resolved against the
 * legacy keys by the settings repository. Kept free of storage APIs so the decoding is shared
 * with the tests instead of living inside the DataStore implementation.
 */
data class StoredWidgetRecord(
    val platformId: String,
    val configured: Boolean,
    val status: String?,
    val error: String? = null,
    val payloadPlatformId: String? = null,
    val payloadPlatformName: String? = null,
    val currency: String? = null,
    val total: String? = null,
    val formatted: String? = null,
    val windows: String? = null,
    val updatedAtEpochMs: Long? = null,
    val unlimitedQuota: Boolean = false,
    val usedDisplay: String = "",
    val emptyLimitedQuota: Boolean = false,
    val tokenExpired: Boolean = false,
    val quotaOverspent: Boolean = false,
)

fun StoredWidgetRecord.toWidgetDisplayState(): WidgetDisplayState {
    if (!configured) return WidgetDisplayState.NotConfigured
    return when (status) {
        StoredWidgetStatus.LOADING -> WidgetDisplayState.Loading
        StoredWidgetStatus.NEEDS_REAUTH -> WidgetDisplayState.NeedsReauth
        StoredWidgetStatus.ERROR -> WidgetDisplayState.Error(error ?: DEFAULT_WIDGET_ERROR)
        StoredWidgetStatus.SUCCESS -> WidgetDisplayState.Success(
            QuotaSnapshot(
                platformId = payloadPlatformId ?: platformId,
                platformName = payloadPlatformName ?: PlatformRegistry.displayName(platformId),
                windows = decodeQuotaWindows(windows) ?: defaultWindows(platformId),
                primaryDisplay = formatted ?: defaultPrimaryDisplay(),
                updatedAtEpochMs = updatedAtEpochMs ?: 0L,
                currency = CurrencyPreference.fromStorage(currency),
                totalBalance = total ?: "0",
                unlimitedQuota = unlimitedQuota,
                usedDisplay = usedDisplay,
                emptyLimitedQuota = emptyLimitedQuota,
                tokenExpired = tokenExpired,
                quotaOverspent = quotaOverspent,
            ),
        )
        else -> WidgetDisplayState.Loading
    }
}

private fun StoredWidgetRecord.defaultPrimaryDisplay(): String =
    if (platformId == PlatformIds.DEEPSEEK) {
        formatBalance(CurrencyPreference.fromStorage(currency), total ?: "0")
    } else {
        ""
    }

private fun defaultWindows(platformId: String): List<QuotaWindow> =
    when (platformId) {
        PlatformIds.DEEPSEEK -> listOf(QuotaWindow(kind = QuotaWindowKind.BALANCE))
        PlatformIds.NEW_API -> listOf(
            QuotaWindow(kind = QuotaWindowKind.BALANCE),
            QuotaWindow(kind = QuotaWindowKind.TOKEN),
        )
        else -> emptyList()
    }
