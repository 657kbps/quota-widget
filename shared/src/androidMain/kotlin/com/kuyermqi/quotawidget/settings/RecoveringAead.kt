package com.kuyermqi.quotawidget.settings

import com.google.crypto.tink.Aead
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.ProviderException

/**
 * Result of [RecoveringAead.load]: the primitive plus whether ciphertexts written by a discarded
 * keyset are still stored and have to be dropped by the next settings write.
 */
internal data class AeadLoadResult(
    val aead: Aead,
    val requiresCiphertextCleanup: Boolean,
)

/**
 * Lazily creates the [Aead] and, when the stored keyset can no longer be read (e.g. the Android
 * Keystore master key did not survive a device migration), discards that keyset and creates a fresh
 * one. Recovery happens at most once per instance; a failing second attempt is propagated.
 *
 * A performed recovery leaves [AeadLoadResult.requiresCiphertextCleanup] set on every following
 * [load], regardless of which path (read or write) triggered it, until the caller reports that the
 * stale ciphertexts have been persisted as removed through [acknowledgeCiphertextCleanup].
 */
internal class RecoveringAead(
    private val createAead: () -> Aead,
    private val resetKeyset: () -> Unit,
) {
    private val lock = Any()

    @Volatile
    private var cached: Aead? = null

    @Volatile
    private var ciphertextCleanupPending = false

    private var recoveryPerformed = false

    fun load(): AeadLoadResult {
        cached?.let { return AeadLoadResult(it, ciphertextCleanupPending) }
        synchronized(lock) {
            cached?.let { return AeadLoadResult(it, ciphertextCleanupPending) }
            return try {
                AeadLoadResult(createAead().also { cached = it }, ciphertextCleanupPending)
            } catch (error: Throwable) {
                if (recoveryPerformed || !isUnusableKeysetFailure(error)) throw error
                recoveryPerformed = true
                resetKeyset()
                ciphertextCleanupPending = true
                AeadLoadResult(createAead().also { cached = it }, requiresCiphertextCleanup = true)
            }
        }
    }

    /**
     * Clears [AeadLoadResult.requiresCiphertextCleanup] once the stale ciphertexts are actually
     * gone from storage. Call it only after that write succeeded.
     */
    fun acknowledgeCiphertextCleanup() {
        synchronized(lock) { ciphertextCleanupPending = false }
    }
}

/**
 * Whether [error] or one of its causes reports a keyset that cannot be read: a missing or unusable
 * Android Keystore master key, or a keyset value that cannot be parsed.
 */
internal fun isUnusableKeysetFailure(error: Throwable): Boolean {
    val visited = mutableSetOf<Throwable>()
    var current: Throwable? = error
    while (current != null && visited.add(current)) {
        if (current is GeneralSecurityException ||
            current is IOException ||
            current is ProviderException
        ) {
            return true
        }
        current = current.cause
    }
    return false
}
