package com.kuyermqi.quotawidget.deepseek.peakvalley

import java.net.URI
import java.time.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

sealed interface HolidayCalendarParseResult {
    data class Valid(
        val year: Int,
        val offDays: Set<LocalDate>,
        val sourcePapers: List<String>,
    ) : HolidayCalendarParseResult

    data class Invalid(val reason: String) : HolidayCalendarParseResult
}

/**
 * Validates the annual holiday-cn JSON shape and its official State Council
 * source references. Days outside [expectedYear] are accepted by the source
 * format but discarded so they cannot make a neighbouring year look covered.
 */
object HolidayCalendarParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val officialNoticeHosts = setOf("www.gov.cn", "gov.cn")
    private val requiredHolidays = listOf(
        "元旦",
        "春节",
        "清明节",
        "劳动节",
        "端午节",
        "中秋节",
        "国庆节",
    )

    fun parse(rawJson: String, expectedYear: Int): HolidayCalendarParseResult {
        if (rawJson.isBlank()) return HolidayCalendarParseResult.Invalid("empty body")
        val file = runCatching {
            json.decodeFromString<HolidayYearFileDto>(rawJson)
        }.getOrElse { error ->
            return HolidayCalendarParseResult.Invalid("invalid json: ${error.message.orEmpty()}")
        }
        if (file.year != expectedYear) {
            return HolidayCalendarParseResult.Invalid("wrong year ${file.year}, expected $expectedYear")
        }
        if (file.papers.isEmpty() || file.papers.any { !isOfficialNotice(it) }) {
            return HolidayCalendarParseResult.Invalid("missing or non-official papers")
        }
        if (file.days.isEmpty()) return HolidayCalendarParseResult.Invalid("empty days")

        val seenDates = mutableSetOf<LocalDate>()
        val inYearOffDays = mutableSetOf<LocalDate>()
        val inYearOffDayNames = mutableSetOf<String>()
        val validatedOffDays = mutableSetOf<LocalDate>()
        for (day in file.days) {
            val date = runCatching { LocalDate.parse(day.date) }.getOrNull()
                ?: return HolidayCalendarParseResult.Invalid("invalid date ${day.date}")
            if (!seenDates.add(date)) {
                return HolidayCalendarParseResult.Invalid("duplicate date ${day.date}")
            }
            val offDay = day.isOffDay
                ?: return HolidayCalendarParseResult.Invalid("missing isOffDay for ${day.date}")
            if (offDay) {
                if (date.year == expectedYear) {
                    inYearOffDays += date
                    day.name.trim().takeIf { it.isNotEmpty() }?.let(inYearOffDayNames::add)
                }
                if (date.isAllowedAnnouncementEdge(expectedYear)) {
                    validatedOffDays += date
                }
            }
        }
        if (inYearOffDays.isEmpty()) {
            return HolidayCalendarParseResult.Invalid("no in-year off days")
        }
        val missingHolidays = requiredHolidays.filterNot { required ->
            inYearOffDayNames.any { name -> name.contains(required) }
        }
        if (missingHolidays.isNotEmpty()) {
            return HolidayCalendarParseResult.Invalid(
                "missing national holidays: ${missingHolidays.joinToString()}",
            )
        }
        return HolidayCalendarParseResult.Valid(
            year = expectedYear,
            offDays = validatedOffDays,
            sourcePapers = file.papers,
        )
    }

    private fun isOfficialNotice(value: String): Boolean {
        val uri = runCatching { URI(value) }.getOrNull() ?: return false
        return uri.scheme == "https" &&
            uri.host in officialNoticeHosts &&
            uri.path.orEmpty().startsWith("/zhengce/")
    }

    @Serializable
    private data class HolidayYearFileDto(
        val year: Int,
        val papers: List<String> = emptyList(),
        val days: List<HolidayDayDto> = emptyList(),
    )

    @Serializable
    private data class HolidayDayDto(
        val name: String = "",
        val date: String,
        val isOffDay: Boolean? = null,
    )
}
