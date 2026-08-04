package com.kuyermqi.quotawidget.settings

import com.kuyermqi.quotawidget.domain.AppSettings
import com.kuyermqi.quotawidget.domain.QuotaSnapshot
import com.kuyermqi.quotawidget.domain.RefreshIconPhase
import com.kuyermqi.quotawidget.domain.WidgetDisplayState
import com.kuyermqi.quotawidget.platform.PlatformIds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakePlatformSettingsRepository(
    deepSeek: DeepSeekSettings = DeepSeekSettings(),
    openCode: OpenCodeGoSettings = OpenCodeGoSettings(),
    codex: CodexSettings = CodexSettings(),
    widgetStates: Map<String, WidgetDisplayState> = emptyMap(),
) : PlatformSettingsRepository {
    private val deepSeekFlow = MutableStateFlow(deepSeek)
    private val openCodeFlow = MutableStateFlow(openCode)
    private val codexFlow = MutableStateFlow(codex)
    private val widgetFlows = mutableMapOf(
        PlatformIds.DEEPSEEK to MutableStateFlow(
            widgetStates[PlatformIds.DEEPSEEK] ?: WidgetDisplayState.NotConfigured,
        ),
        PlatformIds.OPENCODE_GO to MutableStateFlow(
            widgetStates[PlatformIds.OPENCODE_GO] ?: WidgetDisplayState.NotConfigured,
        ),
        PlatformIds.CODEX to MutableStateFlow(
            widgetStates[PlatformIds.CODEX] ?: WidgetDisplayState.NotConfigured,
        ),
    )
    private val refreshPhases = mutableMapOf<String, RefreshIconPhase>()
    private val refreshStartedAt = mutableMapOf<String, Long>()
    private val appSettingsFlow = MutableStateFlow(AppSettings())

    var platformTipDismissed: Boolean = false
    var oemBackgroundTipDismissed: Boolean = false
    var updateIgnoredVersion: String? = null

    override fun observeDeepSeekSettings(): Flow<DeepSeekSettings> = deepSeekFlow.asStateFlow()
    override suspend fun getDeepSeekSettings(): DeepSeekSettings = deepSeekFlow.value
    override suspend fun saveDeepSeekSettings(settings: DeepSeekSettings) {
        deepSeekFlow.value = settings
        if (settings.apiKey.isBlank()) {
            widgetFlow(PlatformIds.DEEPSEEK).value = WidgetDisplayState.NotConfigured
        }
    }

    override fun observeOpenCodeGoSettings(): Flow<OpenCodeGoSettings> = openCodeFlow.asStateFlow()
    override suspend fun getOpenCodeGoSettings(): OpenCodeGoSettings = openCodeFlow.value
    override suspend fun saveOpenCodeGoSettings(settings: OpenCodeGoSettings) {
        openCodeFlow.value = settings
        if (!settings.isConfigured) {
            widgetFlow(PlatformIds.OPENCODE_GO).value = WidgetDisplayState.NotConfigured
        }
    }

    override suspend fun clearOpenCodeGoSettings() {
        val current = openCodeFlow.value
        openCodeFlow.value = OpenCodeGoSettings(
            widgetWindowKind = current.widgetWindowKind,
            usageDisplayMode = current.usageDisplayMode,
            usageProgressStyle = current.usageProgressStyle,
        )
        widgetFlow(PlatformIds.OPENCODE_GO).value = WidgetDisplayState.NotConfigured
    }

    override fun observeCodexSettings(): Flow<CodexSettings> = codexFlow.asStateFlow()
    override suspend fun getCodexSettings(): CodexSettings = codexFlow.value
    override suspend fun saveCodexSettings(settings: CodexSettings) {
        codexFlow.value = settings
        if (!settings.isConfigured) {
            widgetFlow(PlatformIds.CODEX).value = WidgetDisplayState.NotConfigured
        }
    }

    override suspend fun clearCodexSettings() {
        val current = codexFlow.value
        codexFlow.value = CodexSettings(
            widgetWindowKind = current.widgetWindowKind,
            usageDisplayMode = current.usageDisplayMode,
            usageProgressStyle = current.usageProgressStyle,
        )
        widgetFlow(PlatformIds.CODEX).value = WidgetDisplayState.NotConfigured
    }

    override fun observeWidgetState(platformId: String): Flow<WidgetDisplayState> =
        widgetFlow(platformId).asStateFlow()

    override suspend fun getWidgetState(platformId: String): WidgetDisplayState =
        widgetFlow(platformId).value

    override suspend fun saveWidgetSuccess(platformId: String, snapshot: QuotaSnapshot) {
        widgetFlow(platformId).value = WidgetDisplayState.Success(snapshot)
    }

    override suspend fun saveWidgetError(platformId: String, message: String) {
        widgetFlow(platformId).value = WidgetDisplayState.Error(message)
    }

    override suspend fun saveWidgetLoading(platformId: String) {
        widgetFlow(platformId).value = WidgetDisplayState.Loading
    }

    override suspend fun saveWidgetNotConfigured(platformId: String) {
        widgetFlow(platformId).value = WidgetDisplayState.NotConfigured
    }

    override suspend fun saveWidgetNeedsReauth(platformId: String) {
        widgetFlow(platformId).value = WidgetDisplayState.NeedsReauth
    }

    override suspend fun getRefreshIconPhase(platformId: String): RefreshIconPhase =
        refreshPhases[platformId] ?: RefreshIconPhase.Idle

    override suspend fun setRefreshIconPhase(platformId: String, phase: RefreshIconPhase) {
        refreshPhases[platformId] = phase
    }

    override suspend fun getRefreshStartedAtEpochMs(platformId: String): Long =
        refreshStartedAt[platformId] ?: 0L

    override suspend fun setRefreshStartedAtEpochMs(platformId: String, epochMs: Long) {
        refreshStartedAt[platformId] = epochMs
    }

    override suspend fun clearAllRefreshIconPhases() {
        refreshPhases.clear()
    }

    override suspend fun isPlatformTipDismissed(): Boolean = platformTipDismissed
    override suspend fun setPlatformTipDismissed(dismissed: Boolean) {
        platformTipDismissed = dismissed
    }

    override suspend fun isOemBackgroundTipDismissed(): Boolean = oemBackgroundTipDismissed
    override suspend fun setOemBackgroundTipDismissed(dismissed: Boolean) {
        oemBackgroundTipDismissed = dismissed
    }

    override suspend fun getUpdateIgnoredVersion(): String? = updateIgnoredVersion
    override suspend fun setUpdateIgnoredVersion(version: String?) {
        updateIgnoredVersion = version
    }

    override fun observeAppSettings(): Flow<AppSettings> = appSettingsFlow.asStateFlow()
    override suspend fun getAppSettings(): AppSettings = appSettingsFlow.value
    override suspend fun saveAppSettings(settings: AppSettings) {
        appSettingsFlow.value = settings
    }

    private fun widgetFlow(platformId: String): MutableStateFlow<WidgetDisplayState> =
        widgetFlows.getOrPut(platformId) { MutableStateFlow(WidgetDisplayState.NotConfigured) }
}
