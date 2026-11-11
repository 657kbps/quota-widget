package com.kuyermqi.quotawidget.kimi

import com.kuyermqi.quotawidget.deepseek.createHttpClient
import com.kuyermqi.quotawidget.domain.QuotaSnapshot
import com.kuyermqi.quotawidget.domain.QuotaWindow
import com.kuyermqi.quotawidget.domain.QuotaUnavailableException
import com.kuyermqi.quotawidget.domain.QuotaWindowKind
import com.kuyermqi.quotawidget.domain.ServiceRegion
import com.kuyermqi.quotawidget.domain.SessionExpiredException
import com.kuyermqi.quotawidget.domain.classifyUsageWindow
import com.kuyermqi.quotawidget.domain.formatUsagePrimaryDisplay
import com.kuyermqi.quotawidget.platform.PlatformIds
import com.kuyermqi.quotawidget.platform.PlatformRegistry
import com.kuyermqi.quotawidget.util.currentTimeMillis
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * Kimi Code (Kimi 会员编程权益) usage.
 *
 * Uses the endpoint [Kimi Code CLI] and other official clients call:
 * `GET /coding/v1/usages` with a `sk-kimi-` Bearer key. Only the Coding Plan key
 * from the Kimi Code console works here; Kimi open-platform keys are rejected.
 *
 * The payload has two generations and both are parsed:
 * - legacy absolute rows: `usage` (weekly) and `limits[]` (`window` + `detail`);
 * - quota model: `usages` map (`limit_5h` / `limit_7d` / `limit_month_*`) with
 *   `used_ratio` + `reset_time`.
 *
 * [Kimi Code CLI]: https://www.kimi.com/code/docs/kimi-code-cli/guides/getting-started.html
 */
class KimiCodeUsageClient(
    private val httpClient: HttpClient = createHttpClient(),
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    },
) {
    suspend fun fetchUsage(
        apiKey: String,
        region: ServiceRegion = ServiceRegion.CN,
        nowEpochMs: Long = currentTimeMillis(),
    ): QuotaSnapshot {
        val response = try {
            httpClient.get(usageUrl(region)) {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                accept(ContentType.Application.Json)
            }
        } catch (e: ClientRequestException) {
            when (e.response.status) {
                HttpStatusCode.Unauthorized,
                HttpStatusCode.Forbidden,
                -> throw SessionExpiredException("Kimi Code API Key 无效或已失效，请检查配置")
                HttpStatusCode.TooManyRequests ->
                    throw IllegalStateException("Kimi Code 请求过于频繁，请稍后重试", e)
                else ->
                    throw IllegalStateException("查询 Kimi Code 额度失败: ${e.response.status}", e)
            }
        }
        val body = response.bodyAsText()
        if (body.isBlank()) {
            throw IllegalStateException("Kimi Code 返回为空")
        }
        val dto = try {
            json.decodeFromString(KimiCodeUsageResponse.serializer(), body)
        } catch (e: SerializationException) {
            throw IllegalStateException("Kimi Code 返回无法解析", e)
        }
        val snapshot = toSnapshot(
            dto = dto,
            updatedAtEpochMs = nowEpochMs,
            nowEpochMs = nowEpochMs,
        )
        if (snapshot.windows.isEmpty()) {
            // The endpoint legitimately returns `{}` for some keys/plans; do not treat
            // that as 0% usage, and do not mark the key as invalid.
            throw QuotaUnavailableException("Kimi Code 已认证，但未返回额度数据（可能是套餐未生效或接口暂不可用）")
        }
        return snapshot
    }

    fun close() {
        httpClient.close()
    }

    companion object {
        const val USAGE_URL_CN = "https://api.kimi.com/coding/v1/usages"
        const val USAGE_URL_GLOBAL = "https://api.kimi.ai/coding/v1/usages"

        fun usageUrl(region: ServiceRegion): String = when (region) {
            ServiceRegion.CN -> USAGE_URL_CN
            ServiceRegion.GLOBAL -> USAGE_URL_GLOBAL
        }

        fun toSnapshot(
            dto: KimiCodeUsageResponse,
            updatedAtEpochMs: Long,
            nowEpochMs: Long = updatedAtEpochMs,
        ): QuotaSnapshot {
            val mapped = linkedMapOf<QuotaWindowKind, QuotaWindow>()
            dto.usage?.toQuotaWindow(nowEpochMs)?.let {
                mapped[QuotaWindowKind.WEEKLY] = it
            }
            for (limit in dto.limits) {
                val kind = classifyUsageWindow(limit.window?.durationSeconds()) ?: continue
                val window = limit.detail?.toQuotaWindow(nowEpochMs) ?: continue
                mapped[kind] = window.copy(kind = kind)
            }
            dto.usages["limit_5h"]?.toQuotaWindow(nowEpochMs)?.let {
                mapped[QuotaWindowKind.FIVE_HOUR] = it.copy(kind = QuotaWindowKind.FIVE_HOUR)
            }
            dto.usages["limit_7d"]?.toQuotaWindow(nowEpochMs)?.let {
                mapped[QuotaWindowKind.WEEKLY] = it.copy(kind = QuotaWindowKind.WEEKLY)
            }
            // Monthly quota is split into total and code pools. A single widget row cannot
            // show both, so surface the more consumed pool (the tighter constraint).
            val monthly = listOfNotNull(
                dto.usages["limit_month_total"]?.toQuotaWindow(nowEpochMs),
                dto.usages["limit_month_code"]?.toQuotaWindow(nowEpochMs),
            ).maxByOrNull { it.usedPercent ?: 0.0 }
                // Older payloads may still expose the monthly pool here.
                ?: dto.totalQuota?.toQuotaWindow(nowEpochMs)
            if (monthly != null) {
                mapped[QuotaWindowKind.MONTHLY] = monthly.copy(kind = QuotaWindowKind.MONTHLY)
            }
            val windows = listOfNotNull(
                mapped[QuotaWindowKind.FIVE_HOUR],
                mapped[QuotaWindowKind.WEEKLY],
                mapped[QuotaWindowKind.MONTHLY],
            )
            return QuotaSnapshot(
                platformId = PlatformIds.KIMI_CODE,
                platformName = PlatformRegistry.displayName(PlatformIds.KIMI_CODE),
                windows = windows,
                primaryDisplay = formatUsagePrimaryDisplay(windows),
                updatedAtEpochMs = updatedAtEpochMs,
            )
        }

        internal fun KimiCodeWindowDto.toQuotaWindow(nowEpochMs: Long): QuotaWindow? {
            val limitValue = limit.asDoubleOrNull()
            val usedValue = used.asDoubleOrNull()
                ?: if (limitValue != null) {
                    remaining.asDoubleOrNull()?.let { limitValue - it }
                } else {
                    null
                }
            val percent = if (limitValue != null && limitValue > 0.0 && usedValue != null) {
                usedValue / limitValue * 100.0
            } else {
                null
            }
            if (percent == null) return null
            return QuotaWindow(
                kind = QuotaWindowKind.WEEKLY,
                usedPercent = percent,
                resetInSec = resetInSeconds(resetTime, nowEpochMs),
            )
        }

        internal fun KimiCodeQuotaDto.toQuotaWindow(nowEpochMs: Long): QuotaWindow? {
            val ratio = usedRatio.asDoubleOrNull() ?: return null
            // The quota model documents used_ratio as a 0..1 fraction; tolerate a
            // percentage-style value if the backend ever sends one.
            val percent = if (ratio <= 1.0) ratio * 100.0 else ratio
            return QuotaWindow(
                kind = QuotaWindowKind.WEEKLY,
                usedPercent = percent,
                resetInSec = resetInSeconds(resetTime, nowEpochMs),
            )
        }

        internal fun KimiCodeWindowKindDto.durationSeconds(): Long? {
            val value = duration.asDoubleOrNull() ?: return null
            if (value <= 0.0) return null
            val unit = timeUnit.orEmpty().uppercase()
            val secondsPerUnit = when {
                "SECOND" in unit -> 1.0
                "MINUTE" in unit -> 60.0
                "HOUR" in unit -> 3_600.0
                "DAY" in unit -> 86_400.0
                "WEEK" in unit -> 604_800.0
                "MONTH" in unit -> 2_592_000.0
                else -> return null
            }
            return (value * secondsPerUnit).toLong()
        }

        internal fun resetInSeconds(resetTime: String?, nowEpochMs: Long): Long? =
            parseIso8601EpochMs(resetTime)
                ?.let { ((it - nowEpochMs) / 1_000L).coerceAtLeast(0L) }

        internal fun resetInSeconds(resetTime: JsonElement?, nowEpochMs: Long): Long? {
            val raw = (resetTime as? JsonPrimitive)?.content?.trim() ?: return null
            val resetEpochMs = raw.toDoubleOrNull()?.let { value ->
                // Epoch seconds for current dates are ~1e9; milliseconds ~1e12.
                if (value < 10_000_000_000.0) (value * 1_000.0).toLong() else value.toLong()
            } ?: parseIso8601EpochMs(raw) ?: return null
            return ((resetEpochMs - nowEpochMs) / 1_000L).coerceAtLeast(0L)
        }

        /** Minimal ISO-8601 parser: `yyyy-MM-ddTHH:mm:ss[.frac][Z|±HH:MM]`. */
        internal fun parseIso8601EpochMs(raw: String?): Long? {
            if (raw.isNullOrBlank()) return null
            val values = ISO_8601.matchEntire(raw.trim())?.groupValues ?: return null
            val year = values[1].toIntOrNull() ?: return null
            val month = values[2].toIntOrNull() ?: return null
            val day = values[3].toIntOrNull() ?: return null
            val hour = values[4].toIntOrNull() ?: return null
            val minute = values[5].toIntOrNull() ?: return null
            val second = values[6].toIntOrNull() ?: return null
            if (year < 1970 || month !in 1..12 || day !in 1..daysInMonth(year, month)) {
                return null
            }
            if (hour !in 0..23 || minute !in 0..59 || second !in 0..59) return null

            val offsetSeconds = when {
                values[8].isNotEmpty() -> 0L
                values[9].isEmpty() -> 0L
                else -> {
                    val sign = if (values[9] == "-") -1L else 1L
                    val offsetHour = values[10].toLongOrNull() ?: return null
                    val offsetMinute = values[11].toLongOrNull() ?: return null
                    if (offsetHour > 23L || offsetMinute > 59L) return null
                    sign * (offsetHour * 3_600L + offsetMinute * 60L)
                }
            }
            val epochSeconds = daysFromCivil(year, month, day) * 86_400L +
                hour * 3_600L + minute * 60L + second - offsetSeconds
            return epochSeconds * 1_000L
        }

        private fun daysInMonth(year: Int, month: Int): Int = when (month) {
            2 -> if (isLeapYear(year)) 29 else 28
            4, 6, 9, 11 -> 30
            else -> 31
        }

        private fun isLeapYear(year: Int): Boolean =
            year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)

        /** Howard Hinnant's days-from-civil; days since 1970-01-01. */
        private fun daysFromCivil(year: Int, month: Int, day: Int): Long {
            val y = if (month <= 2) year - 1 else year
            val era = y / 400
            val yoe = y - era * 400
            val mp = (month + 9) % 12
            val doy = (153 * mp + 2) / 5 + day - 1
            val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
            return era * 146_097L + doe - 719_468L
        }

        private fun JsonElement?.asDoubleOrNull(): Double? = when (this) {
            is JsonPrimitive -> content.toDoubleOrNull()
            else -> null
        }

        private val ISO_8601 = Regex(
            "^(\\d{4})-(\\d{2})-(\\d{2})[Tt ](\\d{2}):(\\d{2}):(\\d{2})" +
                "(?:\\.(\\d{1,9}))?(?:([Zz])|([+-])(\\d{2}):?(\\d{2}))?$",
        )
    }
}

@Serializable
data class KimiCodeUsageResponse(
    val usage: KimiCodeWindowDto? = null,
    val limits: List<KimiCodeLimitDto> = emptyList(),
    val usages: Map<String, KimiCodeQuotaDto> = emptyMap(),
    /** Legacy monthly pool; usually an empty object in current payloads. */
    val totalQuota: KimiCodeWindowDto? = null,
)

@Serializable
data class KimiCodeLimitDto(
    val window: KimiCodeWindowKindDto? = null,
    val detail: KimiCodeWindowDto? = null,
)

@Serializable
data class KimiCodeWindowKindDto(
    val duration: JsonElement? = null,
    val timeUnit: String? = null,
)

@Serializable
data class KimiCodeWindowDto(
    val limit: JsonElement? = null,
    val used: JsonElement? = null,
    val remaining: JsonElement? = null,
    val resetTime: String? = null,
)

@Serializable
data class KimiCodeQuotaDto(
    @SerialName("used_ratio") val usedRatio: JsonElement? = null,
    @SerialName("reset_time") val resetTime: JsonElement? = null,
)
