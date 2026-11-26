package com.kuyermqi.quotawidget.codex

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class CodexOAuthPkceTest {
    @Test
    fun pkceSessionCarriesHexVerifierAndDerivedChallenge() {
        val session = CodexOAuth.createPkceSession()

        assertEquals(64, session.state.length)
        assertTrue(session.state.all { it.isHexDigit() })
        assertEquals(128, session.codeVerifier.length)
        assertTrue(session.codeVerifier.all { it.isHexDigit() })
        assertEquals(43, session.codeChallenge.length)
        assertTrue(session.codeChallenge.none { it == '=' || it == '+' || it == '/' })
        assertEquals(
            session.codeChallenge,
            CodexOAuth.generateCodeChallenge(session.codeVerifier),
        )
    }

    @Test
    fun authorizeUrlCarriesPkceStateAndRedirect() {
        val session = CodexOAuth.createPkceSession()

        assertTrue(session.authorizeUrl.startsWith("${CodexOAuth.AUTHORIZE_URL}?"))
        assertTrue(session.authorizeUrl.contains("client_id=${CodexOAuth.CLIENT_ID}"))
        assertTrue(
            session.authorizeUrl.contains(
                "redirect_uri=http%3A%2F%2Flocalhost%3A1455%2Fauth%2Fcallback",
            ),
        )
        assertTrue(session.authorizeUrl.contains("state=${session.state}"))
        assertTrue(session.authorizeUrl.contains("code_challenge=${session.codeChallenge}"))
        assertTrue(session.authorizeUrl.contains("code_challenge_method=S256"))
    }

    @Test
    fun everySessionIsIndependent() {
        val first = CodexOAuth.createPkceSession()
        val second = CodexOAuth.createPkceSession()

        assertNotEquals(first.state, second.state)
        assertNotEquals(first.codeVerifier, second.codeVerifier)
    }

    private fun Char.isHexDigit(): Boolean = this in '0'..'9' || this in 'a'..'f'
}
