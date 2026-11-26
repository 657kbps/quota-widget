package com.kuyermqi.quotawidget.settings

import com.kuyermqi.quotawidget.domain.AppLanguage
import com.kuyermqi.quotawidget.domain.AppSettings
import com.kuyermqi.quotawidget.domain.CurrencyPreference
import com.kuyermqi.quotawidget.domain.DarkThemeMode
import com.kuyermqi.quotawidget.domain.QuotaWindow
import com.kuyermqi.quotawidget.domain.QuotaWindowKind
import com.kuyermqi.quotawidget.domain.RefreshIconPhase
import com.kuyermqi.quotawidget.domain.UsageDisplayMode
import com.kuyermqi.quotawidget.domain.UsageProgressStyle
import com.kuyermqi.quotawidget.domain.UsageResetStyle
import com.kuyermqi.quotawidget.domain.UsageWindowKind
import com.kuyermqi.quotawidget.domain.WidgetDisplayState
import com.kuyermqi.quotawidget.domain.encodeQuotaWindows
import com.kuyermqi.quotawidget.domain.formatBalance
import com.kuyermqi.quotawidget.platform.PlatformIds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class WidgetRenderSnapshotTest {
    @Test
    fun unconfiguredPlatformRendersNotConfigured() {
        val state = record(platformId = PlatformIds.DEEPSEEK, configured = false).toWidgetDisplayState()

        assertEquals(WidgetDisplayState.NotConfigured, state)
    }

    @Test
    fun unknownPlatformRendersNotConfigured() {
        val state = record(platformId = "unknown_platform", configured = false).toWidgetDisplayState()

        assertEquals(WidgetDisplayState.NotConfigured, state)
    }

    @Test
    fun missingStatusKeepsLoading() {
        val state = record(platformId = PlatformIds.CODEX, configured = true, status = null)
            .toWidgetDisplayState()

        assertEquals(WidgetDisplayState.Loading, state)
    }

    @Test
    fun errorFallsBackToTheDefaultMessage() {
        val state = record(
            platformId = PlatformIds.CODEX,
            configured = true,
            status = StoredWidgetStatus.ERROR,
        ).toWidgetDisplayState()

        assertEquals(WidgetDisplayState.Error("刷新失败"), state)
    }

    @Test
    fun successUsesTheStoredPayload() {
        val windows = listOf(QuotaWindow(kind = QuotaWindowKind.WEEKLY, usedPercent = 42.0))
        val state = record(
            platformId = PlatformIds.KIMI_CODE,
            configured = true,
            status = StoredWidgetStatus.SUCCESS,
            payloadPlatformName = "Kimi Code",
            formatted = "42%",
            windows = encodeQuotaWindows(windows),
            updatedAtEpochMs = 1_700_000_000_000L,
        ).toWidgetDisplayState()

        val snapshot = assertIs<WidgetDisplayState.Success>(state).snapshot
        assertEquals("Kimi Code", snapshot.platformName)
        assertEquals("42%", snapshot.primaryDisplay)
        assertEquals(windows, snapshot.windows)
        assertEquals(1_700_000_000_000L, snapshot.updatedAtEpochMs)
        assertEquals(PlatformIds.KIMI_CODE, snapshot.platformId)
    }

    @Test
    fun deepSeekSuccessWithoutPayloadFallsBackToBalanceDefaults() {
        val state = record(
            platformId = PlatformIds.DEEPSEEK,
            configured = true,
            status = StoredWidgetStatus.SUCCESS,
            total = "12.50",
            currency = CurrencyPreference.USD.name,
        ).toWidgetDisplayState()

        val snapshot = assertIs<WidgetDisplayState.Success>(state).snapshot
        assertEquals(
            formatBalance(CurrencyPreference.USD, "12.50"),
            snapshot.primaryDisplay,
        )
        assertEquals(listOf(QuotaWindow(kind = QuotaWindowKind.BALANCE)), snapshot.windows)
    }

    @Test
    fun newApiSuccessWithoutPayloadFallsBackToBalanceAndTokenWindows() {
        val state = record(
            platformId = PlatformIds.NEW_API,
            configured = true,
            status = StoredWidgetStatus.SUCCESS,
        ).toWidgetDisplayState()

        val snapshot = assertIs<WidgetDisplayState.Success>(state).snapshot
        assertEquals(
            listOf(
                QuotaWindow(kind = QuotaWindowKind.BALANCE),
                QuotaWindow(kind = QuotaWindowKind.TOKEN),
            ),
            snapshot.windows,
        )
        assertEquals("", snapshot.primaryDisplay)
    }

    @Test
    fun snapshotOnlyMapsTheRequestedPlatforms() {
        val appSettings = AppSettings(
            darkThemeMode = DarkThemeMode.Dark,
            appLanguage = AppLanguage.English,
        )
        val requested = mutableListOf<String>()

        val snapshot = WidgetRenderSnapshot.of(setOf(PlatformIds.OPENCODE_GO), appSettings) { id ->
            requested += id
            renderState()
        }

        assertEquals(listOf(PlatformIds.OPENCODE_GO), requested)
        assertEquals(setOf(PlatformIds.OPENCODE_GO), snapshot.platforms.keys)
        assertEquals(appSettings, snapshot.appSettings)
        assertNull(snapshot.platforms[PlatformIds.CODEX])
        assertEquals(
            WidgetCardSettings(
                usageDisplayMode = UsageDisplayMode.USED,
                usageProgressStyle = UsageProgressStyle.BAR,
                windowKind = UsageWindowKind.ROLLING,
                usageResetStyle = UsageResetStyle.HIDDEN,
            ),
            snapshot.platforms.getValue(PlatformIds.OPENCODE_GO).card,
        )
        assertEquals(
            RefreshIconPhase.Spinning,
            snapshot.platforms.getValue(PlatformIds.OPENCODE_GO).phase,
        )
    }

    @Test
    fun snapshotKeepsEveryRequestedPlatform() {
        val snapshot = WidgetRenderSnapshot.of(
            setOf(PlatformIds.DEEPSEEK, PlatformIds.CODEX),
            AppSettings(),
        ) { renderState() }

        assertEquals(setOf(PlatformIds.DEEPSEEK, PlatformIds.CODEX), snapshot.platforms.keys)
    }

    private fun renderState(): WidgetPlatformRenderState =
        WidgetPlatformRenderState(
            display = WidgetDisplayState.Loading,
            phase = RefreshIconPhase.Spinning,
            card = WidgetCardSettings(
                usageDisplayMode = UsageDisplayMode.USED,
                usageProgressStyle = UsageProgressStyle.BAR,
                windowKind = UsageWindowKind.ROLLING,
                usageResetStyle = UsageResetStyle.HIDDEN,
            ),
        )

    private fun record(
        platformId: String,
        configured: Boolean,
        status: String? = StoredWidgetStatus.SUCCESS,
        payloadPlatformName: String? = null,
        currency: String? = null,
        total: String? = null,
        formatted: String? = null,
        windows: String? = null,
        updatedAtEpochMs: Long? = null,
    ): StoredWidgetRecord = StoredWidgetRecord(
        platformId = platformId,
        configured = configured,
        status = status,
        payloadPlatformName = payloadPlatformName,
        currency = currency,
        total = total,
        formatted = formatted,
        windows = windows,
        updatedAtEpochMs = updatedAtEpochMs,
    )
}
