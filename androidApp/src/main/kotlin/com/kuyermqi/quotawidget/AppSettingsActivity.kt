package com.kuyermqi.quotawidget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kuyermqi.quotawidget.domain.AppSettings
import com.kuyermqi.quotawidget.ui.AppSettingsScreen
import com.kuyermqi.quotawidget.ui.theme.AppNightMode
import com.kuyermqi.quotawidget.ui.theme.QuotaWidgetTheme
import com.kuyermqi.quotawidget.widget.PeriodicRefreshScheduler
import com.kuyermqi.quotawidget.widget.WidgetGlanceState

class AppSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = application as QuotaWidgetApp
        setContent {
            val context = LocalContext.current
            val initialSettings = remember {
                AppSettings(darkThemeMode = AppNightMode.storedMode(context))
            }
            val appSettings by app.settingsRepository.observeAppSettings()
                .collectAsStateWithLifecycle(initialValue = initialSettings)

            LaunchedEffect(
                appSettings.darkThemeMode,
                appSettings.themeColorMode,
                appSettings.customSeedColorArgb,
            ) {
                WidgetGlanceState.syncAndUpdate(this@AppSettingsActivity, "app_theme")
            }

            QuotaWidgetTheme(
                darkThemeMode = appSettings.darkThemeMode,
                themeColorMode = appSettings.themeColorMode,
                seedColor = Color(appSettings.customSeedColorArgb),
            ) {
                AppSettingsScreen(
                    settingsRepository = app.settingsRepository,
                    onBack = { finish() },
                    onRefreshIntervalChanged = { minutes ->
                        PeriodicRefreshScheduler.schedule(this@AppSettingsActivity, minutes)
                    },
                )
            }
        }
    }
}
