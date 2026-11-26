package com.kuyermqi.quotawidget.session

import kotlin.test.Test
import kotlin.test.assertEquals

class DevicePollingIntervalTest {
    @Test
    fun startsAtInitialInterval() {
        val interval = DevicePollingInterval(5_000L)

        assertEquals(5_000L, interval.currentMs)
        assertEquals(5_000L, interval.slowDownIncrementMs)
    }

    @Test
    fun slowDownAddsFiveSeconds() {
        val interval = DevicePollingInterval(5_000L)

        interval.slowDown()

        assertEquals(10_000L, interval.currentMs)
    }

    @Test
    fun slowDownAccumulatesAcrossRepeats() {
        val interval = DevicePollingInterval(2_000L)

        interval.slowDown()
        interval.slowDown()
        interval.slowDown()

        assertEquals(17_000L, interval.currentMs)
    }

    @Test
    fun slowDownUsesConfiguredIncrement() {
        val interval = DevicePollingInterval(1_000L, slowDownIncrementMs = 2_500L)

        interval.slowDown()
        interval.slowDown()

        assertEquals(6_000L, interval.currentMs)
    }
}
