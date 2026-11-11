package com.kuyermqi.quotawidget.provider

import com.kuyermqi.quotawidget.domain.QuotaSnapshot
import com.kuyermqi.quotawidget.platform.PlatformIds
import com.kuyermqi.quotawidget.platform.PlatformRegistry
import com.kuyermqi.quotawidget.platform.QuotaPlatform
import com.kuyermqi.quotawidget.settings.PlatformSettingsRepository
import com.kuyermqi.quotawidget.zhipu.ZhipuUsageClient

class ZhipuQuotaProvider(
    private val client: ZhipuUsageClient = ZhipuUsageClient(),
) : QuotaProvider {
    override val platform: QuotaPlatform =
        PlatformRegistry.find(PlatformIds.ZHIPU)
            ?: error("Zhipu platform missing from registry")

    override suspend fun isConfigured(repo: PlatformSettingsRepository): Boolean =
        repo.getZhipuSettings().isConfigured

    override suspend fun fetch(repo: PlatformSettingsRepository): QuotaSnapshot {
        val settings = repo.getZhipuSettings()
        require(settings.isConfigured) { "智谱 API Key 未配置" }
        return client.fetchUsage(
            apiKey = settings.apiKey,
            region = settings.region,
        )
    }
}
