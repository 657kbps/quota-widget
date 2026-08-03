package com.kuyermqi.quotawidget.domain

import kotlinx.serialization.Serializable

enum class DarkThemeMode {
    FollowSystem,
    Light,
    Dark,
    ;

    companion object {
        fun fromStorage(value: String?): DarkThemeMode =
            entries.find { it.name == value } ?: FollowSystem
    }
}

enum class ThemeColorMode {
    FollowSystem,
    Custom,
    ;

    companion object {
        fun fromStorage(value: String?): ThemeColorMode =
            entries.find { it.name == value } ?: FollowSystem
    }
}

data class AppSettings(
    val darkThemeMode: DarkThemeMode = DarkThemeMode.FollowSystem,
    val themeColorMode: ThemeColorMode = ThemeColorMode.FollowSystem,
    val customSeedColorArgb: Int = DEFAULT_CUSTOM_SEED_COLOR_ARGB,
    val refreshIntervalMinutes: Int = DEFAULT_REFRESH_INTERVAL_MINUTES,
    val checkForUpdatesOnLaunch: Boolean = true,
) {
    init {
        require(refreshIntervalMinutes in ALLOWED_REFRESH_INTERVAL_MINUTES) {
            "refreshIntervalMinutes must be one of $ALLOWED_REFRESH_INTERVAL_MINUTES"
        }
    }
}

const val DEFAULT_CUSTOM_SEED_COLOR_ARGB = 0xFF6750A4.toInt()
const val DEFAULT_REFRESH_INTERVAL_MINUTES = 60
val ALLOWED_REFRESH_INTERVAL_MINUTES = listOf(15, 30, 60, 120, 180, 720, 1440)

@Serializable
enum class CurrencyPreference {
    CNY,
    USD,
    ;

    companion object {
        fun fromStorage(value: String?): CurrencyPreference =
            entries.find { it.name == value } ?: CNY
    }
}

/** DeepSeek balance API result; mapped into [QuotaSnapshot] by [com.kuyermqi.quotawidget.provider.DeepSeekQuotaProvider]. */
data class BalanceSnapshot(
    val platformId: String,
    val platformName: String,
    val currency: CurrencyPreference,
    val totalBalance: String,
    val formattedBalance: String,
    val updatedAtEpochMs: Long,
)

@Serializable
enum class QuotaWindowKind {
    FIVE_HOUR,
    WEEKLY,
    MONTHLY,
    BALANCE,
}

/** Which OpenCode Go usage window the dedicated widget displays. */
enum class OpenCodeWidgetWindowKind {
    ROLLING,
    WEEKLY,
    MONTHLY,
    ;

    fun toQuotaWindowKind(): QuotaWindowKind = when (this) {
        ROLLING -> QuotaWindowKind.FIVE_HOUR
        WEEKLY -> QuotaWindowKind.WEEKLY
        MONTHLY -> QuotaWindowKind.MONTHLY
    }

    companion object {
        fun fromStorage(value: String?): OpenCodeWidgetWindowKind =
            entries.find { it.name == value } ?: ROLLING
    }
}

@Serializable
data class QuotaWindow(
    val kind: QuotaWindowKind,
    val usedPercent: Double? = null,
    val resetInSec: Long? = null,
)

@Serializable
data class QuotaSnapshot(
    val platformId: String,
    val platformName: String,
    val windows: List<QuotaWindow>,
    val primaryDisplay: String,
    val updatedAtEpochMs: Long,
    val currency: CurrencyPreference = CurrencyPreference.CNY,
    val totalBalance: String = "",
)

sealed interface WidgetDisplayState {
    data object NotConfigured : WidgetDisplayState
    data object Loading : WidgetDisplayState
    data object NeedsReauth : WidgetDisplayState
    data class Success(val snapshot: QuotaSnapshot) : WidgetDisplayState
    data class Error(val message: String) : WidgetDisplayState
}

enum class RefreshIconPhase {
    Idle,
    Spinning,
    Settling,
    ;

    companion object {
        fun fromStorage(value: String?): RefreshIconPhase =
            entries.find { it.name == value } ?: Idle
    }
}

class SessionExpiredException(
    message: String = "Session expired",
) : Exception(message)

fun formatBalance(currency: CurrencyPreference, amount: String): String {
    val value = amount.trim().toDoubleOrNull() ?: 0.0
    val abs = kotlin.math.abs(value)
    val (scaled, suffix) = when {
        abs >= 1_000_000_000.0 -> value / 1_000_000_000.0 to "B"
        abs >= 1_000_000.0 -> value / 1_000_000.0 to "M"
        abs >= 10_000.0 -> value / 1_000.0 to "K"
        else -> value to ""
    }
    val symbol = when (currency) {
        CurrencyPreference.CNY -> "￥"
        CurrencyPreference.USD -> "$"
    }
    val number = if (suffix.isEmpty()) {
        formatTwoDecimals(scaled)
    } else {
        formatWhole(scaled)
    }
    return "$symbol$number$suffix"
}

/** Used-percent threshold for warning-colored progress (settings UI and widgets). */
const val USAGE_NEAR_LIMIT_PERCENT = 90.0

fun isUsageNearLimit(usedPercent: Double): Boolean =
    usedPercent >= USAGE_NEAR_LIMIT_PERCENT

fun formatUsagePercent(percent: Double): String {
    val rounded = kotlin.math.round(percent * 10.0) / 10.0
    return if (rounded == kotlin.math.round(rounded)) {
        "${rounded.toLong()} %"
    } else {
        "${formatOneDecimal(rounded)} %"
    }
}

fun formatOpenCodePrimaryDisplay(windows: List<QuotaWindow>): String {
    val parts = buildList {
        windows.find { it.kind == QuotaWindowKind.FIVE_HOUR }?.usedPercent?.let {
            add("5h ${formatUsagePercent(it)}")
        }
        windows.find { it.kind == QuotaWindowKind.WEEKLY }?.usedPercent?.let {
            add("周 ${formatUsagePercent(it)}")
        }
        windows.find { it.kind == QuotaWindowKind.MONTHLY }?.usedPercent?.let {
            add("月 ${formatUsagePercent(it)}")
        }
    }
    return parts.joinToString(" · ")
}

fun remainingUsagePercent(usedPercent: Double): Double =
    (100.0 - usedPercent).coerceIn(0.0, 100.0)

fun formatRemainingUsagePercent(usedPercent: Double): String =
    formatUsagePercent(remainingUsagePercent(usedPercent))

fun formatOpenCodeRemainingForWindow(
    windows: List<QuotaWindow>,
    windowKind: OpenCodeWidgetWindowKind = OpenCodeWidgetWindowKind.ROLLING,
): String? {
    val used = windows.find { it.kind == windowKind.toQuotaWindowKind() }?.usedPercent ?: return null
    return formatRemainingUsagePercent(used)
}

fun formatOpenCodeRemainingRolling(windows: List<QuotaWindow>): String? =
    formatOpenCodeRemainingForWindow(windows, OpenCodeWidgetWindowKind.ROLLING)

private fun formatWhole(value: Double): String =
    kotlin.math.round(value).toLong().toString()

private fun formatTwoDecimals(value: Double): String {
    val cents = kotlin.math.round(value * 100.0).toLong()
    val whole = cents / 100
    val frac = kotlin.math.abs(cents % 100)
    return "$whole.${frac.toString().padStart(2, '0')}"
}

private fun formatOneDecimal(value: Double): String {
    val tenths = kotlin.math.round(value * 10.0).toLong()
    val whole = tenths / 10
    val frac = kotlin.math.abs(tenths % 10)
    return "$whole.$frac"
}
