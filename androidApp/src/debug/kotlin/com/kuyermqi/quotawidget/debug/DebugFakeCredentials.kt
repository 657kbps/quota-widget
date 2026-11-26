package com.kuyermqi.quotawidget.debug

import com.kuyermqi.quotawidget.platform.PlatformIds
import com.kuyermqi.quotawidget.settings.PlatformSettingsRepository

private const val DEBUG_FAKE_CREDENTIAL_PREFIX = "debug-fake-"

/** Keeps seeded debug snapshots intact across launch/save refreshes instead of calling live APIs. */
internal suspend fun shouldSkipRefreshForDebugFake(
    repo: PlatformSettingsRepository,
    platformId: String,
): Boolean {
    val credential = when (platformId) {
        PlatformIds.DEEPSEEK -> repo.getDeepSeekSettings().apiKey
        PlatformIds.OPENCODE_GO -> repo.getOpenCodeGoSettings().apiKey
        PlatformIds.CODEX -> repo.getCodexSettings().accessToken
        PlatformIds.NEW_API -> repo.getNewApiSettings().apiKey
        PlatformIds.MAGPIE -> repo.getMagpieSettings().apiKey
        PlatformIds.KIMI_CODE -> repo.getKimiCodeSettings().apiKey
        PlatformIds.ZHIPU -> repo.getZhipuSettings().apiKey
        in PlatformIds.sessionPlatforms -> repo.getSessionSettings(platformId).apiKey
        else -> ""
    }
    return credential.startsWith(DEBUG_FAKE_CREDENTIAL_PREFIX)
}
