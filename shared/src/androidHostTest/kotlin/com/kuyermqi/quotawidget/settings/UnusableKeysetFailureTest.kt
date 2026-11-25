package com.kuyermqi.quotawidget.settings

import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStoreException
import java.security.ProviderException
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UnusableKeysetFailureTest {
    @Test
    fun reportsKeystoreAndKeysetFailures() {
        assertTrue(isUnusableKeysetFailure(KeyStoreException("master key exists but is unusable")))
        assertTrue(isUnusableKeysetFailure(ProviderException("keystore is unavailable")))
        assertTrue(isUnusableKeysetFailure(IOException("can't read keyset")))
        assertTrue(
            isUnusableKeysetFailure(
                IllegalStateException("failed", GeneralSecurityException("keyset cannot be parsed")),
            ),
        )
    }

    @Test
    fun ignoresUnrelatedFailures() {
        assertFalse(isUnusableKeysetFailure(IllegalStateException("boom")))
        assertFalse(isUnusableKeysetFailure(IllegalArgumentException("bad template")))
    }

    @Test
    fun terminatesOnCyclicCauses() {
        val first = IllegalStateException("first")
        val second = IllegalStateException("second", first)
        first.initCause(second)

        assertFalse(isUnusableKeysetFailure(first))
    }
}
