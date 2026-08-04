package com.kuyermqi.quotawidget.ui.home

import android.app.Activity
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.res.Resources
import com.kuyermqi.quotawidget.R
import com.kuyermqi.quotawidget.domain.QuotaWindow
import com.kuyermqi.quotawidget.domain.UsageDisplayMode
import com.kuyermqi.quotawidget.domain.UsageProgressStyle
import com.kuyermqi.quotawidget.domain.UsageWindowKind
import com.kuyermqi.quotawidget.domain.WidgetDisplayState
import com.kuyermqi.quotawidget.domain.availableUsageWindowKinds
import com.kuyermqi.quotawidget.domain.codex.CodexLoginWebViewFlow
import com.kuyermqi.quotawidget.domain.defaultUsageWindowKind
import com.kuyermqi.quotawidget.platform.PlatformIds
import com.kuyermqi.quotawidget.settings.CodexSettings
import com.kuyermqi.quotawidget.settings.PlatformSettingsRepository
import com.kuyermqi.quotawidget.ui.components.CodexConfigContent
import com.kuyermqi.quotawidget.ui.usage.formatCodexUsageWindowSummary
import com.kuyermqi.quotawidget.widget.WidgetGlanceState
import kotlinx.coroutines.launch

class CodexHomeState internal constructor(
    private val settingsRepository: PlatformSettingsRepository,
) {
    var settings by mutableStateOf(CodexSettings())
        internal set
    var draftWindowKind by mutableStateOf(UsageWindowKind.WEEKLY)
        internal set
    var draftUsageDisplayMode by mutableStateOf(UsageDisplayMode.USED)
        internal set
    var draftUsageProgressStyle by mutableStateOf(UsageProgressStyle.BAR)
        internal set
    var lastDisplay by mutableStateOf<String?>(null)
        internal set
    var isBusy by mutableStateOf(false)
        internal set
    var error by mutableStateOf<String?>(null)
        internal set
    var loaded by mutableStateOf(false)
        internal set

    val isConfigured: Boolean
        get() = settings.isConfigured

    val isDirty: Boolean
        get() = loaded && settings.isConfigured && (
            draftWindowKind != settings.widgetWindowKind ||
                draftUsageDisplayMode != settings.usageDisplayMode ||
                draftUsageProgressStyle != settings.usageProgressStyle
            )

    fun applyDraft(next: CodexSettings = settings) {
        draftWindowKind = next.widgetWindowKind
        draftUsageDisplayMode = next.usageDisplayMode
        draftUsageProgressStyle = next.usageProgressStyle
    }

    fun applyLoaded(next: CodexSettings) {
        settings = next
        applyDraft(next)
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
            WidgetDisplayState.Loading -> lastDisplay ?: loadingMsg
            is WidgetDisplayState.Error -> lastDisplay
            WidgetDisplayState.NeedsReauth -> reauthMsg
            WidgetDisplayState.NotConfigured -> null
        }
    }

    internal fun repository(): PlatformSettingsRepository = settingsRepository
}

data class CodexHomeBindings(
    val widgetState: WidgetDisplayState,
    val windows: List<QuotaWindow>,
    val onLogin: () -> Unit,
)

@Composable
fun rememberCodexHomeState(
    settingsRepository: PlatformSettingsRepository,
): CodexHomeState = remember(settingsRepository) {
    CodexHomeState(settingsRepository)
}

/**
 * Widget observation, Codex summary sync, login launcher, and window-kind clamp.
 * Must be called from HomeScreen (not only when the platform row is expanded).
 */
@Composable
fun CodexHomeEffects(
    state: CodexHomeState,
    onRefreshPlatform: suspend (String) -> WidgetDisplayState,
): CodexHomeBindings {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val resources = LocalResources.current
    val msgLoginFailed = stringResource(R.string.codex_login_failed)

    val observed by state.repository().observeWidgetState(PlatformIds.CODEX)
        .collectAsStateWithLifecycle(initialValue = WidgetDisplayState.NotConfigured)
    val windows = (observed as? WidgetDisplayState.Success)?.snapshot?.windows.orEmpty()

    LaunchedEffect(
        observed,
        state.settings.widgetWindowKind,
        state.settings.usageDisplayMode,
        resources,
    ) {
        val success = observed as? WidgetDisplayState.Success ?: return@LaunchedEffect
        state.lastDisplay = formatCodexUsageWindowSummary(
            resources = resources,
            windows = success.snapshot.windows,
            windowKind = state.settings.widgetWindowKind,
            usageDisplayMode = state.settings.usageDisplayMode,
            fallback = success.snapshot.primaryDisplay,
        )
    }

    // Clamp Codex widget window when plan only exposes a subset (e.g. free → monthly only).
    LaunchedEffect(windows, state.settings.isConfigured) {
        if (!state.settings.isConfigured || windows.isEmpty()) return@LaunchedEffect
        val available = availableUsageWindowKinds(windows)
        if (available.isEmpty()) return@LaunchedEffect

        val settingsInvalid = state.settings.widgetWindowKind !in available
        val draftInvalid = state.draftWindowKind !in available
        if (!settingsInvalid && !draftInvalid) return@LaunchedEffect

        if (settingsInvalid) {
            val preferred = defaultUsageWindowKind(windows)
            val next = state.settings.copy(widgetWindowKind = preferred)
            state.repository().saveCodexSettings(next)
            state.settings = next
            if (draftInvalid) {
                state.draftWindowKind = preferred
            }
            WidgetGlanceState.syncAndUpdate(context, "codex_window_clamp")
        } else {
            // Settings valid; only draft is out of range (e.g. dirty pick no longer offered).
            state.draftWindowKind = state.settings.widgetWindowKind
        }
    }

    val loginLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            state.error = msgLoginFailed
            return@rememberLauncherForActivityResult
        }
        val access = result.data
            ?.getStringExtra(CodexLoginWebViewFlow.EXTRA_ACCESS_TOKEN)
            .orEmpty()
        val refresh = result.data
            ?.getStringExtra(CodexLoginWebViewFlow.EXTRA_REFRESH_TOKEN)
            .orEmpty()
        val accountId = result.data
            ?.getStringExtra(CodexLoginWebViewFlow.EXTRA_ACCOUNT_ID)
            .orEmpty()
        if (access.isBlank() || refresh.isBlank() || accountId.isBlank()) {
            state.error = msgLoginFailed
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            state.isBusy = true
            state.error = null
            try {
                val next = CodexSettings(
                    accessToken = access,
                    refreshToken = refresh,
                    idToken = result.data
                        ?.getStringExtra(CodexLoginWebViewFlow.EXTRA_ID_TOKEN)
                        .orEmpty(),
                    accountId = accountId,
                    expiresAtEpochMs = result.data
                        ?.getLongExtra(CodexLoginWebViewFlow.EXTRA_EXPIRES_AT, 0L)
                        ?: 0L,
                    email = result.data
                        ?.getStringExtra(CodexLoginWebViewFlow.EXTRA_EMAIL)
                        .orEmpty(),
                    planType = result.data
                        ?.getStringExtra(CodexLoginWebViewFlow.EXTRA_PLAN_TYPE)
                        .orEmpty(),
                    widgetWindowKind = state.draftWindowKind,
                    usageDisplayMode = state.draftUsageDisplayMode,
                    usageProgressStyle = state.draftUsageProgressStyle,
                )
                state.repository().saveCodexSettings(next)
                state.settings = next
                state.applyDraft(next)
                when (val refreshState = onRefreshPlatform(PlatformIds.CODEX)) {
                    is WidgetDisplayState.Success -> {
                        val preferred = defaultUsageWindowKind(refreshState.snapshot.windows)
                        val updated = next.copy(widgetWindowKind = preferred)
                        if (updated.widgetWindowKind != next.widgetWindowKind) {
                            state.repository().saveCodexSettings(updated)
                        }
                        state.settings = updated
                        state.applyDraft(updated)
                        state.error = null
                    }
                    is WidgetDisplayState.Error -> {
                        state.error = refreshState.message
                    }
                    WidgetDisplayState.NeedsReauth -> {
                        clearCodexSession(
                            state = state,
                            keepWindowKind = next.widgetWindowKind,
                            keepUsageDisplayMode = next.usageDisplayMode,
                            keepUsageProgressStyle = next.usageProgressStyle,
                        )
                        state.error = msgLoginFailed
                    }
                    else -> {
                        state.error = null
                    }
                }
                WidgetGlanceState.syncAndUpdate(context, "codex_login")
            } finally {
                state.isBusy = false
            }
        }
    }

    return CodexHomeBindings(
        widgetState = observed,
        windows = windows,
        onLogin = {
            state.error = null
            loginLauncher.launch(CodexLoginWebViewFlow.createIntent(context))
        },
    )
}

@Composable
fun ColumnScope.CodexHomeContent(
    state: CodexHomeState,
    bindings: CodexHomeBindings,
    onRefreshPlatform: suspend (String) -> WidgetDisplayState,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val msgNeedsReauth = stringResource(R.string.widget_needs_reauth)

    CodexConfigContent(
        isLoggedIn = state.settings.isConfigured &&
            bindings.widgetState !is WidgetDisplayState.NeedsReauth,
        isBusy = state.isBusy,
        isDirty = state.isDirty,
        errorMessage = state.error,
        email = state.settings.email,
        planType = state.settings.planType,
        windows = bindings.windows,
        widgetWindowKind = state.draftWindowKind,
        onWidgetWindowKindChange = { kind ->
            state.draftWindowKind = kind
            state.error = null
        },
        usageDisplayMode = state.draftUsageDisplayMode,
        onUsageDisplayModeChange = { mode ->
            state.draftUsageDisplayMode = mode
            state.error = null
        },
        usageProgressStyle = state.draftUsageProgressStyle,
        onUsageProgressStyleChange = { style ->
            state.draftUsageProgressStyle = style
            state.error = null
        },
        onLogin = bindings.onLogin,
        onLogout = {
            scope.launch {
                state.isBusy = true
                state.error = null
                try {
                    clearCodexSession(
                        state = state,
                        keepWindowKind = state.draftWindowKind,
                        keepUsageDisplayMode = state.draftUsageDisplayMode,
                        keepUsageProgressStyle = state.draftUsageProgressStyle,
                    )
                    WidgetGlanceState.syncAndUpdate(context, "codex_logout")
                } finally {
                    state.isBusy = false
                }
            }
        },
        onSave = {
            scope.launch {
                state.isBusy = true
                state.error = null
                try {
                    val next = state.settings.copy(
                        widgetWindowKind = state.draftWindowKind,
                        usageDisplayMode = state.draftUsageDisplayMode,
                        usageProgressStyle = state.draftUsageProgressStyle,
                    )
                    state.repository().saveCodexSettings(next)
                    state.settings = next
                    state.applyDraft(next)
                    when (val result = onRefreshPlatform(PlatformIds.CODEX)) {
                        is WidgetDisplayState.Error -> {
                            state.error = result.message
                        }
                        WidgetDisplayState.NeedsReauth -> {
                            clearCodexSession(
                                state = state,
                                keepWindowKind = next.widgetWindowKind,
                                keepUsageDisplayMode = next.usageDisplayMode,
                                keepUsageProgressStyle = next.usageProgressStyle,
                            )
                            state.error = msgNeedsReauth
                        }
                        else -> {
                            state.error = null
                        }
                    }
                    WidgetGlanceState.syncAndUpdate(context, "codex_save")
                } finally {
                    state.isBusy = false
                }
            }
        },
    )
}

private suspend fun clearCodexSession(
    state: CodexHomeState,
    keepWindowKind: UsageWindowKind,
    keepUsageDisplayMode: UsageDisplayMode,
    keepUsageProgressStyle: UsageProgressStyle,
) {
    state.repository().clearCodexSettings()
    state.settings = CodexSettings(
        widgetWindowKind = keepWindowKind,
        usageDisplayMode = keepUsageDisplayMode,
        usageProgressStyle = keepUsageProgressStyle,
    )
    state.applyDraft()
    state.lastDisplay = null
}
