package com.kuyermqi.quotawidget.provider

import com.kuyermqi.quotawidget.domain.CurrencyPreference
import com.kuyermqi.quotawidget.domain.QuotaSnapshot
import com.kuyermqi.quotawidget.domain.QuotaWindow
import com.kuyermqi.quotawidget.domain.QuotaWindowKind
import com.kuyermqi.quotawidget.domain.WidgetDisplayState
import com.kuyermqi.quotawidget.domain.formatBalance
import com.kuyermqi.quotawidget.platform.PlatformIds
import com.kuyermqi.quotawidget.platform.PlatformRegistry
import com.kuyermqi.quotawidget.platform.QuotaPlatform
import com.kuyermqi.quotawidget.refresh.BalanceRefreshInteractor
import com.kuyermqi.quotawidget.refresh.BalanceRefreshResult
import com.kuyermqi.quotawidget.settings.DeepSeekSettings
import com.kuyermqi.quotawidget.settings.FakePlatformSettingsRepository
import com.kuyermqi.quotawidget.settings.KimiCodeSettings
import com.kuyermqi.quotawidget.settings.PlatformSettingsRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class BalanceRefreshInteractorCoalescingTest {
    @Test
    fun samePlatformConcurrently_fetchesOnceAndSharesResult() = runTest {
        val repo = FakePlatformSettingsRepository(deepSeek = DeepSeekSettings(apiKey = "sk-test"))
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var fetchCount = 0
        val provider = object : QuotaProvider {
            override val platform: QuotaPlatform = PlatformRegistry.find(PlatformIds.DEEPSEEK)!!

            override suspend fun isConfigured(repo: PlatformSettingsRepository): Boolean =
                repo.getDeepSeekSettings().apiKey.isNotBlank()

            override suspend fun fetch(repo: PlatformSettingsRepository): QuotaSnapshot {
                fetchCount++
                started.complete(Unit)
                release.await()
                return deepSeekSnapshot("1.00")
            }
        }
        val interactor = BalanceRefreshInteractor(repo, listOf(provider))

        val first = async { interactor.refresh(PlatformIds.DEEPSEEK) }
        started.await()
        val second = async { interactor.refresh(PlatformIds.DEEPSEEK) }
        runCurrent()
        assertEquals(1, fetchCount)

        release.complete(Unit)
        val firstResult = first.await()
        val secondResult = second.await()

        assertEquals(1, fetchCount)
        assertEquals(firstResult, secondResult)
        val success = assertIs<WidgetDisplayState.Success>(
            assertIs<BalanceRefreshResult.Completed>(firstResult).state,
        )
        assertEquals("￥1.00", success.snapshot.primaryDisplay)
    }

    @Test
    fun differentPlatformsConcurrently_doNotShareFlights() = runTest {
        val repo = FakePlatformSettingsRepository(
            deepSeek = DeepSeekSettings(apiKey = "sk-test"),
            kimiCode = KimiCodeSettings(apiKey = "sk-kimi-test"),
        )
        val deepSeekStarted = CompletableDeferred<Unit>()
        val deepSeekRelease = CompletableDeferred<Unit>()
        var deepSeekFetches = 0
        var kimiFetches = 0
        val deepSeekProvider = object : QuotaProvider {
            override val platform: QuotaPlatform = PlatformRegistry.find(PlatformIds.DEEPSEEK)!!

            override suspend fun isConfigured(repo: PlatformSettingsRepository): Boolean =
                repo.getDeepSeekSettings().apiKey.isNotBlank()

            override suspend fun fetch(repo: PlatformSettingsRepository): QuotaSnapshot {
                deepSeekFetches++
                deepSeekStarted.complete(Unit)
                deepSeekRelease.await()
                return deepSeekSnapshot("2.00")
            }
        }
        val kimiProvider = object : QuotaProvider {
            override val platform: QuotaPlatform = PlatformRegistry.find(PlatformIds.KIMI_CODE)!!

            override suspend fun isConfigured(repo: PlatformSettingsRepository): Boolean =
                repo.getKimiCodeSettings().apiKey.isNotBlank()

            override suspend fun fetch(repo: PlatformSettingsRepository): QuotaSnapshot {
                kimiFetches++
                return usageSnapshot(PlatformIds.KIMI_CODE, "Kimi Code", 20.0)
            }
        }
        val interactor = BalanceRefreshInteractor(repo, listOf(deepSeekProvider, kimiProvider))

        val blocked = async { interactor.refresh(PlatformIds.DEEPSEEK) }
        deepSeekStarted.await()
        val independent = async { interactor.refresh(PlatformIds.KIMI_CODE) }

        // Kimi finishes while DeepSeek is still on the wire: platforms never block each other.
        assertIs<WidgetDisplayState.Success>(
            assertIs<BalanceRefreshResult.Completed>(independent.await()).state,
        )
        assertEquals(1, kimiFetches)
        assertEquals(1, deepSeekFetches)
        assertEquals(false, blocked.isCompleted)

        deepSeekRelease.complete(Unit)
        assertIs<WidgetDisplayState.Success>(
            assertIs<BalanceRefreshResult.Completed>(blocked.await()).state,
        )
        assertEquals(1, deepSeekFetches)
    }
}

private fun usageSnapshot(
    platformId: String,
    platformName: String,
    percent: Double,
): QuotaSnapshot = QuotaSnapshot(
    platformId = platformId,
    platformName = platformName,
    windows = listOf(QuotaWindow(kind = QuotaWindowKind.FIVE_HOUR, usedPercent = percent)),
    primaryDisplay = "5h $percent%",
    updatedAtEpochMs = 1L,
)

private fun deepSeekSnapshot(amount: String): QuotaSnapshot {
    val currency = CurrencyPreference.CNY
    return QuotaSnapshot(
        platformId = PlatformIds.DEEPSEEK,
        platformName = "DeepSeek",
        windows = listOf(QuotaWindow(kind = QuotaWindowKind.BALANCE)),
        primaryDisplay = formatBalance(currency, amount),
        updatedAtEpochMs = 1L,
        currency = currency,
        totalBalance = amount,
    )
}
