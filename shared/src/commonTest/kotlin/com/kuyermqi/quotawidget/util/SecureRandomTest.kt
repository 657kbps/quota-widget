package com.kuyermqi.quotawidget.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SecureRandomTest {
    @Test
    fun secureRandomBytes_returnsRequestedLength() {
        assertEquals(0, secureRandomBytes(0).size)
        assertEquals(32, secureRandomBytes(32).size)
        assertEquals(128, secureRandomBytes(128).size)
    }

    @Test
    fun secureRandomHex_usesLowercaseHexOnly() {
        val hex = secureRandomHex(16)

        assertEquals(32, hex.length)
        assertTrue(hex.all { it in '0'..'9' || it in 'a'..'f' })
        assertNotEquals(secureRandomHex(16), hex)
    }

    @Test
    fun secureRandomString_staysInsideAlphabetAndLength() {
        val alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_"
        val value = secureRandomString(32, alphabet)

        assertEquals(32, value.length)
        assertTrue(value.all { it in alphabet })
        assertNotEquals(secureRandomString(32, alphabet), value)
    }

    @Test
    fun secureRandomString_coversEverySymbolOfANonPowerOfTwoAlphabet() {
        val alphabet = "0123456789ABCDEF"
        val seen = secureRandomString(4_096, alphabet).toSet()

        assertEquals(alphabet.toSet(), seen)
    }

    @Test
    fun secureRandomString_rejectsImpossibleArguments() {
        assertFailsWith<IllegalArgumentException> { secureRandomString(-1, "ab") }
        assertFailsWith<IllegalArgumentException> { secureRandomString(4, "a") }
    }
}
