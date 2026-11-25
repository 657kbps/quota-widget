package com.kuyermqi.quotawidget.settings

import com.kuyermqi.quotawidget.domain.UsageWindowKind
import com.kuyermqi.quotawidget.platform.PlatformIds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SessionPlatformSettingsTest {
    @Test
    fun workBuddyRequiresBothTokens() {
        assertFalse(SessionPlatformSettings().isConfiguredFor(PlatformIds.WORKBUDDY))
        assertFalse(
            SessionPlatformSettings(accessToken = "access").isConfiguredFor(PlatformIds.WORKBUDDY),
        )
        assertTrue(
            SessionPlatformSettings(
                accessToken = "access",
                refreshToken = "refresh",
            ).isConfiguredFor(PlatformIds.WORKBUDDY),
        )
    }

    @Test
    fun clineAcceptsCookieSessionOrApiKey() {
        assertTrue(
            SessionPlatformSettings(
                accessToken = "access",
                refreshToken = "refresh",
            ).isConfiguredFor(PlatformIds.CLINE),
        )
        assertTrue(SessionPlatformSettings(apiKey = "key").isConfiguredFor(PlatformIds.CLINE))
        assertFalse(SessionPlatformSettings().isConfiguredFor(PlatformIds.CLINE))
    }

    @Test
    fun commandCodeRequiresApiKeyAndAntigravityRequiresRefreshToken() {
        assertTrue(SessionPlatformSettings(apiKey = "key").isConfiguredFor(PlatformIds.COMMAND_CODE))
        assertFalse(
            SessionPlatformSettings(accessToken = "access")
                .isConfiguredFor(PlatformIds.COMMAND_CODE),
        )
        assertTrue(
            SessionPlatformSettings(refreshToken = "refresh")
                .isConfiguredFor(PlatformIds.ANTIGRAVITY),
        )
        assertFalse(
            SessionPlatformSettings(accessToken = "access")
                .isConfiguredFor(PlatformIds.ANTIGRAVITY),
        )
    }

    @Test
    fun defaultWindowKindsMatchProviderShape() {
        assertEquals(
            UsageWindowKind.MONTHLY,
            SessionPlatformSettings.defaultWindowKind(PlatformIds.WORKBUDDY),
        )
        assertEquals(
            UsageWindowKind.ROLLING,
            SessionPlatformSettings.defaultWindowKind(PlatformIds.COMMAND_CODE),
        )
        assertEquals(
            UsageWindowKind.ROLLING,
            SessionPlatformSettings.defaultWindowKind(PlatformIds.CLINE),
        )
        assertEquals(
            UsageWindowKind.ROLLING,
            SessionPlatformSettings.defaultWindowKind(PlatformIds.ANTIGRAVITY),
        )
    }
}
