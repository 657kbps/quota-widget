package com.kuyermqi.quotawidget.provider

import com.kuyermqi.quotawidget.domain.QuotaSnapshot
import com.kuyermqi.quotawidget.platform.QuotaPlatform
import com.kuyermqi.quotawidget.settings.PlatformSettingsRepository

interface QuotaProvider {
    val platform: QuotaPlatform

    suspend fun isConfigured(repo: PlatformSettingsRepository): Boolean

    suspend fun fetch(repo: PlatformSettingsRepository): QuotaSnapshot
}
