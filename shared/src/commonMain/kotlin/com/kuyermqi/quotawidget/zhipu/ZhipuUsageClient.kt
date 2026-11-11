package com.kuyermqi.quotawidget.zhipu

import com.kuyermqi.quotawidget.deepseek.createHttpClient
import com.kuyermqi.quotawidget.domain.QuotaSnapshot
import com.kuyermqi.quotawidget.domain.QuotaWindow
import com.kuyermqi.quotawidget.domain.QuotaUnavailableException
import com.kuyermqi.quotawidget.domain.QuotaWindowKind
import com.kuyermqi.quotawidget.domain.ServiceRegion
import com.kuyermqi.quotawidget.domain.SessionExpiredException
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Zhipu GLM Coding Plan quota.
 *
 * `GET /api/monitor/usage/quota/limit` on [open.bigmodel.cn] (CN) or [api.z.ai]
 * (global). This is the endpoint used by Zhipu's official `glm-plan-usage`
 * plugin. Coding-plan keys are sent as the raw `Authorization` value (no
 * `Bearer` prefix), matching the official plugin.
 *
 * `limits[]` carries the 5-hour (`unit=3`) and weekly (`unit=6`) windows.
 * `TIME_LIMIT` rows are MCP tool calls, not coding quota, and are skipped.
 *
 * [open.bigmodel.cn]: https://docs.bigmodel.cn/cn/coding-plan/overview
 * [api.z.ai]: https://docs.z.ai
 */
class ZhipuUsageClient(
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
                // Coding-plan keys are accepted as the raw Authorization value.
                header(HttpHeaders.Authorization, apiKey)
                accept(ContentType.Application.Json)
            }
        } catch (e: ClientRequestException) {
            when (e.response.status) {
                HttpStatusCode.Unauthorized,
                HttpStatusCode.Forbidden,
                -> throw SessionExpiredException("Zhipu GLM API Key 无效或已失效，请检查配置")
                HttpStatusCode.TooManyRequests ->
                    throw IllegalStateException("Zhipu GLM 请求过于频繁，请稍后重试", e)
                else ->
                    throw IllegalStateException("查询 Zhipu GLM 用量失败: ${e.response.status}", e)
            }
        }
        val body = response.bodyAsText()
        if (body.isBlank()) {
            throw IllegalStateException("Zhipu GLM 返回为空")
        }
        val envelope = try {
            json.decodeFromString(ZhipuUsageEnvelope.serializer(), body)
        } catch (e: SerializationException) {
            throw IllegalStateException("Zhipu GLM 返回无法解析", e)
        }
        envelope.failureOrNull()?.let { throw it }
        val data = envelope.data ?: throw IllegalStateException("Zhipu GLM 返回数据为空")
        val windows = toWindows(data.limits, nowEpochMs)
        if (windows.isEmpty()) {
            throw QuotaUnavailableException("Zhipu GLM 已认证，但未返回可展示的用量窗口（请确认已开通 Coding Plan）")
        }
        return QuotaSnapshot(
            platformId = PlatformIds.ZHIPU,
            platformName = PlatformRegistry.displayName(PlatformIds.ZHIPU),
            windows = windows,
            primaryDisplay = formatUsagePrimaryDisplay(windows),
            updatedAtEpochMs = nowEpochMs,
        )
    }

    fun close() {
        httpClient.close()
    }

    companion object {
        const val USAGE_URL_CN = "https://open.bigmodel.cn/api/monitor/usage/quota/limit"
        const val USAGE_URL_GLOBAL = "https://api.z.ai/api/monitor/usage/quota/limit"

        fun usageUrl(region: ServiceRegion): String = when (region) {
            ServiceRegion.CN -> USAGE_URL_CN
            ServiceRegion.GLOBAL -> USAGE_URL_GLOBAL
        }

        /** Quota rows that belong to the coding plan; MCP `TIME_LIMIT` is excluded. */
        fun isUsageLimit(type: String?): Boolean =
            type != null && (
                type.equals("TOKENS_LIMIT", ignoreCase = true) ||
                    type.equals("CREDIT_LIMIT", ignoreCase = true)
                )

        /** `unit: 3` → 5-hour rolling window; `unit: 6` → weekly window. */
        fun classifyUnit(unit: Int?): QuotaWindowKind? = when (unit) {
            3 -> QuotaWindowKind.FIVE_HOUR
            6 -> QuotaWindowKind.WEEKLY
            else -> null
        }

        fun toWindows(
            limits: List<ZhipuLimitDto>,
            nowEpochMs: Long,
        ): List<QuotaWindow> {
            val usageLimits = limits.filter { isUsageLimit(it.type) }
            val mapped = linkedMapOf<QuotaWindowKind, QuotaWindow>()
            for (limit in usageLimits) {
                val kind = classifyUnit(limit.unit)
                    ?: if (limit.unit == null && usageLimits.size == 1) {
                        // Legacy plans omit `unit` for their single 5-hour window.
                        QuotaWindowKind.FIVE_HOUR
                    } else {
                        null
                    }
                if (kind == null) continue
                val usedPercent = limit.usedPercent() ?: continue
                mapped[kind] = QuotaWindow(
                    kind = kind,
                    usedPercent = usedPercent,
                    resetInSec = resetInSeconds(limit.nextResetTime, nowEpochMs),
                )
            }
            return listOfNotNull(
                mapped[QuotaWindowKind.FIVE_HOUR],
                mapped[QuotaWindowKind.WEEKLY],
                mapped[QuotaWindowKind.MONTHLY],
            )
        }

        /** `nextResetTime` is epoch milliseconds; clamp clock skew to 0. */
        fun resetInSeconds(nextResetTimeEpochMs: Long?, nowEpochMs: Long): Long? {
            if (nextResetTimeEpochMs == null || nextResetTimeEpochMs <= 0L) return null
            return ((nextResetTimeEpochMs - nowEpochMs) / 1_000L).coerceAtLeast(0L)
        }
    }
}

internal fun ZhipuLimitDto.usedPercent(): Double? =
    percentage ?: run {
        val total = usage
        val used = currentValue
        if (total != null && total > 0.0 && used != null) {
            used / total * 100.0
        } else {
            null
        }
    }

/** Maps business-level failures returned with HTTP 200 into exceptions. */
internal fun ZhipuUsageEnvelope.failureOrNull(): Exception? {
    val failed = success == false || (code != null && code != 200)
    if (!failed) return null
    if (code == 401 || code == 1_000 || code == 1_001) {
        return SessionExpiredException("Zhipu GLM API Key 无效或已失效，请检查配置")
    }
    return IllegalStateException(
        msg?.takeIf { it.isNotBlank() } ?: "查询 Zhipu GLM 用量失败",
    )
}

@Serializable
data class ZhipuUsageEnvelope(
    val code: Int? = null,
    val msg: String? = null,
    val success: Boolean? = null,
    val data: ZhipuUsageData? = null,
)

@Serializable
data class ZhipuUsageData(
    val limits: List<ZhipuLimitDto> = emptyList(),
    /** Plan tier, e.g. "lite" / "pro" / "max". */
    val level: String? = null,
)

@Serializable
data class ZhipuLimitDto(
    val type: String? = null,
    val unit: Int? = null,
    val number: Int? = null,
    val usage: Double? = null,
    val currentValue: Double? = null,
    val remaining: Double? = null,
    /** Already the used percent (0-100). */
    val percentage: Double? = null,
    /** Epoch milliseconds. */
    val nextResetTime: Long? = null,
)
