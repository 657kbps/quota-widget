package com.kuyermqi.quotawidget.settings

import com.google.crypto.tink.Aead
import java.io.IOException
import java.security.KeyStoreException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RecoveringAeadTest {
    @Test
    fun createsPrimitiveOnlyOnce() {
        var creations = 0
        var resets = 0
        val primitive = FakeAead()
        val recovering = RecoveringAead(
            createAead = {
                creations++
                primitive
            },
            resetKeyset = { resets++ },
        )

        val first = recovering.load()
        val second = recovering.load()

        assertSame(primitive, first.aead)
        assertSame(primitive, second.aead)
        assertFalse(first.requiresCiphertextCleanup)
        assertFalse(second.requiresCiphertextCleanup)
        assertEquals(1, creations)
        assertEquals(0, resets)
    }

    @Test
    fun reportsPendingCleanupForTheRecoveringLoad() {
        var creations = 0
        var resets = 0
        val primitive = FakeAead()
        val recovering = RecoveringAead(
            createAead = {
                creations++
                if (creations == 1) throw KeyStoreException("master key is missing") else primitive
            },
            resetKeyset = { resets++ },
        )

        val recovered = recovering.load()

        assertSame(primitive, recovered.aead)
        assertTrue(recovered.requiresCiphertextCleanup)
        assertEquals(2, creations)
        assertEquals(1, resets)
    }

    @Test
    fun keepsCleanupPendingOnLoadsUntilAcknowledged() {
        var creations = 0
        val primitive = FakeAead()
        val recovering = RecoveringAead(
            createAead = {
                creations++
                if (creations == 1) throw KeyStoreException("master key is missing") else primitive
            },
            resetKeyset = {},
        )

        assertTrue(recovering.load().requiresCiphertextCleanup)
        // A read-path load after the recovery must not consume the pending cleanup.
        assertTrue(recovering.load().requiresCiphertextCleanup)
        assertTrue(recovering.load().requiresCiphertextCleanup)

        recovering.acknowledgeCiphertextCleanup()

        assertFalse(recovering.load().requiresCiphertextCleanup)
        assertEquals(2, creations)
    }

    @Test
    fun keepsCleanupPendingWhenRecoveryCannotCreateThePrimitive() {
        var creations = 0
        var resets = 0
        val primitive = FakeAead()
        val recovering = RecoveringAead(
            createAead = {
                creations++
                if (creations <= 2) throw IOException("keyset is not readable") else primitive
            },
            resetKeyset = { resets++ },
        )

        assertFailsWith<IOException> { recovering.load() }
        val recovered = recovering.load()

        assertSame(primitive, recovered.aead)
        assertTrue(recovered.requiresCiphertextCleanup)
        assertEquals(1, resets)
    }

    @Test
    fun attemptsRecoveryAtMostOnce() {
        var resets = 0
        val recovering = RecoveringAead(
            createAead = { throw IOException("keyset is not readable") },
            resetKeyset = { resets++ },
        )

        assertFailsWith<IOException> { recovering.load() }
        assertFailsWith<IOException> { recovering.load() }

        assertEquals(1, resets)
    }

    @Test
    fun propagatesFailuresThatAreNotKeysetRelated() {
        var resets = 0
        val recovering = RecoveringAead(
            createAead = { throw IllegalStateException("keystore provider is broken") },
            resetKeyset = { resets++ },
        )

        assertFailsWith<IllegalStateException> { recovering.load() }

        assertEquals(0, resets)
    }

    private class FakeAead : Aead {
        override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray = plaintext

        override fun decrypt(ciphertext: ByteArray, associatedData: ByteArray): ByteArray = ciphertext
    }
}
