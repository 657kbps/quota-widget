package com.kuyermqi.quotawidget.deepseek.peakvalley

import android.content.Context
import com.kuyermqi.quotawidget.domain.HolidayCalendarCache
import com.kuyermqi.quotawidget.settings.PlatformSettingsRepository
import java.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Merges bundled calendars with the latest validated per-year cache. Invalid
 * cached payloads are ignored; years with neither source stay uncovered.
 */
class HolidayCalendarRepository(
    context: Context,
    private val settings: PlatformSettingsRepository,
    fetcher: HolidayCalendarFetcher = HttpHolidayCalendarFetcher(),
    clock: Clock = Clock.systemUTC(),
) {
    private val appContext = context.applicationContext
    private val builtIn: HolidayCalendarTable by lazy {
        BuiltInHolidayCalendars.load(appContext.assets)
    }
    private val updater = HolidayCalendarUpdater(
        store = object : HolidayCalendarStore {
            override suspend fun read(): HolidayCalendarCache = settings.getHolidayCalendarCache()

            override suspend fun write(cache: HolidayCalendarCache) {
                settings.saveHolidayCalendarCache(cache)
            }
        },
        fetcher = fetcher,
        clock = clock,
    )

    fun observeCache(): Flow<HolidayCalendarCache> = settings.observeHolidayCalendarCache()

    suspend fun cache(): HolidayCalendarCache = settings.getHolidayCalendarCache()

    suspend fun table(): HolidayCalendarTable = withContext(Dispatchers.IO) {
        tableFromCache(cache())
    }

    private fun tableFromCache(cache: HolidayCalendarCache): HolidayCalendarTable {
        val cached = HolidayCalendarTable.fromOffDays(
            buildMap {
                for (entry in cache.calendars) {
                    val parsed = HolidayCalendarParser.parse(entry.rawJson, entry.year)
                    if (parsed is HolidayCalendarParseResult.Valid) {
                        put(parsed.year, parsed.offDays)
                    }
                }
            },
        )
        return builtIn.mergedWith(cached)
    }

    suspend fun update(): HolidayCalendarUpdateResult = withContext(Dispatchers.IO) {
        updater.update()
    }
}
