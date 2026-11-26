package com.kuyermqi.quotawidget.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class WidgetErrorKindTest {
    @Test
    fun storageRoundTripKeepsEveryKind() {
        val kinds = listOf(
            WidgetErrorKind.SessionExpired,
            WidgetErrorKind.QuotaUnavailable,
            WidgetErrorKind.HttpStatus(503),
            WidgetErrorKind.Network,
            WidgetErrorKind.InvalidBaseUrl,
            WidgetErrorKind.ParseFailure,
            WidgetErrorKind.Unknown(),
        )

        for (kind in kinds) {
            assertEquals(kind, WidgetErrorKind.fromStorage(kind.toStorage()), "round trip of $kind")
        }
    }

    @Test
    fun storedCodeCarriesTheHttpStatus() {
        assertEquals("http_status:429", WidgetErrorKind.HttpStatus(429).toStorage())
        assertEquals(
            WidgetErrorKind.HttpStatus(429),
            WidgetErrorKind.fromStorage("http_status:429"),
        )
        assertEquals(WidgetErrorKind.Unknown(), WidgetErrorKind.fromStorage("http_status:abc"))
        assertEquals(WidgetErrorKind.Unknown(), WidgetErrorKind.fromStorage("http_status:999"))
    }

    @Test
    fun legacyMessageBecomesUnknownAndIsNeverPersistedAgain() {
        val kind = WidgetErrorKind.fromStorage("Cline 登录已过期，请重新登录")

        val unknown = assertIs<WidgetErrorKind.Unknown>(kind)
        assertEquals("Cline 登录已过期，请重新登录", unknown.legacyText)
        assertEquals("unknown", unknown.toStorage())
        assertFalse(unknown.toStorage().contains("登录"))
    }

    @Test
    fun blankValueBecomesUnknownWithoutText() {
        assertEquals(WidgetErrorKind.Unknown(), WidgetErrorKind.fromStorage(null))
        assertEquals(WidgetErrorKind.Unknown(), WidgetErrorKind.fromStorage("   "))
    }

    @Test
    fun storedCodesAreLocaleFree() {
        val codes = listOf(
            WidgetErrorKind.SessionExpired,
            WidgetErrorKind.QuotaUnavailable,
            WidgetErrorKind.HttpStatus(404),
            WidgetErrorKind.Network,
            WidgetErrorKind.InvalidBaseUrl,
            WidgetErrorKind.ParseFailure,
            WidgetErrorKind.Unknown(),
        ).map { it.toStorage() }

        assertTrue(codes.none { it.contains(Regex("\\p{IsHan}")) })
    }
}
