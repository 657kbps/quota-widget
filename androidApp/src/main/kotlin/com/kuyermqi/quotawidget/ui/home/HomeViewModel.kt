package com.kuyermqi.quotawidget.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kuyermqi.quotawidget.domain.WidgetDisplayState
import com.kuyermqi.quotawidget.opencode.OpenCodeGoClient
import com.kuyermqi.quotawidget.platform.PlatformIds
import com.kuyermqi.quotawidget.platform.PlatformRegistry
import com.kuyermqi.quotawidget.settings.PlatformSettingsRepository
import com.kuyermqi.quotawidget.util.OneShotGate
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Owns the home edit state for the whole Activity lifetime: a configuration change (rotation,
 * dark/light switch, locale recreate) reuses the same drafts and the first balance refresh runs
 * once per view model instead of once per composition.
 */
class HomeViewModel(
    private val settingsRepository: PlatformSettingsRepository,
    openCodeClient: OpenCodeGoClient,
) : ViewModel() {
    val deepSeek = DeepSeekHomeState(settingsRepository)
    val openCode = OpenCodeGoHomeState(settingsRepository, openCodeClient)
    val codex = CodexHomeState(settingsRepository)
    val newApi = NewApiHomeState(settingsRepository)
    val magpie = MagpieHomeState(settingsRepository)
    val kimiCode = KimiCodeHomeState(settingsRepository)
    val zhipu = ZhipuHomeState(settingsRepository)
    val workBuddy = SessionPlatformHomeState(settingsRepository, PlatformIds.WORKBUDDY)
    val workBuddyAi = SessionPlatformHomeState(settingsRepository, PlatformIds.WORKBUDDY_AI)
    val cline = SessionPlatformHomeState(settingsRepository, PlatformIds.CLINE)
    val commandCode = SessionPlatformHomeState(settingsRepository, PlatformIds.COMMAND_CODE)
    val antigravity = SessionPlatformHomeState(settingsRepository, PlatformIds.ANTIGRAVITY)

    private val sessionStates = listOf(workBuddy, workBuddyAi, cline, commandCode, antigravity)
    private val initialLoadGate = OneShotGate()

    init {
        sessionStates.forEach { state ->
            viewModelScope.launch {
                settingsRepository.observeSessionSettings(state.platformId)
                    .collectLatest { state.applyPersisted(it) }
            }
            viewModelScope.launch {
                settingsRepository.observeWidgetState(state.platformId)
                    .collectLatest { observed ->
                        state.widgetState = observed
                        if (observed is WidgetDisplayState.Success) {
                            state.error = null
                        }
                    }
            }
        }
    }

    /**
     * Loads the persisted settings once per view model and answers whether the caller should run
     * the first balance refresh; later calls return false.
     */
    suspend fun ensureInitialized(): Boolean {
        var configured = false
        initialLoadGate.runOnce {
            deepSeek.applyLoaded(settingsRepository.getDeepSeekSettings())
            openCode.applyLoaded(settingsRepository.getOpenCodeGoSettings())
            codex.applyLoaded(settingsRepository.getCodexSettings())
            newApi.applyLoaded(settingsRepository.getNewApiSettings())
            magpie.applyLoaded(settingsRepository.getMagpieSettings())
            kimiCode.applyLoaded(settingsRepository.getKimiCodeSettings())
            zhipu.applyLoaded(settingsRepository.getZhipuSettings())
            workBuddy.applyPersisted(settingsRepository.getSessionSettings(PlatformIds.WORKBUDDY))
            workBuddyAi.applyPersisted(
                settingsRepository.getSessionSettings(PlatformIds.WORKBUDDY_AI),
            )
            cline.applyPersisted(settingsRepository.getSessionSettings(PlatformIds.CLINE))
            commandCode.applyPersisted(
                settingsRepository.getSessionSettings(PlatformIds.COMMAND_CODE),
            )
            antigravity.applyPersisted(
                settingsRepository.getSessionSettings(PlatformIds.ANTIGRAVITY),
            )
            configured = PlatformRegistry.platforms.any { isConfigured(it.id) }
        }
        return configured
    }

    fun isConfigured(platformId: String): Boolean = when (platformId) {
        PlatformIds.DEEPSEEK -> deepSeek.isConfigured
        PlatformIds.OPENCODE_GO -> openCode.isConfigured
        PlatformIds.CODEX -> codex.isConfigured
        PlatformIds.NEW_API -> newApi.isConfigured
        PlatformIds.MAGPIE -> magpie.isConfigured
        PlatformIds.KIMI_CODE -> kimiCode.isConfigured
        PlatformIds.ZHIPU -> zhipu.isConfigured
        PlatformIds.WORKBUDDY -> workBuddy.isConfigured
        PlatformIds.WORKBUDDY_AI -> workBuddyAi.isConfigured
        PlatformIds.CLINE -> cline.isConfigured
        PlatformIds.COMMAND_CODE -> commandCode.isConfigured
        PlatformIds.ANTIGRAVITY -> antigravity.isConfigured
        else -> false
    }

    fun discardDrafts(platformId: String) {
        when (platformId) {
            PlatformIds.DEEPSEEK -> deepSeek.discardDrafts()
            PlatformIds.OPENCODE_GO -> openCode.discardDrafts()
            PlatformIds.CODEX -> codex.discardDrafts()
            PlatformIds.NEW_API -> newApi.discardDrafts()
            PlatformIds.MAGPIE -> magpie.discardDrafts()
            PlatformIds.KIMI_CODE -> kimiCode.discardDrafts()
            PlatformIds.ZHIPU -> zhipu.discardDrafts()
            PlatformIds.WORKBUDDY -> workBuddy.discardDrafts()
            PlatformIds.WORKBUDDY_AI -> workBuddyAi.discardDrafts()
            PlatformIds.CLINE -> cline.discardDrafts()
            PlatformIds.COMMAND_CODE -> commandCode.discardDrafts()
            PlatformIds.ANTIGRAVITY -> antigravity.discardDrafts()
        }
    }
}
