package com.kuyermqi.quotawidget.ui.home

import android.app.Activity
import android.webkit.CookieManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.kuyermqi.quotawidget.domain.UsageWindowKind
import com.kuyermqi.quotawidget.domain.WidgetDisplayState
import com.kuyermqi.quotawidget.domain.opencode.OpenCodeLoginWebViewFlow
import com.kuyermqi.quotawidget.opencode.OpenCodeGoClient
import com.kuyermqi.quotawidget.opencode.OpenCodeWorkspace
import com.kuyermqi.quotawidget.platform.PlatformIds
import com.kuyermqi.quotawidget.settings.OpenCodeGoSettings
import com.kuyermqi.quotawidget.settings.PlatformSettingsRepository
import com.kuyermqi.quotawidget.ui.components.OpenCodeGoConfigContent
import com.kuyermqi.quotawidget.ui.usage.formatUsageWindowSummary
import com.kuyermqi.quotawidget.widget.WidgetGlanceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OpenCodeGoHomeState internal constructor(
    private val settingsRepository: PlatformSettingsRepository,
    internal val client: OpenCodeGoClient,
) {
    var settings by mutableStateOf(OpenCodeGoSettings())
        internal set
    var draftWorkspaceId by mutableStateOf("")
        internal set
    var draftWorkspaceName by mutableStateOf("")
        internal set
    var draftWindowKind by mutableStateOf(UsageWindowKind.ROLLING)
        internal set
    var draftUsageDisplayMode by mutableStateOf(UsageDisplayMode.USED)
        internal set
    var lastDisplay by mutableStateOf<String?>(null)
        internal set
    var isBusy by mutableStateOf(false)
        internal set
    var error by mutableStateOf<String?>(null)
        internal set
    var workspaces by mutableStateOf<List<OpenCodeWorkspace>>(emptyList())
        internal set
    var isLoadingWorkspaces by mutableStateOf(false)
        internal set
    var workspacesError by mutableStateOf<String?>(null)
        internal set
    var loaded by mutableStateOf(false)
        internal set

    val isConfigured: Boolean
        get() = settings.isConfigured

    val isDirty: Boolean
        get() = loaded && settings.isConfigured && (
            draftWorkspaceId != settings.workspaceId ||
                draftWorkspaceName != settings.workspaceName ||
                draftWindowKind != settings.widgetWindowKind ||
                draftUsageDisplayMode != settings.usageDisplayMode
            )

    fun applyDraft(next: OpenCodeGoSettings = settings) {
        draftWorkspaceId = next.workspaceId
        draftWorkspaceName = next.workspaceName
        draftWindowKind = next.widgetWindowKind
        draftUsageDisplayMode = next.usageDisplayMode
    }

    fun applyLoaded(next: OpenCodeGoSettings) {
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
            is WidgetDisplayState.Success -> formatUsageWindowSummary(
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

    suspend fun reloadWorkspacesForHomeRefresh(
        msgWorkspaceMissing: String,
        msgWorkspacesLoadFailed: String,
    ) {
        if (!isConfigured) return
        loadOpenCodeWorkspaces(
            state = this,
            client = client,
            settings = settings,
            persistMissingSelection = false,
            msgWorkspaceMissing = msgWorkspaceMissing,
            msgWorkspacesLoadFailed = msgWorkspacesLoadFailed,
        )
    }
}

data class OpenCodeGoHomeBindings(
    val widgetState: WidgetDisplayState,
    val windows: List<QuotaWindow>,
    val onLogin: () -> Unit,
)

@Composable
fun rememberOpenCodeGoHomeState(
    settingsRepository: PlatformSettingsRepository,
): OpenCodeGoHomeState {
    val client = remember { OpenCodeGoClient() }
    val state = remember(settingsRepository, client) {
        OpenCodeGoHomeState(settingsRepository, client)
    }
    DisposableEffect(client) {
        onDispose { client.close() }
    }
    return state
}

/**
 * Widget observation, summary sync, and login launcher.
 * Must be called from HomeScreen (not only when the platform row is expanded).
 */
@Composable
fun OpenCodeGoHomeEffects(
    state: OpenCodeGoHomeState,
    onRefreshPlatform: suspend (String) -> WidgetDisplayState,
): OpenCodeGoHomeBindings {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val resources = LocalResources.current
    val msgLoginFailed = stringResource(R.string.opencode_login_failed)
    val msgWorkspaceMissing = stringResource(R.string.opencode_workspace_missing)
    val msgWorkspacesLoadFailed = stringResource(R.string.opencode_workspaces_load_failed)

    val observed by state.repository().observeWidgetState(PlatformIds.OPENCODE_GO)
        .collectAsStateWithLifecycle(initialValue = WidgetDisplayState.NotConfigured)
    val windows = (observed as? WidgetDisplayState.Success)?.snapshot?.windows.orEmpty()

    LaunchedEffect(
        observed,
        state.settings.widgetWindowKind,
        state.settings.usageDisplayMode,
        resources,
    ) {
        val success = observed as? WidgetDisplayState.Success ?: return@LaunchedEffect
        state.lastDisplay = formatUsageWindowSummary(
            resources = resources,
            windows = success.snapshot.windows,
            windowKind = state.settings.widgetWindowKind,
            usageDisplayMode = state.settings.usageDisplayMode,
            fallback = success.snapshot.primaryDisplay,
        )
    }

    val loginLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            state.error = msgLoginFailed
            return@rememberLauncherForActivityResult
        }
        val workspaceId = result.data
            ?.getStringExtra(OpenCodeLoginWebViewFlow.EXTRA_RESULT_WORKSPACE_ID)
            .orEmpty()
        val authCookie = result.data
            ?.getStringExtra(OpenCodeLoginWebViewFlow.EXTRA_RESULT_AUTH_COOKIE)
            .orEmpty()
        if (workspaceId.isBlank() || authCookie.isBlank()) {
            state.error = msgLoginFailed
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            state.isBusy = true
            state.error = null
            try {
                var next = OpenCodeGoSettings(
                    workspaceId = workspaceId,
                    authCookie = authCookie,
                    widgetWindowKind = state.draftWindowKind,
                    usageDisplayMode = state.draftUsageDisplayMode,
                )
                state.repository().saveOpenCodeGoSettings(next)
                state.settings = next
                state.applyDraft(next)
                next = loadOpenCodeWorkspaces(
                    state = state,
                    client = state.client,
                    settings = next,
                    persistMissingSelection = true,
                    msgWorkspaceMissing = msgWorkspaceMissing,
                    msgWorkspacesLoadFailed = msgWorkspacesLoadFailed,
                )
                state.applyDraft(next)
                when (val refresh = onRefreshPlatform(PlatformIds.OPENCODE_GO)) {
                    is WidgetDisplayState.Success -> {
                        state.error = null
                    }
                    is WidgetDisplayState.Error -> {
                        state.error = refresh.message
                    }
                    WidgetDisplayState.NeedsReauth -> {
                        clearOpenCodeSession(
                            state = state,
                            keepWindowKind = next.widgetWindowKind,
                            keepUsageDisplayMode = next.usageDisplayMode,
                        )
                        state.error = msgLoginFailed
                    }
                    else -> {
                        state.error = null
                    }
                }
                WidgetGlanceState.syncAndUpdate(context, "opencode_login")
            } finally {
                state.isBusy = false
            }
        }
    }

    return OpenCodeGoHomeBindings(
        widgetState = observed,
        windows = windows,
        onLogin = {
            state.error = null
            loginLauncher.launch(OpenCodeLoginWebViewFlow.createIntent(context))
        },
    )
}

@Composable
fun ColumnScope.OpenCodeGoHomeContent(
    state: OpenCodeGoHomeState,
    bindings: OpenCodeGoHomeBindings,
    onRefreshPlatform: suspend (String) -> WidgetDisplayState,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val msgNeedsReauth = stringResource(R.string.widget_needs_reauth)

    OpenCodeGoConfigContent(
        isLoggedIn = state.settings.isConfigured &&
            bindings.widgetState !is WidgetDisplayState.NeedsReauth,
        isBusy = state.isBusy,
        isDirty = state.isDirty,
        errorMessage = state.error,
        windows = bindings.windows,
        workspaces = state.workspaces,
        selectedWorkspaceId = state.draftWorkspaceId,
        selectedWorkspaceName = state.draftWorkspaceName,
        isLoadingWorkspaces = state.isLoadingWorkspaces,
        workspacesError = state.workspacesError,
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
        onWorkspaceSelected = { workspace ->
            state.draftWorkspaceId = workspace.id
            state.draftWorkspaceName = workspace.name
            state.error = null
        },
        onLogin = bindings.onLogin,
        onLogout = {
            scope.launch {
                state.isBusy = true
                state.error = null
                try {
                    clearOpenCodeSession(
                        state = state,
                        keepWindowKind = state.draftWindowKind,
                        keepUsageDisplayMode = state.draftUsageDisplayMode,
                    )
                    WidgetGlanceState.syncAndUpdate(context, "opencode_logout")
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
                        workspaceId = state.draftWorkspaceId,
                        workspaceName = state.draftWorkspaceName,
                        widgetWindowKind = state.draftWindowKind,
                        usageDisplayMode = state.draftUsageDisplayMode,
                    )
                    state.repository().saveOpenCodeGoSettings(next)
                    state.settings = next
                    state.applyDraft(next)
                    when (val result = onRefreshPlatform(PlatformIds.OPENCODE_GO)) {
                        is WidgetDisplayState.Error -> {
                            state.error = result.message
                        }
                        WidgetDisplayState.NeedsReauth -> {
                            clearOpenCodeSession(
                                state = state,
                                keepWindowKind = next.widgetWindowKind,
                                keepUsageDisplayMode = next.usageDisplayMode,
                            )
                            state.error = msgNeedsReauth
                        }
                        else -> {
                            state.error = null
                            state.workspacesError = null
                        }
                    }
                    WidgetGlanceState.syncAndUpdate(context, "opencode_save")
                } finally {
                    state.isBusy = false
                }
            }
        },
    )
}

private suspend fun clearOpenCodeSession(
    state: OpenCodeGoHomeState,
    keepWindowKind: UsageWindowKind,
    keepUsageDisplayMode: UsageDisplayMode,
) {
    state.repository().clearOpenCodeGoSettings()
    state.settings = OpenCodeGoSettings(
        widgetWindowKind = keepWindowKind,
        usageDisplayMode = keepUsageDisplayMode,
    )
    state.applyDraft()
    state.workspaces = emptyList()
    state.workspacesError = null
    CookieManager.getInstance().removeAllCookies(null)
    CookieManager.getInstance().flush()
    state.lastDisplay = null
}

/**
 * Fetches workspace list. Does not persist picker changes except when
 * [persistMissingSelection] is true (login bootstrap): if the saved id is absent,
 * the first workspace is written so quota refresh has a valid target.
 * On normal refresh, a missing saved id only updates the draft (marks dirty).
 */
private suspend fun loadOpenCodeWorkspaces(
    state: OpenCodeGoHomeState,
    client: OpenCodeGoClient,
    settings: OpenCodeGoSettings,
    persistMissingSelection: Boolean,
    msgWorkspaceMissing: String,
    msgWorkspacesLoadFailed: String,
): OpenCodeGoSettings {
    if (!settings.isConfigured) {
        state.workspaces = emptyList()
        state.workspacesError = null
        return settings
    }
    state.isLoadingWorkspaces = true
    state.workspacesError = null
    return try {
        val list = withContext(Dispatchers.IO) {
            client.listWorkspaces(settings.authCookie)
        }
        state.workspaces = list
        if (list.isEmpty()) return settings
        val matched = list.find { it.id == settings.workspaceId }
        if (matched != null) {
            return settings
        }
        val first = list.first()
        if (persistMissingSelection) {
            val next = settings.copy(
                workspaceId = first.id,
                workspaceName = first.name,
            )
            state.repository().saveOpenCodeGoSettings(next)
            state.settings = next
            state.applyDraft(next)
            next
        } else {
            val draftStillOnSaved = state.draftWorkspaceId == settings.workspaceId
            val draftMissing = list.none { it.id == state.draftWorkspaceId }
            if (draftStillOnSaved || draftMissing) {
                state.draftWorkspaceId = first.id
                state.draftWorkspaceName = first.name
            }
            state.workspacesError = msgWorkspaceMissing
            settings
        }
    } catch (e: Exception) {
        android.util.Log.w(
            "OpenCodeGo",
            "listWorkspaces failed: ${e.message}",
            e,
        )
        state.workspacesError = msgWorkspacesLoadFailed
        settings
    } finally {
        state.isLoadingWorkspaces = false
    }
}
