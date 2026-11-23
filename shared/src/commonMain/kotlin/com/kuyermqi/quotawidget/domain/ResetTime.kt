package com.kuyermqi.quotawidget.domain

/**
 * Absolute reset instant of a window.
 *
 * [QuotaWindow.resetInSec] is a countdown measured when the provider was queried,
 * so it has to be anchored to [QuotaSnapshot.updatedAtEpochMs]. Rendering the raw
 * countdown later reports a reset that may already have happened.
 */
fun QuotaWindow.resetAtEpochMs(fetchedAtEpochMs: Long): Long? {
    val seconds = resetInSec ?: return null
    if (seconds <= 0L || fetchedAtEpochMs <= 0L) return null
    return fetchedAtEpochMs + seconds * 1000L
}

/** [QuotaWindow.resetAtEpochMs] of [kind], or null when that window reports no reset. */
fun QuotaSnapshot.resetAtEpochMs(kind: QuotaWindowKind): Long? =
    windows.find { it.kind == kind }?.resetAtEpochMs(updatedAtEpochMs)

/**
 * Reset instant of [kind], only while it is still ahead of [nowEpochMs].
 * A stale snapshot whose reset already passed shows nothing instead of a
 * countdown stuck at zero.
 */
fun QuotaSnapshot.upcomingResetAtEpochMs(
    kind: QuotaWindowKind,
    nowEpochMs: Long,
): Long? = resetAtEpochMs(kind)?.takeIf { it > nowEpochMs }

/**
 * Short countdown text shared by the pill and overview layouts: `3h12m`,
 * `1d3h`, `45m`, `<1m`. Minutes are floored so the text never runs ahead of
 * the real reset.
 */
fun formatUsageResetCountdown(remainingMillis: Long): String {
    val totalMinutes = (remainingMillis / MILLIS_PER_MINUTE).coerceAtLeast(0L)
    if (totalMinutes < 1L) return "<1m"
    val days = totalMinutes / MINUTES_PER_DAY
    val hours = (totalMinutes / MINUTES_PER_HOUR) % HOURS_PER_DAY
    val minutes = totalMinutes % MINUTES_PER_HOUR
    return when {
        days > 0L && hours > 0L -> "${days}d${hours}h"
        days > 0L -> "${days}d"
        hours > 0L && minutes > 0L -> "${hours}h${minutes}m"
        hours > 0L -> "${hours}h"
        else -> "${minutes}m"
    }
}

private const val MILLIS_PER_MINUTE = 60_000L
private const val MINUTES_PER_HOUR = 60L
private const val MINUTES_PER_DAY = 24L * MINUTES_PER_HOUR
private const val HOURS_PER_DAY = 24L
