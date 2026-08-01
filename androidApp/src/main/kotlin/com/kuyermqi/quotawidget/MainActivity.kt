package com.kuyermqi.quotawidget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.kuyermqi.quotawidget.ui.SettingsScreen
import com.kuyermqi.quotawidget.ui.theme.QuotaWidgetTheme
import com.kuyermqi.quotawidget.worker.BalanceRefreshWorker

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = application as QuotaWidgetApp
        setContent {
            QuotaWidgetTheme {
                SettingsScreen(
                    settingsRepository = app.settingsRepository,
                    onSettingsSaved = {
                        WorkManager.getInstance(this@MainActivity).enqueue(
                            OneTimeWorkRequestBuilder<BalanceRefreshWorker>().build(),
                        )
                    },
                )
            }
        }
    }
}
