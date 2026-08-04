package com.kuyermqi.quotawidget.codex

import java.security.MessageDigest

internal actual fun codexSha256(input: ByteArray): ByteArray =
    MessageDigest.getInstance("SHA-256").digest(input)
