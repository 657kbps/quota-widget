package com.kuyermqi.quotawidget.settings

import kotlin.test.Test
import kotlin.test.assertEquals
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

    @Test
    fun explicitAuthMethodSelectsActiveCredential() {
        val both = OpenCodeGoSettings(
            workspaceId = "wrk_abc",
            authCookie = "auth=token",
            apiKey = "sk-test",
            authMethod = OpenCodeAuthMethod.COOKIE,
        )
        assertTrue(both.isConfigured)
        assertEquals(OpenCodeAuthMethod.COOKIE, both.effectiveAuthMethod)

        assertFalse(
            both.copy(
                authMethod = OpenCodeAuthMethod.COOKIE,
                workspaceId = "",
                authCookie = "",
            ).isConfigured,
        )
        assertFalse(
            both.copy(
                authMethod = OpenCodeAuthMethod.API_KEY,
                apiKey = "",
            ).isConfigured,
        )
    }
}
