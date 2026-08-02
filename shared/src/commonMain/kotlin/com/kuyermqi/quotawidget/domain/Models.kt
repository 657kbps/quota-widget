package com.kuyermqi.quotawidget.domain

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

enum class CurrencyPreference {
    CNY,
    USD,
    ;

    companion object {
        fun fromStorage(value: String?): CurrencyPreference =
            entries.find { it.name == value } ?: CNY
    }
}

data class BalanceSnapshot(
    val platformId: String,
    val platformName: String,
    val currency: CurrencyPreference,
    val totalBalance: String,
    val formattedBalance: String,
    val updatedAtEpochMs: Long,
)

sealed interface WidgetDisplayState {
    data object NotConfigured : WidgetDisplayState
    data object Loading : WidgetDisplayState
    data class Success(val snapshot: BalanceSnapshot) : WidgetDisplayState
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

private fun formatWhole(value: Double): String =
    kotlin.math.round(value).toLong().toString()

private fun formatTwoDecimals(value: Double): String {
    val cents = kotlin.math.round(value * 100.0).toLong()
    val whole = cents / 100
    val frac = kotlin.math.abs(cents % 100)
    return "$whole.${frac.toString().padStart(2, '0')}"
}
