package com.kuyermqi.quotawidget.deepseek.peakvalley

import android.content.res.AssetManager
import java.time.LocalDate

/** Bundled, offline-safe 2026/2027 calendars, verified against State Council notices. */
object BuiltInHolidayCalendars {
    val YEARS = listOf(2026, 2027)

    fun load(assets: AssetManager): HolidayCalendarTable {
        val offDays = mutableMapOf<Int, Set<LocalDate>>()
        for (year in YEARS) {
            val raw = runCatching {
                assets.open("holiday-cn/$year.json").bufferedReader(Charsets.UTF_8).use {
                    it.readText()
                }
            }.getOrNull() ?: continue
            val parsed = HolidayCalendarParser.parse(raw, year)
            if (parsed is HolidayCalendarParseResult.Valid) {
                offDays[year] = parsed.offDays
            }
        }
        return HolidayCalendarTable.fromOffDays(offDays)
    }
}
