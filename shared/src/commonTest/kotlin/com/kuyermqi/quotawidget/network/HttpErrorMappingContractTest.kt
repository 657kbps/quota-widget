package com.kuyermqi.quotawidget.network

import com.kuyermqi.quotawidget.antigravity.AntigravityClient
import com.kuyermqi.quotawidget.cline.ClineClient
import com.kuyermqi.quotawidget.codex.CodexUsageClient
import com.kuyermqi.quotawidget.commandcode.CommandCodeClient
import com.kuyermqi.quotawidget.deepseek.DeepSeekBalanceClient
import com.kuyermqi.quotawidget.domain.CurrencyPreference
import com.kuyermqi.quotawidget.domain.HttpStatusException
import com.kuyermqi.quotawidget.domain.SessionExpiredException
import com.kuyermqi.quotawidget.kimi.KimiCodeUsageClient
import com.kuyermqi.quotawidget.magpie.MagpieQuotaClient
import com.kuyermqi.quotawidget.newapi.NewApiUsageClient
import com.kuyermqi.quotawidget.opencode.OpenCodeGoClient
import com.kuyermqi.quotawidget.settings.SessionPlatformSettings
import com.kuyermqi.quotawidget.workbuddy.WorkBuddyClient
import com.kuyermqi.quotawidget.workbuddy.WorkBuddyRegion
import com.kuyermqi.quotawidget.zhipu.ZhipuUsageClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Every platform client speaks the same failure vocabulary, because
 * `BalanceRefreshInteractor` branches on exactly these two cases: an expired session asks for
 * re-login, and any other status keeps its code so retries can be classified from data.
 */
class HttpErrorMappingContractTest {
    private data class PlatformCase(
        val platform: String,
        val call: suspend (HttpClient) -> Unit,
    )

    /** A live session: long-lived token, refresh token present, so no client rotates before fetching. */
    private val session = SessionPlatformSettings(
        accessToken = "access-token",
        refreshToken = "refresh-token",
        expiresAtEpochMs = Long.MAX_VALUE,
    )

    /** Clients that only rethrow a lapsed session when a key is already stored. */
    private val keyedSession = session.copy(apiKey = "api-key")
    private val saveSession: suspend (SessionPlatformSettings) -> Unit = {}

    private val cases = listOf(
        PlatformCase("DeepSeek") { http ->
            DeepSeekBalanceClient(http).fetchBalance("sk-test", CurrencyPreference.CNY)
        },
        PlatformCase("OpenCode Go") { http ->
            OpenCodeGoClient(http).fetchQuotaByApiKey("sk-test")
        },
        PlatformCase("Codex") { http ->
            CodexUsageClient(http).fetchUsage("access-token", "account-id")
        },
        PlatformCase("NewAPI") { http ->
            NewApiUsageClient(http).fetchUsage("https://api.example.com", "sk-test")
        },
        PlatformCase("Magpie") { http ->
            MagpieQuotaClient(http).fetchProviders("http://magpie.example:3425", "sk-test")
        },
        PlatformCase("Kimi Code") { http ->
            KimiCodeUsageClient(http).fetchUsage("sk-test")
        },
        PlatformCase("Zhipu") { http ->
            ZhipuUsageClient(http).fetchUsage("sk-test")
        },
        PlatformCase("WorkBuddy") { http ->
            WorkBuddyClient(http).fetchQuota(WorkBuddyRegion.CHINA, session, saveSession)
        },
        PlatformCase("Cline Pass") { http ->
            ClineClient(http).fetchQuota(keyedSession, saveSession)
        },
        PlatformCase("Command Code") { http ->
            CommandCodeClient(http).fetchQuota(keyedSession, saveSession)
        },
        PlatformCase("Antigravity") { http ->
            AntigravityClient(http).fetchQuota(session, saveSession)
        },
    )

    @Test
    fun unauthorizedMapsToSessionExpiredOnEveryPlatform() = runTest {
        for (case in cases) {
            assertFailsWith<SessionExpiredException>(case.platform) {
                case.call(clientResponding(HttpStatusCode.Unauthorized))
            }
        }
    }

    @Test
    fun serverErrorKeepsItsStatusCodeOnEveryPlatform() = runTest {
        for (case in cases) {
            val error = assertFailsWith<HttpStatusException>(case.platform) {
                case.call(clientResponding(HttpStatusCode.ServiceUnavailable))
            }
            assertEquals(
                HttpStatusCode.ServiceUnavailable.value,
                error.statusCode,
                "${case.platform} must keep the upstream status",
            )
        }
    }

    private fun clientResponding(status: HttpStatusCode): HttpClient {
        val engine = MockEngine {
            respond(
                content = """{"error":{"message":"contract test"}}""",
                status = status,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        return HttpClient(engine) { expectSuccess = true }
    }
}
