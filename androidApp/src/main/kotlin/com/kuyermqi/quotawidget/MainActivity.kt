package com.kuyermqi.quotawidget

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kuyermqi.quotawidget.domain.AppSettings
import com.kuyermqi.quotawidget.platform.PlatformRegistry
import com.kuyermqi.quotawidget.refresh.displayState
import com.kuyermqi.quotawidget.ui.FocusPlatformRequest
import com.kuyermqi.quotawidget.ui.HomeScreen
import com.kuyermqi.quotawidget.ui.UpdateAvailableDialog
import com.kuyermqi.quotawidget.ui.theme.AppNightMode
import com.kuyermqi.quotawidget.ui.theme.QuotaWidgetTheme
import com.kuyermqi.quotawidget.update.UpdateAvailability
import com.kuyermqi.quotawidget.widget.WidgetGlanceState
import com.kuyermqi.quotawidget.widget.WidgetRefreshCoordinator
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class MainActivity : ComponentActivity() {
    private var focusPlatformRequest by mutableStateOf<FocusPlatformRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        consumeFocusExtra(intent)
        val app = application as QuotaWidgetApp
        setContent {
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            val initialSettings = remember {
                AppSettings(darkThemeMode = AppNightMode.storedMode(context))
            }
            val appSettings by app.settingsRepository.observeAppSettings()
                .collectAsStateWithLifecycle(initialValue = initialSettings)
            var showPlatformTip by remember { mutableStateOf(false) }
            var showOemBackgroundTip by remember { mutableStateOf(false) }
            var tipLoaded by remember { mutableStateOf(false) }
            var updateAvailability by remember { mutableStateOf<UpdateAvailability?>(null) }

            LaunchedEffect(Unit) {
                showPlatformTip = !app.settingsRepository.isPlatformTipDismissed()
                showOemBackgroundTip = !app.settingsRepository.isOemBackgroundTipDismissed()
                tipLoaded = true
            }

            LaunchedEffect(Unit) {
                if (!updateCheckStarted.compareAndSet(false, true)) return@LaunchedEffect
                val versionName = appVersionName()
                if (versionName.isBlank()) return@LaunchedEffect
                updateAvailability = app.updateCheckInteractor.check(versionName)
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
                updateAvailability?.let { update ->
                    UpdateAvailableDialog(
                        versionName = update.versionName,
                        onIgnoreVersion = {
                            scope.launch {
                                app.settingsRepository.setUpdateIgnoredVersion(update.versionName)
                                updateAvailability = null
                            }
                        },
                        onNeverPrompt = {
                            scope.launch {
                                val current = app.settingsRepository.getAppSettings()
                                app.settingsRepository.saveAppSettings(
                                    current.copy(checkForUpdatesOnLaunch = false),
                                )
                                updateAvailability = null
                            }
                        },
                        onDownload = {
                            runCatching {
                                startActivity(
                                    Intent(Intent.ACTION_VIEW, update.releaseUrl.toUri()),
                                )
                            }
                            updateAvailability = null
                        },
                        onDismiss = { updateAvailability = null },
                    )
                }

                HomeScreen(
                    settingsRepository = app.settingsRepository,
                    onRefreshPlatform = { platformId ->
                        app.refreshInteractor.refresh(platformId).displayState
                    },
                    onRefreshAllConfigured = {
                        WidgetRefreshCoordinator.runBackgroundRefresh(this@MainActivity)
                    },
                    onOpenAppSettings = {
                        startActivity(Intent(this@MainActivity, AppSettingsActivity::class.java))
                    },
                    showPlatformTip = showPlatformTip,
                    showOemBackgroundTip = showOemBackgroundTip,
                    tipLoaded = tipLoaded,
                    onDismissPlatformTip = { showPlatformTip = false },
                    onDismissOemBackgroundTip = { showOemBackgroundTip = false },
                    focusPlatformRequest = focusPlatformRequest,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeFocusExtra(intent)
    }

    private fun consumeFocusExtra(intent: Intent?) {
        val platformId = intent?.getStringExtra(EXTRA_FOCUS_PLATFORM_ID) ?: return
        intent.removeExtra(EXTRA_FOCUS_PLATFORM_ID)
        if (PlatformRegistry.find(platformId) == null) return
        focusPlatformRequest = FocusPlatformRequest(
            platformId = platformId,
            nonce = focusNonce.incrementAndGet(),
        )
    }

    private fun appVersionName(): String = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(
                packageName,
                PackageManager.PackageInfoFlags.of(0),
            ).versionName
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0).versionName
        }
    }.getOrNull().orEmpty()

    companion object {
        const val EXTRA_FOCUS_PLATFORM_ID = "focus_platform_id"
        private val focusNonce = AtomicLong(0L)
        private val updateCheckStarted = AtomicBoolean(false)
    }
}
