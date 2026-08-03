package com.kuyermqi.quotawidget.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class FormatBalanceTest {
    @Test
    fun formatBalance_keepsTwoDecimalsForSmallAmounts() {
        assertEquals("￥12.34", formatBalance(CurrencyPreference.CNY, "12.34"))
        assertEquals("$1.00", formatBalance(CurrencyPreference.USD, "1"))
    }

    @Test
    fun formatBalance_usesSuffixForLargeAmounts() {
        assertEquals("￥12K", formatBalance(CurrencyPreference.CNY, "12345"))
        assertEquals("$2M", formatBalance(CurrencyPreference.USD, "1500000"))
    }

    @Test
    fun formatOpenCodePrimaryDisplay_joinsWindows() {
        val display = formatOpenCodePrimaryDisplay(
            listOf(
                QuotaWindow(kind = QuotaWindowKind.FIVE_HOUR, usedPercent = 19.5),
                QuotaWindow(kind = QuotaWindowKind.WEEKLY, usedPercent = 30.0),
                QuotaWindow(kind = QuotaWindowKind.MONTHLY, usedPercent = 12.0),
            ),
        )
        assertEquals("5h 19.5 % · 周 30 % · 月 12 %", display)
    }

    @Test
    fun formatOpenCodeRemainingRolling_usesRemainingPercent() {
        val remaining = formatOpenCodeRemainingRolling(
            listOf(QuotaWindow(kind = QuotaWindowKind.FIVE_HOUR, usedPercent = 32.0)),
        )
        assertEquals("68 %", remaining)
    }

    @Test
    fun formatOpenCodeUsagePercent_followsDisplayMode() {
        assertEquals("90 %", formatOpenCodeUsagePercent(90.0, OpenCodeUsageDisplayMode.USED))
        assertEquals("10 %", formatOpenCodeUsagePercent(90.0, OpenCodeUsageDisplayMode.REMAINING))
    }

    @Test
    fun displayUsageFillFraction_followsDisplayMode() {
        assertEquals(0.9f, displayUsageFillFraction(90.0, OpenCodeUsageDisplayMode.USED))
        assertEquals(0.1f, displayUsageFillFraction(90.0, OpenCodeUsageDisplayMode.REMAINING))
    }

    @Test
    fun isUsageNearLimitForDisplay_warnsNearLimitInBothModes() {
        assertEquals(true, isUsageNearLimitForDisplay(90.0, OpenCodeUsageDisplayMode.USED))
        assertEquals(true, isUsageNearLimitForDisplay(90.0, OpenCodeUsageDisplayMode.REMAINING))
        assertEquals(false, isUsageNearLimitForDisplay(89.9, OpenCodeUsageDisplayMode.USED))
        assertEquals(false, isUsageNearLimitForDisplay(89.9, OpenCodeUsageDisplayMode.REMAINING))
    }

    @Test
    fun formatOpenCodeUsageForWindow_usesSelectedMode() {
        val windows = listOf(QuotaWindow(kind = QuotaWindowKind.WEEKLY, usedPercent = 25.0))
        assertEquals(
            "25 %",
            formatOpenCodeUsageForWindow(
                windows,
                OpenCodeWidgetWindowKind.WEEKLY,
                OpenCodeUsageDisplayMode.USED,
            ),
        )
        assertEquals(
            "75 %",
            formatOpenCodeUsageForWindow(
                windows,
                OpenCodeWidgetWindowKind.WEEKLY,
                OpenCodeUsageDisplayMode.REMAINING,
            ),
        )
    }

    @Test
    fun openCodeUsageDisplayMode_defaultsToUsed() {
        assertEquals(OpenCodeUsageDisplayMode.USED, OpenCodeUsageDisplayMode.fromStorage(null))
        assertEquals(OpenCodeUsageDisplayMode.USED, OpenCodeUsageDisplayMode.fromStorage("unknown"))
        assertEquals(
            OpenCodeUsageDisplayMode.REMAINING,
            OpenCodeUsageDisplayMode.fromStorage("REMAINING"),
        )
    }
}
