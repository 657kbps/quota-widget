package com.kuyermqi.quotawidget.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kuyermqi.quotawidget.InAppWebViewActivity
import com.kuyermqi.quotawidget.R
import com.kuyermqi.quotawidget.domain.CurrencyPreference
import com.kuyermqi.quotawidget.domain.WidgetDisplayState
import com.kuyermqi.quotawidget.platform.PlatformIds
import com.kuyermqi.quotawidget.platform.PlatformRegistry
import com.kuyermqi.quotawidget.settings.DeepSeekSettings
import com.kuyermqi.quotawidget.settings.PlatformSettingsRepository
import com.kuyermqi.quotawidget.ui.components.DeepSeekConfigContent
import com.kuyermqi.quotawidget.ui.components.PlatformConfigItem
import com.kuyermqi.quotawidget.ui.components.TipBanner
import com.kuyermqi.quotawidget.ui.components.isIgnoringBatteryOptimizations
import com.kuyermqi.quotawidget.ui.components.requestIgnoreBatteryOptimizations
import kotlinx.coroutines.launch

data class FocusPlatformRequest(
    val platformId: String,
    val nonce: Long,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    settingsRepository: PlatformSettingsRepository,
    onRefreshBalance: suspend () -> WidgetDisplayState,
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
    val lifecycleOwner = LocalLifecycleOwner.current
    val listState = rememberLazyListState()
    var showBatteryOptimizationTip by remember {
        mutableStateOf(!context.isIgnoringBatteryOptimizations())
    }
    var savedSettings by remember { mutableStateOf(DeepSeekSettings()) }
    var draftApiKey by remember { mutableStateOf("") }
    var draftCurrency by remember { mutableStateOf(CurrencyPreference.CNY) }
    var expandedPlatformId by remember { mutableStateOf<String?>(null) }
    var highlightPlatformId by remember { mutableStateOf<String?>(null) }
    var highlightNonce by remember { mutableStateOf<Long?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var lastSuccessBalance by remember { mutableStateOf<String?>(null) }

    val widgetState by settingsRepository.observeWidgetState()
        .collectAsStateWithLifecycle(initialValue = WidgetDisplayState.NotConfigured)

    LaunchedEffect(widgetState) {
        val success = widgetState as? WidgetDisplayState.Success ?: return@LaunchedEffect
        lastSuccessBalance = success.snapshot.formattedBalance
    }

    LaunchedEffect(focusPlatformRequest, tipLoaded) {
        val request = focusPlatformRequest ?: return@LaunchedEffect
        if (!tipLoaded) return@LaunchedEffect
        val platformIndex = PlatformRegistry.platforms.indexOfFirst { it.id == request.platformId }
        if (platformIndex < 0) return@LaunchedEffect

        expandedPlatformId = request.platformId
        highlightPlatformId = request.platformId
        highlightNonce = request.nonce

        val tipCount = 3
        listState.animateScrollToItem(tipCount + platformIndex)
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

    fun balanceLabelFor(platformId: String): String? {
        if (platformId != PlatformIds.DEEPSEEK) return null
        if (savedSettings.apiKey.isBlank()) return null
        return when (val state = widgetState) {
            is WidgetDisplayState.Success -> state.snapshot.formattedBalance
            WidgetDisplayState.Loading -> lastSuccessBalance ?: "刷新中…"
            is WidgetDisplayState.Error -> lastSuccessBalance
            WidgetDisplayState.NotConfigured -> null
        }
    }

    suspend fun refreshBalance(showPullIndicator: Boolean = false): WidgetDisplayState {
        if (savedSettings.apiKey.isBlank()) {
            return WidgetDisplayState.NotConfigured
        }
        if (showPullIndicator) isRefreshing = true
        return try {
            onRefreshBalance()
        } finally {
            if (showPullIndicator) isRefreshing = false
        }
    }

    LaunchedEffect(Unit) {
        val settings = settingsRepository.getDeepSeekSettings()
        savedSettings = settings
        draftApiKey = settings.apiKey
        draftCurrency = settings.currency
        loaded = true
        if (settings.apiKey.isNotBlank()) {
            refreshBalance(showPullIndicator = false)
        }
    }

    val isDirty = loaded && (
        draftApiKey != savedSettings.apiKey || draftCurrency != savedSettings.currency
        )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("配额监控") },
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
            onRefresh = { scope.launch { refreshBalance(showPullIndicator = true) } },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .imePadding(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (tipLoaded) {
                    item(key = "battery_optimization_tip") {
                        TipBanner(
                            visible = showBatteryOptimizationTip,
                            iconRes = R.drawable.ic_battery,
                            message = stringResource(R.string.battery_optimization_tip),
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            onCardClick = { context.requestIgnoreBatteryOptimizations() },
                            actionText = stringResource(R.string.battery_optimization_tip_action),
                            onActionClick = { context.requestIgnoreBatteryOptimizations() },
                        )
                    }
                    item(key = "oem_background_tip") {
                        val oemGuideUrl = stringResource(R.string.oem_background_tip_url)
                        val oemGuideTitle = stringResource(R.string.oem_background_tip_link)
                        TipBanner(
                            visible = showOemBackgroundTip,
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
                    item(key = "platform_tip") {
                        TipBanner(
                            visible = showPlatformTip,
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

                items(PlatformRegistry.platforms, key = { it.id }) { platform ->
                    PlatformConfigItem(
                        title = platform.displayName,
                        expanded = expandedPlatformId == platform.id,
                        onToggle = {
                            expandedPlatformId =
                                if (expandedPlatformId == platform.id) null else platform.id
                        },
                        balanceText = balanceLabelFor(platform.id),
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
                                            savedSettings = next
                                            draftApiKey = next.apiKey
                                            if (next.apiKey.isBlank()) {
                                                lastSuccessBalance = null
                                            }
                                            saveError = when (val result = onRefreshBalance()) {
                                                is WidgetDisplayState.Error -> result.message
                                                else -> null
                                            }
                                        } finally {
                                            isSaving = false
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
