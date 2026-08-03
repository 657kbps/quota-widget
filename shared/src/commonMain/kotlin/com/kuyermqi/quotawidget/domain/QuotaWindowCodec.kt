package com.kuyermqi.quotawidget.domain

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val quotaJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

fun encodeQuotaWindows(windows: List<QuotaWindow>): String =
    quotaJson.encodeToString(ListSerializer(QuotaWindow.serializer()), windows)

fun decodeQuotaWindows(raw: String?): List<QuotaWindow>? {
    if (raw.isNullOrBlank()) return null
    return runCatching {
        quotaJson.decodeFromString(ListSerializer(QuotaWindow.serializer()), raw)
    }.getOrNull()
}
