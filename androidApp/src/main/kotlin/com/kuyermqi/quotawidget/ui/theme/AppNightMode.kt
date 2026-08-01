package com.kuyermqi.quotawidget.ui.theme

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.kuyermqi.quotawidget.domain.DarkThemeMode

/**
 * Keeps the platform DayNight configuration in sync with [DarkThemeMode] so Activity
 * window backgrounds (XML themes) match Compose before the first frame.
 */
object AppNightMode {
    private const val PREFS = "quota_widget_night_mode"
    private const val KEY_DARK_THEME_MODE = "dark_theme_mode"

    fun storedMode(context: Context): DarkThemeMode =
        DarkThemeMode.fromStorage(
            context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_DARK_THEME_MODE, null),
        )

    fun apply(context: Context, mode: DarkThemeMode) {
        val appContext = context.applicationContext
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DARK_THEME_MODE, mode.name)
            .apply()

        val nightMode = when (mode) {
            DarkThemeMode.FollowSystem -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            DarkThemeMode.Light -> AppCompatDelegate.MODE_NIGHT_NO
            DarkThemeMode.Dark -> AppCompatDelegate.MODE_NIGHT_YES
        }
        if (AppCompatDelegate.getDefaultNightMode() != nightMode) {
            AppCompatDelegate.setDefaultNightMode(nightMode)
        }
    }
}
