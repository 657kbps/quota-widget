package com.kuyermqi.quotawidget.settings

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.kuyermqi.quotawidget.domain.UsageDisplayMode
import com.kuyermqi.quotawidget.domain.UsageProgressStyle
import com.kuyermqi.quotawidget.domain.UsageResetStyle
import com.kuyermqi.quotawidget.domain.UsageWindowKind

/**
 * Reads the platform's card knobs from a preference store named by [scheme]. Missing or
 * unsupported keys fall back to the platform's defaults.
 */
fun Preferences.toUsageDisplayPrefs(
    platformId: String,
    scheme: UsageDisplayPrefKeyScheme = SettingsUsageDisplayPrefKeys,
): UsageDisplayPrefs {
    fun value(field: UsageDisplayPrefField): String? =
        scheme.keyName(platformId, field)?.let { this[stringPreferencesKey(it)] }
    return UsageDisplayPrefs(
        windowKind = value(UsageDisplayPrefField.WINDOW_KIND)
            ?.let { UsageWindowKind.fromStorage(it) }
            ?: defaultUsageWindowKind(platformId),
        usageDisplayMode = UsageDisplayMode.fromStorage(value(UsageDisplayPrefField.DISPLAY_MODE)),
        usageProgressStyle = UsageProgressStyle.fromStorage(
            value(UsageDisplayPrefField.PROGRESS_STYLE),
        ),
        usageResetStyle = UsageResetStyle.fromStorage(value(UsageDisplayPrefField.RESET_STYLE)),
    )
}

fun MutablePreferences.applyUsageDisplayPrefs(
    platformId: String,
    usagePrefs: UsageDisplayPrefs,
    scheme: UsageDisplayPrefKeyScheme = SettingsUsageDisplayPrefKeys,
) {
    applyWidgetCardSettings(platformId, scheme, usagePrefs.toWidgetCardSettings(platformId))
}

/** Writes [card]; null knobs and fields the platform does not store leave their key alone. */
fun MutablePreferences.applyWidgetCardSettings(
    platformId: String,
    scheme: UsageDisplayPrefKeyScheme,
    card: WidgetCardSettings,
) {
    fun put(field: UsageDisplayPrefField, value: String?) {
        val key = scheme.keyName(platformId, field) ?: return
        if (value != null) this[stringPreferencesKey(key)] = value
    }
    put(UsageDisplayPrefField.WINDOW_KIND, card.windowKind?.name)
    put(UsageDisplayPrefField.DISPLAY_MODE, card.usageDisplayMode.name)
    put(UsageDisplayPrefField.PROGRESS_STYLE, card.usageProgressStyle.name)
    put(UsageDisplayPrefField.RESET_STYLE, card.usageResetStyle?.name)
}
