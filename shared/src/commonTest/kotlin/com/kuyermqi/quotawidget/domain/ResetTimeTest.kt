package com.kuyermqi.quotawidget.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ResetTimeTest {
    private val fetchedAt = 1_700_000_000_000L

    @Test
    fun resetAtEpochMs_anchorsCountdownToFetchTime() {
        val window = QuotaWindow(kind = QuotaWindowKind.WEEKLY, resetInSec = 7_200L)

        assertEquals(fetchedAt + 7_200_000L, window.resetAtEpochMs(fetchedAt))
    }

    @Test
    fun resetAtEpochMs_ignoresMissingPastAndZeroCountdowns() {
        assertNull(QuotaWindow(kind = QuotaWindowKind.WEEKLY).resetAtEpochMs(fetchedAt))
        assertNull(QuotaWindow(kind = QuotaWindowKind.WEEKLY, resetInSec = 0L).resetAtEpochMs(fetchedAt))
        assertNull(QuotaWindow(kind = QuotaWindowKind.WEEKLY, resetInSec = -5L).resetAtEpochMs(fetchedAt))
        assertNull(QuotaWindow(kind = QuotaWindowKind.WEEKLY, resetInSec = 60L).resetAtEpochMs(0L))
    }

    @Test
    fun upcomingResetAtEpochMs_skipsWindowsWithoutData() {
        val snapshot = QuotaSnapshot(
            platformId = "codex",
            platformName = "Codex",
            windows = listOf(
                QuotaWindow(kind = QuotaWindowKind.FIVE_HOUR, usedPercent = 10.0, resetInSec = 600L),
                QuotaWindow(kind = QuotaWindowKind.WEEKLY, usedPercent = 20.0),
            ),
            primaryDisplay = "",
            updatedAtEpochMs = fetchedAt,
        )

        assertEquals(fetchedAt + 600_000L, snapshot.upcomingResetAtEpochMs(QuotaWindowKind.FIVE_HOUR, fetchedAt))
        assertNull(snapshot.upcomingResetAtEpochMs(QuotaWindowKind.WEEKLY, fetchedAt))
        assertNull(snapshot.upcomingResetAtEpochMs(QuotaWindowKind.MONTHLY, fetchedAt))
    }

    @Test
    fun upcomingResetAtEpochMs_hidesResetsThatAlreadyPassed() {
        val snapshot = QuotaSnapshot(
            platformId = "codex",
            platformName = "Codex",
            windows = listOf(
                QuotaWindow(kind = QuotaWindowKind.FIVE_HOUR, usedPercent = 10.0, resetInSec = 600L),
            ),
            primaryDisplay = "",
            updatedAtEpochMs = fetchedAt,
        )

        assertNull(
            snapshot.upcomingResetAtEpochMs(
                QuotaWindowKind.FIVE_HOUR,
                fetchedAt + 600_000L,
            ),
        )
        assertEquals(
            fetchedAt + 600_000L,
            snapshot.upcomingResetAtEpochMs(QuotaWindowKind.FIVE_HOUR, fetchedAt + 599_000L),
        )
    }

    @Test
    fun countdown_usesHoursAndMinutesOrDaysAndHours() {
        assertEquals("3h12m", formatUsageResetCountdown(minutes(3 * 60 + 12)))
        assertEquals("3h", formatUsageResetCountdown(minutes(3 * 60)))
        assertEquals("45m", formatUsageResetCountdown(minutes(45)))
        assertEquals("1d3h", formatUsageResetCountdown(minutes(24 * 60 + 3 * 60)))
        assertEquals("1d", formatUsageResetCountdown(minutes(24 * 60)))
        assertEquals("7d", formatUsageResetCountdown(minutes(7 * 24 * 60)))
    }

    @Test
    fun countdown_floorsSecondsAndHandlesExpiredWindows() {
        assertEquals("3h12m", formatUsageResetCountdown(minutes(3 * 60 + 12) + 59_000L))
        assertEquals("<1m", formatUsageResetCountdown(30_000L))
        assertEquals("<1m", formatUsageResetCountdown(0L))
        assertEquals("<1m", formatUsageResetCountdown(-60_000L))
    }

    private fun minutes(count: Long): Long = count * 60_000L
}
