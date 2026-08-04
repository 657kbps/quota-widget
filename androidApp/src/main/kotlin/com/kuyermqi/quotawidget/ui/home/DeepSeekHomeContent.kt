package com.kuyermqi.quotawidget.ui.home

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.res.Resources
import com.kuyermqi.quotawidget.R
import com.kuyermqi.quotawidget.domain.CurrencyPreference
import com.kuyermqi.quotawidget.domain.WidgetDisplayState
import com.kuyermqi.quotawidget.platform.PlatformIds
import com.kuyermqi.quotawidget.settings.DeepSeekSettings
import com.kuyermqi.quotawidget.settings.PlatformSettingsRepository
import com.kuyermqi.quotawidget.ui.components.DeepSeekConfigContent
import com.kuyermqi.quotawidget.widget.WidgetGlanceState
import kotlinx.coroutines.launch

class DeepSeekHomeState internal constructor(
    private val settingsRepository: PlatformSettingsRepository,
) {
    var saved by mutableStateOf(DeepSeekSettings())
        internal set
    var draftApiKey by mutableStateOf("")
        internal set
    var draftCurrency by mutableStateOf(CurrencyPreference.CNY)
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
        get() = saved.apiKey.isNotBlank()

    val isDirty: Boolean
        get() = loaded && (
            draftApiKey != saved.apiKey || draftCurrency != saved.currency
            )

    fun applyLoaded(settings: DeepSeekSettings) {
        saved = settings
        draftApiKey = settings.apiKey
        draftCurrency = settings.currency
        loaded = true
    }

    fun summaryLabel(
        resources: Resources,
        widgetState: WidgetDisplayState,
        loadingMsg: String,
        reauthMsg: String,
    ): String? {
        if (!isConfigured) return null
        fun balanceLabel(amount: String): String =
            resources.getString(R.string.deepseek_balance_summary, amount)
        return when (widgetState) {
            is WidgetDisplayState.Success -> balanceLabel(widgetState.snapshot.primaryDisplay)
            WidgetDisplayState.Loading -> lastDisplay?.let(::balanceLabel) ?: loadingMsg
            is WidgetDisplayState.Error -> lastDisplay?.let(::balanceLabel)
            WidgetDisplayState.NeedsReauth -> reauthMsg
            WidgetDisplayState.NotConfigured -> null
        }
    }

    internal fun repository(): PlatformSettingsRepository = settingsRepository
}

@Composable
fun rememberDeepSeekHomeState(
    settingsRepository: PlatformSettingsRepository,
): DeepSeekHomeState = remember(settingsRepository) {
    DeepSeekHomeState(settingsRepository)
}

/** Collects widget state and keeps [DeepSeekHomeState.lastDisplay] in sync. */
@Composable
fun DeepSeekHomeEffects(state: DeepSeekHomeState): WidgetDisplayState {
    val observed by state.repository().observeWidgetState(PlatformIds.DEEPSEEK)
        .collectAsStateWithLifecycle(initialValue = WidgetDisplayState.NotConfigured)
    LaunchedEffect(observed) {
        val success = observed as? WidgetDisplayState.Success ?: return@LaunchedEffect
        state.lastDisplay = success.snapshot.primaryDisplay
    }
    return observed
}

@Composable
fun ColumnScope.DeepSeekHomeContent(
    state: DeepSeekHomeState,
    onRefreshPlatform: suspend (String) -> WidgetDisplayState,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    DeepSeekConfigContent(
        apiKey = state.draftApiKey,
        onApiKeyChange = {
            state.draftApiKey = it
            state.saveError = null
        },
        currency = state.draftCurrency,
        onCurrencyChange = {
            state.draftCurrency = it
            state.saveError = null
        },
        isDirty = state.isDirty,
        isSaving = state.isSaving,
        saveError = state.saveError,
        onSave = {
            scope.launch {
                val next = DeepSeekSettings(
                    apiKey = state.draftApiKey.trim(),
                    currency = state.draftCurrency,
                )
                state.isSaving = true
                state.saveError = null
                try {
                    state.repository().saveDeepSeekSettings(next)
                    state.saved = next
                    state.draftApiKey = next.apiKey
                    if (next.apiKey.isBlank()) {
                        state.lastDisplay = null
                    }
                    state.saveError = when (
                        val result = onRefreshPlatform(PlatformIds.DEEPSEEK)
                    ) {
                        is WidgetDisplayState.Error -> result.message
                        else -> null
                    }
                    WidgetGlanceState.syncAndUpdate(context, "deepseek_save")
                } finally {
                    state.isSaving = false
                }
            }
        },
    )
}
