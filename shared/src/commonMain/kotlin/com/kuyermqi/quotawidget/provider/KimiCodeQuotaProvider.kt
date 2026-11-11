package com.kuyermqi.quotawidget.provider

import com.kuyermqi.quotawidget.domain.QuotaSnapshot
import com.kuyermqi.quotawidget.kimi.KimiCodeUsageClient
import com.kuyermqi.quotawidget.platform.PlatformIds
import com.kuyermqi.quotawidget.platform.PlatformRegistry
import com.kuyermqi.quotawidget.platform.QuotaPlatform
import com.kuyermqi.quotawidget.settings.PlatformSettingsRepository

class KimiCodeQuotaProvider(
    private val client: KimiCodeUsageClient = KimiCodeUsageClient(),
) : QuotaProvider {
    override val platform: QuotaPlatform =
        PlatformRegistry.find(PlatformIds.KIMI_CODE)
            ?: error("Kimi Code platform missing from registry")

    override suspend fun isConfigured(repo: PlatformSettingsRepository): Boolean =
        repo.getKimiCodeSettings().isConfigured

    override suspend fun fetch(repo: PlatformSettingsRepository): QuotaSnapshot {
        val settings = repo.getKimiCodeSettings()
        require(settings.isConfigured) { "Kimi Code API Key 未配置" }
        return client.fetchUsage(
            apiKey = settings.apiKey,
            region = settings.region,
        )
    }
}
