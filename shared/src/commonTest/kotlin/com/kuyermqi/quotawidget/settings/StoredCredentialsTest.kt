package com.kuyermqi.quotawidget.settings

import com.kuyermqi.quotawidget.platform.PlatformIds
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StoredCredentialsTest {
    @Test
    fun missingCiphertextMeansNotConfigured() {
        assertFalse(isPlatformConfiguredWithStoredCredentials(PlatformIds.DEEPSEEK, presence()))
        assertFalse(isPlatformConfiguredWithStoredCredentials(PlatformIds.KIMI_CODE, presence()))
        assertFalse(isPlatformConfiguredWithStoredCredentials(PlatformIds.ZHIPU, presence()))
        assertFalse(isPlatformConfiguredWithStoredCredentials(PlatformIds.CODEX, presence()))
    }

    @Test
    fun apiKeyPlatformsOnlyNeedTheCiphertext() {
        val stored = presence(hasApiKey = true)
        assertTrue(isPlatformConfiguredWithStoredCredentials(PlatformIds.DEEPSEEK, stored))
        assertTrue(isPlatformConfiguredWithStoredCredentials(PlatformIds.KIMI_CODE, stored))
        assertTrue(isPlatformConfiguredWithStoredCredentials(PlatformIds.ZHIPU, stored))
    }

    @Test
    fun baseUrlPlatformsNeedUrlAndKey() {
        assertFalse(
            isPlatformConfiguredWithStoredCredentials(
                PlatformIds.NEW_API,
                presence(hasApiKey = true),
            ),
        )
        assertFalse(
            isPlatformConfiguredWithStoredCredentials(
                PlatformIds.MAGPIE,
                presence(hasBaseUrl = true),
            ),
        )
        val complete = presence(hasBaseUrl = true, hasApiKey = true)
        assertTrue(isPlatformConfiguredWithStoredCredentials(PlatformIds.NEW_API, complete))
        assertTrue(isPlatformConfiguredWithStoredCredentials(PlatformIds.MAGPIE, complete))
    }

    @Test
    fun codexNeedsBothTokensAndAccountId() {
        val tokens = presence(hasAccessToken = true, hasRefreshToken = true)
        assertFalse(isPlatformConfiguredWithStoredCredentials(PlatformIds.CODEX, tokens))
        assertTrue(
            isPlatformConfiguredWithStoredCredentials(
                PlatformIds.CODEX,
                tokens.copy(hasAccountId = true),
            ),
        )
    }

    @Test
    fun openCodeStoredAuthMethodPicksTheCredential() {
        val cookie = presence(hasWorkspaceId = true, hasAuthCookie = true)
        assertTrue(
            isPlatformConfiguredWithStoredCredentials(
                PlatformIds.OPENCODE_GO,
                cookie.copy(openCodeAuthMethod = OpenCodeAuthMethod.COOKIE),
            ),
        )
        assertFalse(
            isPlatformConfiguredWithStoredCredentials(
                PlatformIds.OPENCODE_GO,
                cookie.copy(openCodeAuthMethod = OpenCodeAuthMethod.API_KEY),
            ),
        )

        val apiKey = presence(hasApiKey = true)
        assertTrue(
            isPlatformConfiguredWithStoredCredentials(
                PlatformIds.OPENCODE_GO,
                apiKey.copy(openCodeAuthMethod = OpenCodeAuthMethod.API_KEY),
            ),
        )
        assertFalse(
            isPlatformConfiguredWithStoredCredentials(
                PlatformIds.OPENCODE_GO,
                presence(
                    hasAccessToken = true,
                    openCodeAuthMethod = OpenCodeAuthMethod.COOKIE,
                ),
            ),
        )
    }

    @Test
    fun openCodeWithoutStoredMethodFallsBackToWhicheverCredentialExists() {
        assertTrue(
            isPlatformConfiguredWithStoredCredentials(
                PlatformIds.OPENCODE_GO,
                presence(hasApiKey = true),
            ),
        )
        assertTrue(
            isPlatformConfiguredWithStoredCredentials(
                PlatformIds.OPENCODE_GO,
                presence(hasWorkspaceId = true, hasAuthCookie = true),
            ),
        )
        assertFalse(
            isPlatformConfiguredWithStoredCredentials(
                PlatformIds.OPENCODE_GO,
                presence(hasWorkspaceId = true),
            ),
        )
    }

    @Test
    fun sessionPlatformsUseTheirOwnCredentialRules() {
        val tokens = presence(hasAccessToken = true, hasRefreshToken = true)
        val apiKeyOnly = presence(hasApiKey = true)

        assertTrue(
            isPlatformConfiguredWithStoredCredentials(PlatformIds.WORKBUDDY, tokens),
        )
        assertTrue(
            isPlatformConfiguredWithStoredCredentials(PlatformIds.WORKBUDDY_AI, tokens),
        )
        assertFalse(
            isPlatformConfiguredWithStoredCredentials(PlatformIds.WORKBUDDY, apiKeyOnly),
        )

        assertTrue(isPlatformConfiguredWithStoredCredentials(PlatformIds.CLINE, tokens))
        assertTrue(isPlatformConfiguredWithStoredCredentials(PlatformIds.CLINE, apiKeyOnly))
        assertFalse(
            isPlatformConfiguredWithStoredCredentials(
                PlatformIds.CLINE,
                presence(hasAccessToken = true),
            ),
        )

        assertTrue(
            isPlatformConfiguredWithStoredCredentials(PlatformIds.COMMAND_CODE, apiKeyOnly),
        )
        assertFalse(
            isPlatformConfiguredWithStoredCredentials(PlatformIds.COMMAND_CODE, tokens),
        )

        assertTrue(
            isPlatformConfiguredWithStoredCredentials(
                PlatformIds.ANTIGRAVITY,
                presence(hasRefreshToken = true),
            ),
        )
        assertFalse(
            isPlatformConfiguredWithStoredCredentials(
                PlatformIds.ANTIGRAVITY,
                presence(hasAccessToken = true),
            ),
        )
    }

    @Test
    fun unknownPlatformIsNeverConfigured() {
        assertFalse(
            isPlatformConfiguredWithStoredCredentials(
                "unknown_platform",
                presence(
                    hasApiKey = true,
                    hasAccessToken = true,
                    hasRefreshToken = true,
                    hasWorkspaceId = true,
                    hasAuthCookie = true,
                    hasBaseUrl = true,
                    hasAccountId = true,
                ),
            ),
        )
    }

    private fun presence(
        hasApiKey: Boolean = false,
        hasAccessToken: Boolean = false,
        hasRefreshToken: Boolean = false,
        hasWorkspaceId: Boolean = false,
        hasAuthCookie: Boolean = false,
        hasBaseUrl: Boolean = false,
        hasAccountId: Boolean = false,
        openCodeAuthMethod: OpenCodeAuthMethod? = null,
    ): StoredCredentialPresence = StoredCredentialPresence(
        hasApiKey = hasApiKey,
        hasAccessToken = hasAccessToken,
        hasRefreshToken = hasRefreshToken,
        hasWorkspaceId = hasWorkspaceId,
        hasAuthCookie = hasAuthCookie,
        hasBaseUrl = hasBaseUrl,
        hasAccountId = hasAccountId,
        openCodeAuthMethod = openCodeAuthMethod,
    )
}
