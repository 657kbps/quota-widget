package com.kuyermqi.quotawidget.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
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
import com.kuyermqi.quotawidget.ui.components.TipBanner
import com.kuyermqi.quotawidget.ui.components.isIgnoringBatteryOptimizations
import com.kuyermqi.quotawidget.ui.components.providerLogo
import com.kuyermqi.quotawidget.ui.components.requestIgnoreBatteryOptimizations
import com.kuyermqi.quotawidget.ui.home.CodexHomeContent
import com.kuyermqi.quotawidget.ui.home.codexHomeEffects
import com.kuyermqi.quotawidget.ui.home.DeepSeekHomeContent
import com.kuyermqi.quotawidget.ui.home.deepSeekHomeEffects
import com.kuyermqi.quotawidget.ui.home.KimiCodeHomeContent
import com.kuyermqi.quotawidget.ui.home.kimiCodeHomeEffects
import com.kuyermqi.quotawidget.ui.home.MagpieHomeContent
import com.kuyermqi.quotawidget.ui.home.magpieHomeEffects
import com.kuyermqi.quotawidget.ui.home.NewApiHomeContent
import com.kuyermqi.quotawidget.ui.home.newApiHomeEffects
import com.kuyermqi.quotawidget.ui.home.OpenCodeGoHomeContent
import com.kuyermqi.quotawidget.ui.home.openCodeGoHomeEffects
import com.kuyermqi.quotawidget.ui.home.SessionPlatformHomeContent
import com.kuyermqi.quotawidget.ui.home.sessionPlatformHomeEffects
import com.kuyermqi.quotawidget.ui.home.ZhipuHomeContent
import com.kuyermqi.quotawidget.ui.home.zhipuHomeEffects
import com.kuyermqi.quotawidget.ui.home.rememberCodexHomeState
import com.kuyermqi.quotawidget.ui.home.rememberDeepSeekHomeState
import com.kuyermqi.quotawidget.ui.home.rememberKimiCodeHomeState
import com.kuyermqi.quotawidget.ui.home.rememberMagpieHomeState
import com.kuyermqi.quotawidget.ui.home.rememberNewApiHomeState
import com.kuyermqi.quotawidget.ui.home.rememberOpenCodeGoHomeState
import com.kuyermqi.quotawidget.ui.home.rememberSessionPlatformHomeState
import com.kuyermqi.quotawidget.ui.home.rememberZhipuHomeState
import com.kuyermqi.quotawidget.webview.InAppWebViewActivity
import com.kuyermqi.quotawidget.widget.WidgetGlanceState
import kotlinx.coroutines.launch

data class FocusPlatformRequest(
    val platformId: String,
    val nonce: Long,
)

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
    val msgNeedsReauth = stringResource(R.string.widget_needs_reauth)
    val msgLoadingBalance = stringResource(R.string.widget_loading_balance)
    val msgOpenCodeWorkspaceMissing = stringResource(R.string.opencode_workspace_missing)
    val msgOpenCodeWorkspacesLoadFailed = stringResource(R.string.opencode_workspaces_load_failed)

    var showBatteryOptimizationTip by remember {
        mutableStateOf(!context.isIgnoringBatteryOptimizations())
    }
    var isRefreshing by remember { mutableStateOf(false) }
    var activePlatformId by rememberSaveable {
        mutableStateOf(
            focusPlatformRequest?.platformId
                ?.takeIf { PlatformRegistry.find(it) != null }
                .orEmpty(),
        )
    }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var unconfiguredExpanded by rememberSaveable { mutableStateOf(false) }
    val providerListState = rememberLazyListState()

    val deepSeek = rememberDeepSeekHomeState(settingsRepository)
    val openCode = rememberOpenCodeGoHomeState(settingsRepository)
    val codex = rememberCodexHomeState(settingsRepository)
    val newApi = rememberNewApiHomeState(settingsRepository)
    val magpie = rememberMagpieHomeState(settingsRepository)
    val kimiCode = rememberKimiCodeHomeState(settingsRepository)
    val zhipu = rememberZhipuHomeState(settingsRepository)
    val workBuddy = rememberSessionPlatformHomeState(settingsRepository, PlatformIds.WORKBUDDY)
    val workBuddyAi = rememberSessionPlatformHomeState(settingsRepository, PlatformIds.WORKBUDDY_AI)
    val cline = rememberSessionPlatformHomeState(settingsRepository, PlatformIds.CLINE)
    val commandCode = rememberSessionPlatformHomeState(settingsRepository, PlatformIds.COMMAND_CODE)
    val antigravity = rememberSessionPlatformHomeState(settingsRepository, PlatformIds.ANTIGRAVITY)

    val deepSeekWidgetState = deepSeekHomeEffects(deepSeek)
    val openCodeBindings = openCodeGoHomeEffects(openCode, onRefreshPlatform)
    val codexBindings = codexHomeEffects(codex, onRefreshPlatform)
    val newApiWidgetState = newApiHomeEffects(newApi)
    val magpieWidgetState = magpieHomeEffects(magpie)
    val kimiCodeWidgetState = kimiCodeHomeEffects(kimiCode)
    val zhipuWidgetState = zhipuHomeEffects(zhipu)
    val workBuddyBindings = sessionPlatformHomeEffects(workBuddy, settingsRepository, onRefreshPlatform)
    val workBuddyAiBindings =
        sessionPlatformHomeEffects(workBuddyAi, settingsRepository, onRefreshPlatform)
    val clineBindings = sessionPlatformHomeEffects(cline, settingsRepository, onRefreshPlatform)
    val commandCodeBindings =
        sessionPlatformHomeEffects(commandCode, settingsRepository, onRefreshPlatform)
    val antigravityBindings =
        sessionPlatformHomeEffects(antigravity, settingsRepository, onRefreshPlatform)

    val hasVisibleTips = tipLoaded && (
        showBatteryOptimizationTip || showOemBackgroundTip || showPlatformTip
        )

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

    LaunchedEffect(activePlatformId) {
        if (activePlatformId.isNotEmpty() &&
            PlatformRegistry.find(activePlatformId) == null
        ) {
            activePlatformId = ""
        }
    }

    LaunchedEffect(focusPlatformRequest) {
        val request = focusPlatformRequest ?: return@LaunchedEffect
        if (PlatformRegistry.find(request.platformId) == null) return@LaunchedEffect
        if (activePlatformId.isNotEmpty() && activePlatformId != request.platformId) {
            discardDrafts(activePlatformId)
        }
        activePlatformId = request.platformId
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

    fun isConfiguredFor(platformId: String): Boolean = when (platformId) {
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
        PlatformIds.MAGPIE -> magpie.summaryLabel(
            resources = resources,
            widgetState = magpieWidgetState,
            loadingMsg = msgLoadingBalance,
            reauthMsg = msgNeedsReauth,
        )
        PlatformIds.KIMI_CODE -> kimiCode.summaryLabel(
            resources = resources,
            widgetState = kimiCodeWidgetState,
            loadingMsg = msgLoadingBalance,
            reauthMsg = msgNeedsReauth,
        )
        PlatformIds.ZHIPU -> zhipu.summaryLabel(
            resources = resources,
            widgetState = zhipuWidgetState,
            loadingMsg = msgLoadingBalance,
            reauthMsg = msgNeedsReauth,
        )
        PlatformIds.WORKBUDDY -> workBuddy.summaryLabel(
            resources = resources,
            widgetState = workBuddy.widgetState,
            loadingMsg = msgLoadingBalance,
            reauthMsg = msgNeedsReauth,
        )
        PlatformIds.WORKBUDDY_AI -> workBuddyAi.summaryLabel(
            resources = resources,
            widgetState = workBuddyAi.widgetState,
            loadingMsg = msgLoadingBalance,
            reauthMsg = msgNeedsReauth,
        )
        PlatformIds.CLINE -> cline.summaryLabel(
            resources = resources,
            widgetState = cline.widgetState,
            loadingMsg = msgLoadingBalance,
            reauthMsg = msgNeedsReauth,
        )
        PlatformIds.COMMAND_CODE -> commandCode.summaryLabel(
            resources = resources,
            widgetState = commandCode.widgetState,
            loadingMsg = msgLoadingBalance,
            reauthMsg = msgNeedsReauth,
        )
        PlatformIds.ANTIGRAVITY -> antigravity.summaryLabel(
            resources = resources,
            widgetState = antigravity.widgetState,
            loadingMsg = msgLoadingBalance,
            reauthMsg = msgNeedsReauth,
        )
        else -> null
    }

    suspend fun refreshAll(showPullIndicator: Boolean = false) {
        val anyConfigured = PlatformRegistry.platforms.any { isConfiguredFor(it.id) }
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
        magpie.applyLoaded(settingsRepository.getMagpieSettings())
        kimiCode.applyLoaded(settingsRepository.getKimiCodeSettings())
        zhipu.applyLoaded(settingsRepository.getZhipuSettings())
        workBuddy.applyPersisted(settingsRepository.getSessionSettings(PlatformIds.WORKBUDDY))
        workBuddyAi.applyPersisted(settingsRepository.getSessionSettings(PlatformIds.WORKBUDDY_AI))
        cline.applyPersisted(settingsRepository.getSessionSettings(PlatformIds.CLINE))
        commandCode.applyPersisted(settingsRepository.getSessionSettings(PlatformIds.COMMAND_CODE))
        antigravity.applyPersisted(settingsRepository.getSessionSettings(PlatformIds.ANTIGRAVITY))
        if (PlatformRegistry.platforms.any { isConfiguredFor(it.id) }) {
            refreshAll(showPullIndicator = false)
        }
    }

    val providerEntries = PlatformRegistry.platforms.map { platform ->
        val displayName = platformDisplayName(platform.id, homeList = true)
        ProviderEntry(
            platformId = platform.id,
            name = displayName,
            summary = summaryLabelFor(platform.id),
            configured = isConfiguredFor(platform.id),
            searchText = listOf(
                displayName,
                PlatformRegistry.displayName(platform.id),
                platform.id,
            ).joinToString(" "),
        )
    }

    val tips: (@Composable () -> Unit)? = if (hasVisibleTips) {
        {
            HomeTips(
                showBatteryOptimizationTip = showBatteryOptimizationTip,
                showOemBackgroundTip = showOemBackgroundTip,
                showPlatformTip = showPlatformTip,
                onDismissOemBackgroundTip = {
                    onDismissOemBackgroundTip()
                    scope.launch { settingsRepository.setOemBackgroundTipDismissed(true) }
                },
                onDismissPlatformTip = {
                    onDismissPlatformTip()
                    scope.launch { settingsRepository.setPlatformTipDismissed(true) }
                },
            )
        }
    } else {
        null
    }

    AnimatedContent(
        targetState = activePlatformId.ifEmpty { null },
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            if (targetState != null) {
                (slideInHorizontally(tween(300)) { it / 6 } + fadeIn(tween(220))) togetherWith
                    (slideOutHorizontally(tween(300)) { -it / 12 } + fadeOut(tween(160)))
            } else {
                (slideInHorizontally(tween(300)) { -it / 12 } + fadeIn(tween(220))) togetherWith
                    (slideOutHorizontally(tween(300)) { it / 6 } + fadeOut(tween(160)))
            }
        },
        label = "homeDestination",
    ) { platformId ->
        if (platformId == null) {
            ProviderListScreen(
                entries = providerEntries,
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                unconfiguredExpanded = unconfiguredExpanded,
                onUnconfiguredExpandedChange = { unconfiguredExpanded = it },
                isRefreshing = isRefreshing,
                onRefresh = { scope.launch { refreshAll(showPullIndicator = true) } },
                onOpenProvider = { activePlatformId = it },
                onOpenAppSettings = onOpenAppSettings,
                listState = providerListState,
                tips = tips,
            )
        } else {
            ProviderDetailScreen(
                title = platformDisplayName(platformId, homeList = true),
                logo = providerLogo(platformId),
                onBack = {
                    discardDrafts(platformId)
                    activePlatformId = ""
                },
            ) {
                when (platformId) {
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
                    PlatformIds.MAGPIE -> MagpieHomeContent(
                        state = magpie,
                        onRefreshPlatform = onRefreshPlatform,
                    )
                    PlatformIds.KIMI_CODE -> KimiCodeHomeContent(
                        state = kimiCode,
                        onRefreshPlatform = onRefreshPlatform,
                    )
                    PlatformIds.ZHIPU -> ZhipuHomeContent(
                        state = zhipu,
                        onRefreshPlatform = onRefreshPlatform,
                    )
                    PlatformIds.WORKBUDDY -> SessionPlatformHomeContent(
                        state = workBuddy,
                        bindings = workBuddyBindings,
                        widgetState = workBuddy.widgetState,
                    )
                    PlatformIds.WORKBUDDY_AI -> SessionPlatformHomeContent(
                        state = workBuddyAi,
                        bindings = workBuddyAiBindings,
                        widgetState = workBuddyAi.widgetState,
                    )
                    PlatformIds.CLINE -> SessionPlatformHomeContent(
                        state = cline,
                        bindings = clineBindings,
                        widgetState = cline.widgetState,
                    )
                    PlatformIds.COMMAND_CODE -> SessionPlatformHomeContent(
                        state = commandCode,
                        bindings = commandCodeBindings,
                        widgetState = commandCode.widgetState,
                    )
                    PlatformIds.ANTIGRAVITY -> SessionPlatformHomeContent(
                        state = antigravity,
                        bindings = antigravityBindings,
                        widgetState = antigravity.widgetState,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeTips(
    showBatteryOptimizationTip: Boolean,
    showOemBackgroundTip: Boolean,
    showPlatformTip: Boolean,
    onDismissOemBackgroundTip: () -> Unit,
    onDismissPlatformTip: () -> Unit,
) {
    val context = LocalContext.current
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
                onDismiss = onDismissOemBackgroundTip,
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
                onDismiss = onDismissPlatformTip,
            )
        }
    }
}
