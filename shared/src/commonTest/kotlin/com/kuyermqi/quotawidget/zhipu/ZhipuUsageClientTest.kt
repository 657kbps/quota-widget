package com.kuyermqi.quotawidget.zhipu

import com.kuyermqi.quotawidget.domain.QuotaWindowKind
import com.kuyermqi.quotawidget.domain.ServiceRegion
import com.kuyermqi.quotawidget.domain.SessionExpiredException
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ZhipuUsageClientTest {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Test
    fun toWindows_mapsFiveHourAndWeeklyPercentages() {
        val limits = listOf(
            ZhipuLimitDto(
                type = "CREDIT_LIMIT",
                unit = 3,
                percentage = 42.0,
                nextResetTime = NOW + 3_600_000L,
            ),
            ZhipuLimitDto(
                type = "CREDIT_LIMIT",
                unit = 6,
                percentage = 13.0,
                nextResetTime = NOW + 7L * 24L * 3_600_000L,
            ),
            ZhipuLimitDto(type = "TIME_LIMIT", percentage = 5.0),
        )

        val windows = ZhipuUsageClient.toWindows(limits, NOW)

        assertEquals(2, windows.size)
        assertEquals(QuotaWindowKind.FIVE_HOUR, windows[0].kind)
        assertEquals(42.0, windows[0].usedPercent)
        assertEquals(3_600L, windows[0].resetInSec)
        assertEquals(QuotaWindowKind.WEEKLY, windows[1].kind)
        assertEquals(13.0, windows[1].usedPercent)
        assertEquals(604_800L, windows[1].resetInSec)
    }

    @Test
    fun toWindows_supportsTokenLimitTypeAndDerivesPercent() {
        val limits = listOf(
            ZhipuLimitDto(
                type = "TOKENS_LIMIT",
                unit = 3,
                currentValue = 2_500.0,
                usage = 10_000.0,
            ),
        )

        val window = ZhipuUsageClient.toWindows(limits, NOW).single()

        assertEquals(QuotaWindowKind.FIVE_HOUR, window.kind)
        assertEquals(25.0, window.usedPercent)
    }

    @Test
    fun toWindows_treatsLegacyUnitlessSingleLimitAsFiveHour() {
        val limits = listOf(
            ZhipuLimitDto(type = "TOKENS_LIMIT", percentage = 10.0),
        )

        val window = ZhipuUsageClient.toWindows(limits, NOW).single()

        assertEquals(QuotaWindowKind.FIVE_HOUR, window.kind)
    }

    @Test
    fun toWindows_ignoresUnknownUnitAndMissingPercent() {
        val limits = listOf(
            ZhipuLimitDto(type = "CREDIT_LIMIT", unit = 99, percentage = 10.0),
            ZhipuLimitDto(type = "CREDIT_LIMIT", unit = 3),
        )

        assertTrue(ZhipuUsageClient.toWindows(limits, NOW).isEmpty())
    }

    @Test
    fun resetInSeconds_clampsPastAndIgnoresMissing() {
        assertNull(ZhipuUsageClient.resetInSeconds(null, NOW))
        assertNull(ZhipuUsageClient.resetInSeconds(0L, NOW))
        assertEquals(0L, ZhipuUsageClient.resetInSeconds(NOW - 10_000L, NOW))
        assertEquals(120L, ZhipuUsageClient.resetInSeconds(NOW + 120_000L, NOW))
    }

    @Test
    fun failureOrNull_mapsBusinessAuthErrorsToSessionExpired() {
        assertNull(
            ZhipuUsageEnvelope(code = 200, success = true).failureOrNull(),
        )
        assertIs<SessionExpiredException>(
            ZhipuUsageEnvelope(
                code = 401,
                msg = "令牌已过期或验证不正确",
                success = false,
            ).failureOrNull(),
        )
        assertIs<SessionExpiredException>(
            ZhipuUsageEnvelope(code = 1_000, success = false).failureOrNull(),
        )
    }

    @Test
    fun failureOrNull_keepsServerMessageForOtherErrors() {
        val error = ZhipuUsageEnvelope(
            code = 500,
            msg = "服务开小差",
            success = false,
        ).failureOrNull()

        assertEquals("服务开小差", error?.message)
    }

    @Test
    fun decode_envelopeWithLimits() {
        val payload = """
            {
              "code": 200,
              "msg": "success",
              "success": true,
              "data": {
                "planName": "GLM Coding Plan",
                "limits": [
                  {
                    "type": "CREDIT_LIMIT",
                    "unit": 3,
                    "number": 5,
                    "percentage": 42,
                    "currentValue": 5040,
                    "usage": 12000,
                    "remaining": 6960,
                    "nextResetTime": 1758000000000
                  }
                ]
              }
            }
        """.trimIndent()

        val envelope = json.decodeFromString(ZhipuUsageEnvelope.serializer(), payload)
        val windows = ZhipuUsageClient.toWindows(
            envelope.data?.limits.orEmpty(),
            nowEpochMs = 1_757_996_400_000L,
        )

        assertEquals(42.0, windows.single().usedPercent)
        assertEquals(3_600L, windows.single().resetInSec)
    }

    @Test
    fun usageUrl_switchesRegion() {
        assertEquals(
            ZhipuUsageClient.USAGE_URL_CN,
            ZhipuUsageClient.usageUrl(ServiceRegion.CN),
        )
        assertEquals(
            ZhipuUsageClient.USAGE_URL_GLOBAL,
            ZhipuUsageClient.usageUrl(ServiceRegion.GLOBAL),
        )
    }

    private companion object {
        const val NOW = 1_757_996_400_000L
    }
}
