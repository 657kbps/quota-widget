package com.kuyermqi.quotawidget.opencode

import com.kuyermqi.quotawidget.domain.QuotaWindowKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OpenCodeGoClientTest {
    @Test
    fun extractWorkspaceId_fromConsoleUrl() {
        assertEquals(
            "wrk_01ABC",
            OpenCodeGoClient.extractWorkspaceId("https://opencode.ai/console/wrk_01ABC"),
        )
        assertEquals(
            "wrk_01ABC",
            OpenCodeGoClient.extractWorkspaceId("https://opencode.ai/console/wrk_01ABC/"),
        )
        assertEquals(
            "wrk_01ABC",
            OpenCodeGoClient.extractWorkspaceId("https://opencode.ai/console/wrk_01ABC/go"),
        )
        assertEquals(
            "wrk_01ABC",
            OpenCodeGoClient.extractWorkspaceId("https://opencode.ai/console/wrk_01ABC/go?x=1"),
        )
        assertEquals(
            "org_01ABC",
            OpenCodeGoClient.extractWorkspaceId("https://opencode.ai/console/org_01ABC/go"),
        )
        assertTrue(OpenCodeGoClient.isLoginSuccessUrl("https://opencode.ai/console/wrk_xyz"))
        assertTrue(OpenCodeGoClient.isLoginSuccessUrl("https://opencode.ai/console/wrk_xyz/go"))
        assertFalse(OpenCodeGoClient.isLoginSuccessUrl("https://opencode.ai/console/login"))
        assertFalse(OpenCodeGoClient.isLoginSuccessUrl("https://opencode.ai/console/workspace"))
        assertNull(OpenCodeGoClient.extractWorkspaceId("https://opencode.ai/console/login"))
    }

    @Test
    fun extractWorkspaceId_keepsLegacyWorkspaceUrl() {
        assertEquals(
            "wrk_01ABC",
            OpenCodeGoClient.extractWorkspaceId("https://opencode.ai/workspace/wrk_01ABC/go"),
        )
    }

    @Test
    fun extractAuthCookie_fromHeader() {
        assertEquals(
            "secret-value",
            OpenCodeGoClient.extractAuthCookie("foo=1; auth=secret-value; bar=2"),
        )
        assertEquals(
            "session-value",
            OpenCodeGoClient.extractAuthCookie(
                "__Host-console_session=session-value; oc_locale=zh",
            ),
        )
        assertEquals(
            "dev-session",
            OpenCodeGoClient.extractAuthCookie("console_session=dev-session"),
        )
        assertEquals(
            "legacy-session",
            OpenCodeGoClient.extractAuthCookie("__Host-auth=legacy-session"),
        )
        assertNull(OpenCodeGoClient.extractAuthCookie("foo=1; bar=2"))
        assertEquals(
            "auth=secret-value; foo=1",
            OpenCodeGoClient.cookieHeaderFor("auth=secret-value; foo=1"),
        )
        assertEquals(
            "__Host-console_session=session-value; oc_locale=zh",
            OpenCodeGoClient.cookieHeaderFor(
                "__Host-console_session=session-value; oc_locale=zh",
            ),
        )
        assertEquals("auth=token", OpenCodeGoClient.cookieHeaderFor("token"))
        assertTrue(OpenCodeGoClient.hasConsoleSessionCookie("__Host-console_session=abc"))
        assertFalse(OpenCodeGoClient.hasConsoleSessionCookie("auth=abc"))
        assertTrue(OpenCodeGoClient.hasLegacySessionCookie("auth=abc"))
    }

    @Test
    fun parseIsoInstantMillis_handlesFractionAndOffset() {
        assertEquals(1_794_441_600_000L, parseIsoInstantMillis("2026-11-12T00:00:00.000Z"))
        assertEquals(1_794_441_600_000L, parseIsoInstantMillis("2026-11-12T08:00:00+08:00"))
        assertEquals(1_794_873_600_287L, parseIsoInstantMillis("2026-11-17T00:00:00.287Z"))
        assertNull(parseIsoInstantMillis(null))
        assertNull(parseIsoInstantMillis("not-a-date"))
    }

    @Test
    fun parseStatus_readsStringMicroCents() {
        val now = 1_794_345_000_000L
        val windows = OpenCodeGoStatusParser.parse(
            """
            {
              "access": {
                "meters": {
                  "fiveHour": {
                    "usedMicroCents": "84000000",
                    "limitMicroCents": "1200000000",
                    "resetsAt": "2026-11-10T23:10:00Z"
                  },
                  "week": {
                    "usedMicroCents": "900000000",
                    "limitMicroCents": "3000000000",
                    "resetsAt": null
                  },
                  "month": {
                    "usedMicroCents": "3900000000",
                    "limitMicroCents": "6000000000",
                    "resetsAt": "2026-11-17T00:00:00.287Z"
                  }
                }
              }
            }
            """.trimIndent(),
            nowEpochMs = now,
        )

        assertEquals(3, windows.size)
        assertEquals(QuotaWindowKind.FIVE_HOUR, windows[0].kind)
        assertEquals(7.0, windows[0].usedPercent)
        assertEquals(7_200L, windows[0].resetInSec)
        assertEquals(QuotaWindowKind.WEEKLY, windows[1].kind)
        assertEquals(30.0, windows[1].usedPercent)
        assertNull(windows[1].resetInSec)
        assertEquals(QuotaWindowKind.MONTHLY, windows[2].kind)
        assertEquals(65.0, windows[2].usedPercent)
    }

    @Test
    fun parseStatus_readsNumericMicroCentsAndCapsAtHundred() {
        val windows = OpenCodeGoStatusParser.parse(
            """
            {
              "access": {
                "meters": {
                  "fiveHour": {"usedMicroCents": 130, "limitMicroCents": 100, "resetsAt": null}
                }
              }
            }
            """.trimIndent(),
            nowEpochMs = 0L,
        )

        assertEquals(1, windows.size)
        assertEquals(100.0, windows[0].usedPercent)
    }

    @Test
    fun parseStatus_withoutAccessReturnsNoWindows() {
        assertTrue(OpenCodeGoStatusParser.parse("""{"access":null}""", 0L).isEmpty())
    }

    @Test
    fun parseStatus_monthFallsBackToAccessEndsAt() {
        val now = 1_794_345_000_000L
        val windows = OpenCodeGoStatusParser.parse(
            """
            {
              "access": {
                "endsAt": "2026-11-17T00:00:00Z",
                "meters": {
                  "fiveHour": {"usedMicroCents": "0", "limitMicroCents": "1200000000"},
                  "week": {"usedMicroCents": "0", "limitMicroCents": "3000000000"},
                  "month": {"usedMicroCents": "0", "limitMicroCents": "6000000000"}
                }
              }
            }
            """.trimIndent(),
            nowEpochMs = now,
        )

        assertEquals(528_600L, windows[2].resetInSec)
    }
}
