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
}
