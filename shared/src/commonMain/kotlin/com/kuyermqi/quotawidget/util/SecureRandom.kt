package com.kuyermqi.quotawidget.util

/** Bytes from the platform cryptographically secure random generator. */
expect fun secureRandomBytes(size: Int): ByteArray

/**
 * Uniformly distributed string over [alphabet]. Bytes that would favour the low
 * entries of a non power-of-two alphabet are rejected, so every symbol keeps the
 * same probability — no modulo bias for PKCE verifiers or OAuth state.
 */
fun secureRandomString(length: Int, alphabet: String): String {
    require(length >= 0) { "length must not be negative" }
    require(alphabet.length in 2..256) { "alphabet must hold 2..256 symbols" }
    val upperBound = 256 - (256 % alphabet.length)
    val result = StringBuilder(length)
    while (result.length < length) {
        for (byte in secureRandomBytes(length - result.length)) {
            val value = byte.toInt() and 0xFF
            if (value >= upperBound) continue
            result.append(alphabet[value % alphabet.length])
            if (result.length == length) break
        }
    }
    return result.toString()
}

/** Lowercase hex of [byteCount] secure random bytes. */
fun secureRandomHex(byteCount: Int): String {
    require(byteCount >= 0) { "byteCount must not be negative" }
    val digits = "0123456789abcdef"
    return buildString(byteCount * 2) {
        for (byte in secureRandomBytes(byteCount)) {
            val value = byte.toInt() and 0xFF
            append(digits[value shr 4])
            append(digits[value and 0x0F])
        }
    }
}
