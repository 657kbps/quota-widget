package com.kuyermqi.quotawidget.refresh

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class SingleFlightTest {
    @Test
    fun concurrentCallers_shareOwnerResult() = runTest {
        val singleFlight = SingleFlight<Int>()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var runCount = 0

        val owner = async {
            singleFlight.run {
                runCount++
                started.complete(Unit)
                release.await()
                7
            }
        }
        started.await()
        val joiner = async {
            singleFlight.run {
                runCount++
                9
            }
        }
        runCurrent()

        assertEquals(1, runCount)
        release.complete(Unit)
        assertEquals(7, owner.await())
        assertEquals(7, joiner.await())
        assertEquals(1, runCount)
    }

    @Test
    fun ownerCancellation_activeJoinerTakesOver() = runTest {
        val singleFlight = SingleFlight<Int>()
        val started = CompletableDeferred<Unit>()
        var runCount = 0

        val owner = async {
            singleFlight.run {
                runCount++
                started.complete(Unit)
                awaitCancellation()
            }
        }
        started.await()
        val joiner = async {
            singleFlight.run {
                runCount++
                11
            }
        }
        runCurrent()

        owner.cancelAndJoin()

        assertEquals(11, joiner.await())
        assertEquals(2, runCount)
    }
}
