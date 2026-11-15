package com.kuyermqi.quotawidget.deepseek.peakvalley

import java.time.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Read-only holiday lookup. A null result means the year has no trusted calendar. */
interface HolidayCalendarLookup {
    fun isCovered(year: Int): Boolean
    fun isOffDay(date: LocalDate): Boolean?
}

/**
 * Only the announcement year and its immediate New Year edge are allowed in an
 * annual file. The edge may cross into the previous December or next January.
 */
internal fun LocalDate.isAllowedAnnouncementEdge(announcementYear: Int): Boolean =
    year == announcementYear ||
        (year == announcementYear - 1 && monthValue == 12 && dayOfMonth >= 25) ||
        (year == announcementYear + 1 && monthValue == 1 && dayOfMonth <= 7)

/**
 * Validated holiday overrides. Weekends are never consulted here; the schedule
 * always treats Saturday/Sunday as off-peak, including make-up workdays.
 */
class HolidayCalendarTable private constructor(
    private val offDaysByAnnouncementYear: Map<Int, Set<LocalDate>>,
) : HolidayCalendarLookup {
    override fun isCovered(year: Int): Boolean = offDaysByAnnouncementYear.containsKey(year)

    override fun isOffDay(date: LocalDate): Boolean? {
        if (offDaysByAnnouncementYear.values.any { date in it }) return true
        return if (offDaysByAnnouncementYear.containsKey(date.year)) false else null
    }

    fun coveredYears(): Set<Int> = offDaysByAnnouncementYear.keys

    fun offDays(year: Int): Set<LocalDate> = offDaysByAnnouncementYear[year].orEmpty()

    fun mergedWith(other: HolidayCalendarTable): HolidayCalendarTable =
        HolidayCalendarTable(offDaysByAnnouncementYear + other.offDaysByAnnouncementYear)

    fun toPayload(): String = widgetJson.encodeToString(
        Payload(
            years = offDaysByAnnouncementYear.entries
                .sortedBy { it.key }
                .map { (year, days) ->
                    YearPayload(
                        year = year,
                        offDays = days.sorted().map(LocalDate::toString),
                    )
                },
        ),
    )

    companion object {
        val EMPTY = HolidayCalendarTable(emptyMap())

        fun fromOffDays(offDaysByYear: Map<Int, Set<LocalDate>>): HolidayCalendarTable =
            HolidayCalendarTable(
                offDaysByYear.mapValues { (year, days) ->
                    days.filterTo(mutableSetOf()) { it.isAllowedAnnouncementEdge(year) }
                },
            )

        fun fromPayload(raw: String?): HolidayCalendarTable {
            if (raw.isNullOrBlank()) return EMPTY
            return runCatching {
                val payload = widgetJson.decodeFromString<Payload>(raw)
                val years = payload.years.mapNotNull { year ->
                    runCatching {
                        val dates = year.offDays.map(LocalDate::parse)
                        require(dates.size == dates.toSet().size)
                        val allowed = dates
                            .filter { it.isAllowedAnnouncementEdge(year.year) }
                            .toSet()
                        if (dates.isNotEmpty() && allowed.isEmpty()) {
                            null
                        } else {
                            year.year to allowed
                        }
                    }.getOrNull()
                }
                fromOffDays(years.toMap())
            }.getOrDefault(EMPTY)
        }

        private val widgetJson = Json { ignoreUnknownKeys = true }
    }

    @Serializable
    private data class Payload(
        val years: List<YearPayload> = emptyList(),
    )

    @Serializable
    private data class YearPayload(
        val year: Int,
        val offDays: List<String> = emptyList(),
    )
}
