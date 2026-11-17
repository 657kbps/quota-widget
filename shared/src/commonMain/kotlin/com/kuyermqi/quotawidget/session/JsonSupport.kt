package com.kuyermqi.quotawidget.session

import com.kuyermqi.quotawidget.util.parseDateTimeToEpochMs
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

internal fun JsonElement?.asObjectOrNull(): JsonObject? = this as? JsonObject

internal fun JsonObject.stringOrNull(key: String): String? {
    val value = this[key] ?: return null
    if (value is JsonNull) return null
    return (value as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
}

internal fun JsonObject.doubleOrNull(key: String): Double? =
    (this[key] as? JsonPrimitive)?.doubleOrNull

internal fun JsonObject.longOrNull(key: String): Long? =
    (this[key] as? JsonPrimitive)?.longOrNull

internal fun JsonObject.intOrNull(key: String): Int? = longOrNull(key)?.toInt()

internal fun JsonObject.booleanOrNull(key: String): Boolean? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull()

internal fun JsonObject.objectOrNull(key: String): JsonObject? =
    (this[key] as? JsonObject)

internal fun JsonObject.arrayOrNull(key: String): JsonArray? =
    this[key] as? JsonArray

internal fun JsonObject.epochMsOrNull(key: String): Long? =
    stringOrNull(key)?.let(::parseDateTimeToEpochMs)

/** Formats a provider number without a scientific tail or redundant decimals. */
internal fun compactNumber(value: Double): String {
    if (!value.isFinite()) return "0"
    val scaled = kotlin.math.round(value * 100.0) / 100.0
    return if (scaled == kotlin.math.floor(scaled)) {
        scaled.toLong().toString()
    } else {
        scaled.toString().trimEnd('0').trimEnd('.')
    }
}
