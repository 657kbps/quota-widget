package com.kuyermqi.quotawidget.newapi

import com.kuyermqi.quotawidget.domain.QuotaWindowKind
import com.kuyermqi.quotawidget.domain.UsageDisplayMode
import com.kuyermqi.quotawidget.domain.formatNewApiBalanceTitle
import com.kuyermqi.quotawidget.domain.formatNewApiUsageWidgetTitle
import com.kuyermqi.quotawidget.domain.formatNewApiWidgetFooter
import com.kuyermqi.quotawidget.domain.newApiUsageProgressDisplayMode
import com.kuyermqi.quotawidget.domain.newApiUsageProgressUsedPercent
import com.kuyermqi.quotawidget.domain.newApiUsageWidgetShowsProgress
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NewApiUsageClientTest {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Test
    fun normalizeBaseUrl_trimsSlashAndWhitespace() {
        assertEquals(
            "https://api.example.com",
            NewApiUsageClient.normalizeBaseUrl("  https://api.example.com/  "),
        )
    }

    @Test
    fun usageUrl_joinsPath() {
        assertEquals(
            "https://api.example.com/api/usage/token",
            NewApiUsageClient.usageUrl("https://api.example.com/"),
        )
    }

    @Test
    fun usedPercent_computesFromGranted() {
        assertEquals(
            12.345,
            NewApiUsageClient.usedPercent(
                totalGranted = 1_000_000,
                totalUsed = 123_450,
                unlimited = false,
                totalAvailable = 876_550,
            ),
        )
    }

    @Test
    fun usedPercent_nullWhenUnlimited_zeroGrantedIsZeroPercent() {
        assertNull(
            NewApiUsageClient.usedPercent(
                totalGranted = 1_000_000,
                totalUsed = 1,
                unlimited = true,
            ),
        )
        assertEquals(
            0.0,
            NewApiUsageClient.usedPercent(
                totalGranted = 0,
                totalUsed = 0,
                unlimited = false,
                totalAvailable = 0,
            ),
        )
    }

    @Test
    fun emptyLimitedQuota_bothModesShowZeroPercent() {
        val snapshot = NewApiUsageClient.toSnapshot(
            NewApiTokenUsageDto(
                totalGranted = 0.0,
                totalUsed = 0.0,
                totalAvailable = 0.0,
                unlimitedQuota = false,
            ),
            updatedAtEpochMs = 1L,
        )
        assertTrue(snapshot.emptyLimitedQuota)
        assertEquals(
            "0 %",
            formatNewApiUsageWidgetTitle(snapshot, UsageDisplayMode.USED),
        )
        assertEquals(
            "0 %",
            formatNewApiUsageWidgetTitle(snapshot, UsageDisplayMode.REMAINING),
        )
        assertEquals(
            0.0,
            newApiUsageProgressUsedPercent(snapshot, UsageDisplayMode.REMAINING),
        )
        assertEquals(
            UsageDisplayMode.USED,
            newApiUsageProgressDisplayMode(snapshot, UsageDisplayMode.REMAINING),
        )
    }

    @Test
    fun overspendWithoutGranted_isNotEmptyLimitedQuota() {
        val snapshot = NewApiUsageClient.toSnapshot(
            NewApiTokenUsageDto(
                totalGranted = 0.0,
                totalUsed = 0.0,
                totalAvailable = -50_000.0,
                unlimitedQuota = false,
            ),
            updatedAtEpochMs = 1L,
        )
        assertFalse(snapshot.emptyLimitedQuota)
        assertTrue(snapshot.quotaOverspent)
        assertEquals("$-0.10", snapshot.primaryDisplay)
    }

    @Test
    fun usedPercent_derivesGrantedWhenMissing() {
        assertEquals(
            25.0,
            NewApiUsageClient.usedPercent(
                totalGranted = 0,
                totalUsed = 25,
                unlimited = false,
                totalAvailable = 75,
            ),
        )
    }

    @Test
    fun usedPercent_prefersUsedPlusAvailableWhenInconsistentWithGranted() {
        // granted=100 but used+available=110 → use 110 so % matches amounts
        assertEquals(
            80.0 / 110.0 * 100.0,
            NewApiUsageClient.usedPercent(
                totalGranted = 100,
                totalUsed = 80,
                unlimited = false,
                totalAvailable = 30,
            ),
        )
    }

    @Test
    fun usedPercent_allowsOver100WhenOverspent() {
        assertEquals(
            105.0,
            NewApiUsageClient.usedPercent(
                totalGranted = 1_000_000,
                totalUsed = 1_050_000,
                unlimited = false,
                totalAvailable = -50_000,
            ),
        )
    }

    @Test
    fun formatRemainingBalance_convertsQuotaToUsd() {
        // 987655 / 500000 ≈ 1.97531 → $1.98
        assertEquals(
            "$1.98",
            NewApiUsageClient.formatRemainingBalance(987_655),
        )
        assertEquals(
            "$0.00",
            NewApiUsageClient.formatRemainingBalance(0),
        )
    }

    @Test
    fun formatRemainingBalance_usesCustomQuotaPerUsd() {
        // 1000000 / 1000000 = 1 → $1.00
        assertEquals(
            "$1.00",
            NewApiUsageClient.formatRemainingBalance(
                totalAvailable = 1_000_000,
                quotaPerUsd = 1_000_000,
            ),
        )
    }

    @Test
    fun formatRemainingBalance_showsNegativeWhenOverspent() {
        assertEquals(
            "$-0.10",
            NewApiUsageClient.formatRemainingBalance(-50_000),
        )
    }

    @Test
    fun formatQuotaUsd_showsLessThanOneCentForSubCentAmounts() {
        // 1 / 500000 = 0.000002 → <$0.01
        assertEquals(
            "<$0.01",
            NewApiUsageClient.formatQuotaUsd(1),
        )
        assertEquals(
            "-<$0.01",
            NewApiUsageClient.formatQuotaUsd(-1),
        )
    }

    @Test
    fun toSnapshot_mapsBalanceAndTokenWindows() {
        val snapshot = NewApiUsageClient.toSnapshot(
            NewApiTokenUsageDto(
                name = "Default Token",
                totalGranted = 1_000_000.0,
                totalUsed = 123_450.0,
                totalAvailable = 876_550.0,
                unlimitedQuota = false,
            ),
            updatedAtEpochMs = 1L,
        )
        assertEquals("new_api", snapshot.platformId)
        assertEquals("$1.75", snapshot.primaryDisplay)
        assertEquals("$0.25", snapshot.usedDisplay)
        assertEquals(2, snapshot.windows.size)
        assertEquals(QuotaWindowKind.BALANCE, snapshot.windows[0].kind)
        assertEquals(QuotaWindowKind.TOKEN, snapshot.windows[1].kind)
        assertEquals(12.345, snapshot.windows[1].usedPercent)
        assertEquals(
            "$0.25",
            formatNewApiBalanceTitle(snapshot, UsageDisplayMode.USED),
        )
        assertEquals(
            "$1.75",
            formatNewApiBalanceTitle(snapshot, UsageDisplayMode.REMAINING),
        )
    }

    @Test
    fun toSnapshot_unlimitedRemainingIsInfinity_usedIsAmount() {
        val snapshot = NewApiUsageClient.toSnapshot(
            NewApiTokenUsageDto(
                unlimitedQuota = true,
                totalUsed = 500_000.0,
                totalAvailable = 1_500_000.0,
            ),
            updatedAtEpochMs = 1L,
        )
        assertEquals("∞", snapshot.primaryDisplay)
        assertTrue(snapshot.unlimitedQuota)
        assertEquals("$1.00", snapshot.usedDisplay)
        assertNull(snapshot.windows.find { it.kind == QuotaWindowKind.TOKEN }?.usedPercent)
        assertEquals(
            "$1.00",
            formatNewApiBalanceTitle(snapshot, UsageDisplayMode.USED),
        )
        assertEquals(
            "∞",
            formatNewApiBalanceTitle(snapshot, UsageDisplayMode.REMAINING),
        )
        assertEquals(
            "$1.00",
            formatNewApiUsageWidgetTitle(snapshot, UsageDisplayMode.USED),
        )
        assertEquals(
            "∞",
            formatNewApiUsageWidgetTitle(snapshot, UsageDisplayMode.REMAINING),
        )
        assertFalse(newApiUsageWidgetShowsProgress(snapshot))
    }

    @Test
    fun toSnapshot_limitedShowsPercentOnUsageAmountOnBalance() {
        val snapshot = NewApiUsageClient.toSnapshot(
            NewApiTokenUsageDto(
                totalGranted = 1_000_000.0,
                totalUsed = 250_000.0,
                totalAvailable = 750_000.0,
                unlimitedQuota = false,
            ),
            updatedAtEpochMs = 1L,
        )
        assertFalse(snapshot.unlimitedQuota)
        assertTrue(newApiUsageWidgetShowsProgress(snapshot))
        assertEquals(
            "$0.50",
            formatNewApiBalanceTitle(snapshot, UsageDisplayMode.USED),
        )
        assertEquals(
            "$1.50",
            formatNewApiBalanceTitle(snapshot, UsageDisplayMode.REMAINING),
        )
        assertEquals(
            "25 %",
            formatNewApiUsageWidgetTitle(snapshot, UsageDisplayMode.USED),
        )
        assertEquals(
            "75 %",
            formatNewApiUsageWidgetTitle(snapshot, UsageDisplayMode.REMAINING),
        )
    }

    @Test
    fun toSnapshot_overspendShowsNegativeAndOver100Percent() {
        val snapshot = NewApiUsageClient.toSnapshot(
            NewApiTokenUsageDto(
                totalGranted = 1_000_000.0,
                totalUsed = 1_050_000.0,
                totalAvailable = -50_000.0,
                unlimitedQuota = false,
            ),
            updatedAtEpochMs = 1L,
        )
        assertTrue(snapshot.quotaOverspent)
        assertEquals("$-0.10", snapshot.primaryDisplay)
        assertEquals(
            "105 %",
            formatNewApiUsageWidgetTitle(snapshot, UsageDisplayMode.USED),
        )
        assertEquals(
            "0 %",
            formatNewApiUsageWidgetTitle(snapshot, UsageDisplayMode.REMAINING),
        )
    }

    @Test
    fun toSnapshot_marksExpiredToken() {
        val snapshot = NewApiUsageClient.toSnapshot(
            NewApiTokenUsageDto(
                totalGranted = 100.0,
                totalUsed = 10.0,
                totalAvailable = 90.0,
                expiresAt = 1_700_000_000L, // seconds
            ),
            updatedAtEpochMs = 1L,
            nowEpochMs = 1_800_000_000_000L,
        )
        assertTrue(snapshot.tokenExpired)
        assertEquals(
            "令牌已过期 · 更新于刚才",
            formatNewApiWidgetFooter(snapshot, "令牌已过期", "更新于刚才"),
        )
    }

    @Test
    fun toSnapshot_neverExpiresWhenExpiresAtZero() {
        val snapshot = NewApiUsageClient.toSnapshot(
            NewApiTokenUsageDto(
                totalGranted = 100.0,
                totalUsed = 10.0,
                totalAvailable = 90.0,
                expiresAt = 0L,
            ),
            updatedAtEpochMs = 1L,
            nowEpochMs = 1_800_000_000_000L,
        )
        assertFalse(snapshot.tokenExpired)
    }

    @Test
    fun isTokenExpired_acceptsMilliseconds() {
        assertTrue(
            NewApiUsageClient.isTokenExpired(
                expiresAt = 1_700_000_000_000L,
                nowEpochMs = 1_800_000_000_000L,
            ),
        )
    }

    @Test
    fun envelope_acceptsCodeOrSuccess() {
        val withCode = json.decodeFromString(
            NewApiUsageEnvelope.serializer(),
            """{"code":true,"message":"ok","data":{"total_granted":100,"total_used":10,"total_available":90}}""",
        )
        assertTrue(withCode.isOk)
        assertEquals(90L, withCode.data?.totalAvailableLong)

        val withSuccess = json.decodeFromString(
            NewApiUsageEnvelope.serializer(),
            """{"success":true,"data":{"total_granted":100,"total_used":10,"total_available":90}}""",
        )
        assertTrue(withSuccess.isOk)

        val failed = json.decodeFromString(
            NewApiUsageEnvelope.serializer(),
            """{"success":false,"message":"token not found"}""",
        )
        assertFalse(failed.isOk)
    }

    @Test
    fun envelope_acceptsFloatingPointQuotaNumbers() {
        val decoded = json.decodeFromString(
            NewApiUsageEnvelope.serializer(),
            """{"code":true,"data":{"total_granted":100.0,"total_used":10.5,"total_available":89.5}}""",
        )
        assertEquals(10L, decoded.data?.totalUsedLong)
        assertEquals(89L, decoded.data?.totalAvailableLong)
    }
}
