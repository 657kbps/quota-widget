package com.kuyermqi.quotawidget.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class RedirectPolicyTest {
    @Test
    fun classifyRedirect_allowsSameOrigin() {
        assertEquals(
            RedirectDecision.Follow,
            classifyRedirect("https://api.example.com/a", "https://api.example.com/b"),
        )
        assertEquals(
            RedirectDecision.Follow,
            classifyRedirect("http://10.0.0.2:3000/a", "http://10.0.0.2:3000/b"),
        )
    }

    @Test
    fun classifyRedirect_rejectsHttpsDowngrade() {
        assertEquals(
            RedirectDecision.Reject,
            classifyRedirect("https://api.example.com/a", "http://api.example.com/a"),
        )
        assertEquals(
            RedirectDecision.Reject,
            classifyRedirect("https://api.example.com/a", "http://10.0.0.2/a"),
        )
        assertEquals(
            RedirectDecision.Reject,
            classifyRedirect("https://api.example.com/a", "not a url"),
        )
    }

    @Test
    fun classifyRedirect_stripsCrossOriginAndUpgradesWithoutStripping() {
        assertEquals(
            RedirectDecision.FollowWithoutSensitiveHeaders,
            classifyRedirect("https://api.example.com/a", "https://cdn.example.net/a"),
        )
        assertEquals(
            RedirectDecision.FollowWithoutSensitiveHeaders,
            classifyRedirect("https://api.example.com/a", "https://api.example.com:8443/a"),
        )
        // Same host, so the same credential owner: the scheme upgrade keeps the headers.
        assertEquals(
            RedirectDecision.Follow,
            classifyRedirect("http://api.example.com/a", "https://api.example.com/a"),
        )
    }

    @Test
    fun classifyRedirect_rejectsNonHttpTargets() {
        assertEquals(
            RedirectDecision.Reject,
            classifyRedirect("https://a.example.com/x", "ftp://a.example.com/y"),
        )
    }

    @Test
    fun resolveRedirectUrl_handlesAbsoluteRootRelativeAndRelativeTargets() {
        assertEquals(
            "https://cdn.example.net/x",
            resolveRedirectUrl("https://api.example.com/a/b", "https://cdn.example.net/x"),
        )
        assertEquals(
            "https://api.example.com/x",
            resolveRedirectUrl("https://api.example.com/a/b", "/x"),
        )
        assertEquals(
            "https://api.example.com/a/x",
            resolveRedirectUrl("https://api.example.com/a/b", "x"),
        )
        assertEquals(
            "https://api.example.com/x",
            resolveRedirectUrl("https://api.example.com/a/b", "//api.example.com/x"),
        )
        assertEquals(
            "ftp://files.example.net/x",
            resolveRedirectUrl("https://api.example.com/a/b", "ftp://files.example.net/x"),
        )
    }

    @Test
    fun redirectToPlainHttpFailsWithoutIssuingTheNextRequest() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val client = testClient { request ->
            requests += request
            respond(
                content = "",
                status = HttpStatusCode.Found,
                headers = headersOf(HttpHeaders.Location, "http://api.example.com/usage"),
            )
        }

        assertFailsWith<InsecureRedirectException> {
            client.get("https://api.example.com/usage")
        }
        assertEquals(1, requests.size)
        client.close()
    }

    @Test
    fun redirectToNonHttpTargetIsRefused() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val client = testClient { request ->
            requests += request
            respond(
                content = "",
                status = HttpStatusCode.Found,
                headers = headersOf(HttpHeaders.Location, "ftp://files.example.com/x"),
            )
        }

        val error = assertFailsWith<IllegalStateException> {
            client.get("https://api.example.com/x")
        }
        assertEquals(IllegalStateException::class, error::class)
        assertEquals(1, requests.size)
        client.close()
    }

    @Test
    fun crossHostRedirectDropsSensitiveHeaders() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val client = testClient { request ->
            requests += request
            if (requests.size == 1) {
                respond(
                    content = "",
                    status = HttpStatusCode.Found,
                    headers = headersOf(HttpHeaders.Location, "https://cdn.example.net/quota"),
                )
            } else {
                respond(content = "{}", status = HttpStatusCode.OK)
            }
        }

        val response = client.get("https://api.example.com/usage") {
            header(HttpHeaders.Authorization, "Bearer secret")
            header("X-Api-Key", "secret")
            header(HttpHeaders.Cookie, "session=1")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(2, requests.size)
        assertEquals("Bearer secret", requests[0].headers[HttpHeaders.Authorization])
        assertEquals("secret", requests[0].headers["X-Api-Key"])
        assertEquals("session=1", requests[0].headers[HttpHeaders.Cookie])
        assertNull(requests[1].headers[HttpHeaders.Authorization])
        assertNull(requests[1].headers["X-Api-Key"])
        assertNull(requests[1].headers[HttpHeaders.Cookie])
        assertEquals("cdn.example.net", requests[1].url.host)
        client.close()
    }

    @Test
    fun sameOriginRedirectKeepsSensitiveHeaders() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val client = testClient { request ->
            requests += request
            if (requests.size == 1) {
                respond(
                    content = "",
                    status = HttpStatusCode.MovedPermanently,
                    headers = headersOf(HttpHeaders.Location, "/v2/usage"),
                )
            } else {
                respond(content = "{}", status = HttpStatusCode.OK)
            }
        }

        client.get("https://api.example.com/usage") {
            header(HttpHeaders.Authorization, "Bearer secret")
        }

        assertEquals(2, requests.size)
        assertEquals("/v2/usage", requests[1].url.encodedPath)
        assertEquals("Bearer secret", requests[1].headers[HttpHeaders.Authorization])
        client.close()
    }

    @Test
    fun movedPostBecomesGetWithoutBody() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val client = testClient { request ->
            requests += request
            if (requests.size == 1) {
                respond(
                    content = "",
                    status = HttpStatusCode.Found,
                    headers = headersOf(HttpHeaders.Location, "/result"),
                )
            } else {
                respond(content = "{}", status = HttpStatusCode.OK)
            }
        }

        client.post("https://api.example.com/submit") {
            setBody("payload")
        }

        assertEquals(HttpMethod.Post, requests[0].method)
        assertEquals("payload", requests[0].body.toByteArray().decodeToString())
        assertEquals(HttpMethod.Get, requests[1].method)
        assertEquals(0, requests[1].body.toByteArray().size)
        client.close()
    }

    @Test
    fun temporaryRedirectKeepsMethodAndBody() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val client = testClient { request ->
            requests += request
            if (requests.size == 1) {
                respond(
                    content = "",
                    status = HttpStatusCode.TemporaryRedirect,
                    headers = headersOf(HttpHeaders.Location, "/result"),
                )
            } else {
                respond(content = "{}", status = HttpStatusCode.OK)
            }
        }

        client.post("https://api.example.com/submit") {
            setBody("payload")
        }

        assertEquals(2, requests.size)
        assertEquals(HttpMethod.Post, requests[1].method)
        assertEquals("payload", requests[1].body.toByteArray().decodeToString())
        client.close()
    }

    @Test
    fun redirectLoopStopsAfterTheHopLimit() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val client = testClient { request ->
            requests += request
            respond(
                content = "",
                status = HttpStatusCode.Found,
                headers = headersOf(HttpHeaders.Location, "/loop"),
            )
        }

        assertFailsWith<TooManyRedirectsException> {
            client.get("https://api.example.com/loop")
        }
        assertEquals(MAX_REDIRECTS + 1, requests.size)
        client.close()
    }

    private fun testClient(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): HttpClient {
        val engine = MockEngine(handler)
        return HttpClient(engine) {
            expectSuccess = true
            followRedirects = false
        }.followRedirectsSafely()
    }
}
