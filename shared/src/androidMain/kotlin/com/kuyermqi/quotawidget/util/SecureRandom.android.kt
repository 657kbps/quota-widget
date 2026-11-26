package com.kuyermqi.quotawidget.util

import java.security.SecureRandom

private val secureRandom = SecureRandom()

actual fun secureRandomBytes(size: Int): ByteArray {
    require(size >= 0) { "size must not be negative" }
    return ByteArray(size).also(secureRandom::nextBytes)
}
