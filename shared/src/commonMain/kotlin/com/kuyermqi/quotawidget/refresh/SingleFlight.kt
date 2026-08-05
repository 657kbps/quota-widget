package com.kuyermqi.quotawidget.refresh

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Coalesces concurrent calls so one owner executes the supplied block and other callers await its result.
 */
class SingleFlight<T> {
    private val mutex = Mutex()
    private var inFlight: CompletableDeferred<T>? = null

    suspend fun run(block: suspend () -> T): T {
        while (true) {
            val (flight, owner) = mutex.withLock {
                val current = inFlight
                if (current != null && !current.isCompleted) {
                    current to false
                } else {
                    CompletableDeferred<T>().also {
                        inFlight = it
                    } to true
                }
            }

            if (owner) {
                return runAsOwner(flight, block)
            }

            try {
                return flight.await()
            } catch (e: CancellationException) {
                currentCoroutineContext().ensureActive()
                // The owner was cancelled while this caller is still active. Retry as
                // the next owner instead of inheriting an unrelated cancellation.
            }
        }
    }

    private suspend fun runAsOwner(
        flight: CompletableDeferred<T>,
        block: suspend () -> T,
    ): T =
        try {
            val result = block()
            flight.complete(result)
            result
        } catch (t: Throwable) {
            flight.completeExceptionally(t)
            throw t
        } finally {
            withContext(NonCancellable) {
                mutex.withLock {
                    if (inFlight === flight) {
                        inFlight = null
                    }
                }
            }
        }
}
