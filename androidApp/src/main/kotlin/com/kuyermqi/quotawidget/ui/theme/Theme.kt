package com.kuyermqi.quotawidget.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.kuyermqi.quotawidget.domain.DarkThemeMode
import com.kuyermqi.quotawidget.domain.ThemeColorMode
import com.materialkolor.rememberDynamicColorScheme

@Composable
fun QuotaWidgetTheme(
    darkThemeMode: DarkThemeMode = DarkThemeMode.FollowSystem,
    themeColorMode: ThemeColorMode = ThemeColorMode.FollowSystem,
    seedColor: Color = Color(0xFF6750A4),
    content: @Composable () -> Unit,
) {
    val darkTheme = when (darkThemeMode) {
        DarkThemeMode.FollowSystem -> isSystemInDarkTheme()
        DarkThemeMode.Light -> false
        DarkThemeMode.Dark -> true
    }
    val context = LocalContext.current
    val colorScheme = when (themeColorMode) {
        ThemeColorMode.FollowSystem -> when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            darkTheme -> darkColorScheme()
            else -> lightColorScheme()
        }
        ThemeColorMode.Custom -> rememberDynamicColorScheme(
            seedColor = seedColor,
            isDark = darkTheme,
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
