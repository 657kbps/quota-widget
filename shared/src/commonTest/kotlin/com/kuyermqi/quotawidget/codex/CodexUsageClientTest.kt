package com.kuyermqi.quotawidget.codex

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

class CodexUsageClientTest {
    @Test
    fun fetchUsage_unauthorized_isSessionExpired() = runTest {
        val client = clientResponding(HttpStatusCode.Unauthorized, """{"error":"expired"}""")

        assertFailsWith<SessionExpiredException> {
            client.fetchUsage("access-bad", "acc-1")
        }
    }

    @Test
    fun fetchUsage_serverError_keepsStatusCode() = runTest {
        val client = clientResponding(HttpStatusCode.ServiceUnavailable, "upstream down")

        val error = assertFailsWith<HttpStatusException> {
            client.fetchUsage("access", "acc-1")
        }
        assertEquals(HttpStatusCode.ServiceUnavailable.value, error.statusCode)
    }

    @Test
    fun fetchUsage_ok_parsesWindows() = runTest {
        val body = """
            {
              "plan_type": "plus",
              "rate_limit": {
                "primary_window": {
                  "used_percent": 12.5,
                  "limit_window_seconds": 18000,
                  "reset_after_seconds": 600
                }
              }
            }
        """.trimIndent()
        val client = clientResponding(HttpStatusCode.OK, body)

        val snapshot = client.fetchUsage("access", "acc-1")
        assertEquals(1, snapshot.windows.size)
        assertEquals(600L, snapshot.windows.single().resetInSec)
    }

    private fun clientResponding(status: HttpStatusCode, body: String): CodexUsageClient {
        val engine = MockEngine {
            respond(
                content = body,
                status = status,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        return CodexUsageClient(HttpClient(engine) { expectSuccess = true })
    }
}
