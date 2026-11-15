package com.kuyermqi.quotawidget.deepseek.peakvalley

import java.io.File
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class HolidayCalendarParserTest {
    @Test
    fun retainsOfficialCrossYearNewYearHoliday() {
        val result = HolidayCalendarParser.parse(
            validJson(
                year = 2026,
                extraDays = """
                    { "name": "元旦", "date": "2025-12-31", "isOffDay": true },
                    { "name": "春节", "date": "2026-02-14", "isOffDay": false }
                """,
            ),
            expectedYear = 2026,
        )
        val valid = assertIs<HolidayCalendarParseResult.Valid>(result)
        assertEquals(2026, valid.year)
        assertTrue(LocalDate.of(2026, 1, 1) in valid.offDays)
        assertTrue(LocalDate.of(2025, 12, 31) in valid.offDays)
        assertFalse(LocalDate.of(2026, 2, 14) in valid.offDays)
        assertEquals(8, valid.offDays.size)
    }

    @Test
    fun bundledAssetsParseAndCoverEveryNationalHoliday() {
        val root = repoRoot()
        for (year in listOf(2026, 2027)) {
            val raw = File(
                root,
                "androidApp/src/main/assets/holiday-cn/$year.json",
            ).readText(Charsets.UTF_8)
            val valid = assertIs<HolidayCalendarParseResult.Valid>(
                HolidayCalendarParser.parse(raw, year),
            )
            assertEquals(year, valid.year)
            assertTrue(valid.offDays.isNotEmpty())
        }
    }

    @Test
    fun rejectsWrongYear() {
        val result = HolidayCalendarParser.parse(validJson(2027), 2026)
        assertIs<HolidayCalendarParseResult.Invalid>(result)
    }

    @Test
    fun rejectsNonOfficialPaper() {
        val result = HolidayCalendarParser.parse(
            validJson(2026).replace("www.gov.cn", "example.com"),
            2026,
        )
        assertIs<HolidayCalendarParseResult.Invalid>(result)
    }

    @Test
    fun rejectsEmptyFile() {
        assertIs<HolidayCalendarParseResult.Invalid>(
            HolidayCalendarParser.parse("   ", 2026),
        )
    }

    @Test
    fun rejectsDuplicateDates() {
        val result = HolidayCalendarParser.parse(
            validJson(
                2026,
                extraDays = """{ "name": "元旦", "date": "2026-01-01", "isOffDay": true }""",
            ),
            2026,
        )
        assertIs<HolidayCalendarParseResult.Invalid>(result)
    }

    @Test
    fun rejectsMissingNationalHoliday() {
        val result = HolidayCalendarParser.parse(
            validJson(2026).replace(
                """{ "name": "端午节", "date": "2026-06-19", "isOffDay": true },""",
                "",
            ),
            2026,
        )
        assertIs<HolidayCalendarParseResult.Invalid>(result)
    }

    @Test
    fun rejectsNonBooleanOffDay() {
        val result = HolidayCalendarParser.parse(
            validJson(2026).replace(
                """{ "name": "元旦", "date": "2026-01-01", "isOffDay": true }""",
                """{ "name": "元旦", "date": "2026-01-01", "isOffDay": "yes" }""",
            ),
            2026,
        )
        assertIs<HolidayCalendarParseResult.Invalid>(result)
    }

    @Test
    fun rejectsMissingOffDayFlag() {
        val result = HolidayCalendarParser.parse(
            validJson(2026).replace(
                """{ "name": "元旦", "date": "2026-01-01", "isOffDay": true }""",
                """{ "name": "元旦", "date": "2026-01-01" }""",
            ),
            2026,
        )
        assertIs<HolidayCalendarParseResult.Invalid>(result)
    }

    private fun repoRoot(): File {
        var current: File? = File(System.getProperty("user.dir"))
        while (current != null) {
            if (File(current, "androidApp/src/main/assets/holiday-cn/2026.json").isFile) {
                return current
            }
            current = current.parentFile
        }
        error("repository root not found from ${System.getProperty("user.dir")}")
    }

    private fun validJson(year: Int, extraDays: String = ""): String {
        val suffix = if (extraDays.isBlank()) "" else ",$extraDays"
        return """
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
                $suffix
              ]
            }
        """.trimIndent()
    }
}
