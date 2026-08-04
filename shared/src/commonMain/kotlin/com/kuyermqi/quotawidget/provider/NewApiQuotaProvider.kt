package com.kuyermqi.quotawidget.provider

import com.kuyermqi.quotawidget.domain.QuotaSnapshot
import com.kuyermqi.quotawidget.newapi.NewApiUsageClient
import com.kuyermqi.quotawidget.platform.PlatformIds
import com.kuyermqi.quotawidget.platform.PlatformRegistry
import com.kuyermqi.quotawidget.platform.QuotaPlatform
import com.kuyermqi.quotawidget.settings.PlatformSettingsRepository

class NewApiQuotaProvider(
    private val client: NewApiUsageClient = NewApiUsageClient(),
) : QuotaProvider {
    override val platform: QuotaPlatform =
        PlatformRegistry.find(PlatformIds.NEW_API)
            ?: error("NewAPI platform missing from registry")

    override suspend fun isConfigured(repo: PlatformSettingsRepository): Boolean =
        repo.getNewApiSettings().isConfigured

    override suspend fun fetch(repo: PlatformSettingsRepository): QuotaSnapshot {
        val settings = repo.getNewApiSettings()
        require(settings.isConfigured) { "NewAPI 未配置 Base URL 或 API Key" }
        return client.fetchUsage(
            baseUrl = settings.baseUrl,
            apiKey = settings.apiKey,
            quotaPerUsd = settings.quotaPerUsd,
        )
    }
}
