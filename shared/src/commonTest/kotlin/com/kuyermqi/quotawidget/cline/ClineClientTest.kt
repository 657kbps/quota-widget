package com.kuyermqi.quotawidget.cline

import com.kuyermqi.quotawidget.domain.SessionExpiredException
import com.kuyermqi.quotawidget.settings.SessionPlatformSettings
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class ClineClientTest {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Test
    fun fetchQuotaRefreshesAtMostOnceWhenSessionKeepsLapsing() = runTest {
        val paths = mutableListOf<String>()
        val engine = MockEngine { request ->
            val path = request.url.encodedPath
            paths += path
            if (path.endsWith("/auth/refresh")) {
                respond(
                    content = """{"success":true,"data":{"accessToken":"new","refreshToken":"r2"}}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            } else {
                respond(
                    content = """{"success":false,"error":"unauthorized"}""",
                    status = HttpStatusCode.Unauthorized,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        }
        val client = ClineClient(HttpClient(engine) { expectSuccess = true }, json)
        var saved: SessionPlatformSettings? = null
        val settings = SessionPlatformSettings(
            accessToken = "old",
            refreshToken = "r",
            expiresAtEpochMs = 0L,
        )

        assertFailsWith<SessionExpiredException> {
            client.fetchQuota(settings) { saved = it }
        }

        assertEquals(1, paths.count { it.endsWith("/auth/refresh") })
        assertEquals(2, paths.count { it.endsWith("/users/me") })
        assertEquals(3, paths.size)
        assertNotNull(saved, "refresh must persist the rotated tokens")
        assertEquals("new", saved.accessToken)
    }
}
