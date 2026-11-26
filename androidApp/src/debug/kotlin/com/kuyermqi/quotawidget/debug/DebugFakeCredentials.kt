package com.kuyermqi.quotawidget.debug

import com.kuyermqi.quotawidget.platform.PlatformIds
import com.kuyermqi.quotawidget.settings.PlatformSettingsRepository

private const val DEBUG_FAKE_CREDENTIAL_PREFIX = "debug-fake-"

/** Keeps seeded debug snapshots intact across launch/save refreshes instead of calling live APIs. */
internal suspend fun shouldSkipRefreshForDebugFake(
    repo: PlatformSettingsRepository,
    platformId: String,
): Boolean {
    val credentials = when (platformId) {
        PlatformIds.DEEPSEEK -> listOf(repo.getDeepSeekSettings().apiKey)
        PlatformIds.OPENCODE_GO -> repo.getOpenCodeGoSettings().let { listOf(it.apiKey, it.authCookie) }
        PlatformIds.CODEX -> repo.getCodexSettings().accessToken.let(::listOf)
        PlatformIds.NEW_API -> listOf(repo.getNewApiSettings().apiKey)
        PlatformIds.MAGPIE -> listOf(repo.getMagpieSettings().apiKey)
        PlatformIds.KIMI_CODE -> listOf(repo.getKimiCodeSettings().apiKey)
        PlatformIds.ZHIPU -> listOf(repo.getZhipuSettings().apiKey)
        in PlatformIds.sessionPlatforms -> repo.getSessionSettings(platformId).let {
            listOf(it.apiKey, it.accessToken, it.refreshToken)
        }
        else -> emptyList()
    }
    return credentials.any { it.startsWith(DEBUG_FAKE_CREDENTIAL_PREFIX) }
}
