package com.kuyermqi.quotawidget.debug

import com.kuyermqi.quotawidget.settings.PlatformSettingsRepository

/** Release builds never skip live refreshes for synthetic credentials. */
internal suspend fun shouldSkipRefreshForDebugFake(
    repo: PlatformSettingsRepository,
    platformId: String,
): Boolean = false
