package com.kuyermqi.quotawidget.util

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class OneShotGateTest {
    @Test
    fun firstCallRunsBlockAndLaterCallsAreRejected() = runTest {
        val gate = OneShotGate()
        var runCount = 0

        assertTrue(gate.runOnce { runCount++ })
        assertFalse(gate.runOnce { runCount++ })

        assertEquals(1, runCount)
    }

    @Test
    fun concurrentCallersRunBlockOnce() = runTest {
        val gate = OneShotGate()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var runCount = 0

        val owner = async {
            gate.runOnce {
                runCount++
                started.complete(Unit)
                release.await()
            }
        }
        started.await()
        val joiner = async { gate.runOnce { runCount++ } }
        runCurrent()

        assertEquals(1, runCount)
        release.complete(Unit)
        assertTrue(owner.await())
        assertFalse(joiner.await())
    }

    @Test
    fun failingBlockLeavesGateOpenForRetry() = runTest {
        val gate = OneShotGate()
        val boom = IllegalStateException("boom")
        var runCount = 0

        assertFailsWith<IllegalStateException> {
            gate.runOnce {
                runCount++
                throw boom
            }
        }
        assertTrue(gate.runOnce { runCount++ })
        assertEquals(2, runCount)
    }
}
