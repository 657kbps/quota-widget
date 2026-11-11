package com.kuyermqi.quotawidget.opencode

import com.kuyermqi.quotawidget.domain.QuotaWindow
import com.kuyermqi.quotawidget.domain.QuotaWindowKind
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Parses the authenticated `GET /console/api/go/status` response.
 *
 * The counters are encoded as decimal strings because the console uses BigInt.
 */
internal object OpenCodeGoStatusParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun parse(body: String, nowEpochMs: Long): List<QuotaWindow> {
        val status = json.decodeFromString(OpenCodeGoStatusResponse.serializer(), body)
        val access = status.access ?: return emptyList()
        val meters = access.meters ?: return emptyList()
        return listOfNotNull(
            meters.fiveHour?.toWindow(QuotaWindowKind.FIVE_HOUR, nowEpochMs),
            meters.week?.toWindow(QuotaWindowKind.WEEKLY, nowEpochMs),
            // The month meter has no reset timestamp; the Console uses the billing period end.
            meters.month?.toWindow(QuotaWindowKind.MONTHLY, nowEpochMs, access.endsAt),
        )
    }

    private fun OpenCodeGoMeterDto.toWindow(
        kind: QuotaWindowKind,
        nowEpochMs: Long,
        fallbackResetsAt: String? = null,
    ): QuotaWindow? {
        val used = usedMicroCents.asLong() ?: return null
        val limit = limitMicroCents.asLong() ?: return null
        if (limit <= 0L) return null
        val usedPercent = if (used >= limit) {
            100.0
        } else {
            used.coerceAtLeast(0L).toDouble() * 100.0 / limit.toDouble()
        }
        val resetInSec = parseIsoInstantMillis(resetsAt ?: fallbackResetsAt)?.let { resetAt ->
            ((resetAt - nowEpochMs) / 1_000L).coerceAtLeast(0L)
        }
        return QuotaWindow(
            kind = kind,
            usedPercent = usedPercent,
            resetInSec = resetInSec,
        )
    }

    private fun JsonElement?.asLong(): Long? =
        (this as? JsonPrimitive)?.longOrNull
}

@Serializable
internal data class OpenCodeGoStatusResponse(
    val access: OpenCodeGoAccessDto? = null,
)

@Serializable
internal data class OpenCodeGoAccessDto(
    val endsAt: String? = null,
    val meters: OpenCodeGoMetersDto? = null,
)

@Serializable
internal data class OpenCodeGoMetersDto(
    val fiveHour: OpenCodeGoMeterDto? = null,
    val week: OpenCodeGoMeterDto? = null,
    val month: OpenCodeGoMeterDto? = null,
)

@Serializable
internal data class OpenCodeGoMeterDto(
    val usedMicroCents: JsonElement? = null,
    val limitMicroCents: JsonElement? = null,
    val resetsAt: String? = null,
)

private val ISO_INSTANT = Regex(
    """^(\d{4})-(\d{2})-(\d{2})[Tt ](\d{2}):(\d{2}):(\d{2})(?:\.(\d{1,9}))?(Z|z|[+-]\d{2}:?\d{2})?$""",
)

/** Parses an ISO-8601 instant without adding a date-time dependency to commonMain. */
internal fun parseIsoInstantMillis(value: String?): Long? {
    if (value.isNullOrBlank()) return null
    val match = ISO_INSTANT.matchEntire(value.trim()) ?: return null
    val year = match.groupValues[1].toInt()
    val month = match.groupValues[2].toInt()
    val day = match.groupValues[3].toInt()
    val hour = match.groupValues[4].toInt()
    val minute = match.groupValues[5].toInt()
    val second = match.groupValues[6].toInt()
    if (month !in 1..12 || day !in 1..31 || hour > 23 || minute > 59 || second > 60) return null

    val fractionMillis = match.groupValues[7]
        .takeIf { it.isNotEmpty() }
        ?.padEnd(3, '0')
        ?.take(3)
        ?.toInt()
        ?: 0
    val offsetSeconds = parseOffsetSeconds(match.groupValues[8]) ?: return null
    val epochSeconds = daysFromCivil(year, month, day) * 86_400L +
        hour * 3_600L +
        minute * 60L +
        second
    return (epochSeconds - offsetSeconds) * 1_000L + fractionMillis
}

private fun parseOffsetSeconds(zone: String): Int? = when {
    zone.isEmpty() || zone == "Z" || zone == "z" -> 0
    else -> {
        val sign = if (zone.startsWith("-")) -1 else 1
        val digits = zone.drop(1).replace(":", "")
        if (digits.length != 4) return null
        val hours = digits.substring(0, 2).toIntOrNull() ?: return null
        val minutes = digits.substring(2, 4).toIntOrNull() ?: return null
        if (hours > 23 || minutes > 59) return null
        sign * (hours * 3_600 + minutes * 60)
    }
}

/** Howard Hinnant's days-from-civil algorithm; returns days since 1970-01-01. */
private fun daysFromCivil(year: Int, month: Int, day: Int): Long {
    var adjustedYear = year
    if (month <= 2) adjustedYear -= 1
    val era = if (adjustedYear >= 0) adjustedYear / 400 else (adjustedYear - 399) / 400
    val yearOfEra = adjustedYear - era * 400
    val monthIndex = if (month > 2) month - 3 else month + 9
    val dayOfYear = (153 * monthIndex + 2) / 5 + day - 1
    val dayOfEra = yearOfEra * 365 + yearOfEra / 4 - yearOfEra / 100 + dayOfYear
    return era.toLong() * 146_097L + dayOfEra - 719_468L
}
