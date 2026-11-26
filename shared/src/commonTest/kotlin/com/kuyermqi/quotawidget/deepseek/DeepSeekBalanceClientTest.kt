package com.kuyermqi.quotawidget.deepseek

import com.kuyermqi.quotawidget.domain.CurrencyPreference
import com.kuyermqi.quotawidget.domain.HttpStatusException
import com.kuyermqi.quotawidget.domain.SessionExpiredException
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

class DeepSeekBalanceClientTest {
    @Test
    fun fetchBalance_unauthorized_isSessionExpired() = runTest {
        val client = clientResponding(HttpStatusCode.Unauthorized, """{"error":"invalid key"}""")

        assertFailsWith<SessionExpiredException> {
            client.fetchBalance("sk-bad", CurrencyPreference.CNY)
        }
    }

    @Test
    fun fetchBalance_rateLimited_keepsStatusCode() = runTest {
        val client = clientResponding(HttpStatusCode.TooManyRequests, """{"error":"slow down"}""")

        val error = assertFailsWith<HttpStatusException> {
            client.fetchBalance("sk-test", CurrencyPreference.CNY)
        }
        assertEquals(HttpStatusCode.TooManyRequests.value, error.statusCode)
    }

    private fun clientResponding(status: HttpStatusCode, body: String): DeepSeekBalanceClient {
        val engine = MockEngine {
            respond(
                content = body,
                status = status,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        return DeepSeekBalanceClient(HttpClient(engine) { expectSuccess = true })
    }
}
