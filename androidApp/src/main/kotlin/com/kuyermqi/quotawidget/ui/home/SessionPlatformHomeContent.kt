package com.kuyermqi.quotawidget.ui.home

import android.app.Activity
import android.content.res.Resources
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.kuyermqi.quotawidget.R
import com.kuyermqi.quotawidget.antigravity.AntigravityClient
import com.kuyermqi.quotawidget.cline.ClineClient
import com.kuyermqi.quotawidget.domain.QuotaWindow
import com.kuyermqi.quotawidget.domain.QuotaWindowKind
import com.kuyermqi.quotawidget.domain.UsageDisplayMode
import com.kuyermqi.quotawidget.domain.UsageProgressStyle
import com.kuyermqi.quotawidget.domain.UsageResetStyle
import com.kuyermqi.quotawidget.domain.UsageWindowKind
import com.kuyermqi.quotawidget.domain.WidgetDisplayState
import com.kuyermqi.quotawidget.domain.availableUsageWindowKinds
import com.kuyermqi.quotawidget.domain.antigravity.AntigravityLoginWebViewFlow
import com.kuyermqi.quotawidget.domain.cline.ClineLoginWebViewFlow
import com.kuyermqi.quotawidget.domain.commandcode.CommandCodeLoginWebViewFlow
import com.kuyermqi.quotawidget.domain.session.SessionLoginResult
import com.kuyermqi.quotawidget.domain.workbuddy.WorkBuddyLoginWebViewFlow
import com.kuyermqi.quotawidget.platform.PlatformIds
import com.kuyermqi.quotawidget.settings.PlatformSettingsRepository
import com.kuyermqi.quotawidget.settings.SessionPlatformSettings
import com.kuyermqi.quotawidget.ui.components.SessionAuthPanel
import com.kuyermqi.quotawidget.ui.components.SessionPlatformConfigContent
import com.kuyermqi.quotawidget.ui.platformDisplayName
import com.kuyermqi.quotawidget.ui.usage.formatCodexUsageWindowSummary
import com.kuyermqi.quotawidget.widget.WidgetGlanceState
import com.kuyermqi.quotawidget.workbuddy.WorkBuddyClient
import com.kuyermqi.quotawidget.workbuddy.WorkBuddyRegion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SessionPlatformHomeState internal constructor(
    private val settingsRepository: PlatformSettingsRepository,
    val platformId: String,
) {
    var settings by mutableStateOf(
        SessionPlatformSettings(
            widgetWindowKind = SessionPlatformSettings.defaultWindowKind(platformId),
        ),
    )
        internal set
    var draftApiKey by mutableStateOf("")
        internal set
    var authPanel by mutableStateOf(
        if (platformId == PlatformIds.CLINE || platformId == PlatformIds.COMMAND_CODE) {
            SessionAuthPanel.CHOOSE
        } else {
            SessionAuthPanel.SUMMARY
        },
    )
        internal set
    var draftWindowKind by mutableStateOf(
        SessionPlatformSettings.defaultWindowKind(platformId),
    )
        internal set
    var draftUsageDisplayMode by mutableStateOf(UsageDisplayMode.USED)
        internal set
    var draftUsageProgressStyle by mutableStateOf(UsageProgressStyle.BAR)
        internal set
    var draftUsageResetStyle by mutableStateOf(UsageResetStyle.HIDDEN)
        internal set
    var lastDisplay by mutableStateOf<String?>(null)
        internal set
    var widgetState by mutableStateOf<WidgetDisplayState>(WidgetDisplayState.NotConfigured)
        internal set
    var isBusy by mutableStateOf(false)
        internal set
    var error by mutableStateOf<String?>(null)
        internal set
    var loaded by mutableStateOf(false)
        internal set

    val supportsApiKey: Boolean
        get() = platformId == PlatformIds.COMMAND_CODE || platformId == PlatformIds.CLINE

    val isConfigured: Boolean
        get() = settings.isConfiguredFor(platformId)

    val isDirty: Boolean
        get() {
            if (!loaded) return false
            val keyChanged = supportsApiKey &&
                draftApiKey.isNotBlank() && draftApiKey != settings.apiKey
            val prefsChanged = isConfigured && (
                draftWindowKind != settings.widgetWindowKind ||
                    draftUsageDisplayMode != settings.usageDisplayMode ||
                    draftUsageProgressStyle != settings.usageProgressStyle ||
                    draftUsageResetStyle != settings.usageResetStyle
                )
            return keyChanged || prefsChanged
        }

    fun applyDraft(next: SessionPlatformSettings = settings) {
        draftApiKey = next.apiKey
        draftWindowKind = next.widgetWindowKind
        draftUsageDisplayMode = next.usageDisplayMode
        draftUsageProgressStyle = next.usageProgressStyle
        draftUsageResetStyle = next.usageResetStyle
    }

    fun applyPersisted(next: SessionPlatformSettings) {
        val hadChanges = isDirty
        val wasLoaded = loaded
        val wasConfigured = settings.isConfiguredFor(platformId)
        val isNowConfigured = next.isConfiguredFor(platformId)
        settings = next
        if (!hadChanges) applyDraft(next)
        if (!wasLoaded || isNowConfigured != wasConfigured) {
            authPanel = if (!supportsApiKey || isNowConfigured) {
                SessionAuthPanel.SUMMARY
            } else {
                SessionAuthPanel.CHOOSE
            }
        }
        loaded = true
    }

    fun summaryLabel(
        resources: Resources,
        widgetState: WidgetDisplayState,
        loadingMsg: String,
        reauthMsg: String,
    ): String? {
        if (!isConfigured) return null
        return when (widgetState) {
            is WidgetDisplayState.Success -> formatCodexUsageWindowSummary(
                resources = resources,
                windows = widgetState.snapshot.windows,
                windowKind = settings.widgetWindowKind,
                usageDisplayMode = settings.usageDisplayMode,
                fallback = widgetState.snapshot.primaryDisplay,
            )
            WidgetDisplayState.Loading -> loadingMsg
            WidgetDisplayState.NeedsReauth -> reauthMsg
            is WidgetDisplayState.Error -> widgetState.message
            WidgetDisplayState.NotConfigured -> null
        }
    }

    fun applyLoginResult(result: SessionLoginResult): SessionPlatformSettings {
        var next = result.applyTo(settings)
        if (platformId == PlatformIds.CLINE) {
            // Cline uses the API key when present; clear the other auth method
            // and its account metadata when switching between WorkOS login and
            // API key login.
            next = if (result.apiKey.isNotBlank()) {
                next.copy(
                    accessToken = "",
                    refreshToken = "",
                    expiresAtEpochMs = 0L,
                    accountId = "",
                    email = "",
                    plan = "",
                    projectId = "",
                    uid = "",
                    enterpriseId = "",
                    domain = "",
                )
            } else {
                next.copy(apiKey = "")
            }
        }
        settings = next
        applyDraft(next)
        if (supportsApiKey && next.isConfiguredFor(platformId)) {
            authPanel = SessionAuthPanel.SUMMARY
        }
        error = null
        return next
    }

    suspend fun savePrefs(settingsRepository: PlatformSettingsRepository): SessionPlatformSettings {
        val clearAccountMetadata =
            platformId == PlatformIds.CLINE || platformId == PlatformIds.COMMAND_CODE
        val clearSessionTokens = platformId == PlatformIds.CLINE
        val withKey = if (supportsApiKey && draftApiKey.isNotBlank() &&
            draftApiKey != settings.apiKey
        ) {
            settings.copy(
                apiKey = draftApiKey,
                accessToken = if (clearSessionTokens) "" else settings.accessToken,
                refreshToken = if (clearSessionTokens) "" else settings.refreshToken,
                expiresAtEpochMs = if (clearSessionTokens) 0L else settings.expiresAtEpochMs,
                accountId = if (clearAccountMetadata) "" else settings.accountId,
                email = if (clearAccountMetadata) "" else settings.email,
                plan = if (clearAccountMetadata) "" else settings.plan,
                projectId = if (clearAccountMetadata) "" else settings.projectId,
                uid = if (clearAccountMetadata) "" else settings.uid,
                enterpriseId = if (clearAccountMetadata) "" else settings.enterpriseId,
                domain = if (clearAccountMetadata) "" else settings.domain,
            )
        } else {
            settings
        }
        val next = withKey.copy(
            widgetWindowKind = draftWindowKind,
            usageDisplayMode = draftUsageDisplayMode,
            usageProgressStyle = draftUsageProgressStyle,
            usageResetStyle = draftUsageResetStyle,
        )
        settings = next
        applyDraft(next)
        settingsRepository.saveSessionSettings(platformId, next)
        return next
    }
}

data class SessionPlatformHomeBindings(
    val onLogin: () -> Unit,
    val onLogout: () -> Unit,
    val onSave: () -> Unit,
)

@Composable
fun rememberSessionPlatformHomeState(
    settingsRepository: PlatformSettingsRepository,
    platformId: String,
): SessionPlatformHomeState {
    val state = remember(platformId) {
        SessionPlatformHomeState(settingsRepository, platformId)
    }
    LaunchedEffect(platformId) {
        settingsRepository.observeSessionSettings(platformId).collectLatest { persisted ->
            state.applyPersisted(persisted)
        }
    }
    LaunchedEffect(platformId) {
        settingsRepository.observeWidgetState(platformId).collectLatest { widgetState ->
            state.widgetState = widgetState
            if (widgetState is WidgetDisplayState.Success) {
                state.error = null
            }
        }
    }
    return state
}

@Composable
fun sessionPlatformHomeEffects(
    state: SessionPlatformHomeState,
    settingsRepository: PlatformSettingsRepository,
    onRefreshPlatform: suspend (String) -> WidgetDisplayState,
): SessionPlatformHomeBindings {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val loginFailed = stringResource(R.string.session_login_failed)
    val reauthMessage = stringResource(R.string.session_reauth_hint)
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        scope.launch {
            state.isBusy = false
            if (result.resultCode != Activity.RESULT_OK) {
                state.error = loginFailed
                return@launch
            }
            val login = SessionLoginResult.fromIntent(result.data)
            if (login == null) {
                state.error = loginFailed
                return@launch
            }
            try {
                val next = state.applyLoginResult(login)
                settingsRepository.saveSessionSettings(state.platformId, next)
                settingsRepository.saveWidgetLoading(state.platformId)
                val refreshed = withContext(Dispatchers.IO) {
                    onRefreshPlatform(state.platformId)
                }
                handleRefreshResult(state, settingsRepository, refreshed, reauthMessage)
                WidgetGlanceState.syncAndUpdate(context, "session_login")
            } catch (e: Exception) {
                state.error = e.message ?: loginFailed
            }
        }
    }

    val onLogin: () -> Unit = {
        scope.launch {
            state.isBusy = true
            state.error = null
            try {
                val intent = when (state.platformId) {
                    PlatformIds.WORKBUDDY, PlatformIds.WORKBUDDY_AI -> {
                        val region = if (state.platformId == PlatformIds.WORKBUDDY) {
                            WorkBuddyRegion.CHINA
                        } else {
                            WorkBuddyRegion.INTERNATIONAL
                        }
                        val client = WorkBuddyClient()
                        try {
                            val session = withContext(Dispatchers.IO) {
                                client.createLoginSession(region)
                            }
                            WorkBuddyLoginWebViewFlow.createIntent(context, region, session)
                        } finally {
                            client.close()
                        }
                    }
                    PlatformIds.CLINE -> {
                        val client = ClineClient()
                        try {
                            val session = withContext(Dispatchers.IO) {
                                client.createDeviceSession()
                            }
                            ClineLoginWebViewFlow.createIntent(context, session)
                        } finally {
                            client.close()
                        }
                    }
                    PlatformIds.COMMAND_CODE -> withContext(Dispatchers.IO) {
                        CommandCodeLoginWebViewFlow.createIntent(context)
                    }
                    PlatformIds.ANTIGRAVITY -> AntigravityLoginWebViewFlow.createIntent(context)
                    else -> error("未知平台: ${state.platformId}")
                }
                launcher.launch(intent)
            } catch (e: Exception) {
                state.isBusy = false
                state.error = e.message ?: loginFailed
            }
        }
    }

    val onLogout: () -> Unit = {
        scope.launch {
            state.isBusy = true
            try {
                settingsRepository.clearSessionSettings(state.platformId)
                state.applyPersisted(settingsRepository.getSessionSettings(state.platformId))
                state.applyDraft()
                if (state.supportsApiKey) {
                    state.authPanel = SessionAuthPanel.CHOOSE
                }
                state.lastDisplay = null
                state.error = null
                WidgetGlanceState.syncAndUpdate(context, "session_logout")
            } catch (e: Exception) {
                state.error = e.message
            } finally {
                state.isBusy = false
            }
        }
    }

    val onSave: () -> Unit = {
        scope.launch {
            state.isBusy = true
            try {
                state.error = null
                state.savePrefs(settingsRepository)
                settingsRepository.saveWidgetLoading(state.platformId)
                val refreshed = withContext(Dispatchers.IO) {
                    onRefreshPlatform(state.platformId)
                }
                handleRefreshResult(state, settingsRepository, refreshed, reauthMessage)
                if (state.supportsApiKey && state.isConfigured && state.error == null) {
                    state.authPanel = SessionAuthPanel.SUMMARY
                }
                WidgetGlanceState.syncAndUpdate(context, "session_save")
            } catch (e: Exception) {
                state.error = e.message
            } finally {
                state.isBusy = false
            }
        }
    }

    return SessionPlatformHomeBindings(
        onLogin = onLogin,
        onLogout = onLogout,
        onSave = onSave,
    )
}

private suspend fun handleRefreshResult(
    state: SessionPlatformHomeState,
    settingsRepository: PlatformSettingsRepository,
    refreshed: WidgetDisplayState,
    reauthMessage: String,
) {
    when (refreshed) {
        is WidgetDisplayState.Success -> {
            state.lastDisplay = refreshed.snapshot.primaryDisplay
            val available = availableUsageWindowKinds(refreshed.snapshot.windows)
            if (available.isNotEmpty() && state.draftWindowKind !in available) {
                state.draftWindowKind = available.first()
                val next = state.settings.copy(widgetWindowKind = state.draftWindowKind)
                state.settings = next
                settingsRepository.saveSessionSettings(state.platformId, next)
            }
            state.error = null
        }
        WidgetDisplayState.NeedsReauth -> {
            state.error = reauthMessage
        }
        is WidgetDisplayState.Error -> {
            state.error = refreshed.message
        }
        else -> Unit
    }
}

@Composable
fun ColumnScope.SessionPlatformHomeContent(
    state: SessionPlatformHomeState,
    bindings: SessionPlatformHomeBindings,
    widgetState: WidgetDisplayState,
) {
    val windows: List<QuotaWindow> =
        (widgetState as? WidgetDisplayState.Success)?.snapshot?.windows.orEmpty()
    val overviewKinds = when (state.platformId) {
        PlatformIds.WORKBUDDY, PlatformIds.WORKBUDDY_AI -> listOf(QuotaWindowKind.MONTHLY)
        PlatformIds.ANTIGRAVITY -> listOf(QuotaWindowKind.FIVE_HOUR, QuotaWindowKind.WEEKLY)
        else -> listOf(
            QuotaWindowKind.FIVE_HOUR,
            QuotaWindowKind.WEEKLY,
            QuotaWindowKind.MONTHLY,
        )
    }
    SessionPlatformConfigContent(
        settings = state.settings,
        supportsApiKey = state.supportsApiKey,
        authPanel = state.authPanel,
        draftApiKey = state.draftApiKey,
        onDraftApiKeyChange = { state.draftApiKey = it },
        onAuthPanelChange = { panel ->
            if (state.authPanel == SessionAuthPanel.API_KEY && panel != SessionAuthPanel.API_KEY) {
                state.draftApiKey = state.settings.apiKey
            }
            state.authPanel = panel
            state.error = null
        },
        windows = windows,
        widgetWindowKind = state.draftWindowKind,
        onWidgetWindowKindChange = { state.draftWindowKind = it },
        usageDisplayMode = state.draftUsageDisplayMode,
        onUsageDisplayModeChange = { state.draftUsageDisplayMode = it },
        usageProgressStyle = state.draftUsageProgressStyle,
        onUsageProgressStyleChange = { state.draftUsageProgressStyle = it },
        usageResetStyle = state.draftUsageResetStyle,
        onUsageResetStyleChange = { state.draftUsageResetStyle = it },
        overviewKinds = overviewKinds,
        notice = if (state.platformId == PlatformIds.ANTIGRAVITY) {
            stringResource(R.string.session_antigravity_notice)
        } else {
            null
        },
        isConfigured = state.isConfigured,
        isBusy = state.isBusy,
        isDirty = state.isDirty,
        error = state.error,
        onLogin = bindings.onLogin,
        onLogout = bindings.onLogout,
        onSave = bindings.onSave,
        platformName = platformDisplayName(state.platformId),
        updatedAtEpochMs =
            (widgetState as? WidgetDisplayState.Success)?.snapshot?.updatedAtEpochMs ?: 0L,
    )
}
