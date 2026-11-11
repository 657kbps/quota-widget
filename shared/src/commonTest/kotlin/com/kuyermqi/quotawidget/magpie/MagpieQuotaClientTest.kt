package com.kuyermqi.quotawidget.magpie

import com.kuyermqi.quotawidget.domain.QuotaUnavailableException
import com.kuyermqi.quotawidget.domain.QuotaWindowKind
import com.kuyermqi.quotawidget.platform.PlatformIds
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MagpieQuotaClientTest {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /** Subscription windows, credit balances and per-model pools, as a live gateway sends them. */
    private val payload = """
        {
          "object": "list",
          "data": [
            {
              "provider": "antigravity",
              "name": "Antigravity",
              "kind": "subscription",
              "plan": "Google AI Pro",
              "user": "a@example.com",
              "windows": [
                {"name": "Gemini · 7 days", "used": 77.53271, "remaining": 22.46729,
                 "resetsAt": "2026-11-14T00:00:05Z"},
                {"name": "Gemini · 5 hours", "used": 36.286609999999996,
                 "remaining": 63.71339, "resetsAt": "2026-11-11T03:47:47Z"},
                {"name": "Claude & GPT · 5 hours", "used": 0, "remaining": 100,
                 "resetsAt": "2026-11-11T08:41:10Z"},
                {"name": "Gemini 4 Argon (High)", "used": 2.47792,
                 "resetsAt": "2026-11-17T10:44:30Z"}
              ],
              "readAt": "2026-11-11T11:37:48.819245059+08:00"
            },
            {
              "provider": "antigravity",
              "name": "Antigravity",
              "kind": "subscription",
              "plan": "Google AI Pro",
              "user": "b@example.com",
              "windows": [
                {"name": "Gemini · 7 days", "used": 40, "remaining": 60},
                {"name": "Gemini · 5 hours", "used": 12.5, "remaining": 87.5}
              ]
            },
            {
              "provider": "workbuddy",
              "name": "WorkBuddy",
              "kind": "subscription",
              "plan": "Pro",
              "user": "131****6579",
              "windows": [
                {"name": "Credits", "used": 100, "remaining": 0,
                 "resetsAt": "2026-12-01T18:51:04Z", "display": "2000 / 2000",
                 "amount": 2000, "limit": 2000, "unit": "credits"},
                {"name": "Extra credits", "used": 54.616316302992516,
                 "display": "3066.16 / 5614", "amount": 3066.16, "limit": 5614,
                 "unit": "credits"},
                {"name": "Total credits", "used": 66.53743101195167,
                 "resetsAt": "2026-12-01T18:51:04Z", "display": "5066.16 / 7614",
                 "amount": 5066.16, "limit": 7614, "unit": "credits"},
                {"name": "deepseek-v4.1-flash tokens (est.)", "used": 41.630044,
                 "resetsAt": "2026-11-11T23:38:06.357Z"}
              ]
            },
            {
              "provider": "opencode-go",
              "name": "OpenCode Go",
              "kind": "plan",
              "windows": [
                {"name": "5 hours", "used": 12.5},
                {"name": "7 days", "used": 40},
                {"name": "Month", "used": 5}
              ]
            }
          ]
        }
    """.trimIndent()

    /** 2026-11-11T03:00:00Z, before every reset above. */
    private val nowEpochMs = 1_794_366_000_000L

    private fun cards(): List<MagpieQuotaCard> =
        json.decodeFromString(MagpieQuotaEnvelope.serializer(), payload).data.orEmpty()

    @Test
    fun normalizeBaseUrl_trimsSlashAndWhitespace() {
        assertEquals(
            "http://macmini.example:3425",
            MagpieQuotaClient.normalizeBaseUrl("  http://macmini.example:3425/  "),
        )
    }

    @Test
    fun quotasUrl_joinsPath() {
        assertEquals(
            "http://127.0.0.1:3425/v1/magpie/quotas",
            MagpieQuotaClient.quotasUrl("http://127.0.0.1:3425/"),
        )
    }

    @Test
    fun windowKindOf_mapsSubscriptionWindows() {
        assertEquals(QuotaWindowKind.FIVE_HOUR, MagpieQuotaClient.windowKindOf("Gemini · 5 hours"))
        assertEquals(QuotaWindowKind.FIVE_HOUR, MagpieQuotaClient.windowKindOf("5 hours"))
        assertEquals(QuotaWindowKind.WEEKLY, MagpieQuotaClient.windowKindOf("Gemini · 7 days"))
        assertEquals(QuotaWindowKind.WEEKLY, MagpieQuotaClient.windowKindOf("7 days"))
        assertEquals(QuotaWindowKind.MONTHLY, MagpieQuotaClient.windowKindOf("Month"))
    }

    @Test
    fun windowKindOf_mapsCreditPoolsToMonthlyAndSkipsExtraCredits() {
        assertEquals(QuotaWindowKind.MONTHLY, MagpieQuotaClient.windowKindOf("Credits"))
        assertEquals(QuotaWindowKind.MONTHLY, MagpieQuotaClient.windowKindOf("Total credits"))
        assertNull(MagpieQuotaClient.windowKindOf("Extra credits"))
    }

    @Test
    fun windowKindOf_ignoresModelPoolsAndMissingNames() {
        assertNull(MagpieQuotaClient.windowKindOf("deepseek-v4.1-flash tokens (est.)"))
        assertNull(MagpieQuotaClient.windowKindOf("Gemini 4 Argon (High)"))
        assertNull(MagpieQuotaClient.windowKindOf(null))
    }

    @Test
    fun providersOf_groupsAccountsOfTheSameProvider() {
        val providers = MagpieQuotaClient.providersOf(cards(), nowEpochMs)

        assertEquals(listOf("antigravity", "workbuddy", "opencode-go"), providers.map { it.id })
        // Two antigravity accounts collapse into one option.
        assertEquals(2, providers[0].accountCount)
        assertEquals(listOf("Google AI Pro"), providers[0].plans)
        assertEquals("Antigravity", providers[0].displayName)
    }

    @Test
    fun providersOf_aggregatesWindowsAcrossAccounts() {
        val antigravity = MagpieQuotaClient.providersOf(cards(), nowEpochMs).first()

        assertEquals(
            listOf(QuotaWindowKind.FIVE_HOUR, QuotaWindowKind.WEEKLY),
            antigravity.windows.map { it.kind },
        )
        // a@example.com: 5h 36.29 / 7d 77.53; b@example.com: 5h 12.5 / 7d 40 -> tightest wins.
        assertEquals(36.286609999999996, antigravity.windows[0].usedPercent)
        assertEquals(77.53271, antigravity.windows[1].usedPercent)
    }

    @Test
    fun providersOf_mapsCreditPoolsToTheMonthlyWindow() {
        val workbuddy = MagpieQuotaClient.providersOf(cards(), nowEpochMs)
            .first { it.id == "workbuddy" }

        // "Total credits" holds the whole allowance, so it beats plain "Credits".
        assertEquals(listOf(QuotaWindowKind.MONTHLY), workbuddy.windows.map { it.kind })
        assertEquals(66.53743101195167, workbuddy.windows[0].usedPercent)
    }

    @Test
    fun providersOf_skipsProvidersWithoutDisplayableWindows() {
        val tokensOnly = listOf(
            MagpieQuotaCard(
                provider = "workbuddy",
                name = "WorkBuddy",
                windows = listOf(
                    MagpieQuotaWindow(name = "deepseek-v4.1-flash tokens (est.)", used = 42.0),
                    MagpieQuotaWindow(name = "Extra credits", used = 54.0),
                ),
            ),
        )

        assertEquals(emptyList(), MagpieQuotaClient.providersOf(tokensOnly, nowEpochMs))
    }

    @Test
    fun windowsOf_prefersTotalCreditsOverPartialPools() {
        val card = cards().first { it.provider == "workbuddy" }
        val monthly = MagpieQuotaClient.windowsOf(card, nowEpochMs).single()

        assertEquals(QuotaWindowKind.MONTHLY, monthly.kind)
        assertEquals(66.53743101195167, monthly.usedPercent)
    }

    @Test
    fun windowsOf_keepsResetOfTheChosenWindow() {
        val windows = MagpieQuotaClient.windowsOf(
            cards().first { it.provider == "antigravity" },
            nowEpochMs,
        )

        // 03:47:47Z is 2867s after the fixed now; the week resets 2026-11-14T00:00:05Z.
        assertEquals(2_867L, windows[0].resetInSec)
        assertEquals(248_405L, windows[1].resetInSec)
    }

    @Test
    fun windowsOf_pastResetIsZeroNotNegative() {
        val windows = MagpieQuotaClient.providersOf(
            cards(),
            nowEpochMs = 1_794_400_000_000L,
        ).first().windows

        assertEquals(0L, windows[0].resetInSec)
    }

    @Test
    fun selectProvider_defaultsToTheFirstProviderTheGatewayReturns() {
        val providers = MagpieQuotaClient.providersOf(cards(), nowEpochMs)

        assertEquals("antigravity", MagpieQuotaClient.selectProvider(providers, "")?.id)
        assertEquals("opencode-go", MagpieQuotaClient.selectProvider(providers, "opencode-go")?.id)
        // A provider the gateway no longer reports falls back to the first one.
        assertEquals("antigravity", MagpieQuotaClient.selectProvider(providers, "gone")?.id)
    }

    @Test
    fun toSnapshot_usesOnlyTheSelectedProviderWindows() {
        val providers = MagpieQuotaClient.providersOf(cards(), nowEpochMs)
        val selected = MagpieQuotaClient.selectProvider(providers, "opencode-go")!!

        val snapshot = MagpieQuotaClient.toSnapshot(
            selected.windows,
            updatedAtEpochMs = nowEpochMs,
        )

        assertEquals(PlatformIds.MAGPIE, snapshot.platformId)
        assertEquals("Magpie", snapshot.platformName)
        assertEquals("滚动 12.5 % · 每周 40 % · 每月 5 %", snapshot.primaryDisplay)
        assertEquals(nowEpochMs, snapshot.updatedAtEpochMs)
    }

    @Test
    fun toSnapshot_reportsUnavailableInsteadOfCrashing() {
        val error = assertFailsWith<QuotaUnavailableException> {
            MagpieQuotaClient.toSnapshot(emptyList(), updatedAtEpochMs = nowEpochMs)
        }
        assertTrue(error.message.orEmpty().contains("额度窗口"))
    }

    @Test
    fun encodeMagpieProviders_roundTripsPersistedProviders() {
        val providers = MagpieQuotaClient.providersOf(cards(), nowEpochMs)

        assertEquals(providers, decodeMagpieProviders(encodeMagpieProviders(providers)))
        assertEquals(emptyList(), decodeMagpieProviders("not json"))
        assertEquals(emptyList(), decodeMagpieProviders(null))
    }

    @Test
    fun envelope_toleratesUnknownFieldsAndMissingWindows() {
        val envelope = json.decodeFromString(
            MagpieQuotaEnvelope.serializer(),
            """{"object":"list","data":[{"provider":"x","windows":null,"extra":1}]}""",
        )

        assertEquals(1, envelope.data?.size)
        assertNull(envelope.data?.first()?.windows)
        assertEquals(emptyList(), MagpieQuotaClient.providersOf(envelope.data.orEmpty()))
    }

    @Test
    fun errorEnvelope_readsServerMessage() {
        val envelope = json.decodeFromString(
            MagpieErrorEnvelope.serializer(),
            """{"error":{"code":null,"type":"authentication_error",
               "message":"the API key ending in 3456 is not an enabled magpie gateway key"}}""",
        )

        assertEquals(
            "the API key ending in 3456 is not an enabled magpie gateway key",
            envelope.error?.message,
        )
    }
}
