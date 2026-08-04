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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kuyermqi.quotawidget.R
import com.kuyermqi.quotawidget.domain.QuotaSnapshot
import com.kuyermqi.quotawidget.domain.QuotaWindow
import com.kuyermqi.quotawidget.domain.UsageDisplayMode
import com.kuyermqi.quotawidget.domain.UsageProgressStyle
import com.kuyermqi.quotawidget.domain.WidgetDisplayState
import com.kuyermqi.quotawidget.domain.formatNewApiBalanceTitle
import com.kuyermqi.quotawidget.platform.PlatformIds
import com.kuyermqi.quotawidget.settings.DEFAULT_NEW_API_QUOTA_PER_USD
import com.kuyermqi.quotawidget.settings.NewApiSettings
import com.kuyermqi.quotawidget.settings.PlatformSettingsRepository
import com.kuyermqi.quotawidget.ui.components.NewApiConfigContent
import com.kuyermqi.quotawidget.widget.WidgetGlanceState
import kotlinx.coroutines.launch

class NewApiHomeState internal constructor(
    private val settingsRepository: PlatformSettingsRepository,
) {
    var saved by mutableStateOf(NewApiSettings())
        internal set
    var draftBaseUrl by mutableStateOf("")
        internal set
    var draftApiKey by mutableStateOf("")
        internal set
    var draftQuotaPerUsd by mutableStateOf(DEFAULT_NEW_API_QUOTA_PER_USD.toString())
        internal set
    var draftUsageDisplayMode by mutableStateOf(UsageDisplayMode.USED)
        internal set
    var draftUsageProgressStyle by mutableStateOf(UsageProgressStyle.BAR)
        internal set
    var lastDisplay by mutableStateOf<String?>(null)
        internal set
    var lastUsedDisplay by mutableStateOf("")
        internal set
    var lastUnlimitedQuota by mutableStateOf(false)
        internal set
    var lastEmptyLimitedQuota by mutableStateOf(false)
        internal set
    var lastTokenExpired by mutableStateOf(false)
        internal set
    var lastQuotaOverspent by mutableStateOf(false)
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

    private val parsedQuotaPerUsd: Long?
        get() = draftQuotaPerUsd.trim().toLongOrNull()?.takeIf { it > 0L }

    val isDirty: Boolean
        get() = loaded && (
            draftBaseUrl.trim().trimEnd('/') != saved.baseUrl ||
                draftApiKey != saved.apiKey ||
                parsedQuotaPerUsd != saved.quotaPerUsd ||
                draftUsageDisplayMode != saved.usageDisplayMode ||
                draftUsageProgressStyle != saved.usageProgressStyle
            )

    fun applyLoaded(settings: NewApiSettings) {
        saved = settings
        draftBaseUrl = settings.baseUrl
        draftApiKey = settings.apiKey
        draftQuotaPerUsd = settings.quotaPerUsd.toString()
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
        fun balanceLabel(amount: String): String =
            resources.getString(R.string.new_api_balance_summary, amount)
        val mode = saved.usageDisplayMode
        fun amountFor(snapshot: QuotaSnapshot): String? =
            formatNewApiBalanceTitle(snapshot, mode)
        return when (widgetState) {
            is WidgetDisplayState.Success -> {
                val amount = amountFor(widgetState.snapshot) ?: return null
                if (widgetState.snapshot.tokenExpired) {
                    resources.getString(R.string.new_api_balance_summary_expired, amount)
                } else {
                    balanceLabel(amount)
                }
            }
            WidgetDisplayState.Loading -> {
                val cached = when (mode) {
                    UsageDisplayMode.USED -> lastUsedDisplay.takeIf { it.isNotBlank() }
                    UsageDisplayMode.REMAINING -> lastDisplay
                }
                cached?.let(::balanceLabel) ?: loadingMsg
            }
            is WidgetDisplayState.Error -> {
                val cached = when (mode) {
                    UsageDisplayMode.USED -> lastUsedDisplay.takeIf { it.isNotBlank() }
                    UsageDisplayMode.REMAINING -> lastDisplay
                }
                cached?.let(::balanceLabel)
            }
            WidgetDisplayState.NeedsReauth -> reauthMsg
            WidgetDisplayState.NotConfigured -> null
        }
    }

    internal fun repository(): PlatformSettingsRepository = settingsRepository
}

@Composable
fun rememberNewApiHomeState(
    settingsRepository: PlatformSettingsRepository,
): NewApiHomeState = remember(settingsRepository) {
    NewApiHomeState(settingsRepository)
}

@Composable
fun NewApiHomeEffects(state: NewApiHomeState): WidgetDisplayState {
    val observed by state.repository().observeWidgetState(PlatformIds.NEW_API)
        .collectAsStateWithLifecycle(initialValue = WidgetDisplayState.NotConfigured)
    LaunchedEffect(observed) {
        val success = observed as? WidgetDisplayState.Success ?: return@LaunchedEffect
        state.lastDisplay = success.snapshot.primaryDisplay
        state.lastUsedDisplay = success.snapshot.usedDisplay
        state.lastUnlimitedQuota = success.snapshot.unlimitedQuota
        state.lastEmptyLimitedQuota = success.snapshot.emptyLimitedQuota
        state.lastTokenExpired = success.snapshot.tokenExpired
        state.lastQuotaOverspent = success.snapshot.quotaOverspent
        state.lastWindows = success.snapshot.windows
    }
    return observed
}

@Composable
fun ColumnScope.NewApiHomeContent(
    state: NewApiHomeState,
    onRefreshPlatform: suspend (String) -> WidgetDisplayState,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    NewApiConfigContent(
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
        quotaPerUsd = state.draftQuotaPerUsd,
        onQuotaPerUsdChange = { value ->
            if (value.isEmpty() || value.all { it.isDigit() }) {
                state.draftQuotaPerUsd = value
                state.saveError = null
            }
        },
        windows = state.lastWindows,
        unlimitedQuota = state.lastUnlimitedQuota,
        emptyLimitedQuota = state.lastEmptyLimitedQuota,
        tokenExpired = state.lastTokenExpired,
        quotaOverspent = state.lastQuotaOverspent,
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
        isDirty = state.isDirty,
        isSaving = state.isSaving,
        saveError = state.saveError,
        onSave = {
            scope.launch {
                val quotaPerUsd = state.draftQuotaPerUsd.trim().toLongOrNull()?.takeIf { it > 0L }
                if (quotaPerUsd == null) {
                    state.saveError = context.getString(R.string.new_api_quota_per_usd_invalid)
                    return@launch
                }
                val next = NewApiSettings(
                    baseUrl = state.draftBaseUrl.trim().trimEnd('/'),
                    apiKey = state.draftApiKey.trim(),
                    quotaPerUsd = quotaPerUsd,
                    usageDisplayMode = state.draftUsageDisplayMode,
                    usageProgressStyle = state.draftUsageProgressStyle,
                )
                state.isSaving = true
                state.saveError = null
                try {
                    state.repository().saveNewApiSettings(next)
                    state.saved = next
                    state.draftBaseUrl = next.baseUrl
                    state.draftApiKey = next.apiKey
                    state.draftQuotaPerUsd = next.quotaPerUsd.toString()
                    if (!next.isConfigured) {
                        state.lastDisplay = null
                        state.lastUsedDisplay = ""
                        state.lastUnlimitedQuota = false
                        state.lastEmptyLimitedQuota = false
                        state.lastTokenExpired = false
                        state.lastQuotaOverspent = false
                        state.lastWindows = emptyList()
                    }
                    state.saveError = when (
                        val result = onRefreshPlatform(PlatformIds.NEW_API)
                    ) {
                        is WidgetDisplayState.Error -> result.message
                        else -> null
                    }
                    WidgetGlanceState.syncAndUpdate(context, "new_api_save")
                } finally {
                    state.isSaving = false
                }
            }
        },
    )
}
