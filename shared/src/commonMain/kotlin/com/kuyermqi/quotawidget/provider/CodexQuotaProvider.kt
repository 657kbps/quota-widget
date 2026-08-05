package com.kuyermqi.quotawidget.provider

import com.kuyermqi.quotawidget.codex.CodexOAuth
import com.kuyermqi.quotawidget.codex.CodexUsageClient
import com.kuyermqi.quotawidget.domain.QuotaSnapshot
import com.kuyermqi.quotawidget.domain.QuotaWindow
import com.kuyermqi.quotawidget.domain.SessionExpiredException
import com.kuyermqi.quotawidget.domain.clampCodexWidgetWindowKind
import com.kuyermqi.quotawidget.platform.PlatformIds
import com.kuyermqi.quotawidget.platform.PlatformRegistry
import com.kuyermqi.quotawidget.platform.QuotaPlatform
import com.kuyermqi.quotawidget.settings.CodexSettings
import com.kuyermqi.quotawidget.settings.PlatformSettingsRepository

class CodexQuotaProvider(
    private val oauth: CodexOAuth = CodexOAuth(),
    private val usageClient: CodexUsageClient = CodexUsageClient(),
) : QuotaProvider {
    override val platform: QuotaPlatform =
        PlatformRegistry.find(PlatformIds.CODEX)
            ?: error("Codex platform missing from registry")

    override suspend fun isConfigured(repo: PlatformSettingsRepository): Boolean =
        repo.getCodexSettings().isConfigured

    override suspend fun fetch(repo: PlatformSettingsRepository): QuotaSnapshot {
        var settings = repo.getCodexSettings()
        require(settings.isConfigured) { "Codex 未登录" }
        if (CodexOAuth.isAccessTokenExpiringSoon(settings.expiresAtEpochMs)) {
            settings = refreshAndPersist(repo, settings)
        }
        val snapshot = try {
            usageClient.fetchUsage(settings.accessToken, settings.accountId)
        } catch (e: SessionExpiredException) {
            settings = refreshAndPersist(repo, settings)
            usageClient.fetchUsage(settings.accessToken, settings.accountId)
        }
        persistClampedWindowKind(repo, settings, snapshot.windows)
        return snapshot
    }

    private suspend fun persistClampedWindowKind(
        repo: PlatformSettingsRepository,
        settings: CodexSettings,
        windows: List<QuotaWindow>,
    ) {
        val nextKind = clampCodexWidgetWindowKind(settings.widgetWindowKind, windows)
        if (nextKind != settings.widgetWindowKind) {
            repo.saveCodexSettings(settings.copy(widgetWindowKind = nextKind))
        }
    }

    private suspend fun refreshAndPersist(
        repo: PlatformSettingsRepository,
        settings: CodexSettings,
    ): CodexSettings {
        val bundle = oauth.refresh(settings.refreshToken)
        val next = settings.copy(
            accessToken = bundle.accessToken,
            refreshToken = bundle.refreshToken,
            idToken = bundle.idToken.ifBlank { settings.idToken },
            accountId = bundle.accountId.ifBlank { settings.accountId },
            expiresAtEpochMs = bundle.expiresAtEpochMs,
            email = bundle.email.ifBlank { settings.email },
            planType = bundle.planType.ifBlank { settings.planType },
        )
        repo.saveCodexSettings(next)
        return next
    }
}
