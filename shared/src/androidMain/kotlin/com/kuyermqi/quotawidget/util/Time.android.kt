package com.kuyermqi.quotawidget.util

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

actual fun parseDateTimeToEpochMs(value: String): Long? {
    val text = value.trim()
    if (text.isEmpty()) return null
    text.toLongOrNull()?.let { numeric ->
        // Providers mix seconds and milliseconds; values this small are seconds.
        return if (numeric < 10_000_000_000L) numeric * 1000L else numeric
    }
    val patterns = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ss.SSS",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd HH:mm:ss",
    )
    for (pattern in patterns) {
        val parsed = runCatching {
            SimpleDateFormat(pattern, Locale.US).apply {
                isLenient = false
                timeZone = if (pattern.endsWith("'Z'")) {
                    TimeZone.getTimeZone("UTC")
                } else if (!pattern.contains("XXX")) {
                    TimeZone.getTimeZone("Asia/Shanghai")
                } else {
                    TimeZone.getDefault()
                }
            }.parse(text)
        }.getOrNull()
        if (parsed != null) return parsed.time
    }
    return null
}
