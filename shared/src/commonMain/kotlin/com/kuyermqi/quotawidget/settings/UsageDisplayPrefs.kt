package com.kuyermqi.quotawidget.settings

import com.kuyermqi.quotawidget.domain.UsageDisplayMode
import com.kuyermqi.quotawidget.domain.UsageProgressStyle
import com.kuyermqi.quotawidget.domain.UsageResetStyle
import com.kuyermqi.quotawidget.domain.UsageWindowKind
import com.kuyermqi.quotawidget.platform.PlatformIds

/** Card knobs every usage widget shares, resolved for one platform. */
data class UsageDisplayPrefs(
    val windowKind: UsageWindowKind,
    val usageDisplayMode: UsageDisplayMode,
    val usageProgressStyle: UsageProgressStyle,
    val usageResetStyle: UsageResetStyle,
) {
    companion object {
        fun defaults(platformId: String): UsageDisplayPrefs = UsageDisplayPrefs(
            windowKind = defaultUsageWindowKind(platformId),
            usageDisplayMode = UsageDisplayMode.fromStorage(null),
            usageProgressStyle = UsageProgressStyle.fromStorage(null),
            usageResetStyle = UsageResetStyle.fromStorage(null),
        )
    }
}

/** Window kind a platform shows when no choice is stored yet. */
fun defaultUsageWindowKind(platformId: String): UsageWindowKind = when (platformId) {
    PlatformIds.CODEX -> UsageWindowKind.WEEKLY
    in PlatformIds.sessionPlatforms -> SessionPlatformSettings.defaultWindowKind(platformId)
    else -> UsageWindowKind.ROLLING
}

enum class UsageDisplayPrefField {
    WINDOW_KIND,
    DISPLAY_MODE,
    PROGRESS_STYLE,
    RESET_STYLE,
}

/** Which card knobs a platform's settings model stores. */
object UsageDisplayPrefFields {
    private val ALL = UsageDisplayPrefField.entries.toSet()

    fun supportedBy(platformId: String): Set<UsageDisplayPrefField> = when (platformId) {
        PlatformIds.OPENCODE_GO,
        PlatformIds.CODEX,
        PlatformIds.MAGPIE,
        PlatformIds.KIMI_CODE,
        PlatformIds.ZHIPU -> ALL
        PlatformIds.NEW_API -> setOf(
            UsageDisplayPrefField.DISPLAY_MODE,
            UsageDisplayPrefField.PROGRESS_STYLE,
        )
        in PlatformIds.sessionPlatforms -> ALL
        else -> emptySet()
    }
}

/**
 * Key naming of one preference store: [prefixOf] yields the platform prefix and [tokens] the
 * suffix of each field. [keyName] answers with null for fields the platform does not store.
 */
class UsageDisplayPrefKeyScheme(
    private val prefixOf: (platformId: String) -> String?,
    private val tokens: Map<UsageDisplayPrefField, String>,
) {
    fun keyName(platformId: String, field: UsageDisplayPrefField): String? {
        if (field !in UsageDisplayPrefFields.supportedBy(platformId)) return null
        val prefix = prefixOf(platformId) ?: return null
        return "${prefix}_${tokens.getValue(field)}"
    }
}

/** Names of the card keys in the `quota_widget_settings` DataStore. */
val SettingsUsageDisplayPrefKeys: UsageDisplayPrefKeyScheme = UsageDisplayPrefKeyScheme(
    prefixOf = { platformId ->
        if (platformId in PlatformIds.sessionPlatforms) "session_$platformId" else platformId
    },
    tokens = mapOf(
        UsageDisplayPrefField.WINDOW_KIND to "widget_window",
        UsageDisplayPrefField.DISPLAY_MODE to "usage_display",
        UsageDisplayPrefField.PROGRESS_STYLE to "usage_progress_style",
        UsageDisplayPrefField.RESET_STYLE to "usage_reset_style",
    ),
)

/** Card knobs actually persisted for [platformId]; null means the platform has no such knob. */
fun UsageDisplayPrefs.toWidgetCardSettings(platformId: String): WidgetCardSettings = WidgetCardSettings(
    usageDisplayMode = usageDisplayMode,
    usageProgressStyle = usageProgressStyle,
    windowKind = windowKind.takeIf { supports(platformId, UsageDisplayPrefField.WINDOW_KIND) },
    usageResetStyle = usageResetStyle.takeIf { supports(platformId, UsageDisplayPrefField.RESET_STYLE) },
)

private fun supports(platformId: String, field: UsageDisplayPrefField): Boolean =
    field in UsageDisplayPrefFields.supportedBy(platformId)

internal fun OpenCodeGoSettings.toUsageDisplayPrefs(): UsageDisplayPrefs = UsageDisplayPrefs(
    windowKind = widgetWindowKind,
    usageDisplayMode = usageDisplayMode,
    usageProgressStyle = usageProgressStyle,
    usageResetStyle = usageResetStyle,
)

internal fun CodexSettings.toUsageDisplayPrefs(): UsageDisplayPrefs = UsageDisplayPrefs(
    windowKind = widgetWindowKind,
    usageDisplayMode = usageDisplayMode,
    usageProgressStyle = usageProgressStyle,
    usageResetStyle = usageResetStyle,
)

internal fun NewApiSettings.toUsageDisplayPrefs(): UsageDisplayPrefs =
    UsageDisplayPrefs.defaults(PlatformIds.NEW_API).copy(
        usageDisplayMode = usageDisplayMode,
        usageProgressStyle = usageProgressStyle,
    )

internal fun MagpieSettings.toUsageDisplayPrefs(): UsageDisplayPrefs = UsageDisplayPrefs(
    windowKind = widgetWindowKind,
    usageDisplayMode = usageDisplayMode,
    usageProgressStyle = usageProgressStyle,
    usageResetStyle = usageResetStyle,
)

internal fun KimiCodeSettings.toUsageDisplayPrefs(): UsageDisplayPrefs = UsageDisplayPrefs(
    windowKind = widgetWindowKind,
    usageDisplayMode = usageDisplayMode,
    usageProgressStyle = usageProgressStyle,
    usageResetStyle = usageResetStyle,
)

internal fun ZhipuSettings.toUsageDisplayPrefs(): UsageDisplayPrefs = UsageDisplayPrefs(
    windowKind = widgetWindowKind,
    usageDisplayMode = usageDisplayMode,
    usageProgressStyle = usageProgressStyle,
    usageResetStyle = usageResetStyle,
)

internal fun SessionPlatformSettings.toUsageDisplayPrefs(): UsageDisplayPrefs = UsageDisplayPrefs(
    windowKind = widgetWindowKind,
    usageDisplayMode = usageDisplayMode,
    usageProgressStyle = usageProgressStyle,
    usageResetStyle = usageResetStyle,
)
