package com.kuyermqi.quotawidget.deepseek.peakvalley

import com.kuyermqi.quotawidget.domain.CachedHolidayCalendar
import com.kuyermqi.quotawidget.domain.HolidayCalendarCache
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HolidayCalendarUpdaterTest {
    private val fixedClock = Clock.fixed(
        Instant.parse("2026-11-15T00:00:00Z"),
        ZoneOffset.UTC,
    )

    @Test
    fun updatesCurrentAndNextYearAndRecordsSuccess() = runTest {
        val store = FakeStore()
        val updater = HolidayCalendarUpdater(
            store = store,
            fetcher = FakeFetcher(
                mapOf(
                    2026 to HolidayFetchResult.Success(validJson(2026)),
                    2027 to HolidayFetchResult.Success(validJson(2027)),
                ),
            ),
            clock = fixedClock,
        )

        val result = updater.update()

        assertEquals(setOf(2026, 2027), result.updatedYears)
        assertTrue(result.notPublishedYears.isEmpty())
        assertTrue(result.failedYears.isEmpty())
        assertEquals(2, store.value.calendars.size)
        assertEquals(fixedClock.millis(), store.value.lastSuccessAtEpochMs)
        assertTrue(store.value.lastAttemptFailedYears.isEmpty())
    }

    @Test
    fun failedUpdateKeepsPreviousValidCache() = runTest {
        val previous = HolidayCalendarCache(
            calendars = listOf(
                CachedHolidayCalendar(
                    year = 2026,
                    rawJson = validJson(2026),
                    fetchedAtEpochMs = 123L,
                ),
            ),
            lastSuccessAtEpochMs = 123L,
        )
        val store = FakeStore(previous)
        val updater = HolidayCalendarUpdater(
            store = store,
            fetcher = FakeFetcher(
                mapOf(
                    2026 to HolidayFetchResult.Failure("timeout"),
                    2027 to HolidayFetchResult.Failure("timeout"),
                ),
            ),
            clock = fixedClock,
        )

        val result = updater.update()

        assertEquals(setOf(2026, 2027), result.failedYears)
        assertEquals(1, store.value.calendars.size)
        assertEquals(2026, store.value.calendars.single().year)
        assertEquals(123L, store.value.lastSuccessAtEpochMs)
        assertEquals(listOf(2026, 2027), store.value.lastAttemptFailedYears)
        assertEquals(fixedClock.millis(), store.value.lastAttemptAtEpochMs)
    }

    @Test
    fun unpublishedNextYearIsDistinctFromNetworkFailure() = runTest {
        val store = FakeStore()
        val updater = HolidayCalendarUpdater(
            store = store,
            fetcher = FakeFetcher(
                mapOf(
                    2026 to HolidayFetchResult.Success(validJson(2026)),
                    2027 to HolidayFetchResult.NotPublished,
                ),
            ),
            clock = fixedClock,
        )

        val result = updater.update()

        assertEquals(setOf(2026), result.updatedYears)
        assertEquals(setOf(2027), result.notPublishedYears)
        assertTrue(result.failedYears.isEmpty())
        assertFalse(store.value.lastAttemptNotPublishedYears.isEmpty())
        assertTrue(store.value.lastAttemptFailedYears.isEmpty())
    }

    @Test
    fun offlineFailureLeavesCachedYearUsableForTheSchedule() = runTest {
        val cachedJson = validJson(2026)
        val previous = HolidayCalendarCache(
            calendars = listOf(
                CachedHolidayCalendar(
                    year = 2026,
                    rawJson = cachedJson,
                    fetchedAtEpochMs = 123L,
                ),
            ),
            lastSuccessAtEpochMs = 123L,
        )
        val store = FakeStore(previous)
        val updater = HolidayCalendarUpdater(
            store = store,
            fetcher = FakeFetcher(
                mapOf(
                    2026 to HolidayFetchResult.Failure("offline"),
                    2027 to HolidayFetchResult.Failure("offline"),
                ),
            ),
            clock = fixedClock,
        )

        updater.update()

        val parsed = HolidayCalendarParser.parse(cachedJson, 2026)
        val valid = parsed as HolidayCalendarParseResult.Valid
        val table = HolidayCalendarTable.fromOffDays(mapOf(2026 to valid.offDays))
        val snapshot = DeepSeekPeakValleyCalculator(table).snapshotAt(
            Instant.parse("2026-03-02T02:00:00Z"),
        )
        assertEquals(PeakValleyTier.PEAK, snapshot.tier)
        assertTrue(table.isCovered(2026))
    }

    @Test
    fun invalidDownloadedPayloadDoesNotReplaceCache() = runTest {
        val previous = HolidayCalendarCache(
            calendars = listOf(
                CachedHolidayCalendar(
                    year = 2026,
                    rawJson = validJson(2026),
                    fetchedAtEpochMs = 123L,
                ),
            ),
            lastSuccessAtEpochMs = 123L,
        )
        val store = FakeStore(previous)
        val updater = HolidayCalendarUpdater(
            store = store,
            fetcher = FakeFetcher(
                mapOf(
                    2026 to HolidayFetchResult.Success(validJson(2027)),
                    2027 to HolidayFetchResult.NotPublished,
                ),
            ),
            clock = fixedClock,
        )

        val result = updater.update()

        assertEquals(setOf(2026), result.failedYears)
        assertEquals(1, store.value.calendars.size)
        assertEquals(validJson(2026), store.value.calendars.single().rawJson)
        assertEquals(123L, store.value.lastSuccessAtEpochMs)
    }

    private class FakeStore(
        initial: HolidayCalendarCache = HolidayCalendarCache.EMPTY,
    ) : HolidayCalendarStore {
        var value: HolidayCalendarCache = initial

        override suspend fun read(): HolidayCalendarCache = value

        override suspend fun write(cache: HolidayCalendarCache) {
            value = cache
        }
    }

    private class FakeFetcher(
        private val responses: Map<Int, HolidayFetchResult>,
    ) : HolidayCalendarFetcher {
        override suspend fun fetch(year: Int): HolidayFetchResult =
            responses[year] ?: HolidayFetchResult.Failure("no fake response")
    }

    private fun validJson(year: Int): String = """
        {
          "year": $year,
          "papers": ["https://www.gov.cn/zhengce/zhengceku/202511/content_7047091.htm"],
          "days": [
            { "name": "元旦", "date": "$year-01-01", "isOffDay": true },
            { "name": "春节", "date": "$year-02-15", "isOffDay": true },
            { "name": "清明节", "date": "$year-04-04", "isOffDay": true },
            { "name": "劳动节", "date": "$year-05-01", "isOffDay": true },
            { "name": "端午节", "date": "$year-06-19", "isOffDay": true },
            { "name": "中秋节", "date": "$year-09-25", "isOffDay": true },
            { "name": "国庆节", "date": "$year-10-01", "isOffDay": true }
          ]
        }
    """.trimIndent()
}
