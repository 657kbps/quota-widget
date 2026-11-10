package com.kuyermqi.quotawidget.opencode

import com.kuyermqi.quotawidget.domain.QuotaWindow
import com.kuyermqi.quotawidget.domain.QuotaWindowKind
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/**
 * Parses `GET /zen/go/v1/usage`, the API-key auth usage endpoint.
 *
 * It reports `usage.rolling/weekly/monthly.percent` on a 0..100 scale and
 * an ISO `resetsAt` for each window.
 */
internal object OpenCodeGoApiUsageParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun parse(body: String, nowEpochMs: Long): List<QuotaWindow> {
        val usage = json.decodeFromString(OpenCodeGoApiUsageResponse.serializer(), body).usage
            ?: return emptyList()
        return listOfNotNull(
            usage.rolling?.toWindow(QuotaWindowKind.FIVE_HOUR, nowEpochMs),
            usage.weekly?.toWindow(QuotaWindowKind.WEEKLY, nowEpochMs),
            usage.monthly?.toWindow(QuotaWindowKind.MONTHLY, nowEpochMs),
        )
    }

    private fun OpenCodeGoApiWindowDto.toWindow(
        kind: QuotaWindowKind,
        nowEpochMs: Long,
    ): QuotaWindow? {
        val usedPercent = percent.asDouble()?.coerceIn(0.0, 100.0) ?: return null
        return QuotaWindow(
            kind = kind,
            usedPercent = usedPercent,
            resetInSec = resolveResetInSec(nowEpochMs),
        )
    }

    private fun OpenCodeGoApiWindowDto.resolveResetInSec(nowEpochMs: Long): Long? {
        resetInSec.asLong()?.let { return it.coerceAtLeast(0L) }
        resetInSec.asDouble()?.let { return it.toLong().coerceAtLeast(0L) }

        val raw = (resetsAt as? JsonPrimitive)?.content?.trim().orEmpty()
        if (raw.isEmpty()) return null
        parseIsoInstantMillis(raw)?.let { resetAt ->
            return ((resetAt - nowEpochMs) / 1_000L).coerceAtLeast(0L)
        }
        val numeric = raw.toLongOrNull() ?: return null
        val epochMs = if (numeric > 10_000_000_000L) numeric else numeric * 1_000L
        return ((epochMs - nowEpochMs) / 1_000L).coerceAtLeast(0L)
    }

    private fun JsonElement?.asDouble(): Double? =
        (this as? JsonPrimitive)?.doubleOrNull

    private fun JsonElement?.asLong(): Long? =
        (this as? JsonPrimitive)?.let { primitive ->
            primitive.doubleOrNull?.toLong() ?: primitive.content.toLongOrNull()
        }
}

@Serializable
internal data class OpenCodeGoApiUsageResponse(
    val usage: OpenCodeGoApiUsageDto? = null,
)

@Serializable
internal data class OpenCodeGoApiUsageDto(
    val rolling: OpenCodeGoApiWindowDto? = null,
    val weekly: OpenCodeGoApiWindowDto? = null,
    val monthly: OpenCodeGoApiWindowDto? = null,
)

@Serializable
internal data class OpenCodeGoApiWindowDto(
    val status: String? = null,
    val percent: JsonElement? = null,
    val resetsAt: JsonElement? = null,
    val resetInSec: JsonElement? = null,
)
