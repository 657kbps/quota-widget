package com.kuyermqi.quotawidget.deepseek.peakvalley

import com.kuyermqi.quotawidget.domain.CachedHolidayCalendar
import com.kuyermqi.quotawidget.domain.HolidayCalendarCache
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface HolidayFetchResult {
    data class Success(val rawJson: String) : HolidayFetchResult
    data object NotPublished : HolidayFetchResult
    data class Failure(val reason: String) : HolidayFetchResult
}

interface HolidayCalendarFetcher {
    suspend fun fetch(year: Int): HolidayFetchResult
}

interface HolidayCalendarStore {
    suspend fun read(): HolidayCalendarCache
    suspend fun write(cache: HolidayCalendarCache)
}

data class HolidayCalendarUpdateResult(
    val updatedYears: Set<Int>,
    val notPublishedYears: Set<Int>,
    val failedYears: Set<Int>,
    val lastSuccessAtEpochMs: Long,
) {
    val hasFailure: Boolean get() = failedYears.isNotEmpty()
}

private val holidayCalendarUpdateMutex = Mutex()

/**
 * Updates only the current and next Shanghai calendar years. A failed or
 * unpublished year never removes an earlier validated cache entry.
 */
class HolidayCalendarUpdater(
    private val store: HolidayCalendarStore,
    private val fetcher: HolidayCalendarFetcher,
    private val clock: Clock = Clock.systemUTC(),
    private val zone: ZoneId = SHANGHAI_ZONE,
) {
    suspend fun update(): HolidayCalendarUpdateResult = holidayCalendarUpdateMutex.withLock {
        val previous = store.read()
        val now = clock.millis()
        val currentYear = LocalDate.now(clock.withZone(zone)).year
        val targetYears = listOf(currentYear, currentYear + 1)
        val calendars = previous.calendars.associateBy { it.year }.toMutableMap()
        val updated = mutableSetOf<Int>()
        val notPublished = mutableSetOf<Int>()
        val failed = mutableSetOf<Int>()

        for (year in targetYears) {
            val fetched = try {
                fetcher.fetch(year)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                HolidayFetchResult.Failure(error.message.orEmpty())
            }
            when (fetched) {
                is HolidayFetchResult.Success -> {
                    when (val parsed = HolidayCalendarParser.parse(fetched.rawJson, year)) {
                        is HolidayCalendarParseResult.Valid -> {
                            calendars[year] = CachedHolidayCalendar(
                                year = parsed.year,
                                rawJson = fetched.rawJson,
                                fetchedAtEpochMs = now,
                            )
                            updated += year
                        }
                        is HolidayCalendarParseResult.Invalid -> failed += year
                    }
                }
                HolidayFetchResult.NotPublished -> notPublished += year
                is HolidayFetchResult.Failure -> failed += year
            }
        }

        val next = HolidayCalendarCache(
            calendars = calendars.values.sortedBy { it.year },
            lastSuccessAtEpochMs = if (updated.isEmpty()) {
                previous.lastSuccessAtEpochMs
            } else {
                now
            },
            lastAttemptAtEpochMs = now,
            lastAttemptFailedYears = failed.sorted(),
            lastAttemptNotPublishedYears = notPublished.sorted(),
        )
        store.write(next)
        HolidayCalendarUpdateResult(
            updatedYears = updated,
            notPublishedYears = notPublished,
            failedYears = failed,
            lastSuccessAtEpochMs = next.lastSuccessAtEpochMs,
        )
    }
}
