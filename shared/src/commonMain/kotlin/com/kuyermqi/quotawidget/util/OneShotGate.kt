package com.kuyermqi.quotawidget.util

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Runs the supplied block at most once per instance and reports whether this call was the one
 * that ran it. Concurrent callers wait for the first run instead of repeating it; a run that
 * throws or is cancelled leaves the gate open so the caller can retry.
 */
class OneShotGate {
    private val mutex = Mutex()
    private var consumed = false

    suspend fun runOnce(block: suspend () -> Unit): Boolean = mutex.withLock {
        if (consumed) {
            false
        } else {
            block()
            consumed = true
            true
        }
    }
}
