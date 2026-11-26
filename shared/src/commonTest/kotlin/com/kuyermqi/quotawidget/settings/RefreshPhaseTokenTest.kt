package com.kuyermqi.quotawidget.settings

import com.kuyermqi.quotawidget.domain.RefreshIconPhase
import com.kuyermqi.quotawidget.platform.PlatformIds
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RefreshPhaseTokenTest {
    @Test
    fun setRefreshIconPhaseIfStartedAt_writes_whenTokenMatches() = runTest {
        val repo = FakePlatformSettingsRepository()
        repo.setRefreshStartedAtEpochMs(PlatformIds.DEEPSEEK, 1_000L)
        repo.setRefreshIconPhase(PlatformIds.DEEPSEEK, RefreshIconPhase.Spinning)

        val applied = repo.setRefreshIconPhaseIfStartedAt(
            PlatformIds.DEEPSEEK,
            expectedStartedAtEpochMs = 1_000L,
            phase = RefreshIconPhase.Idle,
        )

        assertTrue(applied)
        assertEquals(RefreshIconPhase.Idle, repo.getRefreshIconPhase(PlatformIds.DEEPSEEK))
    }

    @Test
    fun setRefreshIconPhaseIfStartedAt_skips_whenNewerRunOwnsPhase() = runTest {
        val repo = FakePlatformSettingsRepository()
        repo.setRefreshStartedAtEpochMs(PlatformIds.DEEPSEEK, 2_000L)
        repo.setRefreshIconPhase(PlatformIds.DEEPSEEK, RefreshIconPhase.Spinning)

        val applied = repo.setRefreshIconPhaseIfStartedAt(
            PlatformIds.DEEPSEEK,
            expectedStartedAtEpochMs = 1_000L,
            phase = RefreshIconPhase.Idle,
        )

        assertFalse(applied)
        assertEquals(RefreshIconPhase.Spinning, repo.getRefreshIconPhase(PlatformIds.DEEPSEEK))
    }

    @Test
    fun setRefreshIconPhaseIfStartedAt_skips_whenTokenWasCleared() = runTest {
        val repo = FakePlatformSettingsRepository()
        repo.setRefreshIconPhase(PlatformIds.DEEPSEEK, RefreshIconPhase.Spinning)

        val applied = repo.setRefreshIconPhaseIfStartedAt(
            PlatformIds.DEEPSEEK,
            expectedStartedAtEpochMs = 1_000L,
            phase = RefreshIconPhase.Idle,
        )

        assertFalse(applied)
        assertEquals(RefreshIconPhase.Spinning, repo.getRefreshIconPhase(PlatformIds.DEEPSEEK))
    }
}
