package com.kuyermqi.quotawidget.refresh

/**
 * A refresh phase older than this was left behind by a killed process, not by a live run.
 */
const val REFRESH_PHASE_STALE_TIMEOUT_MS: Long = 45_000L

/**
 * A run may reset the refresh phase to Idle only while it still owns the phase, i.e. the
 * stored start token still equals the token it captured on entry. A newer run stores a new
 * token, so a cancelled run must leave the newer run's Spinning phase untouched.
 */
fun shouldResetRefreshPhase(storedStartedAtEpochMs: Long, ownStartedAtEpochMs: Long): Boolean =
    ownStartedAtEpochMs > 0L && storedStartedAtEpochMs == ownStartedAtEpochMs

fun isRefreshPhaseStale(startedAtEpochMs: Long, nowEpochMs: Long): Boolean =
    startedAtEpochMs <= 0L || nowEpochMs - startedAtEpochMs >= REFRESH_PHASE_STALE_TIMEOUT_MS
