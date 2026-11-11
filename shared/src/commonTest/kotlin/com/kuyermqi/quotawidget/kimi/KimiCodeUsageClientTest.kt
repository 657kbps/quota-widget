package com.kuyermqi.quotawidget.kimi

import com.kuyermqi.quotawidget.domain.QuotaWindowKind
import com.kuyermqi.quotawidget.domain.ServiceRegion
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KimiCodeUsageClientTest {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Test
    fun toSnapshot_mapsLegacyUsageAndLimits() {
        val payload = """
            {
              "user": {"membership": {"level": "LEVEL_INTERMEDIATE"}},
              "usage": {
                "limit": "100",
                "used": "32",
                "remaining": "68",
                "resetTime": "2026-08-21T01:07:45.776567Z"
              },
              "limits": [
                {
                  "window": {"duration": 300, "timeUnit": "TIME_UNIT_MINUTE"},
                  "detail": {
                    "limit": "100",
                    "used": "20",
                    "remaining": "80",
                    "resetTime": "2026-08-17T14:07:45.776567Z"
                  }
                }
              ],
              "totalQuota": {},
              "subType": "TYPE_PURCHASE"
            }
        """.trimIndent()

        val snapshot = KimiCodeUsageClient.toSnapshot(decode(payload), updatedAtEpochMs = NOW)

        assertEquals(2, snapshot.windows.size)
        val fiveHour = snapshot.windows[0]
        assertEquals(QuotaWindowKind.FIVE_HOUR, fiveHour.kind)
        assertEquals(20.0, fiveHour.usedPercent)
        assertEquals(7_665L, fiveHour.resetInSec)
        val weekly = snapshot.windows[1]
        assertEquals(QuotaWindowKind.WEEKLY, weekly.kind)
        assertEquals(32.0, weekly.usedPercent)
        assertEquals(306_465L, weekly.resetInSec)
        assertEquals("kimi_code", snapshot.platformId)
        assertEquals("Kimi Code", snapshot.platformName)
        assertTrue(snapshot.primaryDisplay.contains("滚动"))
        assertTrue(snapshot.primaryDisplay.contains("每周"))
    }

    @Test
    fun toSnapshot_mapsQuotaModelUsages() {
        val payload = """
            {
              "usages": {
                "limit_5h": {"used_ratio": 0.25, "reset_time": "2026-08-17T14:00:00Z"},
                "limit_7d": {"used_ratio": 0.5, "reset_time": "2026-08-24T12:00:00Z"},
                "limit_month_total": {
                  "used_ratio": 0.1,
                  "reset_time": "2026-09-17T12:00:00Z"
                },
                "limit_month_code": {
                  "used_ratio": 0.2,
                  "reset_time": "2026-09-17T12:00:00Z"
                }
              }
            }
        """.trimIndent()

        val snapshot = KimiCodeUsageClient.toSnapshot(decode(payload), updatedAtEpochMs = NOW)

        assertEquals(3, snapshot.windows.size)
        assertEquals(QuotaWindowKind.FIVE_HOUR, snapshot.windows[0].kind)
        assertEquals(25.0, snapshot.windows[0].usedPercent)
        assertEquals(7_200L, snapshot.windows[0].resetInSec)
        assertEquals(QuotaWindowKind.WEEKLY, snapshot.windows[1].kind)
        assertEquals(50.0, snapshot.windows[1].usedPercent)
        assertEquals(604_800L, snapshot.windows[1].resetInSec)
        assertEquals(QuotaWindowKind.MONTHLY, snapshot.windows[2].kind)
        // Both monthly pools are present; the tighter (more consumed) one wins.
        assertEquals(20.0, snapshot.windows[2].usedPercent)
        assertEquals(2_678_400L, snapshot.windows[2].resetInSec)
    }

    @Test
    fun toSnapshot_mapsMonthTotalOnly() {
        val payload = """
            {"usages": {"limit_month_total": {"used_ratio": 0.15}}}
        """.trimIndent()

        val snapshot = KimiCodeUsageClient.toSnapshot(decode(payload), updatedAtEpochMs = NOW)

        assertEquals(QuotaWindowKind.MONTHLY, snapshot.windows.single().kind)
        assertEquals(15.0, snapshot.windows.single().usedPercent)
        assertNull(snapshot.windows.single().resetInSec)
    }

    @Test
    fun toSnapshot_acceptsEpochResetTimes() {
        val payload = """
            {
              "usages": {
                "limit_5h": {"used_ratio": 0.25, "reset_time": 1786975200},
                "limit_7d": {"used_ratio": 0.5, "reset_time": 1786975200000}
              }
            }
        """.trimIndent()

        val snapshot = KimiCodeUsageClient.toSnapshot(decode(payload), updatedAtEpochMs = NOW)

        assertEquals(7_200L, snapshot.windows[0].resetInSec)
        assertEquals(7_200L, snapshot.windows[1].resetInSec)
    }

    @Test
    fun toSnapshot_mapsLegacyTotalQuotaAsMonthly() {
        val payload = """
            {"totalQuota": {"limit": "100", "remaining": "90"}}
        """.trimIndent()

        val snapshot = KimiCodeUsageClient.toSnapshot(decode(payload), updatedAtEpochMs = NOW)

        assertEquals(QuotaWindowKind.MONTHLY, snapshot.windows.single().kind)
        assertEquals(10.0, snapshot.windows.single().usedPercent)
    }

    @Test
    fun toSnapshot_toleratesPercentageStyleRatio() {
        val payload = """
            {"usages": {"limit_5h": {"used_ratio": 42}}}
        """.trimIndent()

        val snapshot = KimiCodeUsageClient.toSnapshot(decode(payload), updatedAtEpochMs = NOW)

        assertEquals(42.0, snapshot.windows.single().usedPercent)
    }

    @Test
    fun toSnapshot_toleratesUnquotedNumbers() {
        val payload = """
            {
              "usage": {"limit": 100, "used": 32, "resetTime": "2026-08-21T01:07:45Z"},
              "limits": []
            }
        """.trimIndent()

        val snapshot = KimiCodeUsageClient.toSnapshot(decode(payload), updatedAtEpochMs = NOW)

        assertEquals(32.0, snapshot.windows.single().usedPercent)
    }

    @Test
    fun toSnapshot_derivesUsedFromRemaining() {
        val payload = """
            {"usage": {"limit": "50", "remaining": "40"}, "limits": []}
        """.trimIndent()

        val snapshot = KimiCodeUsageClient.toSnapshot(decode(payload), updatedAtEpochMs = NOW)

        val weekly = snapshot.windows.single()
        assertEquals(QuotaWindowKind.WEEKLY, weekly.kind)
        assertEquals(20.0, weekly.usedPercent)
        assertNull(weekly.resetInSec)
    }

    @Test
    fun toSnapshot_mapsHourAndMonthLimitWindows() {
        val payload = """
            {
              "limits": [
                {
                  "window": {"duration": 5, "timeUnit": "TIME_UNIT_HOUR"},
                  "detail": {"limit": "10", "used": "1"}
                },
                {
                  "window": {"duration": 30, "timeUnit": "TIME_UNIT_DAY"},
                  "detail": {"limit": "100", "used": "90"}
                }
              ]
            }
        """.trimIndent()

        val snapshot = KimiCodeUsageClient.toSnapshot(decode(payload), updatedAtEpochMs = NOW)

        assertEquals(2, snapshot.windows.size)
        assertEquals(QuotaWindowKind.FIVE_HOUR, snapshot.windows[0].kind)
        assertEquals(QuotaWindowKind.MONTHLY, snapshot.windows[1].kind)
        assertEquals(90.0, snapshot.windows[1].usedPercent)
    }

    @Test
    fun toSnapshot_emptyPayloadYieldsNoWindows() {
        val snapshot = KimiCodeUsageClient.toSnapshot(
            decode("{}"),
            updatedAtEpochMs = NOW,
        )

        assertTrue(snapshot.windows.isEmpty())
        assertEquals("", snapshot.primaryDisplay)
    }

    @Test
    fun parseIso8601EpochMs_handlesUtcFractionAndOffsets() {
        assertEquals(0L, KimiCodeUsageClient.parseIso8601EpochMs("1970-01-01T00:00:00Z"))
        assertEquals(
            1_787_274_465_000L,
            KimiCodeUsageClient.parseIso8601EpochMs("2026-08-21T01:07:45.776567Z"),
        )
        assertEquals(
            1_787_274_465_000L,
            KimiCodeUsageClient.parseIso8601EpochMs("2026-08-21T09:07:45+08:00"),
        )
        assertEquals(
            1_787_274_465_000L,
            KimiCodeUsageClient.parseIso8601EpochMs("2026-08-20T20:07:45-05:00"),
        )
    }

    @Test
    fun parseIso8601EpochMs_rejectsMalformedInput() {
        assertNull(KimiCodeUsageClient.parseIso8601EpochMs(null))
        assertNull(KimiCodeUsageClient.parseIso8601EpochMs(""))
        assertNull(KimiCodeUsageClient.parseIso8601EpochMs("2026-08-21"))
        assertNull(KimiCodeUsageClient.parseIso8601EpochMs("not a date"))
        assertNull(KimiCodeUsageClient.parseIso8601EpochMs("2026-13-01T00:00:00Z"))
        assertNull(KimiCodeUsageClient.parseIso8601EpochMs("2026-02-30T00:00:00Z"))
        assertNull(KimiCodeUsageClient.parseIso8601EpochMs("2026-08-21T25:00:00Z"))
    }

    @Test
    fun usageUrl_switchesRegion() {
        assertEquals(
            KimiCodeUsageClient.USAGE_URL_CN,
            KimiCodeUsageClient.usageUrl(ServiceRegion.CN),
        )
        assertEquals(
            KimiCodeUsageClient.USAGE_URL_GLOBAL,
            KimiCodeUsageClient.usageUrl(ServiceRegion.GLOBAL),
        )
    }

    private fun decode(payload: String): KimiCodeUsageResponse =
        json.decodeFromString(KimiCodeUsageResponse.serializer(), payload)

    private companion object {
        /** 2026-08-17T12:00:00Z */
        const val NOW = 1_786_968_000_000L
    }
}
