package com.kuyermqi.quotawidget.ui.home

import android.content.res.Resources
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kuyermqi.quotawidget.R
import com.kuyermqi.quotawidget.domain.QuotaWindow
import com.kuyermqi.quotawidget.domain.UsageDisplayMode
import com.kuyermqi.quotawidget.domain.UsageProgressStyle
import com.kuyermqi.quotawidget.domain.UsageWindowKind
import com.kuyermqi.quotawidget.domain.WidgetDisplayState
import com.kuyermqi.quotawidget.domain.availableUsageWindowKinds
import com.kuyermqi.quotawidget.domain.defaultUsageWindowKind
import com.kuyermqi.quotawidget.magpie.MagpieProvider
import com.kuyermqi.quotawidget.platform.PlatformIds
import com.kuyermqi.quotawidget.settings.MagpieSettings
import com.kuyermqi.quotawidget.settings.PlatformSettingsRepository
import com.kuyermqi.quotawidget.ui.components.MagpieConfigContent
import com.kuyermqi.quotawidget.ui.usage.formatUsageWindowSummary
import com.kuyermqi.quotawidget.widget.WidgetGlanceState
import kotlinx.coroutines.launch

class MagpieHomeState internal constructor(
    private val settingsRepository: PlatformSettingsRepository,
) {
    var saved by mutableStateOf(MagpieSettings())
        internal set
    var draftBaseUrl by mutableStateOf("")
        internal set
    var draftApiKey by mutableStateOf("")
        internal set
    var draftProviderId by mutableStateOf("")
        internal set
    var draftWindowKind by mutableStateOf(UsageWindowKind.ROLLING)
        internal set
    var draftUsageDisplayMode by mutableStateOf(UsageDisplayMode.USED)
        internal set
    var draftUsageProgressStyle by mutableStateOf(UsageProgressStyle.BAR)
        internal set
    var lastDisplay by mutableStateOf<String?>(null)
        internal set
    var lastWindows by mutableStateOf<List<QuotaWindow>>(emptyList())
        internal set
    var isSaving by mutableStateOf(false)
        internal set
    var saveError by mutableStateOf<String?>(null)
        internal set
    var loaded by mutableStateOf(false)
        internal set

    val isConfigured: Boolean
        get() = saved.isConfigured

    /** Providers the gateway reported last time, in first-seen order. */
    val providers: List<MagpieProvider>
        get() = saved.providers

    val isDirty: Boolean
        get() = loaded && (
            draftBaseUrl.trim().trimEnd('/') != saved.baseUrl ||
                draftApiKey != saved.apiKey ||
                draftProviderId != saved.selectedProviderId ||
                draftWindowKind != saved.widgetWindowKind ||
                draftUsageDisplayMode != saved.usageDisplayMode ||
                draftUsageProgressStyle != saved.usageProgressStyle
            )

    fun applyLoaded(settings: MagpieSettings) {
        saved = settings
        draftBaseUrl = settings.baseUrl
        draftApiKey = settings.apiKey
        draftProviderId = settings.selectedProviderId
        draftWindowKind = settings.widgetWindowKind
        draftUsageDisplayMode = settings.usageDisplayMode
        draftUsageProgressStyle = settings.usageProgressStyle
        loaded = true
    }

    /**
     * A refresh can learn the gateway's providers and clamp the widget window;
     * adopt both unless the user was in the middle of editing when it landed.
     */
    fun applyRefreshed(settings: MagpieSettings) {
        val wasDirty = isDirty
        saved = settings
        if (!wasDirty) {
            draftProviderId = settings.selectedProviderId
            draftWindowKind = settings.widgetWindowKind
        }
    }

    /** Picking a provider also re-targets the window kind it actually reports. */
    fun selectProvider(provider: MagpieProvider) {
        draftProviderId = provider.id
        val available = availableUsageWindowKinds(provider.windows)
        if (available.isNotEmpty() && draftWindowKind !in available) {
            draftWindowKind = defaultUsageWindowKind(provider.windows)
        }
        saveError = null
    }

    /** Preview windows of the provider picked in the UI, falling back to the last snapshot. */
    val previewWindows: List<QuotaWindow>
        get() = providers.find { it.id == draftProviderId }?.windows
            .orEmpty()
            .ifEmpty { lastWindows }

    fun summaryLabel(
        resources: Resources,
        widgetState: WidgetDisplayState,
        loadingMsg: String,
        reauthMsg: String,
    ): String? {
        if (!isConfigured) return null
        val summary = when (widgetState) {
            is WidgetDisplayState.Success -> magpieUsageSummary(
                resources = resources,
                windows = widgetState.snapshot.windows,
                windowKind = saved.widgetWindowKind,
                usageDisplayMode = saved.usageDisplayMode,
            )
            WidgetDisplayState.Loading -> lastDisplay ?: loadingMsg
            is WidgetDisplayState.Error -> lastDisplay
            WidgetDisplayState.NeedsReauth -> return reauthMsg
            WidgetDisplayState.NotConfigured -> return null
        } ?: return null
        // The row names the platform already; lead with the provider it shows.
        val providerName = saved.providers.find { it.id == saved.selectedProviderId }?.displayName
        return if (providerName.isNullOrBlank()) {
            summary
        } else {
            resources.getString(R.string.magpie_summary_with_provider, providerName, summary)
        }
    }

    internal fun repository(): PlatformSettingsRepository = settingsRepository
}

/**
 * Home-list summary for the selected window. Gateway accounts vary, so fall
 * back to the smallest window the gateway reports.
 */
private fun magpieUsageSummary(
    resources: Resources,
    windows: List<QuotaWindow>,
    windowKind: UsageWindowKind,
    usageDisplayMode: UsageDisplayMode,
): String {
    val available = availableUsageWindowKinds(windows)
    val kind = if (windowKind in available) windowKind else defaultUsageWindowKind(windows)
    return formatUsageWindowSummary(
        resources = resources,
        windows = windows,
        windowKind = kind,
        usageDisplayMode = usageDisplayMode,
        fallback = resources.getString(R.string.usage_unavailable),
    )
}

@Composable
fun rememberMagpieHomeState(
    settingsRepository: PlatformSettingsRepository,
): MagpieHomeState = remember(settingsRepository) {
    MagpieHomeState(settingsRepository)
}

@Composable
fun MagpieHomeEffects(state: MagpieHomeState): WidgetDisplayState {
    val resources = LocalResources.current
    val observed by state.repository().observeWidgetState(PlatformIds.MAGPIE)
        .collectAsStateWithLifecycle(initialValue = WidgetDisplayState.NotConfigured)
    LaunchedEffect(observed, state.saved.usageDisplayMode, state.saved.widgetWindowKind, resources) {
        val success = observed as? WidgetDisplayState.Success ?: return@LaunchedEffect
        state.lastWindows = success.snapshot.windows
        state.lastDisplay = magpieUsageSummary(
            resources = resources,
            windows = success.snapshot.windows,
            windowKind = state.saved.widgetWindowKind,
            usageDisplayMode = state.saved.usageDisplayMode,
        )
        state.applyRefreshed(state.repository().getMagpieSettings())
    }
    return observed
}

@Composable
fun ColumnScope.MagpieHomeContent(
    state: MagpieHomeState,
    onRefreshPlatform: suspend (String) -> WidgetDisplayState,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    MagpieConfigContent(
        baseUrl = state.draftBaseUrl,
        onBaseUrlChange = {
            state.draftBaseUrl = it
            state.saveError = null
        },
        apiKey = state.draftApiKey,
        onApiKeyChange = {
            state.draftApiKey = it
            state.saveError = null
        },
        providers = state.providers,
        selectedProviderId = state.draftProviderId,
        onProviderSelected = { id ->
            state.providers.find { it.id == id }?.let(state::selectProvider)
            state.saveError = null
        },
        windows = state.previewWindows,
        widgetWindowKind = state.draftWindowKind,
        onWidgetWindowKindChange = {
            state.draftWindowKind = it
            state.saveError = null
        },
        usageDisplayMode = state.draftUsageDisplayMode,
        onUsageDisplayModeChange = {
            state.draftUsageDisplayMode = it
            state.saveError = null
        },
        usageProgressStyle = state.draftUsageProgressStyle,
        onUsageProgressStyleChange = {
            state.draftUsageProgressStyle = it
            state.saveError = null
        },
        isSaving = state.isSaving,
        isDirty = state.isDirty,
        saveError = state.saveError,
        onSave = {
            scope.launch {
                val next = MagpieSettings(
                    baseUrl = state.draftBaseUrl.trim().trimEnd('/'),
                    apiKey = state.draftApiKey.trim(),
                    selectedProviderId = state.draftProviderId,
                    providers = state.providers,
                    widgetWindowKind = state.draftWindowKind,
                    usageDisplayMode = state.draftUsageDisplayMode,
                    usageProgressStyle = state.draftUsageProgressStyle,
                )
                state.isSaving = true
                state.saveError = null
                try {
                    state.repository().saveMagpieSettings(next)
                    state.saved = next
                    state.draftBaseUrl = next.baseUrl
                    state.draftApiKey = next.apiKey
                    if (!next.isConfigured) {
                        state.lastDisplay = null
                        state.lastWindows = emptyList()
                    }
                    when (val result = onRefreshPlatform(PlatformIds.MAGPIE)) {
                        is WidgetDisplayState.Error -> state.saveError = result.message
                        WidgetDisplayState.NeedsReauth ->
                            state.saveError = context.getString(R.string.magpie_key_rejected)
                        is WidgetDisplayState.Success -> {
                            // The provider may clamp the widget window and learn the provider list.
                            state.applyRefreshed(state.repository().getMagpieSettings())
                        }
                        else -> Unit
                    }
                    WidgetGlanceState.syncAndUpdate(context, "magpie_save")
                } finally {
                    state.isSaving = false
                }
            }
        },
    )
}
