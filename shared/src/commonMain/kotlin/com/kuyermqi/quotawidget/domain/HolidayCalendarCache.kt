package com.kuyermqi.quotawidget.domain

import kotlinx.serialization.Serializable

/** One validated annual holiday file kept as the original JSON payload. */
@Serializable
data class CachedHolidayCalendar(
    val year: Int,
    val rawJson: String,
    val fetchedAtEpochMs: Long,
)

/**
 * Non-sensitive local holiday cache. Invalid or missing years are simply absent;
 * callers must not infer a year that has never been validated.
 */
@Serializable
data class HolidayCalendarCache(
    val calendars: List<CachedHolidayCalendar> = emptyList(),
    val lastSuccessAtEpochMs: Long = 0L,
    val lastAttemptAtEpochMs: Long = 0L,
    val lastAttemptFailedYears: List<Int> = emptyList(),
    val lastAttemptNotPublishedYears: List<Int> = emptyList(),
) {
    companion object {
        val EMPTY = HolidayCalendarCache()
    }
}
