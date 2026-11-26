package com.kuyermqi.quotawidget.commandcode

import com.kuyermqi.quotawidget.domain.QuotaWindowKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CommandCodeClientTest {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Test
    fun loginUrlCarriesLoopbackCallbackAndState() {
        val url = CommandCodeClient.createLoginUrl("state-123")

        assertTrue(url.startsWith("https://commandcode.ai/studio/auth/cli?"))
        assertTrue(url.contains("callback=http%3A%2F%2F127.0.0.1%3A53682%2Fcallback"))
        assertTrue(url.contains("state=state-123"))
        assertTrue(url.contains("mode=redirect"))
    }

    @Test
    fun loginUrlUsesTheActuallyBoundCallbackPort() {
        val url = CommandCodeClient.createLoginUrl("state-123", port = 45123)

        assertTrue(url.contains("callback=http%3A%2F%2F127.0.0.1%3A45123%2Fcallback"))
    }

    @Test
    fun callbackParsesAndDecodesCredentials() {
        val encodedKey = "key" + "%2B" + "1"
        val callback = CommandCodeClient.parseLoginCallback(
            "http://127.0.0.1:53682/callback?apiKey=$encodedKey" +
                "&state=state&userId=u1&userName=Test%20User&keyName=my%20key",
        )

        assertEquals("key+1", callback?.apiKey)
        assertEquals("state", callback?.state)
        assertEquals("u1", callback?.userId)
        assertEquals("Test User", callback?.userName)
        assertEquals("my key", callback?.keyName)
    }

    @Test
    fun callbackWithoutKeyIsIgnored() {
        assertNull(CommandCodeClient.parseLoginCallback("http://127.0.0.1:53682/callback?state=s"))
        assertNull(CommandCodeClient.parseLoginCallback("https://commandcode.ai/studio/auth/cli"))
    }

    @Test
    fun newStateIsUrlSafeAndUnique() {
        val first = CommandCodeClient.newState()
        val second = CommandCodeClient.newState()

        assertEquals(32, first.length)
        assertTrue(first.all { it.isLetterOrDigit() || it == '-' || it == '_' })
        assertTrue(first != second)
    }

    @Test
    fun parseCredits_parsesTopLevelWindowLimitsAndMonthlyWindow() {
        val now = 1_795_630_000_000L
        val response = json.parseToJsonElement(
            """
            {
              "credits": {
                "belowThreshold": false,
                "creditThreshold": 0,
                "monthlyCredits": 69.25,
                "purchasedCredits": 0,
                "freeCredits": 0
              },
              "windowLimits": {
                "limited": true,
                "exceeded": null,
                "fiveHour": { "used": 0.76, "cap": 14, "exceeded": false, "resetAt": 1795635757424 },
                "weekly":   { "used": 0.76, "cap": 35, "exceeded": false, "resetAt": 1796222557424 }
              },
              "sandboxAccess": true
            }
            """.trimIndent(),
        ).jsonObject
        val subscription = json.parseToJsonElement(
            """
            {
              "planId": "individual-goat",
              "currentPeriodEnd": 1796083200000
            }
            """.trimIndent(),
        ).jsonObject

        val parsed = CommandCodeClient.parseCredits(
            creditsResponse = response,
            subscription = "GOAT" to subscription,
            nowEpochMs = now,
        )

        assertEquals("GOAT", parsed.plan)
        assertEquals(69.25, parsed.balance)
        assertEquals(3, parsed.windows.size)

        val fiveHour = parsed.windows[0]
        assertEquals(QuotaWindowKind.FIVE_HOUR, fiveHour.kind)
        assertEquals(0.76 / 14.0 * 100.0, fiveHour.usedPercent!!, 1e-6)
        assertEquals(5_757L, fiveHour.resetInSec)

        val weekly = parsed.windows[1]
        assertEquals(QuotaWindowKind.WEEKLY, weekly.kind)
        assertEquals(0.76 / 35.0 * 100.0, weekly.usedPercent!!, 1e-6)
        assertEquals(592_557L, weekly.resetInSec)

        val monthly = parsed.windows[2]
        assertEquals(QuotaWindowKind.MONTHLY, monthly.kind)
        assertEquals((70.0 - 69.25) / 70.0 * 100.0, monthly.usedPercent!!, 1e-6)
        assertEquals(453_200L, monthly.resetInSec)
    }

    @Test
    fun parseCredits_usesRootPlanIdWhenSubscriptionMissing() {
        val response = json.parseToJsonElement(
            """
            {
              "planId": "individual-goat",
              "credits": {
                "monthlyCredits": 69.25,
                "purchasedCredits": 0,
                "freeCredits": 0
              },
              "windowLimits": {
                "fiveHour": { "used": 7, "cap": 14 }
              }
            }
            """.trimIndent(),
        ).jsonObject

        val parsed = CommandCodeClient.parseCredits(
            creditsResponse = response,
            subscription = null,
            nowEpochMs = 1_795_630_000_000L,
        )

        assertEquals("GOAT", parsed.plan)
        assertEquals(2, parsed.windows.size)
        assertEquals(QuotaWindowKind.FIVE_HOUR, parsed.windows[0].kind)
        assertEquals(50.0, parsed.windows[0].usedPercent!!, 1e-6)
        assertEquals(QuotaWindowKind.MONTHLY, parsed.windows[1].kind)
        assertEquals((70.0 - 69.25) / 70.0 * 100.0, parsed.windows[1].usedPercent!!, 1e-6)
    }
}
