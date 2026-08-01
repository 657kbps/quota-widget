package com.kuyermqi.quotawidget.widget

import android.content.Context
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.glance.color.ColorProviders
import androidx.glance.material3.ColorProviders as Material3ColorProviders
import com.kuyermqi.quotawidget.domain.AppSettings
import com.kuyermqi.quotawidget.domain.DarkThemeMode
import com.kuyermqi.quotawidget.domain.ThemeColorMode
import com.materialkolor.dynamicColorScheme

/**
 * Builds Glance [ColorProviders] from [AppSettings], mirroring [com.kuyermqi.quotawidget.ui.theme.QuotaWidgetTheme].
 *
 * Returns null when the widget should use the default system / dynamic Glance theme
 * (follow-system dark mode and follow-system colors).
 */
fun colorProvidersFor(context: Context, settings: AppSettings): ColorProviders? {
    val followSystemColors = settings.themeColorMode == ThemeColorMode.FollowSystem
    val followSystemDark = settings.darkThemeMode == DarkThemeMode.FollowSystem
    if (followSystemColors && followSystemDark) {
        return null
    }

    val seed = Color(settings.customSeedColorArgb)
    val lightScheme = schemeFor(
        context = context,
        themeColorMode = settings.themeColorMode,
        seedColor = seed,
        dark = false,
    )
    val darkScheme = schemeFor(
        context = context,
        themeColorMode = settings.themeColorMode,
        seedColor = seed,
        dark = true,
    )
    return when (settings.darkThemeMode) {
        DarkThemeMode.FollowSystem -> Material3ColorProviders(light = lightScheme, dark = darkScheme)
        DarkThemeMode.Light -> Material3ColorProviders(light = lightScheme, dark = lightScheme)
        DarkThemeMode.Dark -> Material3ColorProviders(light = darkScheme, dark = darkScheme)
    }
}

private fun schemeFor(
    context: Context,
    themeColorMode: ThemeColorMode,
    seedColor: Color,
    dark: Boolean,
): ColorScheme = when (themeColorMode) {
    ThemeColorMode.Custom -> dynamicColorScheme(seedColor = seedColor, isDark = dark)
    ThemeColorMode.FollowSystem -> when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
}
