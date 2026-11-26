package com.kuyermqi.quotawidget.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.kuyermqi.quotawidget.QuotaWidgetApp
import com.kuyermqi.quotawidget.domain.AppLanguage
import com.kuyermqi.quotawidget.domain.DarkThemeMode
import com.kuyermqi.quotawidget.domain.UsageProgressStyle
import com.kuyermqi.quotawidget.domain.UsageResetStyle
import com.kuyermqi.quotawidget.platform.PlatformIds
import com.kuyermqi.quotawidget.widget.WidgetSyncCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Debug-only: seeds fake usage snapshots so widget layouts can be inspected
 * without live credentials.
 *
 * adb shell am broadcast -a com.kuyermqi.quotawidget.debug.ACTION_SEED \
 *   -n com.kuyermqi.quotawidget.debug/com.kuyermqi.quotawidget.debug.SeedWidgetsReceiver \
 *   [--es progress_style RING] [--es reset_style FOOTER_STACKED] \
 *   [--es style_opencode_go RING_SEGMENTED] [--es lang Japanese] [--es theme dark]
 */
class SeedWidgetsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pending = goAsync()
        val app = context.applicationContext as QuotaWidgetApp
        val progressStyle = intent?.enumExtra<UsageProgressStyle>(EXTRA_PROGRESS_STYLE)
        val resetStyle = intent?.enumExtra<UsageResetStyle>(EXTRA_RESET_STYLE)
        val language = intent?.enumExtra<AppLanguage>(EXTRA_LANGUAGE)
        val theme = intent?.themeExtra()
        val styleOverrides = intent.styleOverrides()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                DebugWidgetSeed.seed(
                    app = app,
                    progressStyle = progressStyle,
                    resetStyle = resetStyle,
                    styleOverrides = styleOverrides,
                    language = language,
                    darkThemeMode = theme,
                )
                WidgetSyncCoordinator.syncWidgets(app, "debug_seed")
                Log.i(
                    TAG,
                    "seed done style=$progressStyle overrides=$styleOverrides lang=$language theme=$theme",
                )
            } catch (t: Throwable) {
                Log.e(TAG, "seed failed", t)
            } finally {
                pending.finish()
            }
        }
    }

    private inline fun <reified T : Enum<T>> Intent.enumExtra(name: String): T? {
        val value = getStringExtra(name) ?: return null
        return enumValues<T>().find { it.name.equals(value, ignoreCase = true) }
    }

    private fun Intent.themeExtra(): DarkThemeMode? = when (
        getStringExtra(EXTRA_THEME)?.lowercase()
    ) {
        null -> null
        "dark" -> DarkThemeMode.Dark
        "light" -> DarkThemeMode.Light
        else -> DarkThemeMode.FollowSystem
    }

    private fun Intent?.styleOverrides(): Map<String, UsageProgressStyle> {
        val extras = this?.extras ?: return emptyMap()
        return extras.keySet()
            .mapNotNull { key ->
                val platformId = key.removePrefix(STYLE_PREFIX)
                if (platformId == key) return@mapNotNull null
                val style = enumExtra<UsageProgressStyle>(key) ?: return@mapNotNull null
                platformId to style
            }
            .toMap()
    }

    companion object {
        private const val TAG = "QuotaSeed"
        const val ACTION = "com.kuyermqi.quotawidget.debug.ACTION_SEED"
        const val EXTRA_PROGRESS_STYLE = "progress_style"
        const val EXTRA_RESET_STYLE = "reset_style"
        const val EXTRA_LANGUAGE = "lang"
        const val EXTRA_THEME = "theme"
        private const val STYLE_PREFIX = "style_"
    }
}
