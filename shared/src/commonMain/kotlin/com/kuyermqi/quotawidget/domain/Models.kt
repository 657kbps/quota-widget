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
    /** Single NewAPI token quota pool. */
    TOKEN,
}

/** Which usage window a percent widget displays (OpenCode Go, Codex, NewAPI, …). */
enum class UsageWindowKind {
    ROLLING,
    WEEKLY,
    MONTHLY,
    TOKEN,
    ;

    fun toQuotaWindowKind(): QuotaWindowKind = when (this) {
        ROLLING -> QuotaWindowKind.FIVE_HOUR
        WEEKLY -> QuotaWindowKind.WEEKLY
        MONTHLY -> QuotaWindowKind.MONTHLY
        TOKEN -> QuotaWindowKind.TOKEN
    }

    companion object {
        fun fromStorage(value: String?): UsageWindowKind =
            entries.find { it.name == value } ?: ROLLING
    }
}

/**
 * Smallest available usage window for widget default:
 * 5h → weekly → monthly.
 */
fun defaultUsageWindowKind(windows: List<QuotaWindow>): UsageWindowKind {
    val kinds = windows.mapNotNull { window ->
        window.usedPercent?.let { window.kind }
    }.toSet()
    return when {
        QuotaWindowKind.FIVE_HOUR in kinds -> UsageWindowKind.ROLLING
        QuotaWindowKind.WEEKLY in kinds -> UsageWindowKind.WEEKLY
        QuotaWindowKind.MONTHLY in kinds -> UsageWindowKind.MONTHLY
        else -> UsageWindowKind.MONTHLY
    }
}

/** Widget window kinds that have data in [windows], shortest first. */
fun availableUsageWindowKinds(windows: List<QuotaWindow>): List<UsageWindowKind> =
    buildList {
        if (windows.any { it.kind == QuotaWindowKind.FIVE_HOUR && it.usedPercent != null }) {
            add(UsageWindowKind.ROLLING)
        }
        if (windows.any { it.kind == QuotaWindowKind.WEEKLY && it.usedPercent != null }) {
            add(UsageWindowKind.WEEKLY)
        }
        if (windows.any { it.kind == QuotaWindowKind.MONTHLY && it.usedPercent != null }) {
            add(UsageWindowKind.MONTHLY)
        }
    }

/** Prefer [preferred]; if that window has no data, use the smallest available kind.
 * Codex-only — OpenCode summaries must not use this fallback.
 */
fun resolveCodexUsageSummaryWindowKind(
    windows: List<QuotaWindow>,
    preferred: UsageWindowKind,
): UsageWindowKind? {
    if (windows.any { it.kind == preferred.toQuotaWindowKind() && it.usedPercent != null }) {
        return preferred
    }
    return availableUsageWindowKinds(windows).firstOrNull()
}

/**
 * Persistable Codex widget window: keep [current] when available, otherwise the
 * smallest window present in [windows].
 */
fun clampCodexWidgetWindowKind(
    current: UsageWindowKind,
    windows: List<QuotaWindow>,
): UsageWindowKind {
    val available = availableUsageWindowKinds(windows)
    if (available.isEmpty() || current in available) return current
    return defaultUsageWindowKind(windows)
}

/**
 * Codex overview rows: kinds present in [windows] (presence only, not usedPercent).
 * Empty when none of the usage windows appear.
 */
fun presentCodexOverviewWindowKinds(windows: List<QuotaWindow>): List<QuotaWindowKind> =
    buildList {
        if (windows.any { it.kind == QuotaWindowKind.FIVE_HOUR }) {
            add(QuotaWindowKind.FIVE_HOUR)
        }
        if (windows.any { it.kind == QuotaWindowKind.WEEKLY }) {
            add(QuotaWindowKind.WEEKLY)
        }
        if (windows.any { it.kind == QuotaWindowKind.MONTHLY }) {
            add(QuotaWindowKind.MONTHLY)
        }
    }
/** Whether usage surfaces show used percent or remaining percent. */
enum class UsageDisplayMode {
    USED,
    REMAINING,
    ;

    companion object {
        fun fromStorage(value: String?): UsageDisplayMode =
            entries.find { it.name == value } ?: USED
    }
}

/** Progress bar layout for usage widgets (percent and overview). */
enum class UsageProgressStyle {
    /** Continuous thin bar; labels outside the bar. */
    BAR,
    /**
     * Rounded usage track.
     * Overview embeds label and percent inside a rounded rectangle;
     * percent widgets draw a pill track under the large value.
     */
    CAPSULE,
    ;

    companion object {
        fun fromStorage(value: String?): UsageProgressStyle =
            entries.find { it.name == value } ?: BAR
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
    /** NewAPI: token has unlimited quota (usage widget shows amounts). */
    val unlimitedQuota: Boolean = false,
    /** NewAPI: formatted used amount for amount-mode display. */
    val usedDisplay: String = "",
    /**
     * NewAPI: limited key with zero granted/used/available.
     * Usage and remaining both display 0% (not 100% remaining).
     */
    val emptyLimitedQuota: Boolean = false,
    /** NewAPI: token [expires_at] is in the past (0 / absent = never expires). */
    val tokenExpired: Boolean = false,
    /** NewAPI: [total_available] is negative (overspend). */
    val quotaOverspent: Boolean = false,
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

/** NewAPI unlimited remaining balance / usage title. */
const val NEW_API_UNLIMITED_REMAINING_DISPLAY = "∞"

/** Remaining-percent threshold equivalent to [USAGE_NEAR_LIMIT_PERCENT] used. */
const val REMAINING_NEAR_LIMIT_PERCENT = 100.0 - USAGE_NEAR_LIMIT_PERCENT

fun isUsageNearLimit(usedPercent: Double): Boolean =
    usedPercent >= USAGE_NEAR_LIMIT_PERCENT

fun isUsageNearLimitForDisplay(
    usedPercent: Double,
    mode: UsageDisplayMode,
): Boolean = when (mode) {
    UsageDisplayMode.USED -> isUsageNearLimit(usedPercent)
    UsageDisplayMode.REMAINING ->
        remainingUsagePercent(usedPercent) <= REMAINING_NEAR_LIMIT_PERCENT
}

fun formatUsagePercent(percent: Double): String {
    val rounded = kotlin.math.round(percent * 10.0) / 10.0
    return if (rounded == kotlin.math.round(rounded)) {
        "${rounded.toLong()} %"
    } else {
        "${formatOneDecimal(rounded)} %"
    }
}

fun formatUsagePrimaryDisplay(windows: List<QuotaWindow>): String {
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

fun displayUsagePercent(
    usedPercent: Double,
    mode: UsageDisplayMode,
): Double = when (mode) {
    UsageDisplayMode.USED -> usedPercent.coerceIn(0.0, 100.0)
    UsageDisplayMode.REMAINING -> remainingUsagePercent(usedPercent)
}

fun displayUsageFillFraction(
    usedPercent: Double,
    mode: UsageDisplayMode,
): Float = (displayUsagePercent(usedPercent, mode) / 100.0).toFloat().coerceIn(0f, 1f)

fun formatRemainingUsagePercent(usedPercent: Double): String =
    formatUsagePercent(remainingUsagePercent(usedPercent))

fun formatUsageDisplayPercent(
    usedPercent: Double,
    mode: UsageDisplayMode,
): String = formatUsagePercent(displayUsagePercent(usedPercent, mode))

/**
 * NewAPI balance widgets: used mode shows currency; remaining mode shows currency
 * or [NEW_API_UNLIMITED_REMAINING_DISPLAY] when unlimited.
 */
fun formatNewApiBalanceTitle(
    snapshot: QuotaSnapshot,
    mode: UsageDisplayMode,
): String? = when (mode) {
    UsageDisplayMode.USED ->
        snapshot.usedDisplay.takeIf { it.isNotBlank() }
    UsageDisplayMode.REMAINING ->
        when {
            snapshot.unlimitedQuota -> NEW_API_UNLIMITED_REMAINING_DISPLAY
            else -> snapshot.primaryDisplay.takeIf { it.isNotBlank() }
        }
}

/**
 * NewAPI usage widget title: unlimited keys use amounts (remaining → ∞);
 * limited keys use percent like OpenCode / Codex.
 * Empty limited pools show 0% for both used and remaining modes.
 * Overspend may show used > 100% (progress fill still caps at 100%).
 */
fun formatNewApiUsageWidgetTitle(
    snapshot: QuotaSnapshot,
    mode: UsageDisplayMode,
): String? {
    if (snapshot.unlimitedQuota) {
        return formatNewApiBalanceTitle(snapshot, mode)
    }
    if (snapshot.emptyLimitedQuota) {
        return formatUsagePercent(0.0)
    }
    val used = snapshot.windows
        .find { it.kind == QuotaWindowKind.TOKEN }
        ?.usedPercent
        ?: return null
    return when (mode) {
        // Allow >100% when overspent; do not coerce like [formatUsageDisplayPercent].
        UsageDisplayMode.USED -> formatUsagePercent(used.coerceAtLeast(0.0))
        UsageDisplayMode.REMAINING -> formatRemainingUsagePercent(used)
    }
}

/** Footer line for NewAPI widgets: expired warning + updated-at text. */
fun formatNewApiWidgetFooter(
    snapshot: QuotaSnapshot,
    expiredLabel: String,
    updatedAtText: String,
): String =
    if (snapshot.tokenExpired) {
        "$expiredLabel · $updatedAtText"
    } else {
        updatedAtText
    }

/** Used-percent value passed into progress tracks for NewAPI (empty pool → 0 for both modes). */
fun newApiUsageProgressUsedPercent(
    snapshot: QuotaSnapshot,
    mode: UsageDisplayMode,
): Double? {
    if (snapshot.unlimitedQuota) return null
    if (snapshot.emptyLimitedQuota) return 0.0
    return snapshot.windows
        .find { it.kind == QuotaWindowKind.TOKEN }
        ?.usedPercent
}

/** Display mode for NewAPI progress fill; empty pool forces USED so fill stays 0%. */
fun newApiUsageProgressDisplayMode(
    snapshot: QuotaSnapshot,
    mode: UsageDisplayMode,
): UsageDisplayMode =
    if (snapshot.emptyLimitedQuota) UsageDisplayMode.USED else mode

fun newApiUsageWidgetShowsProgress(snapshot: QuotaSnapshot): Boolean =
    !snapshot.unlimitedQuota && (
        snapshot.emptyLimitedQuota ||
            snapshot.windows.any { it.kind == QuotaWindowKind.TOKEN && it.usedPercent != null }
        )

fun formatUsageRemainingForWindow(
    windows: List<QuotaWindow>,
    windowKind: UsageWindowKind = UsageWindowKind.ROLLING,
): String? {
    val used = windows.find { it.kind == windowKind.toQuotaWindowKind() }?.usedPercent ?: return null
    return formatRemainingUsagePercent(used)
}

fun formatUsageForWindow(
    windows: List<QuotaWindow>,
    windowKind: UsageWindowKind = UsageWindowKind.ROLLING,
    mode: UsageDisplayMode = UsageDisplayMode.USED,
): String? {
    val used = windows.find { it.kind == windowKind.toQuotaWindowKind() }?.usedPercent ?: return null
    return formatUsageDisplayPercent(used, mode)
}

fun formatUsageRemainingRolling(windows: List<QuotaWindow>): String? =
    formatUsageRemainingForWindow(windows, UsageWindowKind.ROLLING)

private fun formatWhole(value: Double): String {
    val rounded = kotlin.math.round(value).toLong()
    return rounded.toString()
}

private fun formatTwoDecimals(value: Double): String {
    val cents = kotlin.math.round(value * 100.0).toLong()
    val sign = if (cents < 0) "-" else ""
    val absCents = kotlin.math.abs(cents)
    val whole = absCents / 100
    val frac = absCents % 100
    return "$sign$whole.${frac.toString().padStart(2, '0')}"
}

private fun formatOneDecimal(value: Double): String {
    val tenths = kotlin.math.round(value * 10.0).toLong()
    val sign = if (tenths < 0) "-" else ""
    val absTenths = kotlin.math.abs(tenths)
    val whole = absTenths / 10
    val frac = absTenths % 10
    return "$sign$whole.$frac"
}
