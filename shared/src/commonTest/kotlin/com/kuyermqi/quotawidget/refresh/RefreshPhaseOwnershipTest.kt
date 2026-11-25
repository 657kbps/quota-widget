package com.kuyermqi.quotawidget.refresh

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RefreshPhaseOwnershipTest {
    @Test
    fun shouldResetRefreshPhase_true_whenStoredTokenMatchesOwnToken() {
        assertTrue(shouldResetRefreshPhase(storedStartedAtEpochMs = 1_000L, ownStartedAtEpochMs = 1_000L))
    }

    @Test
    fun shouldResetRefreshPhase_false_whenNewerRunTookOwnership() {
        assertFalse(shouldResetRefreshPhase(storedStartedAtEpochMs = 2_000L, ownStartedAtEpochMs = 1_000L))
    }

    @Test
    fun shouldResetRefreshPhase_false_whenStoredTokenWasCleared() {
        assertFalse(shouldResetRefreshPhase(storedStartedAtEpochMs = 0L, ownStartedAtEpochMs = 1_000L))
    }

    @Test
    fun shouldResetRefreshPhase_false_whenOwnTokenMissing() {
        assertFalse(shouldResetRefreshPhase(storedStartedAtEpochMs = 0L, ownStartedAtEpochMs = 0L))
    }

    @Test
    fun isRefreshPhaseStale_true_forMissingAndExpiredTokens() {
        assertTrue(isRefreshPhaseStale(startedAtEpochMs = 0L, nowEpochMs = 1_000_000L))
        assertTrue(
            isRefreshPhaseStale(
                startedAtEpochMs = 1_000L,
                nowEpochMs = 1_000L + REFRESH_PHASE_STALE_TIMEOUT_MS,
            ),
        )
    }

    @Test
    fun isRefreshPhaseStale_false_forLiveRefresh() {
        assertFalse(isRefreshPhaseStale(startedAtEpochMs = 1_000L, nowEpochMs = 1_500L))
    }
}
