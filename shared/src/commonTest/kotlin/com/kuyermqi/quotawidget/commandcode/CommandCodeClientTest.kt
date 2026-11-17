package com.kuyermqi.quotawidget.commandcode

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CommandCodeClientTest {
    private val client = CommandCodeClient()

    @Test
    fun loginUrlCarriesLoopbackCallbackAndState() {
        val url = client.createLoginUrl("state-123")

        assertTrue(url.startsWith("https://commandcode.ai/studio/auth/cli?"))
        assertTrue(url.contains("callback=http%3A%2F%2F127.0.0.1%3A53682%2Fcallback"))
        assertTrue(url.contains("state=state-123"))
        assertTrue(url.contains("mode=redirect"))
    }

    @Test
    fun callbackParsesAndDecodesCredentials() {
        val encodedKey = "key" + "%2B" + "1"
        val callback = client.parseLoginCallback(
            "http://127.0.0.1:53682/callback?apiKey=$encodedKey" +
                "&state=state&userId=u1&userName=Test%20User&keyName=my%20key",
        )

        assertEquals("key+1", callback?.apiKey)
        assertEquals("state", callback?.state)
        assertEquals("u1", callback?.userId)
        assertEquals("Test User", callback?.userName)
        assertEquals("my key", callback?.keyName)
    }

    @Test
    fun callbackWithoutKeyIsIgnored() {
        assertNull(client.parseLoginCallback("http://127.0.0.1:53682/callback?state=s"))
        assertNull(client.parseLoginCallback("https://commandcode.ai/studio/auth/cli"))
    }
}
