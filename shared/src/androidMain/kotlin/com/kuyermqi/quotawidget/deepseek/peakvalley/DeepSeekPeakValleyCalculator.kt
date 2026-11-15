package com.kuyermqi.quotawidget.deepseek.peakvalley

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

val SHANGHAI_ZONE: ZoneId = ZoneId.of("Asia/Shanghai")

enum class PeakValleyTier {
    PEAK,
    OFF_PEAK,
    UNCONFIRMED,
}

/**
 * Current DeepSeek price tier and the next instant worth recalculating.
 *
 * [nextChangeEpochMs] is non-null only when the current calendar makes the next
 * transition certain. [nextEvaluationEpochMs] may be earlier or later than the
 * visible segment end and is always safe to schedule a local refresh for.
 */
data class PeakValleySnapshot(
    val tier: PeakValleyTier,
    val nextChangeEpochMs: Long?,
    val nextChangeTier: PeakValleyTier?,
    val currentSegmentEndEpochMs: Long?,
    val nextEvaluationEpochMs: Long,
    val referenceEpochMs: Long,
    val currentYear: Int,
    val currentYearCovered: Boolean,
) {
    val countdownToNextChangeMs: Long?
        get() = nextChangeEpochMs?.let { (it - referenceEpochMs).coerceAtLeast(0L) }

    val countdownToEvaluationMs: Long
        get() = (nextEvaluationEpochMs - referenceEpochMs).coerceAtLeast(0L)
}

/**
 * DeepSeek's Beijing-time price schedule:
 * weekdays 09:00–12:00 and 14:00–18:00 are peak, every other instant is
 * off-peak. Weekends stay off-peak even on make-up workdays; a validated
 * `isOffDay=true` date makes the whole day off-peak.
 */
class DeepSeekPeakValleyCalculator(
    private val calendar: HolidayCalendarLookup,
    private val clock: Clock = Clock.systemUTC(),
    private val zone: ZoneId = SHANGHAI_ZONE,
) {
    fun snapshot(): PeakValleySnapshot = snapshotAt(clock.instant())

    fun snapshotAt(instant: Instant): PeakValleySnapshot {
        val now = instant.atZone(zone)
        val date = now.toLocalDate()
        val peakEnd = peakEndAt(now, date)
        if (peakEnd != null) {
            val end = endOf(date, peakEnd)
            val yearCovered = calendar.isCovered(date.year)
            return if (yearCovered) {
                PeakValleySnapshot(
                    tier = PeakValleyTier.PEAK,
                    nextChangeEpochMs = end,
                    nextChangeTier = PeakValleyTier.OFF_PEAK,
                    currentSegmentEndEpochMs = end,
                    nextEvaluationEpochMs = end,
                    referenceEpochMs = instant.toEpochMilli(),
                    currentYear = date.year,
                    currentYearCovered = true,
                )
            } else {
                PeakValleySnapshot(
                    tier = PeakValleyTier.UNCONFIRMED,
                    nextChangeEpochMs = null,
                    nextChangeTier = null,
                    currentSegmentEndEpochMs = null,
                    nextEvaluationEpochMs = end,
                    referenceEpochMs = instant.toEpochMilli(),
                    currentYear = date.year,
                    currentYearCovered = false,
                )
            }
        }

        val yearCovered = calendar.isCovered(date.year)
        val nextPeak = findNextPotentialPeak(instant, date)
        return PeakValleySnapshot(
            tier = PeakValleyTier.OFF_PEAK,
            nextChangeEpochMs = nextPeak?.takeIf { it.known }?.startEpochMs,
            nextChangeTier = nextPeak?.takeIf { it.known }?.let { PeakValleyTier.PEAK },
            currentSegmentEndEpochMs = nextPeak?.takeIf { it.known }?.startEpochMs,
            nextEvaluationEpochMs = nextPeak?.startEpochMs
                ?: instant.plusSeconds(24L * 60L * 60L).toEpochMilli(),
            referenceEpochMs = instant.toEpochMilli(),
            currentYear = date.year,
            currentYearCovered = yearCovered,
        )
    }

    private fun peakEndAt(
        now: ZonedDateTime,
        date: LocalDate,
    ): LocalTime? {
        if (date.isWeekend()) return null
        if (calendar.isOffDay(date) == true) return null
        val time = now.toLocalTime()
        return PEAK_WINDOWS.firstOrNull { (start, end) ->
            !time.isBefore(start) && time.isBefore(end)
        }?.second
    }

    private fun findNextPotentialPeak(
        instant: Instant,
        startDate: LocalDate,
    ): PotentialPeak? {
        var date = startDate
        repeat(MAX_SEARCH_DAYS) {
            if (!date.isWeekend() && calendar.isOffDay(date) != true) {
                val covered = calendar.isCovered(date.year)
                for ((start, end) in PEAK_WINDOWS) {
                    val startInstant = startOf(date, start)
                    val endInstant = endOf(date, end)
                    if (endInstant <= instant.toEpochMilli()) continue
                    if (startInstant.isAfter(instant)) {
                        return PotentialPeak(
                            startEpochMs = startInstant.toEpochMilli(),
                            known = covered,
                        )
                    }
                }
            }
            date = date.plusDays(1)
        }
        return null
    }

    private fun startOf(date: LocalDate, time: LocalTime): Instant =
        date.atTime(time).atZone(zone).toInstant()

    private fun endOf(date: LocalDate, time: LocalTime): Long =
        date.atTime(time).atZone(zone).toInstant().toEpochMilli()

    private fun LocalDate.isWeekend(): Boolean =
        dayOfWeek == java.time.DayOfWeek.SATURDAY || dayOfWeek == java.time.DayOfWeek.SUNDAY

    private data class PotentialPeak(
        val startEpochMs: Long,
        val known: Boolean,
    )

    private companion object {
        const val MAX_SEARCH_DAYS = 400
        val PEAK_WINDOWS = listOf(
            LocalTime.of(9, 0) to LocalTime.of(12, 0),
            LocalTime.of(14, 0) to LocalTime.of(18, 0),
        )
    }
}

data class PeakValleyCountdown(
    val totalMinutes: Long,
    val days: Long,
    val hours: Long,
    val minutes: Long,
)

/** Rounds a positive remainder up to the next minute so a boundary never shows 0. */
fun peakValleyCountdown(remainingMs: Long): PeakValleyCountdown {
    if (remainingMs <= 0L) return PeakValleyCountdown(0, 0, 0, 0)
    val totalMinutes = (remainingMs + 59_999L) / 60_000L
    return PeakValleyCountdown(
        totalMinutes = totalMinutes,
        days = totalMinutes / (24L * 60L),
        hours = (totalMinutes % (24L * 60L)) / 60L,
        minutes = totalMinutes % 60L,
    )
}

object PeakValleyWidgetLayout {
    const val COMPACT_MAX_HEIGHT_DP = 160f
    const val COUNTDOWN_MIN_HEIGHT_DP = 170f
    const val TARGET_MIN_WIDTH_DP = 120f

    fun useCompactLayout(heightDp: Float): Boolean = heightDp < COMPACT_MAX_HEIGHT_DP

    fun showTarget(widthDp: Float): Boolean = widthDp >= TARGET_MIN_WIDTH_DP

    fun showCountdown(heightDp: Float): Boolean = heightDp >= COUNTDOWN_MIN_HEIGHT_DP
}
