package com.kuyermqi.quotawidget.opencode

import com.kuyermqi.quotawidget.domain.QuotaWindowKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OpenCodeGoApiUsageParserTest {
    @Test
    fun parseApiUsage_readsPercentAndIsoResetsAt() {
        val now = 1_794_345_000_000L
        val windows = OpenCodeGoApiUsageParser.parse(
            """
            {
              "usage": {
                "rolling": {
                  "status": "ok",
                  "percent": 12.5,
                  "resetsAt": "2026-11-10T23:10:00Z"
                },
                "weekly": {
                  "status": "ok",
                  "percent": "30",
                  "resetsAt": "2026-11-17T00:00:00Z"
                },
                "monthly": {
                  "status": "ok",
                  "percent": 65.0,
                  "resetsAt": "2026-12-10T00:00:00Z"
                }
              }
            }
            """.trimIndent(),
            nowEpochMs = now,
        )

        assertEquals(3, windows.size)
        assertEquals(QuotaWindowKind.FIVE_HOUR, windows[0].kind)
        assertEquals(12.5, windows[0].usedPercent)
        assertEquals(7_200L, windows[0].resetInSec)
        assertEquals(QuotaWindowKind.WEEKLY, windows[1].kind)
        assertEquals(30.0, windows[1].usedPercent)
        assertEquals(528_600L, windows[1].resetInSec)
        assertEquals(QuotaWindowKind.MONTHLY, windows[2].kind)
        assertEquals(65.0, windows[2].usedPercent)
    }

    @Test
    fun parseApiUsage_readsNumericResetInSecAndCapsPercent() {
        val windows = OpenCodeGoApiUsageParser.parse(
            """
            {
              "usage": {
                "rolling": {
                  "percent": 130,
                  "resetInSec": 900
                }
              }
            }
            """.trimIndent(),
            nowEpochMs = 0L,
        )

        assertEquals(1, windows.size)
        assertEquals(100.0, windows[0].usedPercent)
        assertEquals(900L, windows[0].resetInSec)
    }

    @Test
    fun parseApiUsage_withoutUsageReturnsNoWindows() {
        assertTrue(OpenCodeGoApiUsageParser.parse("""{"usage":null}""", 0L).isEmpty())
    }
}
