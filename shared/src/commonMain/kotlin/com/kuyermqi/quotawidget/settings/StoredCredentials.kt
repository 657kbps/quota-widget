package com.kuyermqi.quotawidget.settings

import com.kuyermqi.quotawidget.platform.PlatformIds

/**
 * Credential fields that exist in storage, read without decrypting anything.
 *
 * Secrets are stored only as ciphertext and blank secrets are removed, so a present
 * ciphertext implies a non-blank secret. The only difference from decrypting is ciphertext
 * the current keyset cannot decrypt: it still counts as present here, and the keyset
 * recovery path drops it on the next settings write.
 */
data class StoredCredentialPresence(
    val hasApiKey: Boolean = false,
    val hasAccessToken: Boolean = false,
    val hasRefreshToken: Boolean = false,
    val hasWorkspaceId: Boolean = false,
    val hasAuthCookie: Boolean = false,
    val hasBaseUrl: Boolean = false,
    val hasAccountId: Boolean = false,
    val openCodeAuthMethod: OpenCodeAuthMethod? = null,
)

/**
 * Whether [platformId] is configured, judged from [presence] alone, so callers can gate
 * refreshes and widget rendering without paying for a decrypt per platform.
 *
 * Mirrors the `isConfigured` / `isConfiguredFor` rules of the settings data classes.
 */
fun isPlatformConfiguredWithStoredCredentials(
    platformId: String,
    presence: StoredCredentialPresence,
): Boolean = when (platformId) {
    PlatformIds.DEEPSEEK -> presence.hasApiKey
    PlatformIds.OPENCODE_GO -> when (presence.openCodeAuthMethod) {
        OpenCodeAuthMethod.API_KEY -> presence.hasApiKey
        OpenCodeAuthMethod.COOKIE -> presence.hasWorkspaceId && presence.hasAuthCookie
        null -> presence.hasApiKey || (presence.hasWorkspaceId && presence.hasAuthCookie)
    }
    PlatformIds.CODEX ->
        presence.hasAccessToken && presence.hasRefreshToken && presence.hasAccountId
    PlatformIds.NEW_API, PlatformIds.MAGPIE -> presence.hasBaseUrl && presence.hasApiKey
    PlatformIds.KIMI_CODE, PlatformIds.ZHIPU -> presence.hasApiKey
    PlatformIds.WORKBUDDY, PlatformIds.WORKBUDDY_AI ->
        presence.hasAccessToken && presence.hasRefreshToken
    PlatformIds.CLINE ->
        (presence.hasAccessToken && presence.hasRefreshToken) || presence.hasApiKey
    PlatformIds.COMMAND_CODE -> presence.hasApiKey
    PlatformIds.ANTIGRAVITY -> presence.hasRefreshToken
    else -> false
}
