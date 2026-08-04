package com.kuyermqi.quotawidget.ui

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
import com.kuyermqi.quotawidget.R
import com.kuyermqi.quotawidget.domain.WidgetDisplayState
import com.kuyermqi.quotawidget.platform.PlatformIds
import com.kuyermqi.quotawidget.platform.PlatformRegistry
import com.kuyermqi.quotawidget.settings.PlatformSettingsRepository
import com.kuyermqi.quotawidget.ui.components.PlatformConfigItem
import com.kuyermqi.quotawidget.ui.components.TipBanner
import com.kuyermqi.quotawidget.ui.components.isIgnoringBatteryOptimizations
import com.kuyermqi.quotawidget.ui.components.requestIgnoreBatteryOptimizations
import com.kuyermqi.quotawidget.ui.home.CodexHomeContent
import com.kuyermqi.quotawidget.ui.home.CodexHomeEffects
import com.kuyermqi.quotawidget.ui.home.DeepSeekHomeContent
import com.kuyermqi.quotawidget.ui.home.DeepSeekHomeEffects
import com.kuyermqi.quotawidget.ui.home.NewApiHomeContent
import com.kuyermqi.quotawidget.ui.home.NewApiHomeEffects
import com.kuyermqi.quotawidget.ui.home.OpenCodeGoHomeContent
import com.kuyermqi.quotawidget.ui.home.OpenCodeGoHomeEffects
import com.kuyermqi.quotawidget.ui.home.rememberCodexHomeState
import com.kuyermqi.quotawidget.ui.home.rememberDeepSeekHomeState
import com.kuyermqi.quotawidget.ui.home.rememberNewApiHomeState
import com.kuyermqi.quotawidget.ui.home.rememberOpenCodeGoHomeState
import com.kuyermqi.quotawidget.webview.InAppWebViewActivity
import com.kuyermqi.quotawidget.widget.WidgetGlanceState
import kotlinx.coroutines.launch

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
    val msgNeedsReauth = stringResource(R.string.widget_needs_reauth)
    val msgLoadingBalance = stringResource(R.string.widget_loading_balance)
    val msgOpenCodeWorkspaceMissing = stringResource(R.string.opencode_workspace_missing)
    val msgOpenCodeWorkspacesLoadFailed = stringResource(R.string.opencode_workspaces_load_failed)

    var showBatteryOptimizationTip by remember {
        mutableStateOf(!context.isIgnoringBatteryOptimizations())
    }
    var expandedPlatformId by remember { mutableStateOf<String?>(null) }
    var highlightPlatformId by remember { mutableStateOf<String?>(null) }
    var highlightNonce by remember { mutableStateOf<Long?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }

    val deepSeek = rememberDeepSeekHomeState(settingsRepository)
    val openCode = rememberOpenCodeGoHomeState(settingsRepository)
    val codex = rememberCodexHomeState(settingsRepository)
    val newApi = rememberNewApiHomeState(settingsRepository)

    val deepSeekWidgetState = DeepSeekHomeEffects(deepSeek)
    val openCodeBindings = OpenCodeGoHomeEffects(openCode, onRefreshPlatform)
    val codexBindings = CodexHomeEffects(codex, onRefreshPlatform)
    val newApiWidgetState = NewApiHomeEffects(newApi)

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

    fun summaryLabelFor(platformId: String): String? = when (platformId) {
        PlatformIds.DEEPSEEK -> deepSeek.summaryLabel(
            resources = resources,
            widgetState = deepSeekWidgetState,
            loadingMsg = msgLoadingBalance,
            reauthMsg = msgNeedsReauth,
        )
        PlatformIds.OPENCODE_GO -> openCode.summaryLabel(
            resources = resources,
            widgetState = openCodeBindings.widgetState,
            loadingMsg = msgLoadingBalance,
            reauthMsg = msgNeedsReauth,
        )
        PlatformIds.CODEX -> codex.summaryLabel(
            resources = resources,
            widgetState = codexBindings.widgetState,
            loadingMsg = msgLoadingBalance,
            reauthMsg = msgNeedsReauth,
        )
        PlatformIds.NEW_API -> newApi.summaryLabel(
            resources = resources,
            widgetState = newApiWidgetState,
            loadingMsg = msgLoadingBalance,
            reauthMsg = msgNeedsReauth,
        )
        else -> null
    }

    suspend fun refreshAll(showPullIndicator: Boolean = false) {
        val anyConfigured =
            deepSeek.isConfigured || openCode.isConfigured ||
                codex.isConfigured || newApi.isConfigured
        if (!anyConfigured) return
        if (showPullIndicator) isRefreshing = true
        try {
            onRefreshAllConfigured()
            if (openCode.isConfigured) {
                openCode.reloadWorkspacesForHomeRefresh(
                    msgWorkspaceMissing = msgOpenCodeWorkspaceMissing,
                    msgWorkspacesLoadFailed = msgOpenCodeWorkspacesLoadFailed,
                )
            }
            WidgetGlanceState.syncAndUpdate(context, "home_refresh")
        } finally {
            if (showPullIndicator) isRefreshing = false
        }
    }

    LaunchedEffect(Unit) {
        deepSeek.applyLoaded(settingsRepository.getDeepSeekSettings())
        openCode.applyLoaded(settingsRepository.getOpenCodeGoSettings())
        codex.applyLoaded(settingsRepository.getCodexSettings())
        newApi.applyLoaded(settingsRepository.getNewApiSettings())
        if (deepSeek.isConfigured || openCode.isConfigured ||
            codex.isConfigured || newApi.isConfigured
        ) {
            refreshAll(showPullIndicator = false)
        }
    }

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
                                                title = "Don‘t Kill My App",
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
                            PlatformIds.DEEPSEEK -> DeepSeekHomeContent(
                                state = deepSeek,
                                onRefreshPlatform = onRefreshPlatform,
                            )
                            PlatformIds.OPENCODE_GO -> OpenCodeGoHomeContent(
                                state = openCode,
                                bindings = openCodeBindings,
                                onRefreshPlatform = onRefreshPlatform,
                            )
                            PlatformIds.CODEX -> CodexHomeContent(
                                state = codex,
                                bindings = codexBindings,
                                onRefreshPlatform = onRefreshPlatform,
                            )
                            PlatformIds.NEW_API -> NewApiHomeContent(
                                state = newApi,
                                onRefreshPlatform = onRefreshPlatform,
                            )
                        }
                    }
                }
            }
        }
    }
}
