package com.kuyermqi.quotawidget

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kuyermqi.quotawidget.domain.AppSettings
import com.kuyermqi.quotawidget.refresh.displayState
import com.kuyermqi.quotawidget.ui.SettingsScreen
import com.kuyermqi.quotawidget.ui.theme.AppNightMode
import com.kuyermqi.quotawidget.ui.theme.QuotaWidgetTheme
import com.kuyermqi.quotawidget.widget.WidgetGlanceState
import com.kuyermqi.quotawidget.widget.WidgetRefreshCoordinator

class MainActivity : ComponentActivity() {
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
            var showPlatformTip by remember { mutableStateOf(false) }
            var tipLoaded by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                showPlatformTip = !app.settingsRepository.isPlatformTipDismissed()
                tipLoaded = true
            }

            LaunchedEffect(
                appSettings.darkThemeMode,
                appSettings.themeColorMode,
                appSettings.customSeedColorArgb,
            ) {
                WidgetGlanceState.syncAndUpdate(this@MainActivity, "app_theme")
            }

            QuotaWidgetTheme(
                darkThemeMode = appSettings.darkThemeMode,
                themeColorMode = appSettings.themeColorMode,
                seedColor = Color(appSettings.customSeedColorArgb),
            ) {
                SettingsScreen(
                    settingsRepository = app.settingsRepository,
                    onRefreshBalance = {
                        WidgetRefreshCoordinator.runBackgroundRefresh(this@MainActivity)
                            .let { it.displayState }
                    },
                    onOpenAppSettings = {
                        startActivity(Intent(this@MainActivity, AppSettingsActivity::class.java))
                    },
                    showPlatformTip = showPlatformTip,
                    tipLoaded = tipLoaded,
                    onDismissPlatformTip = { showPlatformTip = false },
                )
            }
        }
    }
}
