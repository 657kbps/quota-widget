package com.kuyermqi.quotawidget.provider

import com.kuyermqi.quotawidget.domain.QuotaWindow
import com.kuyermqi.quotawidget.domain.QuotaWindowKind
import com.kuyermqi.quotawidget.domain.UsageDisplayMode
import com.kuyermqi.quotawidget.domain.UsageProgressStyle
import com.kuyermqi.quotawidget.domain.UsageWindowKind
import com.kuyermqi.quotawidget.magpie.MagpieProvider
import com.kuyermqi.quotawidget.settings.FakePlatformSettingsRepository
import com.kuyermqi.quotawidget.settings.MagpieSettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MagpieQuotaProviderTest {
    @Test
    fun refreshDuringPreferenceChange_mergesProvidersAndKeepsPreferences() = runTest {
        val repo = FakePlatformSettingsRepository(
            magpie = MagpieSettings(
                baseUrl = "https://gw.local",
                apiKey = "gateway-key",
                widgetWindowKind = UsageWindowKind.MONTHLY,
                usageDisplayMode = UsageDisplayMode.USED,
                usageProgressStyle = UsageProgressStyle.BAR,
            ),
        )
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val provider = MagpieQuotaProvider(
            MagpieProvidersFetcher { _, _ ->
                started.complete(Unit)
                release.await()
                listOf(fiveHourProvider())
            },
        )

        val job = async { provider.fetch(repo) }
        started.await()
        val inFlight = repo.getMagpieSettings()
        repo.saveMagpieSettings(
            inFlight.copy(
                usageDisplayMode = UsageDisplayMode.REMAINING,
                usageProgressStyle = UsageProgressStyle.CAPSULE,
                selectedProviderId = "antigravity",
            ),
        )
        release.complete(Unit)
        job.await()

        val stored = repo.getMagpieSettings()
        assertEquals(listOf("antigravity"), stored.providers.map { it.id })
        // MONTHLY has no data on that provider, so the clamp applies to the stored preference.
        assertEquals(UsageWindowKind.ROLLING, stored.widgetWindowKind)
        assertEquals(UsageDisplayMode.REMAINING, stored.usageDisplayMode)
        assertEquals(UsageProgressStyle.CAPSULE, stored.usageProgressStyle)
        // An in-flight provider switch wins over the provider this refresh selected.
        assertEquals("antigravity", stored.selectedProviderId)
    }

    @Test
    fun availableWindowKindSurvivesRefresh() = runTest {
        val repo = FakePlatformSettingsRepository(
            magpie = MagpieSettings(
                baseUrl = "https://gw.local",
                apiKey = "gateway-key",
                selectedProviderId = "antigravity",
                widgetWindowKind = UsageWindowKind.ROLLING,
            ),
        )
        val provider = MagpieQuotaProvider(
            MagpieProvidersFetcher { _, _ -> listOf(fiveHourProvider()) },
        )

        provider.fetch(repo)

        val stored = repo.getMagpieSettings()
        assertEquals(listOf("antigravity"), stored.providers.map { it.id })
        assertEquals("antigravity", stored.selectedProviderId)
        assertEquals(UsageWindowKind.ROLLING, stored.widgetWindowKind)
    }

    @Test
    fun gatewayCredentialChangeMidFlight_skipsPersistence() = runTest {
        val repo = FakePlatformSettingsRepository(
            magpie = MagpieSettings(baseUrl = "https://gw.local", apiKey = "gateway-key"),
        )
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val provider = MagpieQuotaProvider(
            MagpieProvidersFetcher { _, _ ->
                started.complete(Unit)
                release.await()
                listOf(fiveHourProvider())
            },
        )

        val job = async { provider.fetch(repo) }
        started.await()
        repo.saveMagpieSettings(MagpieSettings(baseUrl = "https://other.local", apiKey = "new-key"))
        release.complete(Unit)
        job.await()

        val stored = repo.getMagpieSettings()
        assertEquals("https://other.local", stored.baseUrl)
        assertEquals("new-key", stored.apiKey)
        assertTrue(stored.providers.isEmpty())
    }
}

private fun fiveHourProvider(): MagpieProvider = MagpieProvider(
    id = "antigravity",
    name = "Antigravity",
    windows = listOf(QuotaWindow(kind = QuotaWindowKind.FIVE_HOUR, usedPercent = 12.0)),
)
