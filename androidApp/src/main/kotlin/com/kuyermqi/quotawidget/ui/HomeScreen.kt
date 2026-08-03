package com.kuyermqi.quotawidget.ui

import android.app.Activity
import android.webkit.CookieManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.res.Resources
import com.kuyermqi.quotawidget.R
import com.kuyermqi.quotawidget.domain.CurrencyPreference
import com.kuyermqi.quotawidget.domain.OpenCodeWidgetWindowKind
import com.kuyermqi.quotawidget.domain.QuotaWindow
import com.kuyermqi.quotawidget.domain.WidgetDisplayState
import com.kuyermqi.quotawidget.domain.formatOpenCodeRemainingForWindow
import com.kuyermqi.quotawidget.domain.opencode.OpenCodeLoginWebViewFlow
import com.kuyermqi.quotawidget.opencode.OpenCodeGoClient
import com.kuyermqi.quotawidget.opencode.OpenCodeWorkspace
import com.kuyermqi.quotawidget.platform.PlatformIds
import com.kuyermqi.quotawidget.platform.PlatformRegistry
import com.kuyermqi.quotawidget.settings.DeepSeekSettings
import com.kuyermqi.quotawidget.settings.OpenCodeGoSettings
import com.kuyermqi.quotawidget.settings.PlatformSettingsRepository
import com.kuyermqi.quotawidget.ui.components.DeepSeekConfigContent
import com.kuyermqi.quotawidget.ui.components.OpenCodeGoConfigContent
import com.kuyermqi.quotawidget.ui.components.PlatformConfigItem
import com.kuyermqi.quotawidget.ui.components.TipBanner
import com.kuyermqi.quotawidget.ui.components.isIgnoringBatteryOptimizations
import com.kuyermqi.quotawidget.ui.components.requestIgnoreBatteryOptimizations
import com.kuyermqi.quotawidget.webview.InAppWebViewActivity
import com.kuyermqi.quotawidget.widget.WidgetGlanceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class FocusPlatformRequest(
    val platformId: String,
    val nonce: Long,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    settingsRepository: PlatformSettingsRepository,
    onRefreshPlatform: suspend (String) -> WidgetDisplayState,
    onRefreshAllConfigured: suspend () -> Unit,
    onOpenAppSettings: () -> Unit,
    showPlatformTip: Boolean,
    showOemBackgroundTip: Boolean,
    tipLoaded: Boolean,
    onDismissPlatformTip: () -> Unit,
    onDismissOemBackgroundTip: () -> Unit,
    focusPlatformRequest: FocusPlatformRequest? = null,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val resources = LocalResources.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val listState = rememberLazyListState()
    val msgLoginFailed = stringResource(R.string.opencode_login_failed)
    val msgWorkspacesLoadFailed = stringResource(R.string.opencode_workspaces_load_failed)
    val msgWorkspaceMissing = stringResource(R.string.opencode_workspace_missing)
    val msgNeedsReauth = stringResource(R.string.widget_needs_reauth)
    val msgLoadingBalance = stringResource(R.string.widget_loading_balance)
    var showBatteryOptimizationTip by remember {
        mutableStateOf(!context.isIgnoringBatteryOptimizations())
    }
    var savedDeepSeek by remember { mutableStateOf(DeepSeekSettings()) }
    var draftApiKey by remember { mutableStateOf("") }
    var draftCurrency by remember { mutableStateOf(CurrencyPreference.CNY) }
    var openCodeSettings by remember { mutableStateOf(OpenCodeGoSettings()) }
    var draftOpenCodeWorkspaceId by remember { mutableStateOf("") }
    var draftOpenCodeWorkspaceName by remember { mutableStateOf("") }
    var draftOpenCodeWindowKind by remember {
        mutableStateOf(OpenCodeWidgetWindowKind.ROLLING)
    }
    var expandedPlatformId by remember { mutableStateOf<String?>(null) }
    var highlightPlatformId by remember { mutableStateOf<String?>(null) }
    var highlightNonce by remember { mutableStateOf<Long?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var isOpenCodeBusy by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var openCodeError by remember { mutableStateOf<String?>(null) }
    var opencodeWorkspaces by remember { mutableStateOf<List<OpenCodeWorkspace>>(emptyList()) }
    var isLoadingWorkspaces by remember { mutableStateOf(false) }
    var workspacesError by remember { mutableStateOf<String?>(null) }
    var lastDeepSeekDisplay by remember { mutableStateOf<String?>(null) }
    var lastOpenCodeDisplay by remember { mutableStateOf<String?>(null) }
    val openCodeClient = remember { OpenCodeGoClient() }
    DisposableEffect(openCodeClient) {
        onDispose { openCodeClient.close() }
    }

    fun applyOpenCodeDraft(settings: OpenCodeGoSettings) {
        draftOpenCodeWorkspaceId = settings.workspaceId
        draftOpenCodeWorkspaceName = settings.workspaceName
        draftOpenCodeWindowKind = settings.widgetWindowKind
    }

    suspend fun clearOpenCodeSession(keepWindowKind: OpenCodeWidgetWindowKind) {
        settingsRepository.clearOpenCodeGoSettings()
        openCodeSettings = OpenCodeGoSettings(widgetWindowKind = keepWindowKind)
        applyOpenCodeDraft(openCodeSettings)
        opencodeWorkspaces = emptyList()
        workspacesError = null
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        lastOpenCodeDisplay = null
    }

    /**
     * Fetches workspace list. Does not persist picker changes except when
     * [persistMissingSelection] is true (login bootstrap): if the saved id is absent,
     * the first workspace is written so quota refresh has a valid target.
     * On normal refresh, a missing saved id only updates the draft (marks dirty).
     */
    suspend fun loadOpenCodeWorkspaces(
        settings: OpenCodeGoSettings = openCodeSettings,
        persistMissingSelection: Boolean = false,
    ): OpenCodeGoSettings {
        if (!settings.isConfigured) {
            opencodeWorkspaces = emptyList()
            workspacesError = null
            return settings
        }
        isLoadingWorkspaces = true
        workspacesError = null
        return try {
            val list = withContext(Dispatchers.IO) {
                openCodeClient.listWorkspaces(settings.authCookie)
            }
            opencodeWorkspaces = list
            if (list.isEmpty()) return settings
            val matched = list.find { it.id == settings.workspaceId }
            if (matched != null) {
                // Display name comes from the live list; do not auto-persist name.
                return settings
            }
            val first = list.first()
            if (persistMissingSelection) {
                val next = settings.copy(
                    workspaceId = first.id,
                    workspaceName = first.name,
                )
                settingsRepository.saveOpenCodeGoSettings(next)
                openCodeSettings = next
                applyOpenCodeDraft(next)
                next
            } else {
                val draftStillOnSaved = draftOpenCodeWorkspaceId == settings.workspaceId
                val draftMissing = list.none { it.id == draftOpenCodeWorkspaceId }
                if (draftStillOnSaved || draftMissing) {
                    draftOpenCodeWorkspaceId = first.id
                    draftOpenCodeWorkspaceName = first.name
                }
                workspacesError = msgWorkspaceMissing
                settings
            }
        } catch (e: Exception) {
            android.util.Log.w(
                "OpenCodeGo",
                "listWorkspaces failed: ${e.message}",
                e,
            )
            workspacesError = msgWorkspacesLoadFailed
            settings
        } finally {
            isLoadingWorkspaces = false
        }
    }

    val deepSeekWidgetState by settingsRepository.observeWidgetState(PlatformIds.DEEPSEEK)
        .collectAsStateWithLifecycle(initialValue = WidgetDisplayState.NotConfigured)
    val openCodeWidgetState by settingsRepository.observeWidgetState(PlatformIds.OPENCODE_GO)
        .collectAsStateWithLifecycle(initialValue = WidgetDisplayState.NotConfigured)

    val openCodeLoginLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            openCodeError = msgLoginFailed
            return@rememberLauncherForActivityResult
        }
        val workspaceId = result.data
            ?.getStringExtra(OpenCodeLoginWebViewFlow.EXTRA_RESULT_WORKSPACE_ID)
            .orEmpty()
        val authCookie = result.data
            ?.getStringExtra(OpenCodeLoginWebViewFlow.EXTRA_RESULT_AUTH_COOKIE)
            .orEmpty()
        if (workspaceId.isBlank() || authCookie.isBlank()) {
            openCodeError = msgLoginFailed
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            isOpenCodeBusy = true
            openCodeError = null
            try {
                var next = OpenCodeGoSettings(
                    workspaceId = workspaceId,
                    authCookie = authCookie,
                    widgetWindowKind = draftOpenCodeWindowKind,
                )
                settingsRepository.saveOpenCodeGoSettings(next)
                openCodeSettings = next
                applyOpenCodeDraft(next)
                next = loadOpenCodeWorkspaces(next, persistMissingSelection = true)
                applyOpenCodeDraft(next)
                when (val refresh = onRefreshPlatform(PlatformIds.OPENCODE_GO)) {
                    is WidgetDisplayState.Success -> {
                        openCodeError = null
                    }
                    is WidgetDisplayState.Error -> {
                        // Keep credentials only when fetch failed for a non-auth reason.
                        openCodeError = refresh.message
                    }
                    WidgetDisplayState.NeedsReauth -> {
                        // Cookie was captured but session is still public/invalid — don't leave
                        // a contradictory "已登录" + "需登录" state.
                        clearOpenCodeSession(keepWindowKind = next.widgetWindowKind)
                        openCodeError = msgLoginFailed
                    }
                    else -> {
                        openCodeError = null
                    }
                }
                WidgetGlanceState.syncAndUpdate(context, "opencode_login")
            } finally {
                isOpenCodeBusy = false
            }
        }
    }

    LaunchedEffect(deepSeekWidgetState) {
        val success = deepSeekWidgetState as? WidgetDisplayState.Success ?: return@LaunchedEffect
        lastDeepSeekDisplay = success.snapshot.primaryDisplay
    }
    LaunchedEffect(openCodeWidgetState, openCodeSettings.widgetWindowKind, resources) {
        val success = openCodeWidgetState as? WidgetDisplayState.Success ?: return@LaunchedEffect
        lastOpenCodeDisplay = formatOpenCodeRemainingSummary(
            resources = resources,
            windows = success.snapshot.windows,
            windowKind = openCodeSettings.widgetWindowKind,
            fallback = success.snapshot.primaryDisplay,
        )
    }

    val hasVisibleTips = tipLoaded && (
        showBatteryOptimizationTip || showOemBackgroundTip || showPlatformTip
        )

    LaunchedEffect(focusPlatformRequest, tipLoaded, hasVisibleTips) {
        val request = focusPlatformRequest ?: return@LaunchedEffect
        if (!tipLoaded) return@LaunchedEffect
        val platformIndex = PlatformRegistry.platforms.indexOfFirst { it.id == request.platformId }
        if (platformIndex < 0) return@LaunchedEffect

        expandedPlatformId = request.platformId
        highlightPlatformId = request.platformId
        highlightNonce = request.nonce

        val tipItemOffset = if (hasVisibleTips) 1 else 0
        listState.animateScrollToItem(tipItemOffset + platformIndex)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                showBatteryOptimizationTip = !context.isIgnoringBatteryOptimizations()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun summaryLabelFor(platformId: String): String? {
        val configured = when (platformId) {
            PlatformIds.DEEPSEEK -> savedDeepSeek.apiKey.isNotBlank()
            PlatformIds.OPENCODE_GO -> openCodeSettings.isConfigured
            else -> false
        }
        if (!configured) return null
        val state = when (platformId) {
            PlatformIds.DEEPSEEK -> deepSeekWidgetState
            PlatformIds.OPENCODE_GO -> openCodeWidgetState
            else -> return null
        }
        fun deepSeekBalanceLabel(amount: String): String =
            resources.getString(R.string.deepseek_balance_summary, amount)
        return when (state) {
            is WidgetDisplayState.Success -> when (platformId) {
                PlatformIds.OPENCODE_GO ->
                    formatOpenCodeRemainingSummary(
                        resources = resources,
                        windows = state.snapshot.windows,
                        windowKind = openCodeSettings.widgetWindowKind,
                        fallback = state.snapshot.primaryDisplay,
                    )
                PlatformIds.DEEPSEEK -> deepSeekBalanceLabel(state.snapshot.primaryDisplay)
                else -> state.snapshot.primaryDisplay
            }
            WidgetDisplayState.Loading -> when (platformId) {
                PlatformIds.DEEPSEEK -> lastDeepSeekDisplay?.let(::deepSeekBalanceLabel)
                    ?: msgLoadingBalance
                PlatformIds.OPENCODE_GO -> lastOpenCodeDisplay ?: msgLoadingBalance
                else -> null
            }
            is WidgetDisplayState.Error -> when (platformId) {
                PlatformIds.DEEPSEEK -> lastDeepSeekDisplay?.let(::deepSeekBalanceLabel)
                PlatformIds.OPENCODE_GO -> lastOpenCodeDisplay
                else -> null
            }
            WidgetDisplayState.NeedsReauth -> msgNeedsReauth
            WidgetDisplayState.NotConfigured -> null
        }
    }

    suspend fun refreshAll(showPullIndicator: Boolean = false) {
        val anyConfigured =
            savedDeepSeek.apiKey.isNotBlank() || openCodeSettings.isConfigured
        if (!anyConfigured) return
        if (showPullIndicator) isRefreshing = true
        try {
            onRefreshAllConfigured()
            if (openCodeSettings.isConfigured) {
                // List only — do not persist workspace changes; user must Save.
                loadOpenCodeWorkspaces(persistMissingSelection = false)
            }
            WidgetGlanceState.syncAndUpdate(context, "home_refresh")
        } finally {
            if (showPullIndicator) isRefreshing = false
        }
    }

    LaunchedEffect(Unit) {
        val deepSeek = settingsRepository.getDeepSeekSettings()
        savedDeepSeek = deepSeek
        draftApiKey = deepSeek.apiKey
        draftCurrency = deepSeek.currency
        openCodeSettings = settingsRepository.getOpenCodeGoSettings()
        applyOpenCodeDraft(openCodeSettings)
        loaded = true
        if (deepSeek.apiKey.isNotBlank() || openCodeSettings.isConfigured) {
            refreshAll(showPullIndicator = false)
        }
    }

    val isDirty = loaded && (
        draftApiKey != savedDeepSeek.apiKey || draftCurrency != savedDeepSeek.currency
        )
    val isOpenCodeDirty = loaded && openCodeSettings.isConfigured && (
        draftOpenCodeWorkspaceId != openCodeSettings.workspaceId ||
            draftOpenCodeWorkspaceName != openCodeSettings.workspaceName ||
            draftOpenCodeWindowKind != openCodeSettings.widgetWindowKind
        )

    val openCodeWindows =
        (openCodeWidgetState as? WidgetDisplayState.Success)?.snapshot?.windows.orEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_title)) },
                actions = {
                    IconButton(onClick = onOpenAppSettings) {
                        Icon(
                            painter = painterResource(R.drawable.ic_settings),
                            contentDescription = stringResource(R.string.app_settings_open),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { scope.launch { refreshAll(showPullIndicator = true) } },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .imePadding(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (hasVisibleTips) {
                    item(key = "tips") {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (showBatteryOptimizationTip) {
                                TipBanner(
                                    visible = true,
                                    iconRes = R.drawable.ic_battery,
                                    message = stringResource(R.string.battery_optimization_tip),
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                    onCardClick = { context.requestIgnoreBatteryOptimizations() },
                                    actionText = stringResource(R.string.battery_optimization_tip_action),
                                    onActionClick = { context.requestIgnoreBatteryOptimizations() },
                                )
                            }
                            if (showOemBackgroundTip) {
                                val oemGuideUrl = stringResource(R.string.oem_background_tip_url)
                                val oemGuideTitle = stringResource(R.string.oem_background_tip_link)
                                TipBanner(
                                    visible = true,
                                    iconRes = R.drawable.ic_internet,
                                    message = stringResource(R.string.oem_background_tip),
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    onDismiss = {
                                        onDismissOemBackgroundTip()
                                        scope.launch {
                                            settingsRepository.setOemBackgroundTipDismissed(true)
                                        }
                                    },
                                    linkText = oemGuideTitle,
                                    onLinkClick = {
                                        context.startActivity(
                                            InAppWebViewActivity.createIntent(
                                                context = context,
                                                url = oemGuideUrl,
                                                title = "DontKillMyApp",
                                            ),
                                        )
                                    },
                                )
                            }
                            if (showPlatformTip) {
                                TipBanner(
                                    visible = true,
                                    iconRes = R.drawable.ic_tip_lightbulb,
                                    message = stringResource(R.string.platform_tip),
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    onDismiss = {
                                        onDismissPlatformTip()
                                        scope.launch {
                                            settingsRepository.setPlatformTipDismissed(true)
                                        }
                                    },
                                )
                            }
                        }
                    }
                }

                items(PlatformRegistry.platforms, key = { it.id }) { platform ->
                    PlatformConfigItem(
                        title = platform.displayName,
                        expanded = expandedPlatformId == platform.id,
                        onToggle = {
                            expandedPlatformId =
                                if (expandedPlatformId == platform.id) null else platform.id
                        },
                        balanceText = summaryLabelFor(platform.id),
                        highlightNonce = highlightNonce.takeIf { highlightPlatformId == platform.id },
                        onHighlightFinished = {
                            if (highlightPlatformId == platform.id) {
                                highlightPlatformId = null
                                highlightNonce = null
                            }
                        },
                    ) {
                        when (platform.id) {
                            PlatformIds.DEEPSEEK -> DeepSeekConfigContent(
                                apiKey = draftApiKey,
                                onApiKeyChange = {
                                    draftApiKey = it
                                    saveError = null
                                },
                                currency = draftCurrency,
                                onCurrencyChange = {
                                    draftCurrency = it
                                    saveError = null
                                },
                                isDirty = isDirty,
                                isSaving = isSaving,
                                saveError = saveError,
                                onSave = {
                                    scope.launch {
                                        val next = DeepSeekSettings(
                                            apiKey = draftApiKey.trim(),
                                            currency = draftCurrency,
                                        )
                                        isSaving = true
                                        saveError = null
                                        try {
                                            settingsRepository.saveDeepSeekSettings(next)
                                            savedDeepSeek = next
                                            draftApiKey = next.apiKey
                                            if (next.apiKey.isBlank()) {
                                                lastDeepSeekDisplay = null
                                            }
                                            saveError = when (
                                                val result = onRefreshPlatform(PlatformIds.DEEPSEEK)
                                            ) {
                                                is WidgetDisplayState.Error -> result.message
                                                else -> null
                                            }
                                            WidgetGlanceState.syncAndUpdate(context, "deepseek_save")
                                        } finally {
                                            isSaving = false
                                        }
                                    }
                                },
                            )
                            PlatformIds.OPENCODE_GO -> OpenCodeGoConfigContent(
                                isLoggedIn = openCodeSettings.isConfigured &&
                                    openCodeWidgetState !is WidgetDisplayState.NeedsReauth,
                                isBusy = isOpenCodeBusy,
                                isDirty = isOpenCodeDirty,
                                errorMessage = openCodeError,
                                windows = openCodeWindows,
                                workspaces = opencodeWorkspaces,
                                selectedWorkspaceId = draftOpenCodeWorkspaceId,
                                selectedWorkspaceName = draftOpenCodeWorkspaceName,
                                isLoadingWorkspaces = isLoadingWorkspaces,
                                workspacesError = workspacesError,
                                widgetWindowKind = draftOpenCodeWindowKind,
                                onWidgetWindowKindChange = { kind ->
                                    draftOpenCodeWindowKind = kind
                                    openCodeError = null
                                },
                                onWorkspaceSelected = { workspace ->
                                    draftOpenCodeWorkspaceId = workspace.id
                                    draftOpenCodeWorkspaceName = workspace.name
                                    openCodeError = null
                                },
                                onLogin = {
                                    openCodeError = null
                                    openCodeLoginLauncher.launch(
                                        OpenCodeLoginWebViewFlow.createIntent(context),
                                    )
                                },
                                onLogout = {
                                    scope.launch {
                                        isOpenCodeBusy = true
                                        openCodeError = null
                                        try {
                                            clearOpenCodeSession(
                                                keepWindowKind = draftOpenCodeWindowKind,
                                            )
                                            WidgetGlanceState.syncAndUpdate(
                                                context,
                                                "opencode_logout",
                                            )
                                        } finally {
                                            isOpenCodeBusy = false
                                        }
                                    }
                                },
                                onSave = {
                                    scope.launch {
                                        isOpenCodeBusy = true
                                        openCodeError = null
                                        try {
                                            val next = openCodeSettings.copy(
                                                workspaceId = draftOpenCodeWorkspaceId,
                                                workspaceName = draftOpenCodeWorkspaceName,
                                                widgetWindowKind = draftOpenCodeWindowKind,
                                            )
                                            settingsRepository.saveOpenCodeGoSettings(next)
                                            openCodeSettings = next
                                            applyOpenCodeDraft(next)
                                            when (
                                                val result =
                                                    onRefreshPlatform(PlatformIds.OPENCODE_GO)
                                            ) {
                                                is WidgetDisplayState.Error -> {
                                                    openCodeError = result.message
                                                }
                                                WidgetDisplayState.NeedsReauth -> {
                                                    clearOpenCodeSession(
                                                        keepWindowKind = next.widgetWindowKind,
                                                    )
                                                    openCodeError = msgNeedsReauth
                                                }
                                                else -> {
                                                    openCodeError = null
                                                    workspacesError = null
                                                }
                                            }
                                            WidgetGlanceState.syncAndUpdate(
                                                context,
                                                "opencode_save",
                                            )
                                        } finally {
                                            isOpenCodeBusy = false
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatOpenCodeRemainingSummary(
    resources: Resources,
    windows: List<QuotaWindow>,
    windowKind: OpenCodeWidgetWindowKind,
    fallback: String,
): String {
    val percent = formatOpenCodeRemainingForWindow(windows, windowKind) ?: return fallback
    val resId = when (windowKind) {
        OpenCodeWidgetWindowKind.ROLLING -> R.string.opencode_remaining_rolling
        OpenCodeWidgetWindowKind.WEEKLY -> R.string.opencode_remaining_weekly
        OpenCodeWidgetWindowKind.MONTHLY -> R.string.opencode_remaining_monthly
    }
    return resources.getString(resId, percent)
}
