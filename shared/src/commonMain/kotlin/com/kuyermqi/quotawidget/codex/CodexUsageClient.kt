package com.kuyermqi.quotawidget.codex

import com.kuyermqi.quotawidget.deepseek.createHttpClient
import com.kuyermqi.quotawidget.domain.QuotaSnapshot
import com.kuyermqi.quotawidget.domain.QuotaWindow
import com.kuyermqi.quotawidget.domain.QuotaWindowKind
import com.kuyermqi.quotawidget.domain.SessionExpiredException
import com.kuyermqi.quotawidget.domain.formatUsagePrimaryDisplay
import com.kuyermqi.quotawidget.platform.PlatformIds
import com.kuyermqi.quotawidget.platform.PlatformRegistry
import com.kuyermqi.quotawidget.util.currentTimeMillis
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class CodexUsageClient(
    private val httpClient: HttpClient = createHttpClient(),
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    },
) {
    suspend fun fetchUsage(accessToken: String, accountId: String): QuotaSnapshot {
        val response = try {
            httpClient.get(USAGE_URL) {
                header(HttpHeaders.Authorization, "Bearer $accessToken")
                header("ChatGPT-Account-Id", accountId)
                header(HttpHeaders.Accept, "application/json")
                header(HttpHeaders.UserAgent, USER_AGENT)
                header(HttpHeaders.Origin, "https://chatgpt.com")
                header(HttpHeaders.Referrer, "https://chatgpt.com/")
            }
        } catch (e: ClientRequestException) {
            val status = e.response.status
            if (status == HttpStatusCode.Unauthorized || status == HttpStatusCode.Forbidden) {
                throw SessionExpiredException("Codex 登录已失效，请重新登录")
            }
            throw IllegalStateException("查询 Codex 额度失败: $status", e)
        }
        val body = response.bodyAsText()
        val dto = json.decodeFromString(CodexUsageResponse.serializer(), body)
        val windows = dto.toWindows()
        if (windows.isEmpty()) {
            throw IllegalStateException("无法解析 Codex 额度窗口")
        }
        return QuotaSnapshot(
            platformId = PlatformIds.CODEX,
            platformName = PlatformRegistry.displayName(PlatformIds.CODEX),
            windows = windows,
            primaryDisplay = formatUsagePrimaryDisplay(windows),
            updatedAtEpochMs = currentTimeMillis(),
        )
    }

    fun close() {
        httpClient.close()
    }

    companion object {
        const val USAGE_URL = "https://chatgpt.com/backend-api/wham/usage"
        const val USER_AGENT = "quota-widget"
    }
}

@Serializable
data class CodexUsageResponse(
    @SerialName("plan_type") val planType: String? = null,
    @SerialName("rate_limit") val rateLimit: CodexRateLimitDto? = null,
)

@Serializable
data class CodexRateLimitDto(
    @SerialName("primary_window") val primaryWindow: CodexWindowDto? = null,
    @SerialName("secondary_window") val secondaryWindow: CodexWindowDto? = null,
)

@Serializable
data class CodexWindowDto(
    @SerialName("used_percent") val usedPercent: Double? = null,
    @SerialName("limit_window_seconds") val limitWindowSeconds: Long? = null,
    @SerialName("reset_after_seconds") val resetAfterSeconds: Long? = null,
    @SerialName("reset_at") val resetAt: Long? = null,
)

fun CodexUsageResponse.toWindows(): List<QuotaWindow> {
    val rate = rateLimit ?: return emptyList()
    val mapped = linkedMapOf<QuotaWindowKind, QuotaWindow>()
    for (window in listOfNotNull(rate.primaryWindow, rate.secondaryWindow)) {
        val kind = classifyWindow(window.limitWindowSeconds) ?: continue
        mapped[kind] = QuotaWindow(
            kind = kind,
            usedPercent = window.usedPercent,
            resetInSec = window.resetAfterSeconds,
        )
    }
    return listOfNotNull(
        mapped[QuotaWindowKind.FIVE_HOUR],
        mapped[QuotaWindowKind.WEEKLY],
        mapped[QuotaWindowKind.MONTHLY],
    )
}

/**
 * Classify by duration, not primary/secondary name.
 * ≤6h → 5h; ~7d → weekly; ~30d → monthly.
 */
fun classifyWindow(limitWindowSeconds: Long?): QuotaWindowKind? {
    if (limitWindowSeconds == null || limitWindowSeconds <= 0L) return null
    return when {
        limitWindowSeconds <= 6L * 3600L -> QuotaWindowKind.FIVE_HOUR
        limitWindowSeconds <= 8L * 24L * 3600L -> QuotaWindowKind.WEEKLY
        else -> QuotaWindowKind.MONTHLY
    }
}
