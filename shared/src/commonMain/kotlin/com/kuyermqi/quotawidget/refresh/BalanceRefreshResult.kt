package com.kuyermqi.quotawidget.refresh

import com.kuyermqi.quotawidget.domain.WidgetDisplayState

sealed interface BalanceRefreshResult {
    data class Completed(val state: WidgetDisplayState) : BalanceRefreshResult
    data class TransientFailure(
        val retained: WidgetDisplayState,
        val retryable: Boolean = true,
    ) : BalanceRefreshResult
}

val BalanceRefreshResult.displayState: WidgetDisplayState
    get() = when (this) {
        is BalanceRefreshResult.Completed -> state
        is BalanceRefreshResult.TransientFailure -> retained
    }
