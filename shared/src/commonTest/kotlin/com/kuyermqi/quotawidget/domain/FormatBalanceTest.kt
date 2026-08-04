package com.kuyermqi.quotawidget.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FormatBalanceTest {
    @Test
    fun formatBalance_keepsTwoDecimalsForSmallAmounts() {
        assertEquals("￥12.34", formatBalance(CurrencyPreference.CNY, "12.34"))
        assertEquals("$1.00", formatBalance(CurrencyPreference.USD, "1"))
    }

    @Test
    fun formatBalance_preservesNegativeSignForSubDollarAmounts() {
        assertEquals("$-0.10", formatBalance(CurrencyPreference.USD, "-0.1"))
        assertEquals("￥-0.01", formatBalance(CurrencyPreference.CNY, "-0.01"))
    }

    @Test
    fun formatBalance_usesSuffixForLargeAmounts() {
        assertEquals("￥12K", formatBalance(CurrencyPreference.CNY, "12345"))
        assertEquals("$2M", formatBalance(CurrencyPreference.USD, "1500000"))
    }

    @Test
    fun formatUsagePrimaryDisplay_joinsWindows() {
        val display = formatUsagePrimaryDisplay(
            listOf(
                QuotaWindow(kind = QuotaWindowKind.FIVE_HOUR, usedPercent = 19.5),
                QuotaWindow(kind = QuotaWindowKind.WEEKLY, usedPercent = 30.0),
                QuotaWindow(kind = QuotaWindowKind.MONTHLY, usedPercent = 12.0),
            ),
        )
        assertEquals("5h 19.5 % · 周 30 % · 月 12 %", display)
    }

    @Test
    fun formatUsageRemainingRolling_usesRemainingPercent() {
        val remaining = formatUsageRemainingRolling(
            listOf(QuotaWindow(kind = QuotaWindowKind.FIVE_HOUR, usedPercent = 32.0)),
        )
        assertEquals("68 %", remaining)
    }

    @Test
    fun formatUsageDisplayPercent_followsDisplayMode() {
        assertEquals("90 %", formatUsageDisplayPercent(90.0, UsageDisplayMode.USED))
        assertEquals("10 %", formatUsageDisplayPercent(90.0, UsageDisplayMode.REMAINING))
    }

    @Test
    fun displayUsageFillFraction_followsDisplayMode() {
        assertEquals(0.9f, displayUsageFillFraction(90.0, UsageDisplayMode.USED))
        assertEquals(0.1f, displayUsageFillFraction(90.0, UsageDisplayMode.REMAINING))
    }

    @Test
    fun isUsageNearLimitForDisplay_warnsNearLimitInBothModes() {
        assertEquals(true, isUsageNearLimitForDisplay(90.0, UsageDisplayMode.USED))
        assertEquals(true, isUsageNearLimitForDisplay(90.0, UsageDisplayMode.REMAINING))
        assertEquals(false, isUsageNearLimitForDisplay(89.9, UsageDisplayMode.USED))
        assertEquals(false, isUsageNearLimitForDisplay(89.9, UsageDisplayMode.REMAINING))
    }

    @Test
    fun formatUsageForWindow_usesSelectedMode() {
        val windows = listOf(QuotaWindow(kind = QuotaWindowKind.WEEKLY, usedPercent = 25.0))
        assertEquals(
            "25 %",
            formatUsageForWindow(
                windows,
                UsageWindowKind.WEEKLY,
                UsageDisplayMode.USED,
            ),
        )
        assertEquals(
            "75 %",
            formatUsageForWindow(
                windows,
                UsageWindowKind.WEEKLY,
                UsageDisplayMode.REMAINING,
            ),
        )
    }

    @Test
    fun openCodeUsageDisplayMode_defaultsToUsed() {
        assertEquals(UsageDisplayMode.USED, UsageDisplayMode.fromStorage(null))
        assertEquals(UsageDisplayMode.USED, UsageDisplayMode.fromStorage("unknown"))
        assertEquals(
            UsageDisplayMode.REMAINING,
            UsageDisplayMode.fromStorage("REMAINING"),
        )
    }

    @Test
    fun usageProgressStyle_defaultsToBar() {
        assertEquals(UsageProgressStyle.BAR, UsageProgressStyle.fromStorage(null))
        assertEquals(UsageProgressStyle.BAR, UsageProgressStyle.fromStorage("unknown"))
        assertEquals(
            UsageProgressStyle.CAPSULE,
            UsageProgressStyle.fromStorage("CAPSULE"),
        )
    }

    @Test
    fun defaultUsageWindowKind_picksShortestAvailable() {
        assertEquals(
            UsageWindowKind.MONTHLY,
            defaultUsageWindowKind(
                listOf(QuotaWindow(kind = QuotaWindowKind.MONTHLY, usedPercent = 10.0)),
            ),
        )
        assertEquals(
            UsageWindowKind.WEEKLY,
            defaultUsageWindowKind(
                listOf(
                    QuotaWindow(kind = QuotaWindowKind.WEEKLY, usedPercent = 10.0),
                    QuotaWindow(kind = QuotaWindowKind.MONTHLY, usedPercent = 20.0),
                ),
            ),
        )
        assertEquals(
            UsageWindowKind.ROLLING,
            defaultUsageWindowKind(
                listOf(
                    QuotaWindow(kind = QuotaWindowKind.FIVE_HOUR, usedPercent = 5.0),
                    QuotaWindow(kind = QuotaWindowKind.WEEKLY, usedPercent = 10.0),
                    QuotaWindow(kind = QuotaWindowKind.MONTHLY, usedPercent = 20.0),
                ),
            ),
        )
    }

    @Test
    fun availableUsageWindowKinds_listsPresentKindsShortestFirst() {
        assertEquals(
            listOf(UsageWindowKind.MONTHLY),
            availableUsageWindowKinds(
                listOf(QuotaWindow(kind = QuotaWindowKind.MONTHLY, usedPercent = 0.0)),
            ),
        )
        assertEquals(
            listOf(UsageWindowKind.WEEKLY, UsageWindowKind.MONTHLY),
            availableUsageWindowKinds(
                listOf(
                    QuotaWindow(kind = QuotaWindowKind.MONTHLY, usedPercent = 1.0),
                    QuotaWindow(kind = QuotaWindowKind.WEEKLY, usedPercent = 2.0),
                ),
            ),
        )
    }

    @Test
    fun resolveCodexUsageSummaryWindowKind_prefersRequestedThenFallsBack() {
        val windows = listOf(
            QuotaWindow(kind = QuotaWindowKind.MONTHLY, usedPercent = 10.0),
        )
        assertEquals(
            UsageWindowKind.MONTHLY,
            resolveCodexUsageSummaryWindowKind(windows, UsageWindowKind.MONTHLY),
        )
        assertEquals(
            UsageWindowKind.MONTHLY,
            resolveCodexUsageSummaryWindowKind(windows, UsageWindowKind.WEEKLY),
        )
        assertNull(
            resolveCodexUsageSummaryWindowKind(emptyList(), UsageWindowKind.WEEKLY),
        )
    }

    @Test
    fun presentCodexOverviewWindowKinds_listsPresentKindsWithoutRequiringPercent() {
        assertEquals(
            listOf(QuotaWindowKind.WEEKLY, QuotaWindowKind.MONTHLY),
            presentCodexOverviewWindowKinds(
                listOf(
                    QuotaWindow(kind = QuotaWindowKind.WEEKLY, usedPercent = null),
                    QuotaWindow(kind = QuotaWindowKind.MONTHLY, usedPercent = 1.0),
                ),
            ),
        )
        assertEquals(
            emptyList(),
            presentCodexOverviewWindowKinds(emptyList()),
        )
    }
}
