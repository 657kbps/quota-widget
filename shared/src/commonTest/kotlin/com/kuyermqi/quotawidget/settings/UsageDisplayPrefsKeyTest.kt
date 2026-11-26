package com.kuyermqi.quotawidget.settings

import com.kuyermqi.quotawidget.domain.UsageDisplayMode
import com.kuyermqi.quotawidget.domain.UsageProgressStyle
import com.kuyermqi.quotawidget.domain.UsageResetStyle
import com.kuyermqi.quotawidget.domain.UsageWindowKind
import com.kuyermqi.quotawidget.platform.PlatformIds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UsageDisplayPrefsKeyTest {
    private fun key(platformId: String, field: UsageDisplayPrefField): String? =
        SettingsUsageDisplayPrefKeys.keyName(platformId, field)

    @Test
    fun platformKeysKeepStoredNames() {
        val expected = mapOf(
            PlatformIds.OPENCODE_GO to listOf(
                "opencode_go_widget_window",
                "opencode_go_usage_display",
                "opencode_go_usage_progress_style",
                "opencode_go_usage_reset_style",
            ),
            PlatformIds.CODEX to listOf(
                "codex_widget_window",
                "codex_usage_display",
                "codex_usage_progress_style",
                "codex_usage_reset_style",
            ),
            PlatformIds.MAGPIE to listOf(
                "magpie_widget_window",
                "magpie_usage_display",
                "magpie_usage_progress_style",
                "magpie_usage_reset_style",
            ),
            PlatformIds.KIMI_CODE to listOf(
                "kimi_code_widget_window",
                "kimi_code_usage_display",
                "kimi_code_usage_progress_style",
                "kimi_code_usage_reset_style",
            ),
            PlatformIds.ZHIPU to listOf(
                "zhipu_widget_window",
                "zhipu_usage_display",
                "zhipu_usage_progress_style",
                "zhipu_usage_reset_style",
            ),
        )
        val fields = UsageDisplayPrefField.entries
        for ((platformId, names) in expected) {
            assertEquals(names, fields.map { key(platformId, it) }, platformId)
        }
    }

    @Test
    fun newApiKeepsOnlyTheStoredNames() {
        assertNull(key(PlatformIds.NEW_API, UsageDisplayPrefField.WINDOW_KIND))
        assertEquals(
            "new_api_usage_display",
            key(PlatformIds.NEW_API, UsageDisplayPrefField.DISPLAY_MODE),
        )
        assertEquals(
            "new_api_usage_progress_style",
            key(PlatformIds.NEW_API, UsageDisplayPrefField.PROGRESS_STYLE),
        )
        assertNull(key(PlatformIds.NEW_API, UsageDisplayPrefField.RESET_STYLE))
    }

    @Test
    fun sessionKeysInterpolateThePlatformId() {
        for (platformId in PlatformIds.sessionPlatforms) {
            assertEquals(
                "session_${platformId}_widget_window",
                key(platformId, UsageDisplayPrefField.WINDOW_KIND),
            )
            assertEquals(
                "session_${platformId}_usage_display",
                key(platformId, UsageDisplayPrefField.DISPLAY_MODE),
            )
            assertEquals(
                "session_${platformId}_usage_progress_style",
                key(platformId, UsageDisplayPrefField.PROGRESS_STYLE),
            )
            assertEquals(
                "session_${platformId}_usage_reset_style",
                key(platformId, UsageDisplayPrefField.RESET_STYLE),
            )
        }
    }

    @Test
    fun platformsWithoutCardPrefsHaveNoKeys() {
        assertEquals(emptySet(), UsageDisplayPrefFields.supportedBy(PlatformIds.DEEPSEEK))
        for (field in UsageDisplayPrefField.entries) {
            assertNull(key(PlatformIds.DEEPSEEK, field))
        }
    }

    @Test
    fun widgetCardSettingsDropUnsupportedKnobs() {
        val prefs = UsageDisplayPrefs(
            windowKind = UsageWindowKind.WEEKLY,
            usageDisplayMode = UsageDisplayMode.REMAINING,
            usageProgressStyle = UsageProgressStyle.CAPSULE,
            usageResetStyle = UsageResetStyle.PILL,
        )
        val newApi = prefs.toWidgetCardSettings(PlatformIds.NEW_API)
        assertEquals(UsageDisplayMode.REMAINING, newApi.usageDisplayMode)
        assertEquals(UsageProgressStyle.CAPSULE, newApi.usageProgressStyle)
        assertNull(newApi.windowKind)
        assertNull(newApi.usageResetStyle)

        val codex = prefs.toWidgetCardSettings(PlatformIds.CODEX)
        assertEquals(UsageWindowKind.WEEKLY, codex.windowKind)
        assertEquals(UsageResetStyle.PILL, codex.usageResetStyle)
    }

    @Test
    fun settingsRoundTripThroughPrefs() {
        val codex = CodexSettings(
            widgetWindowKind = UsageWindowKind.MONTHLY,
            usageDisplayMode = UsageDisplayMode.REMAINING,
            usageProgressStyle = UsageProgressStyle.CAPSULE,
            usageResetStyle = UsageResetStyle.FOOTER_STACKED,
        )
        assertEquals(
            UsageDisplayPrefs(
                windowKind = UsageWindowKind.MONTHLY,
                usageDisplayMode = UsageDisplayMode.REMAINING,
                usageProgressStyle = UsageProgressStyle.CAPSULE,
                usageResetStyle = UsageResetStyle.FOOTER_STACKED,
            ),
            codex.toUsageDisplayPrefs(),
        )
        val newApi = NewApiSettings(
            usageDisplayMode = UsageDisplayMode.REMAINING,
            usageProgressStyle = UsageProgressStyle.CAPSULE,
        ).toUsageDisplayPrefs()
        assertEquals(UsageDisplayMode.REMAINING, newApi.usageDisplayMode)
        assertEquals(UsageProgressStyle.CAPSULE, newApi.usageProgressStyle)
        val card = newApi.toWidgetCardSettings(PlatformIds.NEW_API)
        assertNull(card.windowKind)
        assertNull(card.usageResetStyle)
    }

    @Test
    fun defaultWindowKindFollowsThePlatform() {
        assertEquals(UsageWindowKind.WEEKLY, defaultUsageWindowKind(PlatformIds.CODEX))
        assertEquals(UsageWindowKind.MONTHLY, defaultUsageWindowKind(PlatformIds.WORKBUDDY))
        assertEquals(UsageWindowKind.MONTHLY, defaultUsageWindowKind(PlatformIds.WORKBUDDY_AI))
        assertEquals(UsageWindowKind.ROLLING, defaultUsageWindowKind(PlatformIds.CLINE))
        assertEquals(UsageWindowKind.ROLLING, defaultUsageWindowKind(PlatformIds.MAGPIE))
    }
}
