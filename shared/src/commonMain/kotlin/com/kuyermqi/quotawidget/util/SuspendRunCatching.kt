package com.kuyermqi.quotawidget.util

import kotlin.coroutines.cancellation.CancellationException

/**
 * [runCatching] for blocks that suspend.
 *
 * A plain `runCatching` around a suspending call also captures [CancellationException] and turns
 * the coroutine cancellation into a `Result.failure`, so the calling scope keeps running instead of
 * unwinding. This variant converts any other [Throwable] into `Result.failure` but rethrows
 * [CancellationException] unchanged.
 */
suspend fun <T> suspendRunCatching(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }
