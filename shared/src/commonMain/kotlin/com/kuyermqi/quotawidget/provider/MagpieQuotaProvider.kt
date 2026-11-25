package com.kuyermqi.quotawidget.provider

import com.kuyermqi.quotawidget.domain.QuotaSnapshot
import com.kuyermqi.quotawidget.domain.QuotaUnavailableException
import com.kuyermqi.quotawidget.domain.QuotaWindow
import com.kuyermqi.quotawidget.domain.UsageWindowKind
import com.kuyermqi.quotawidget.domain.availableUsageWindowKinds
import com.kuyermqi.quotawidget.domain.defaultUsageWindowKind
import com.kuyermqi.quotawidget.magpie.MagpieProvider
import com.kuyermqi.quotawidget.magpie.MagpieQuotaClient
import com.kuyermqi.quotawidget.platform.PlatformIds
import com.kuyermqi.quotawidget.platform.PlatformRegistry
import com.kuyermqi.quotawidget.platform.QuotaPlatform
import com.kuyermqi.quotawidget.settings.PlatformSettingsRepository

internal fun interface MagpieProvidersFetcher {
    suspend fun fetchProviders(baseUrl: String, apiKey: String): List<MagpieProvider>
}

class MagpieQuotaProvider internal constructor(
    private val fetchProviders: MagpieProvidersFetcher,
) : QuotaProvider {
    constructor(
        client: MagpieQuotaClient = MagpieQuotaClient(),
    ) : this(
        fetchProviders = MagpieProvidersFetcher { baseUrl, apiKey ->
            client.fetchProviders(baseUrl, apiKey)
        },
    )

    override val platform: QuotaPlatform =
        PlatformRegistry.find(PlatformIds.MAGPIE)
            ?: error("Magpie platform missing from registry")

    override suspend fun isConfigured(repo: PlatformSettingsRepository): Boolean =
        repo.getMagpieSettings().isConfigured

    override suspend fun fetch(repo: PlatformSettingsRepository): QuotaSnapshot {
        val settings = repo.getMagpieSettings()
        require(settings.isConfigured) { "Magpie 未配置网关地址或 API Key" }
        val providers = fetchProviders.fetchProviders(
            baseUrl = settings.baseUrl,
            apiKey = settings.apiKey,
        )
        val selected = MagpieQuotaClient.selectProvider(providers, settings.selectedProviderId)
            ?: throw QuotaUnavailableException(
                "Magpie 网关未返回 5 小时/每周/每月 额度窗口，" +
                    "请确认网关已连接订阅账号或带限额的 Key",
            )
        val snapshot = MagpieQuotaClient.toSnapshot(
            windows = selected.windows,
            usageDisplayMode = settings.usageDisplayMode,
        )
        repo.editMagpieSettings(
            guard = { current ->
                current.isConfigured &&
                    current.baseUrl == settings.baseUrl &&
                    current.apiKey == settings.apiKey
            },
            transform = { current ->
                current.copy(
                    providers = providers,
                    // An in-flight provider switch by the user wins over this refresh.
                    selectedProviderId = if (current.selectedProviderId == settings.selectedProviderId) {
                        selected.id
                    } else {
                        current.selectedProviderId
                    },
                    widgetWindowKind = clampedWindowKind(current.widgetWindowKind, selected.windows),
                )
            },
        )
        return snapshot
    }

    /**
     * Window kinds depend on the selected provider; keep the widget window
     * pointing at one that provider actually reports, otherwise fall back to the
     * default kind (e.g. antigravity reports no month window).
     */
    private fun clampedWindowKind(
        current: UsageWindowKind,
        windows: List<QuotaWindow>,
    ): UsageWindowKind {
        val available = availableUsageWindowKinds(windows)
        return if (available.isEmpty() || current in available) {
            current
        } else {
            defaultUsageWindowKind(windows)
        }
    }
}
