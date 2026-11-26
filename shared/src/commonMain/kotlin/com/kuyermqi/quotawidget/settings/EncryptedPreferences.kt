package com.kuyermqi.quotawidget.settings

private const val ENCRYPTED_PREFERENCE_SUFFIX = "_enc"

/**
 * Whether the stored preference [name] holds a ciphertext produced by the settings [Aead].
 */
internal fun isEncryptedPreferenceName(name: String): Boolean =
    name.endsWith(ENCRYPTED_PREFERENCE_SUFFIX)
