package com.kuyermqi.quotawidget.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SuspendRunCatchingTest {
    @Test
    fun returnsSuccessForNormalBlock() = runTest {
        val result = suspendRunCatching {
            delay(1)
            7
        }

        assertTrue(result.isSuccess)
        assertEquals(7, result.getOrNull())
    }

    @Test
    fun returnsFailureForOrdinaryException() = runTest {
        val boom = IllegalStateException("boom")

        val result = suspendRunCatching<Int> { throw boom }

        assertTrue(result.isFailure)
        assertSame(boom, result.exceptionOrNull())
    }

    @Test
    fun rethrowsCancellationInsteadOfWrappingIt() = runTest {
        val thrown = assertFailsWith<CancellationException> {
            suspendRunCatching<Int> { throw CancellationException("cancelled") }
        }

        assertEquals("cancelled", thrown.message)
    }

    @Test
    fun cancellationOfParentScopeStopsLaunchedCoroutine() = runTest {
        val started = CompletableDeferred<Unit>()
        var continuedAfterCancellation = false
        val parent = Job()
        val scope = CoroutineScope(coroutineContext + parent)

        val job = scope.launch {
            suspendRunCatching {
                started.complete(Unit)
                delay(60_000)
            }
            continuedAfterCancellation = true
        }
        started.await()
        parent.cancel()
        job.join()

        assertTrue(job.isCancelled)
        assertFalse(continuedAfterCancellation)
    }
}
