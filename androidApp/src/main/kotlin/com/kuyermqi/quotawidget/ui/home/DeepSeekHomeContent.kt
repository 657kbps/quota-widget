package com.kuyermqi.quotawidget.ui.home

import android.content.res.Resources
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kuyermqi.quotawidget.QuotaWidgetApp
import com.kuyermqi.quotawidget.R
import com.kuyermqi.quotawidget.deepseek.peakvalley.DeepSeekPeakValleyCalculator
import com.kuyermqi.quotawidget.deepseek.peakvalley.HolidayCalendarTable
import com.kuyermqi.quotawidget.domain.CurrencyPreference
import com.kuyermqi.quotawidget.domain.HolidayCalendarCache
import com.kuyermqi.quotawidget.domain.WidgetDisplayState
import com.kuyermqi.quotawidget.platform.PlatformIds
import com.kuyermqi.quotawidget.settings.DeepSeekSettings
import com.kuyermqi.quotawidget.settings.PlatformSettingsRepository
import com.kuyermqi.quotawidget.ui.components.CalendarUpdateNotice
import com.kuyermqi.quotawidget.ui.components.DeepSeekConfigContent
import com.kuyermqi.quotawidget.ui.components.DeepSeekPeakValleySection
import com.kuyermqi.quotawidget.widget.DeepSeekPeakValleyCoordinator
import com.kuyermqi.quotawidget.widget.WidgetGlanceState
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
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
    val calendarRepository = remember(context) {
        (context.applicationContext as QuotaWidgetApp).holidayCalendarRepository
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    val peakValleyEnabled by state.repository().observeDeepSeekPeakValleyEnabled()
        .collectAsStateWithLifecycle(initialValue = true)
    val calendarCache by state.repository().observeHolidayCalendarCache()
        .collectAsStateWithLifecycle(initialValue = HolidayCalendarCache.EMPTY)
    var nowEpochMs by remember { mutableStateOf(System.currentTimeMillis()) }
    var isUpdatingCalendar by remember { mutableStateOf(false) }
    var calendarNotice by remember { mutableStateOf<CalendarUpdateNotice?>(null) }
    var calendarTable by remember { mutableStateOf(HolidayCalendarTable.EMPTY) }
    LaunchedEffect(calendarCache) {
        calendarTable = try {
            calendarRepository.table()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            HolidayCalendarTable.EMPTY
        }
    }
    val peakValleySnapshot = remember(calendarTable, nowEpochMs) {
        DeepSeekPeakValleyCalculator(calendarTable).snapshotAt(
            Instant.ofEpochMilli(nowEpochMs),
        )
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                nowEpochMs = System.currentTimeMillis()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(lifecycleOwner) {
        while (true) {
            delay(30_000L)
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                nowEpochMs = System.currentTimeMillis()
            }
        }
    }

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
        peakValleyContent = {
            DeepSeekPeakValleySection(
                enabled = peakValleyEnabled,
                onEnabledChange = { enabled ->
                    scope.launch {
                        state.repository().setDeepSeekPeakValleyEnabled(enabled)
                    }
                },
                snapshot = peakValleySnapshot,
                currentYear = peakValleySnapshot.currentYear,
                coveredYears = calendarTable.coveredYears(),
                lastSuccessAtEpochMs = calendarCache.lastSuccessAtEpochMs,
                failedYears = calendarCache.lastAttemptFailedYears,
                notPublishedYears = calendarCache.lastAttemptNotPublishedYears,
                isUpdating = isUpdatingCalendar,
                updateNotice = calendarNotice,
                onUpdate = {
                    scope.launch {
                        isUpdatingCalendar = true
                        calendarNotice = null
                        val result = try {
                            calendarRepository.update()
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Throwable) {
                            null
                        }
                        isUpdatingCalendar = false
                        calendarNotice = when {
                            result == null || result.failedYears.isNotEmpty() ->
                                CalendarUpdateNotice.Failed
                            result.notPublishedYears.isNotEmpty() ->
                                CalendarUpdateNotice.NotPublished(
                                    result.notPublishedYears.sorted(),
                                )
                            else -> CalendarUpdateNotice.Success
                        }
                        DeepSeekPeakValleyCoordinator.refresh(
                            context,
                            "deepseek_calendar_manual",
                        )
                    }
                },
            )
        },
    )
}
