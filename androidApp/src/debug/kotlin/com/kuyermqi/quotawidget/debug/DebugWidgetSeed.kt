package com.kuyermqi.quotawidget.debug

import com.kuyermqi.quotawidget.QuotaWidgetApp
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
import com.kuyermqi.quotawidget.settings.KimiCodeSettings
import com.kuyermqi.quotawidget.settings.MagpieSettings
import com.kuyermqi.quotawidget.settings.OpenCodeGoSettings
import com.kuyermqi.quotawidget.settings.ZhipuSettings
import com.kuyermqi.quotawidget.util.currentTimeMillis

/** Debug-only fake snapshots for screenshotting the widget layouts. */
object DebugWidgetSeed {
    suspend fun seed(app: QuotaWidgetApp) {
        val repo = app.settingsRepository
        val now = currentTimeMillis()

        repo.saveOpenCodeGoSettings(
            OpenCodeGoSettings(
                apiKey = "debug-fake-key",
                widgetWindowKind = UsageWindowKind.ROLLING,
                usageDisplayMode = UsageDisplayMode.USED,
                usageProgressStyle = UsageProgressStyle.BAR,
                usageResetStyle = UsageResetStyle.FOOTER,
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
                apiKey = "debug-fake-key",
                widgetWindowKind = UsageWindowKind.ROLLING,
                usageDisplayMode = UsageDisplayMode.USED,
                usageProgressStyle = UsageProgressStyle.BAR,
                usageResetStyle = UsageResetStyle.FOOTER_STACKED,
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
                apiKey = "debug-fake-key",
                widgetWindowKind = UsageWindowKind.ROLLING,
                usageDisplayMode = UsageDisplayMode.USED,
                usageProgressStyle = UsageProgressStyle.BAR,
                usageResetStyle = UsageResetStyle.PILL,
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
                widgetWindowKind = UsageWindowKind.WEEKLY,
                usageDisplayMode = UsageDisplayMode.USED,
                usageProgressStyle = UsageProgressStyle.CAPSULE,
                usageResetStyle = UsageResetStyle.PILL,
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
                apiKey = "debug-fake-key",
                widgetWindowKind = UsageWindowKind.ROLLING,
                usageDisplayMode = UsageDisplayMode.USED,
                usageProgressStyle = UsageProgressStyle.CAPSULE,
                usageResetStyle = UsageResetStyle.FOOTER,
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
    }

    private fun snapshot(
        platformId: String,
        nowEpochMs: Long,
        windows: List<QuotaWindow>,
    ): QuotaSnapshot = QuotaSnapshot(
        platformId = platformId,
        platformName = PlatformRegistry.displayName(platformId),
        windows = windows,
        primaryDisplay = "",
        updatedAtEpochMs = nowEpochMs,
    )

    private fun window(kind: QuotaWindowKind, used: Double, resetInSec: Long): QuotaWindow =
        QuotaWindow(kind = kind, usedPercent = used, resetInSec = resetInSec)

    private fun minutes(count: Int): Long = count * 60L
}
