package com.kuyermqi.quotawidget.settings

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OpenCodeGoSettingsTest {
    @Test
    fun apiKeyAloneIsConfigured() {
        assertTrue(OpenCodeGoSettings(apiKey = "sk-test").isConfigured)
        assertTrue(OpenCodeGoSettings(apiKey = "sk-test").hasApiKey)
        assertFalse(OpenCodeGoSettings(apiKey = "sk-test").hasCookieSession)
    }

    @Test
    fun cookieSessionRequiresWorkspaceAndCookie() {
        assertFalse(OpenCodeGoSettings(workspaceId = "wrk_abc").isConfigured)
        assertFalse(OpenCodeGoSettings(authCookie = "auth=token").isConfigured)
        assertTrue(
            OpenCodeGoSettings(
                workspaceId = "wrk_abc",
                authCookie = "auth=token",
            ).isConfigured,
        )
    }
}
