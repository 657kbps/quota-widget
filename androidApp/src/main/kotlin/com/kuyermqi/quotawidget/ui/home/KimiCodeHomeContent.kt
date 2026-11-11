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
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kuyermqi.quotawidget.R
import com.kuyermqi.quotawidget.domain.QuotaWindow
import com.kuyermqi.quotawidget.domain.ServiceRegion
import com.kuyermqi.quotawidget.domain.UsageDisplayMode
import com.kuyermqi.quotawidget.domain.UsageProgressStyle
import com.kuyermqi.quotawidget.domain.UsageWindowKind
import com.kuyermqi.quotawidget.domain.WidgetDisplayState
import com.kuyermqi.quotawidget.platform.PlatformIds
import com.kuyermqi.quotawidget.settings.KimiCodeSettings
import com.kuyermqi.quotawidget.settings.PlatformSettingsRepository
import com.kuyermqi.quotawidget.ui.components.ApiKeyUsageConfigContent
import com.kuyermqi.quotawidget.ui.usage.formatUsageWindowSummaryWithFallback
import com.kuyermqi.quotawidget.widget.WidgetGlanceState
import kotlinx.coroutines.launch

class KimiCodeHomeState internal constructor(
    private val settingsRepository: PlatformSettingsRepository,
) {
    var saved by mutableStateOf(KimiCodeSettings())
        internal set
    var draftApiKey by mutableStateOf("")
        internal set
    var draftRegion by mutableStateOf(ServiceRegion.CN)
        internal set
    var draftWindowKind by mutableStateOf(UsageWindowKind.ROLLING)
        internal set
    var draftUsageDisplayMode by mutableStateOf(UsageDisplayMode.USED)
        internal set
    var draftUsageProgressStyle by mutableStateOf(UsageProgressStyle.BAR)
        internal set
    var lastWindows by mutableStateOf<List<QuotaWindow>>(emptyList())
        internal set
    var lastDisplay by mutableStateOf<String?>(null)
        internal set
    var isSaving by mutableStateOf(false)
        internal set
    var saveError by mutableStateOf<String?>(null)
        internal set
    var loaded by mutableStateOf(false)
        internal set

    val isConfigured: Boolean
        get() = saved.isConfigured

    val isDirty: Boolean
        get() = loaded && (
            draftApiKey != saved.apiKey ||
                draftRegion != saved.region ||
                draftWindowKind != saved.widgetWindowKind ||
                draftUsageDisplayMode != saved.usageDisplayMode ||
                draftUsageProgressStyle != saved.usageProgressStyle
        )

    fun applyLoaded(settings: KimiCodeSettings) {
        saved = settings
        draftApiKey = settings.apiKey
        draftRegion = settings.region
        draftWindowKind = settings.widgetWindowKind
        draftUsageDisplayMode = settings.usageDisplayMode
        draftUsageProgressStyle = settings.usageProgressStyle
        loaded = true
    }

    fun summaryLabel(
        resources: Resources,
        widgetState: WidgetDisplayState,
        loadingMsg: String,
        reauthMsg: String,
    ): String? {
        if (!isConfigured) return null
        fun format(windows: List<QuotaWindow>): String? =
            windows.takeIf { it.isNotEmpty() }?.let {
                formatUsageWindowSummaryWithFallback(
                    resources = resources,
                    windows = it,
                    windowKind = saved.widgetWindowKind,
                    usageDisplayMode = saved.usageDisplayMode,
                    fallback = resources.getString(R.string.usage_unavailable),
                )
            }
        return when (widgetState) {
            is WidgetDisplayState.Success -> format(widgetState.snapshot.windows)
            WidgetDisplayState.Loading -> lastDisplay ?: loadingMsg
            is WidgetDisplayState.Error -> lastDisplay
            WidgetDisplayState.NeedsReauth -> reauthMsg
            WidgetDisplayState.NotConfigured -> null
        }
    }

    internal fun repository(): PlatformSettingsRepository = settingsRepository
}

@Composable
fun rememberKimiCodeHomeState(
    settingsRepository: PlatformSettingsRepository,
): KimiCodeHomeState = remember(settingsRepository) {
    KimiCodeHomeState(settingsRepository)
}

/** Collects widget state and keeps the home summary in sync. */
@Composable
fun KimiCodeHomeEffects(state: KimiCodeHomeState): WidgetDisplayState {
    val resources = LocalResources.current
    val observed by state.repository().observeWidgetState(PlatformIds.KIMI_CODE)
        .collectAsStateWithLifecycle(initialValue = WidgetDisplayState.NotConfigured)
    LaunchedEffect(
        observed,
        state.saved.widgetWindowKind,
        state.saved.usageDisplayMode,
        resources,
    ) {
        val success = observed as? WidgetDisplayState.Success ?: return@LaunchedEffect
        state.lastWindows = success.snapshot.windows
        state.lastDisplay = formatUsageWindowSummaryWithFallback(
            resources = resources,
            windows = success.snapshot.windows,
            windowKind = state.saved.widgetWindowKind,
            usageDisplayMode = state.saved.usageDisplayMode,
            fallback = "",
        ).ifBlank { null }
    }
    return observed
}

@Composable
fun ColumnScope.KimiCodeHomeContent(
    state: KimiCodeHomeState,
    onRefreshPlatform: suspend (String) -> WidgetDisplayState,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val msgInvalidKey = stringResource(R.string.kimi_code_invalid_key)

    ApiKeyUsageConfigContent(
        apiKey = state.draftApiKey,
        onApiKeyChange = {
            state.draftApiKey = it
            state.saveError = null
        },
        apiKeyHint = stringResource(R.string.kimi_code_api_key_hint),
        region = state.draftRegion,
        onRegionChange = {
            state.draftRegion = it
            state.saveError = null
        },
        windows = state.lastWindows,
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
        regionHint = stringResource(R.string.service_region_hint),
        isDirty = state.isDirty,
        isSaving = state.isSaving,
        saveError = state.saveError,
        onSave = {
            scope.launch {
                val next = KimiCodeSettings(
                    apiKey = state.draftApiKey.trim(),
                    region = state.draftRegion,
                    widgetWindowKind = state.draftWindowKind,
                    usageDisplayMode = state.draftUsageDisplayMode,
                    usageProgressStyle = state.draftUsageProgressStyle,
                )
                state.isSaving = true
                state.saveError = null
                try {
                    state.repository().saveKimiCodeSettings(next)
                    state.saved = next
                    state.draftApiKey = next.apiKey
                    if (!next.isConfigured) {
                        state.lastWindows = emptyList()
                        state.lastDisplay = null
                    }
                    state.saveError = when (
                        val result = onRefreshPlatform(PlatformIds.KIMI_CODE)
                    ) {
                        is WidgetDisplayState.Error -> result.message
                        WidgetDisplayState.NeedsReauth -> msgInvalidKey
                        else -> null
                    }
                    WidgetGlanceState.syncAndUpdate(context, "kimi_code_save")
                } finally {
                    state.isSaving = false
                }
            }
        },
    )
}
