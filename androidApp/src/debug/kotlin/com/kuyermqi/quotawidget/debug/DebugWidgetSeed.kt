package com.kuyermqi.quotawidget.debug

import com.kuyermqi.quotawidget.QuotaWidgetApp
import com.kuyermqi.quotawidget.domain.AppLanguage
import com.kuyermqi.quotawidget.domain.CurrencyPreference
import com.kuyermqi.quotawidget.domain.DarkThemeMode
import com.kuyermqi.quotawidget.domain.QuotaSnapshot
import com.kuyermqi.quotawidget.domain.QuotaWindow
import com.kuyermqi.quotawidget.domain.QuotaWindowKind
import com.kuyermqi.quotawidget.domain.UsageDisplayMode
import com.kuyermqi.quotawidget.domain.UsageProgressStyle
import com.kuyermqi.quotawidget.domain.UsageResetStyle
import com.kuyermqi.quotawidget.domain.UsageWindowKind
import com.kuyermqi.quotawidget.platform.PlatformIds
import com.kuyermqi.quotawidget.platform.PlatformRegistry
import com.kuyermqi.quotawidget.settings.CodexSettings
import com.kuyermqi.quotawidget.settings.DeepSeekSettings
import com.kuyermqi.quotawidget.settings.KimiCodeSettings
import com.kuyermqi.quotawidget.settings.MagpieSettings
import com.kuyermqi.quotawidget.settings.NewApiSettings
import com.kuyermqi.quotawidget.settings.OpenCodeGoSettings
import com.kuyermqi.quotawidget.settings.SessionPlatformSettings
import com.kuyermqi.quotawidget.settings.ZhipuSettings
import com.kuyermqi.quotawidget.util.currentTimeMillis

/** Debug-only fake snapshots for screenshotting the widget layouts. */
object DebugWidgetSeed {
    private const val FAKE_KEY = "debug-fake-key"

    suspend fun seed(
        app: QuotaWidgetApp,
        progressStyle: UsageProgressStyle? = null,
        resetStyle: UsageResetStyle? = null,
        styleOverrides: Map<String, UsageProgressStyle> = emptyMap(),
        language: AppLanguage? = null,
        darkThemeMode: DarkThemeMode? = null,
    ) {
        val repo = app.settingsRepository
        val now = currentTimeMillis()

        fun style(platformId: String, fallback: UsageProgressStyle): UsageProgressStyle =
            styleOverrides[platformId] ?: progressStyle ?: fallback

        if (language != null || darkThemeMode != null) {
            val current = repo.getAppSettings()
            repo.saveAppSettings(
                current.copy(
                    appLanguage = language ?: current.appLanguage,
                    darkThemeMode = darkThemeMode ?: current.darkThemeMode,
                ),
            )
        }

        repo.saveOpenCodeGoSettings(
            OpenCodeGoSettings(
                apiKey = FAKE_KEY,
                widgetWindowKind = UsageWindowKind.ROLLING,
                usageDisplayMode = UsageDisplayMode.USED,
                usageProgressStyle = style(PlatformIds.OPENCODE_GO, UsageProgressStyle.BAR),
                usageResetStyle = resetStyle ?: UsageResetStyle.FOOTER,
            ),
        )
        repo.saveWidgetSuccess(
            PlatformIds.OPENCODE_GO,
            snapshot(
                PlatformIds.OPENCODE_GO,
                now,
                listOf(
                    window(QuotaWindowKind.FIVE_HOUR, 62.0, minutes(3 * 60 + 12)),
                    window(QuotaWindowKind.WEEKLY, 41.0, minutes(3 * 24 * 60 + 4 * 60)),
                    window(QuotaWindowKind.MONTHLY, 18.0, minutes(12 * 24 * 60)),
                ),
            ),
        )

        repo.saveKimiCodeSettings(
            KimiCodeSettings(
                apiKey = FAKE_KEY,
                widgetWindowKind = UsageWindowKind.ROLLING,
                usageDisplayMode = UsageDisplayMode.USED,
                usageProgressStyle = style(PlatformIds.KIMI_CODE, UsageProgressStyle.BAR),
                usageResetStyle = resetStyle ?: UsageResetStyle.FOOTER_STACKED,
            ),
        )
        repo.saveWidgetSuccess(
            PlatformIds.KIMI_CODE,
            snapshot(
                PlatformIds.KIMI_CODE,
                now,
                listOf(
                    window(QuotaWindowKind.FIVE_HOUR, 38.0, minutes(25 * 60 + 30)),
                    window(QuotaWindowKind.WEEKLY, 72.0, minutes(2 * 24 * 60)),
                ),
            ),
        )

        repo.saveMagpieSettings(
            MagpieSettings(
                baseUrl = "http://127.0.0.1:9/",
                apiKey = FAKE_KEY,
                widgetWindowKind = UsageWindowKind.ROLLING,
                usageDisplayMode = UsageDisplayMode.USED,
                usageProgressStyle = style(PlatformIds.MAGPIE, UsageProgressStyle.BAR),
                usageResetStyle = resetStyle ?: UsageResetStyle.PILL,
            ),
        )
        repo.saveWidgetSuccess(
            PlatformIds.MAGPIE,
            snapshot(
                PlatformIds.MAGPIE,
                now,
                listOf(
                    window(QuotaWindowKind.FIVE_HOUR, 55.0, minutes(65)),
                    window(QuotaWindowKind.WEEKLY, 90.0, minutes(27 * 60)),
                    window(QuotaWindowKind.MONTHLY, 20.0, minutes(10 * 24 * 60)),
                ),
            ),
        )

        repo.saveCodexSettings(
            CodexSettings(
                accessToken = "debug-fake-token",
                refreshToken = "debug-fake-refresh",
                accountId = "debug-fake-account",
                email = "codex@example.com",
                planType = "plus",
                widgetWindowKind = UsageWindowKind.WEEKLY,
                usageDisplayMode = UsageDisplayMode.USED,
                usageProgressStyle = style(PlatformIds.CODEX, UsageProgressStyle.CAPSULE),
                usageResetStyle = resetStyle ?: UsageResetStyle.PILL,
            ),
        )
        repo.saveWidgetSuccess(
            PlatformIds.CODEX,
            snapshot(
                PlatformIds.CODEX,
                now,
                listOf(
                    window(QuotaWindowKind.FIVE_HOUR, 72.0, minutes(45)),
                    window(QuotaWindowKind.WEEKLY, 41.0, minutes(5 * 24 * 60)),
                ),
            ),
        )

        repo.saveZhipuSettings(
            ZhipuSettings(
                apiKey = FAKE_KEY,
                widgetWindowKind = UsageWindowKind.ROLLING,
                usageDisplayMode = UsageDisplayMode.USED,
                usageProgressStyle = style(PlatformIds.ZHIPU, UsageProgressStyle.CAPSULE),
                usageResetStyle = resetStyle ?: UsageResetStyle.FOOTER,
            ),
        )
        repo.saveWidgetSuccess(
            PlatformIds.ZHIPU,
            snapshot(
                PlatformIds.ZHIPU,
                now,
                listOf(
                    window(QuotaWindowKind.FIVE_HOUR, 26.0, minutes(2 * 60)),
                    window(QuotaWindowKind.WEEKLY, 41.0, minutes(3 * 24 * 60)),
                ),
            ),
        )

        repo.saveSessionSettings(
            PlatformIds.COMMAND_CODE,
            SessionPlatformSettings(
                apiKey = FAKE_KEY,
                accountId = "debug-account",
                email = "dev@example.com",
                plan = "Pro",
                widgetWindowKind = UsageWindowKind.MONTHLY,
                usageDisplayMode = UsageDisplayMode.USED,
                usageProgressStyle = style(PlatformIds.COMMAND_CODE, UsageProgressStyle.BAR),
                usageResetStyle = resetStyle ?: UsageResetStyle.PILL,
            ),
        )
        repo.saveWidgetSuccess(
            PlatformIds.COMMAND_CODE,
            snapshot(
                PlatformIds.COMMAND_CODE,
                now,
                listOf(
                    window(QuotaWindowKind.FIVE_HOUR, 42.0, minutes(2 * 60 + 35)),
                    window(QuotaWindowKind.WEEKLY, 68.0, minutes(4 * 24 * 60 + 6 * 60)),
                    window(QuotaWindowKind.MONTHLY, 35.0, minutes(18 * 24 * 60)),
                ),
            ),
        )

        repo.saveSessionSettings(
            PlatformIds.CLINE,
            SessionPlatformSettings(
                accessToken = "debug-fake-token",
                refreshToken = "debug-fake-refresh",
                apiKey = FAKE_KEY,
                email = "cline@example.com",
                plan = "Pro",
                widgetWindowKind = UsageWindowKind.ROLLING,
                usageDisplayMode = UsageDisplayMode.USED,
                usageProgressStyle = style(PlatformIds.CLINE, UsageProgressStyle.BAR),
                usageResetStyle = resetStyle ?: UsageResetStyle.FOOTER,
            ),
        )
        repo.saveWidgetSuccess(
            PlatformIds.CLINE,
            snapshot(
                PlatformIds.CLINE,
                now,
                listOf(
                    window(QuotaWindowKind.FIVE_HOUR, 31.0, minutes(4 * 60 + 5)),
                    window(QuotaWindowKind.WEEKLY, 45.0, minutes(6 * 24 * 60)),
                    window(QuotaWindowKind.MONTHLY, 12.0, minutes(21 * 24 * 60)),
                ),
            ),
        )

        repo.saveSessionSettings(
            PlatformIds.WORKBUDDY,
            SessionPlatformSettings(
                accessToken = "debug-fake-token",
                refreshToken = "debug-fake-refresh",
                apiKey = FAKE_KEY,
                uid = "debug-uid",
                email = "buddy@example.com",
                plan = "Team",
                widgetWindowKind = UsageWindowKind.MONTHLY,
                usageDisplayMode = UsageDisplayMode.USED,
                usageProgressStyle = style(PlatformIds.WORKBUDDY, UsageProgressStyle.CAPSULE),
                usageResetStyle = resetStyle ?: UsageResetStyle.PILL,
            ),
        )
        repo.saveWidgetSuccess(
            PlatformIds.WORKBUDDY,
            snapshot(
                PlatformIds.WORKBUDDY,
                now,
                listOf(
                    window(QuotaWindowKind.MONTHLY, 27.0, minutes(13 * 24 * 60 + 6 * 60)),
                ),
            ),
        )

        repo.saveSessionSettings(
            PlatformIds.ANTIGRAVITY,
            SessionPlatformSettings(
                accessToken = "debug-fake-token",
                refreshToken = "debug-fake-refresh",
                apiKey = FAKE_KEY,
                projectId = "debug-project",
                email = "gravity@example.com",
                widgetWindowKind = UsageWindowKind.ROLLING,
                usageDisplayMode = UsageDisplayMode.USED,
                usageProgressStyle = style(PlatformIds.ANTIGRAVITY, UsageProgressStyle.BAR),
                usageResetStyle = resetStyle ?: UsageResetStyle.HIDDEN,
            ),
        )
        repo.saveWidgetSuccess(
            PlatformIds.ANTIGRAVITY,
            snapshot(
                PlatformIds.ANTIGRAVITY,
                now,
                listOf(
                    window(QuotaWindowKind.FIVE_HOUR, 73.0, minutes(2 * 60 + 18)),
                    window(QuotaWindowKind.WEEKLY, 58.0, minutes(6 * 24 * 60)),
                ),
            ),
        )

        repo.saveNewApiSettings(
            NewApiSettings(
                baseUrl = "http://127.0.0.1:9/",
                apiKey = FAKE_KEY,
                usageDisplayMode = UsageDisplayMode.USED,
                usageProgressStyle = style(PlatformIds.NEW_API, UsageProgressStyle.BAR),
            ),
        )
        repo.saveWidgetSuccess(
            PlatformIds.NEW_API,
            snapshot(
                PlatformIds.NEW_API,
                now,
                listOf(window(QuotaWindowKind.TOKEN, 43.0, 0L)),
                primaryDisplay = "$23.45",
            ),
        )

        repo.saveDeepSeekSettings(
            DeepSeekSettings(apiKey = FAKE_KEY, currency = CurrencyPreference.CNY),
        )
        repo.saveWidgetSuccess(
            PlatformIds.DEEPSEEK,
            snapshot(
                PlatformIds.DEEPSEEK,
                now,
                emptyList(),
                primaryDisplay = "¥78.20",
            ),
        )
    }

    private fun snapshot(
        platformId: String,
        nowEpochMs: Long,
        windows: List<QuotaWindow>,
        primaryDisplay: String = "",
    ): QuotaSnapshot = QuotaSnapshot(
        platformId = platformId,
        platformName = PlatformRegistry.displayName(platformId),
        windows = windows,
        primaryDisplay = primaryDisplay,
        updatedAtEpochMs = nowEpochMs,
    )

    private fun window(kind: QuotaWindowKind, used: Double, resetInSec: Long): QuotaWindow =
        QuotaWindow(kind = kind, usedPercent = used, resetInSec = resetInSec)

    private fun minutes(count: Int): Long = count * 60L
}
