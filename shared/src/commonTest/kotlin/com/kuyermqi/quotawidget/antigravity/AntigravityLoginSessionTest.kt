package com.kuyermqi.quotawidget.antigravity

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AntigravityLoginSessionTest {
    @Test
    fun loginSessionBuildsGooglePkceUrl() {
        val session = AntigravityClient.createLoginSession()

        assertTrue(session.authorizeUrl.startsWith("https://accounts.google.com/o/oauth2/v2/auth?"))
        assertTrue(session.authorizeUrl.contains("response_type=code"))
        assertTrue(
            session.authorizeUrl.contains(
                "redirect_uri=http%3A%2F%2Flocalhost%3A53683%2Foauth-callback",
            ),
        )
        assertTrue(session.authorizeUrl.contains("state=${session.state}"))
        assertTrue(session.authorizeUrl.contains("code_challenge_method=S256"))
        assertTrue(session.authorizeUrl.contains("code_challenge="))
        assertEquals("http://localhost:53683/oauth-callback", session.redirectUri)
    }

    @Test
    fun everyLoginSessionIsIndependent() {
        val first = AntigravityClient.createLoginSession()
        val second = AntigravityClient.createLoginSession()

        assertNotEquals(first.state, second.state)
        assertNotEquals(first.codeVerifier, second.codeVerifier)
    }
}
