package com.kuyermqi.quotawidget.provider

import com.kuyermqi.quotawidget.deepseek.DeepSeekBalanceClient
import com.kuyermqi.quotawidget.domain.QuotaSnapshot
import com.kuyermqi.quotawidget.domain.QuotaWindow
import com.kuyermqi.quotawidget.domain.QuotaWindowKind
import com.kuyermqi.quotawidget.platform.PlatformIds
import com.kuyermqi.quotawidget.platform.PlatformRegistry
import com.kuyermqi.quotawidget.platform.QuotaPlatform
import com.kuyermqi.quotawidget.settings.PlatformSettingsRepository

class DeepSeekQuotaProvider(
    private val client: DeepSeekBalanceClient = DeepSeekBalanceClient(),
) : QuotaProvider {
    override val platform: QuotaPlatform =
        PlatformRegistry.find(PlatformIds.DEEPSEEK)
            ?: error("DeepSeek platform missing from registry")

    override suspend fun isConfigured(repo: PlatformSettingsRepository): Boolean =
        repo.getDeepSeekSettings().apiKey.isNotBlank()

    override suspend fun fetch(repo: PlatformSettingsRepository): QuotaSnapshot {
        val settings = repo.getDeepSeekSettings()
        require(settings.apiKey.isNotBlank()) { "DeepSeek API Key 未配置" }
        val balance = client.fetchBalance(
            apiKey = settings.apiKey,
            preferredCurrency = settings.currency,
        )
        return QuotaSnapshot(
            platformId = balance.platformId,
            platformName = balance.platformName,
            windows = listOf(QuotaWindow(kind = QuotaWindowKind.BALANCE)),
            primaryDisplay = balance.formattedBalance,
            updatedAtEpochMs = balance.updatedAtEpochMs,
            currency = balance.currency,
            totalBalance = balance.totalBalance,
        )
    }
}
