package com.kuyermqi.quotawidget.provider

import com.kuyermqi.quotawidget.antigravity.AntigravityClient
import com.kuyermqi.quotawidget.cline.ClineClient
import com.kuyermqi.quotawidget.commandcode.CommandCodeClient
import com.kuyermqi.quotawidget.domain.QuotaSnapshot
import com.kuyermqi.quotawidget.platform.PlatformIds
import com.kuyermqi.quotawidget.platform.PlatformRegistry
import com.kuyermqi.quotawidget.platform.QuotaPlatform
import com.kuyermqi.quotawidget.settings.PlatformSettingsRepository
import com.kuyermqi.quotawidget.settings.SessionPlatformSettings
import com.kuyermqi.quotawidget.workbuddy.WorkBuddyClient
import com.kuyermqi.quotawidget.workbuddy.WorkBuddyRegion
import io.ktor.client.HttpClient

/** Provider logic for one generic [SessionPlatformSettings] account. */
interface SessionQuotaClient {
    fun isConfigured(settings: SessionPlatformSettings): Boolean

    suspend fun fetch(
        settings: SessionPlatformSettings,
        save: suspend (SessionPlatformSettings) -> Unit,
    ): QuotaSnapshot
}

class SessionQuotaProvider(
    platformId: String,
    private val client: SessionQuotaClient,
) : QuotaProvider {
    override val platform: QuotaPlatform =
        PlatformRegistry.find(platformId)
            ?: error("Session platform $platformId missing from registry")

    override suspend fun isConfigured(repo: PlatformSettingsRepository): Boolean =
        client.isConfigured(repo.getSessionSettings(platform.id))

    override suspend fun fetch(repo: PlatformSettingsRepository): QuotaSnapshot {
        val platformId = platform.id
        val settings = repo.getSessionSettings(platformId)
        require(client.isConfigured(settings)) { "${platform.displayName} 未配置" }
        // Credentials this fetch is allowed to replace: the ones it sent, then whatever it
        // rotated them into. A user who logs out or re-logs-in meanwhile fails the guard below,
        // so a late network result can never resurrect or overwrite their credentials.
        var baseline = settings
        return client.fetch(settings) { next ->
            val expected = baseline
            val applied = repo.editSessionSettings(
                platformId = platformId,
                guard = { current ->
                    current.isConfiguredFor(platformId) && current.hasSameCredentialsAs(expected)
                },
                transform = { current -> current.mergeRefreshResult(next) },
            )
            if (applied) baseline = next
        }
    }
}

private fun SessionPlatformSettings.hasSameCredentialsAs(other: SessionPlatformSettings): Boolean =
    accessToken == other.accessToken &&
        refreshToken == other.refreshToken &&
        apiKey == other.apiKey

/**
 * What a refresh may persist: rotated tokens and the account metadata derived from them.
 * Widget display preferences belong to the user, so they always come from the stored value.
 */
private fun SessionPlatformSettings.mergeRefreshResult(
    next: SessionPlatformSettings,
): SessionPlatformSettings = copy(
    accessToken = next.accessToken,
    refreshToken = next.refreshToken,
    expiresAtEpochMs = next.expiresAtEpochMs,
    apiKey = next.apiKey,
    accountId = next.accountId,
    email = next.email,
    plan = next.plan,
    projectId = next.projectId,
    domain = next.domain,
    uid = next.uid,
    enterpriseId = next.enterpriseId,
)

class WorkBuddyQuotaClient(
    private val region: WorkBuddyRegion,
    private val client: WorkBuddyClient,
) : SessionQuotaClient {
    override fun isConfigured(settings: SessionPlatformSettings): Boolean =
        settings.accessToken.isNotBlank() && settings.refreshToken.isNotBlank()

    override suspend fun fetch(
        settings: SessionPlatformSettings,
        save: suspend (SessionPlatformSettings) -> Unit,
    ): QuotaSnapshot = client.fetchQuota(region, settings, save)
}

class ClineQuotaClient(
    private val client: ClineClient,
) : SessionQuotaClient {
    override fun isConfigured(settings: SessionPlatformSettings): Boolean =
        (settings.accessToken.isNotBlank() && settings.refreshToken.isNotBlank()) ||
            settings.apiKey.isNotBlank()

    override suspend fun fetch(
        settings: SessionPlatformSettings,
        save: suspend (SessionPlatformSettings) -> Unit,
    ): QuotaSnapshot = client.fetchQuota(settings, save)
}

class CommandCodeQuotaClient(
    private val client: CommandCodeClient,
) : SessionQuotaClient {
    override fun isConfigured(settings: SessionPlatformSettings): Boolean =
        settings.apiKey.isNotBlank()

    override suspend fun fetch(
        settings: SessionPlatformSettings,
        save: suspend (SessionPlatformSettings) -> Unit,
    ): QuotaSnapshot = client.fetchQuota(settings, save)
}

class AntigravityQuotaClient(
    private val client: AntigravityClient,
) : SessionQuotaClient {
    override fun isConfigured(settings: SessionPlatformSettings): Boolean =
        settings.refreshToken.isNotBlank()

    override suspend fun fetch(
        settings: SessionPlatformSettings,
        save: suspend (SessionPlatformSettings) -> Unit,
    ): QuotaSnapshot = client.fetchQuota(settings, save)
}

fun sessionQuotaProviders(httpClient: HttpClient): List<QuotaProvider> = listOf(
    SessionQuotaProvider(
        PlatformIds.WORKBUDDY,
        WorkBuddyQuotaClient(WorkBuddyRegion.CHINA, WorkBuddyClient(httpClient)),
    ),
    SessionQuotaProvider(
        PlatformIds.WORKBUDDY_AI,
        WorkBuddyQuotaClient(WorkBuddyRegion.INTERNATIONAL, WorkBuddyClient(httpClient)),
    ),
    SessionQuotaProvider(PlatformIds.CLINE, ClineQuotaClient(ClineClient(httpClient))),
    SessionQuotaProvider(
        PlatformIds.COMMAND_CODE,
        CommandCodeQuotaClient(CommandCodeClient(httpClient)),
    ),
    SessionQuotaProvider(
        PlatformIds.ANTIGRAVITY,
        AntigravityQuotaClient(AntigravityClient(httpClient)),
    ),
)
