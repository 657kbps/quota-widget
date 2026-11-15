package com.kuyermqi.quotawidget.deepseek.peakvalley

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeepSeekPeakValleyCalculatorTest {
    private val calendar = HolidayCalendarTable.fromOffDays(
        mapOf(
            2026 to setOf(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 2),
                LocalDate.of(2026, 1, 3),
                LocalDate.of(2026, 2, 15),
                LocalDate.of(2026, 2, 16),
                LocalDate.of(2026, 2, 17),
                LocalDate.of(2026, 2, 18),
                LocalDate.of(2026, 2, 19),
                LocalDate.of(2026, 2, 20),
                LocalDate.of(2026, 2, 21),
                LocalDate.of(2026, 2, 22),
                LocalDate.of(2026, 2, 23),
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 2),
                LocalDate.of(2026, 10, 3),
                LocalDate.of(2026, 10, 4),
                LocalDate.of(2026, 10, 5),
                LocalDate.of(2026, 10, 6),
                LocalDate.of(2026, 10, 7),
            ),
            2028 to emptySet(),
        ),
    )

    @Test
    fun workdayBoundariesUseLeftClosedRightOpenWindows() {
        val beforeMorning = snapshotAt(2026, 3, 2, 8, 59, 59)
        assertEquals(PeakValleyTier.OFF_PEAK, beforeMorning.tier)
        assertEquals(at(2026, 3, 2, 9, 0), beforeMorning.nextChangeEpochMs)
        assertEquals(PeakValleyTier.PEAK, beforeMorning.nextChangeTier)

        val morningStart = snapshotAt(2026, 3, 2, 9, 0, 0)
        assertEquals(PeakValleyTier.PEAK, morningStart.tier)
        assertEquals(at(2026, 3, 2, 12, 0), morningStart.nextChangeEpochMs)
        assertEquals(PeakValleyTier.OFF_PEAK, morningStart.nextChangeTier)

        val morningLastSecond = snapshotAt(2026, 3, 2, 11, 59, 59)
        assertEquals(PeakValleyTier.PEAK, morningLastSecond.tier)
        assertEquals(at(2026, 3, 2, 12, 0), morningLastSecond.nextChangeEpochMs)

        val lunchStart = snapshotAt(2026, 3, 2, 12, 0, 0)
        assertEquals(PeakValleyTier.OFF_PEAK, lunchStart.tier)
        assertEquals(at(2026, 3, 2, 14, 0), lunchStart.nextChangeEpochMs)

        val lunchLastSecond = snapshotAt(2026, 3, 2, 13, 59, 59)
        assertEquals(PeakValleyTier.OFF_PEAK, lunchLastSecond.tier)
        assertEquals(at(2026, 3, 2, 14, 0), lunchLastSecond.nextChangeEpochMs)

        val afternoonStart = snapshotAt(2026, 3, 2, 14, 0, 0)
        assertEquals(PeakValleyTier.PEAK, afternoonStart.tier)
        assertEquals(at(2026, 3, 2, 18, 0), afternoonStart.nextChangeEpochMs)

        val afternoonLastSecond = snapshotAt(2026, 3, 2, 17, 59, 59)
        assertEquals(PeakValleyTier.PEAK, afternoonLastSecond.tier)
        assertEquals(at(2026, 3, 2, 18, 0), afternoonLastSecond.nextChangeEpochMs)

        val evening = snapshotAt(2026, 3, 2, 18, 0, 0)
        assertEquals(PeakValleyTier.OFF_PEAK, evening.tier)
        assertEquals(at(2026, 3, 3, 9, 0), evening.nextChangeEpochMs)
    }

    @Test
    fun weekendsAreOffPeakEvenOnMakeUpDays() {
        val saturday = snapshotAt(2026, 3, 7, 10, 0, 0)
        assertEquals(PeakValleyTier.OFF_PEAK, saturday.tier)
        assertEquals(at(2026, 3, 9, 9, 0), saturday.nextChangeEpochMs)

        val makeUpSunday = snapshotAt(2026, 1, 4, 10, 0, 0)
        assertEquals(PeakValleyTier.OFF_PEAK, makeUpSunday.tier)
        assertEquals(at(2026, 1, 5, 9, 0), makeUpSunday.nextChangeEpochMs)
    }

    @Test
    fun longHolidaysAreOffPeakForTheWholeOfficialSpan() {
        val nationalDay = snapshotAt(2026, 10, 1, 10, 0, 0)
        assertEquals(PeakValleyTier.OFF_PEAK, nationalDay.tier)
        assertEquals(at(2026, 10, 8, 9, 0), nationalDay.nextChangeEpochMs)

        val beforeNationalDay = snapshotAt(2026, 9, 30, 18, 0, 0)
        assertEquals(PeakValleyTier.OFF_PEAK, beforeNationalDay.tier)
        assertEquals(at(2026, 10, 8, 9, 0), beforeNationalDay.nextChangeEpochMs)

        val springFestivalMakeUp = snapshotAt(2026, 2, 14, 10, 0, 0)
        assertEquals(PeakValleyTier.OFF_PEAK, springFestivalMakeUp.tier)
        assertEquals(at(2026, 2, 24, 9, 0), springFestivalMakeUp.nextChangeEpochMs)
    }

    @Test
    fun missingYearShowsUnconfirmedOnlyInsidePotentialPeakWindows() {
        val insidePotentialPeak = snapshotAt(2027, 1, 4, 10, 0, 0)
        assertEquals(PeakValleyTier.UNCONFIRMED, insidePotentialPeak.tier)
        assertNull(insidePotentialPeak.nextChangeEpochMs)
        assertEquals(at(2027, 1, 4, 12, 0), insidePotentialPeak.nextEvaluationEpochMs)

        val lunch = snapshotAt(2027, 1, 4, 12, 30, 0)
        assertEquals(PeakValleyTier.OFF_PEAK, lunch.tier)
        assertNull(lunch.nextChangeEpochMs)
        assertEquals(at(2027, 1, 4, 14, 0), lunch.nextEvaluationEpochMs)

        val weekend = snapshotAt(2027, 1, 2, 10, 0, 0)
        assertEquals(PeakValleyTier.OFF_PEAK, weekend.tier)
        assertNull(weekend.nextChangeEpochMs)
        assertEquals(at(2027, 1, 4, 9, 0), weekend.nextEvaluationEpochMs)
    }

    @Test
    fun searchStopsAtFirstUncertainPeakInsteadOfPromisingLaterPeaks() {
        val eveningBeforeUnknownYear = snapshotAt(2026, 12, 31, 18, 0, 0)
        assertEquals(PeakValleyTier.OFF_PEAK, eveningBeforeUnknownYear.tier)
        assertNull(eveningBeforeUnknownYear.nextChangeEpochMs)
        assertEquals(at(2027, 1, 1, 9, 0), eveningBeforeUnknownYear.nextEvaluationEpochMs)

        val knownPeakBeforeUnknownYear = snapshotAt(2026, 12, 31, 10, 0, 0)
        assertEquals(PeakValleyTier.PEAK, knownPeakBeforeUnknownYear.tier)
        assertEquals(at(2026, 12, 31, 12, 0), knownPeakBeforeUnknownYear.nextChangeEpochMs)
    }

    @Test
    fun nextYearsNoticeCanConfirmAnAdjacentHolidayWithoutConfirmingThePriorYear() {
        val newYearHoliday = HolidayCalendarTable.fromOffDays(
            mapOf(
                2027 to setOf(
                    LocalDate.of(2026, 12, 31),
                    LocalDate.of(2027, 1, 1),
                ),
            ),
        )
        val table = HolidayCalendarTable.fromPayload(newYearHoliday.toPayload())
        assertFalse(table.isCovered(2026))
        assertNull(table.isOffDay(LocalDate.of(2026, 12, 30)))
        assertEquals(true, table.isOffDay(LocalDate.of(2026, 12, 31)))

        val calculator = DeepSeekPeakValleyCalculator(table)
        val afternoonBeforeTheHoliday =
            calculator.snapshotAt(Instant.ofEpochMilli(at(2026, 12, 30, 14, 0)))
        assertEquals(PeakValleyTier.UNCONFIRMED, afternoonBeforeTheHoliday.tier)
        val eveningBeforeTheHoliday =
            calculator.snapshotAt(Instant.ofEpochMilli(at(2026, 12, 30, 18, 0)))
        assertEquals(at(2027, 1, 4, 9, 0), eveningBeforeTheHoliday.nextChangeEpochMs)
        val holiday = calculator.snapshotAt(Instant.ofEpochMilli(at(2026, 12, 31, 10, 0)))
        assertEquals(PeakValleyTier.OFF_PEAK, holiday.tier)
    }

    @Test
    fun adjacentHolidaySurvivesMergingThePreviousYearsCachedCalendar() {
        val previous = HolidayCalendarTable.fromOffDays(mapOf(2026 to emptySet()))
        val next = HolidayCalendarTable.fromOffDays(
            mapOf(2027 to setOf(LocalDate.of(2026, 12, 31), LocalDate.of(2027, 1, 1))),
        )
        val table = previous.mergedWith(next)
        assertTrue(table.isCovered(2026))
        assertEquals(true, table.isOffDay(LocalDate.of(2026, 12, 31)))
        assertEquals(false, table.isOffDay(LocalDate.of(2026, 12, 30)))
    }

    @Test
    fun leapDayIsEvaluatedWithTheShanghaiCalendar() {
        val leapDay = snapshotAt(2028, 2, 29, 10, 0, 0)
        assertEquals(PeakValleyTier.PEAK, leapDay.tier)
        assertTrue(leapDay.currentYearCovered)
    }

    @Test
    fun shanghaiZoneIsUsedRegardlessOfDeviceZone() {
        val instant = Instant.parse("2026-03-02T01:00:00Z")
        val snapshot = DeepSeekPeakValleyCalculator(calendar).snapshotAt(instant)
        assertEquals(PeakValleyTier.PEAK, snapshot.tier)
        assertEquals(at(2026, 3, 2, 12, 0), snapshot.nextChangeEpochMs)

        val before = DeepSeekPeakValleyCalculator(calendar)
            .snapshotAt(Instant.parse("2026-03-02T00:59:59Z"))
        assertEquals(PeakValleyTier.OFF_PEAK, before.tier)
    }

    @Test
    fun countdownRoundsUpToTheMinuteAndUsesDayGranularityAboveADay() {
        val twentyFiveHours = peakValleyCountdown(25L * 60L * 60L * 1000L)
        assertEquals(1, twentyFiveHours.days)
        assertEquals(1, twentyFiveHours.hours)
        assertEquals(0, twentyFiveHours.minutes)

        val underOneMinute = peakValleyCountdown(59_000L)
        assertEquals(1, underOneMinute.totalMinutes)

        val ninetyMinutes = peakValleyCountdown(90L * 60L * 1000L)
        assertEquals(1, ninetyMinutes.hours)
        assertEquals(30, ninetyMinutes.minutes)

        val exactlyOneDay = peakValleyCountdown(24L * 60L * 60L * 1000L)
        assertEquals(1, exactlyOneDay.days)
        assertEquals(0, exactlyOneDay.hours)
    }

    @Test
    fun glancePayloadRoundTripKeepsCoverageAndOffDays() {
        val restored = HolidayCalendarTable.fromPayload(calendar.toPayload())
        assertTrue(restored.isCovered(2026))
        assertEquals(true, restored.isOffDay(LocalDate.of(2026, 10, 1)))
        assertEquals(false, restored.isOffDay(LocalDate.of(2026, 10, 8)))
        assertFalse(restored.isCovered(2027))
    }

    @Test
    fun widgetLayoutKeepsStatusAtSmallSizesAndAddsCountdownWhenTall() {
        assertTrue(PeakValleyWidgetLayout.useCompactLayout(110f))
        assertTrue(PeakValleyWidgetLayout.useCompactLayout(140f))
        assertFalse(PeakValleyWidgetLayout.useCompactLayout(160f))
        assertFalse(PeakValleyWidgetLayout.showTarget(110f))
        assertTrue(PeakValleyWidgetLayout.showTarget(140f))
        assertFalse(PeakValleyWidgetLayout.showCountdown(110f))
        assertFalse(PeakValleyWidgetLayout.showCountdown(140f))
        assertTrue(PeakValleyWidgetLayout.showCountdown(200f))
    }

    private fun snapshotAt(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        second: Int,
    ): PeakValleySnapshot = DeepSeekPeakValleyCalculator(calendar).snapshotAt(
        Instant.ofEpochMilli(at(year, month, day, hour, minute, second)),
    )

    private fun at(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        second: Int = 0,
    ): Long = LocalDateTime.of(year, month, day, hour, minute, second)
        .atZone(SHANGHAI_ZONE)
        .toInstant()
        .toEpochMilli()
}
