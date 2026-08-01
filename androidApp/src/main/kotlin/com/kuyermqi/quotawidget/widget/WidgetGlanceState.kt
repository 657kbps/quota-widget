package com.kuyermqi.quotawidget.widget

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.kuyermqi.quotawidget.QuotaWidgetApp
import com.kuyermqi.quotawidget.domain.BalanceSnapshot
import com.kuyermqi.quotawidget.domain.CurrencyPreference
import com.kuyermqi.quotawidget.domain.RefreshIconPhase
import com.kuyermqi.quotawidget.domain.WidgetDisplayState
import com.kuyermqi.quotawidget.domain.formatBalance
import com.kuyermqi.quotawidget.platform.PlatformIds
import com.kuyermqi.quotawidget.settings.PlatformSettingsRepository

/**
 * Glance [update] often recomposes without re-entering [GlanceAppWidget.provideGlance].
 * Widget UI must therefore read from Glance state ([currentState]), which we sync from
 * the app repository before each update.
 */
object WidgetGlanceState {
    private const val TAG = "QuotaRefresh"

    val refreshPhaseKey = stringPreferencesKey("qw_refresh_phase")
    val statusKey = stringPreferencesKey("qw_status")
    val errorKey = stringPreferencesKey("qw_error")
    val platformNameKey = stringPreferencesKey("qw_platform_name")
    val currencyKey = stringPreferencesKey("qw_currency")
    val totalKey = stringPreferencesKey("qw_total")
    val formattedKey = stringPreferencesKey("qw_formatted")
    val updatedAtKey = longPreferencesKey("qw_updated_at")

    private object Status {
        const val NOT_CONFIGURED = "not_configured"
        const val LOADING = "loading"
        const val SUCCESS = "success"
        const val ERROR = "error"
    }

    suspend fun syncAndUpdate(context: Context, reason: String) {
        val app = context.applicationContext as QuotaWidgetApp
        val repo = app.settingsRepository
        val phase = repo.getRefreshIconPhase()
        val display = repo.getWidgetState()
        Log.i(TAG, "syncAndUpdate reason=$reason phase=$phase state=${display::class.simpleName}")

        val manager = GlanceAppWidgetManager(context)
        val targets = listOf(
            DeepSeekBalanceWidget() to DeepSeekBalanceWidget::class.java,
            DeepSeekBalanceCompactWidget() to DeepSeekBalanceCompactWidget::class.java,
        )
        var updated = 0
        for ((widget, clazz) in targets) {
            val ids = manager.getGlanceIds(clazz)
            ids.forEach { id ->
                updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) { prefs ->
                    prefs.toMutablePreferences().apply {
                        write(phase, display)
                    }
                }
                widget.update(context, id)
                updated++
                Log.i(TAG, "syncAndUpdate applied id=$id type=${clazz.simpleName} reason=$reason")
            }
        }
        if (updated == 0) {
            Log.w(TAG, "syncAndUpdate no glance ids")
        }
    }

    /**
     * Always copy the latest repository state into this Glance id.
     * Returns the display state that was written (from the app repository).
     */
    suspend fun syncFromRepository(
        context: Context,
        id: GlanceId,
        repo: PlatformSettingsRepository,
    ): WidgetDisplayState {
        val phase = repo.getRefreshIconPhase()
        val display = repo.getWidgetState()
        val hasKey = repo.getDeepSeekSettings().apiKey.isNotBlank()
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) { prefs ->
            prefs.toMutablePreferences().apply {
                write(phase, display)
            }
        }
        val verify = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
        Log.i(
            TAG,
            "syncFromRepository id=$id hasKey=$hasKey repo=${display::class.simpleName} " +
                "glanceStatus=${verify[statusKey]} glanceFormatted=${verify[formattedKey]}",
        )
        return display
    }

    fun Preferences.toRefreshPhase(): RefreshIconPhase =
        RefreshIconPhase.fromStorage(this[refreshPhaseKey])

    fun Preferences.toDisplayState(): WidgetDisplayState {
        return when (this[statusKey]) {
            Status.LOADING -> WidgetDisplayState.Loading
            Status.ERROR -> WidgetDisplayState.Error(this[errorKey] ?: "刷新失败")
            Status.SUCCESS -> {
                val currency = CurrencyPreference.fromStorage(this[currencyKey])
                val total = this[totalKey] ?: "0"
                WidgetDisplayState.Success(
                    BalanceSnapshot(
                        platformId = PlatformIds.DEEPSEEK,
                        platformName = this[platformNameKey] ?: "DeepSeek",
                        currency = currency,
                        totalBalance = total,
                        formattedBalance = this[formattedKey] ?: formatBalance(currency, total),
                        updatedAtEpochMs = this[updatedAtKey] ?: 0L,
                    ),
                )
            }
            else -> WidgetDisplayState.NotConfigured
        }
    }

    private fun MutablePreferences.write(phase: RefreshIconPhase, display: WidgetDisplayState) {
        this[refreshPhaseKey] = phase.name
        when (display) {
            WidgetDisplayState.NotConfigured -> {
                this[statusKey] = Status.NOT_CONFIGURED
                remove(errorKey)
            }
            WidgetDisplayState.Loading -> {
                this[statusKey] = Status.LOADING
                remove(errorKey)
            }
            is WidgetDisplayState.Error -> {
                this[statusKey] = Status.ERROR
                this[errorKey] = display.message
            }
            is WidgetDisplayState.Success -> {
                this[statusKey] = Status.SUCCESS
                this[platformNameKey] = display.snapshot.platformName
                this[currencyKey] = display.snapshot.currency.name
                this[totalKey] = display.snapshot.totalBalance
                this[formattedKey] = display.snapshot.formattedBalance
                this[updatedAtKey] = display.snapshot.updatedAtEpochMs
                remove(errorKey)
            }
        }
    }
}
