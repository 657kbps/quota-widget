package com.kuyermqi.quotawidget.newapi

import com.kuyermqi.quotawidget.deepseek.createHttpClient
import com.kuyermqi.quotawidget.domain.CurrencyPreference
import com.kuyermqi.quotawidget.domain.NEW_API_UNLIMITED_REMAINING_DISPLAY
import com.kuyermqi.quotawidget.domain.QuotaSnapshot
import com.kuyermqi.quotawidget.domain.QuotaWindow
import com.kuyermqi.quotawidget.domain.QuotaWindowKind
import com.kuyermqi.quotawidget.domain.SessionExpiredException
import com.kuyermqi.quotawidget.domain.formatBalance
import com.kuyermqi.quotawidget.platform.PlatformIds
import com.kuyermqi.quotawidget.platform.PlatformRegistry
import com.kuyermqi.quotawidget.settings.DEFAULT_NEW_API_QUOTA_PER_USD
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
import kotlinx.serialization.json.Json
import kotlin.math.abs

class NewApiUsageClient(
    private val httpClient: HttpClient = createHttpClient(),
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    },
) {
    suspend fun fetchUsage(
        baseUrl: String,
        apiKey: String,
        quotaPerUsd: Long = DEFAULT_NEW_API_QUOTA_PER_USD,
    ): QuotaSnapshot {
        val url = usageUrl(baseUrl)
        val response = try {
            httpClient.get(url) {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                accept(ContentType.Application.Json)
            }
        } catch (e: ClientRequestException) {
            val status = e.response.status
            if (status == HttpStatusCode.Unauthorized || status == HttpStatusCode.Forbidden) {
                throw SessionExpiredException("NewAPI Token 无效或已失效，请检查配置")
            }
            throw IllegalStateException("查询 NewAPI 额度失败: $status", e)
        }
        if (response.status == HttpStatusCode.Unauthorized ||
            response.status == HttpStatusCode.Forbidden
        ) {
            throw SessionExpiredException("NewAPI Token 无效或已失效，请检查配置")
        }
        val body = response.bodyAsText()
        if (body.isBlank()) {
            throw IllegalStateException("NewAPI 返回为空")
        }
        val envelope = try {
            json.decodeFromString(NewApiUsageEnvelope.serializer(), body)
        } catch (e: Exception) {
            throw IllegalStateException("NewAPI 返回无法解析", e)
        }
        if (!envelope.isOk) {
            val message = envelope.message?.takeIf { it.isNotBlank() } ?: "查询 NewAPI 额度失败"
            throw IllegalStateException(message)
        }
        val data = envelope.data ?: throw IllegalStateException("NewAPI 返回数据为空")
        return toSnapshot(data, quotaPerUsd = quotaPerUsd)
    }

    fun close() {
        httpClient.close()
    }

    companion object {
        fun normalizeBaseUrl(baseUrl: String): String =
            baseUrl.trim().trimEnd('/')

        fun usageUrl(baseUrl: String): String =
            "${normalizeBaseUrl(baseUrl)}/api/usage/token"

        /**
         * Limited-key used percent.
         * Prefers `used / (used + max(0, available))` so % matches displayed amounts;
         * when overspent (`available < 0`) uses `granted` and may exceed 100%.
         */
        fun usedPercent(
            totalGranted: Long,
            totalUsed: Long,
            unlimited: Boolean,
            totalAvailable: Long = 0L,
        ): Double? {
            if (unlimited) return null
            val used = totalUsed.coerceAtLeast(0L)
            val pool = effectivePool(
                totalGranted = totalGranted,
                totalUsed = used,
                totalAvailable = totalAvailable,
            )
            if (pool <= 0L) return 0.0
            return (used.toDouble() / pool.toDouble() * 100.0).coerceAtLeast(0.0)
        }

        /**
         * Denominator for used %. Prefer amount-consistent pool; overspend falls back to granted.
         */
        fun effectivePool(
            totalGranted: Long,
            totalUsed: Long,
            totalAvailable: Long,
        ): Long {
            val used = totalUsed.coerceAtLeast(0L)
            if (totalAvailable < 0L) {
                return when {
                    totalGranted > 0L -> totalGranted
                    used > 0L -> used
                    else -> 0L
                }
            }
            val derived = used + totalAvailable.coerceAtLeast(0L)
            return when {
                derived > 0L -> derived
                totalGranted > 0L -> totalGranted
                else -> 0L
            }
        }

        fun isEmptyLimitedQuota(
            unlimited: Boolean,
            totalGranted: Long,
            totalUsed: Long,
            totalAvailable: Long,
        ): Boolean =
            !unlimited &&
                totalGranted <= 0L &&
                totalUsed <= 0L &&
                totalAvailable == 0L

        /**
         * `expires_at` is Unix seconds per NewAPI docs; some forks use milliseconds.
         * `<= 0` means never expires.
         */
        fun isTokenExpired(
            expiresAt: Long,
            nowEpochMs: Long = currentTimeMillis(),
        ): Boolean {
            if (expiresAt <= 0L) return false
            val expiresAtMs =
                if (expiresAt >= 1_000_000_000_000L) expiresAt else expiresAt * 1000L
            return nowEpochMs >= expiresAtMs
        }

        fun resolveQuotaPerUsd(quotaPerUsd: Long): Double =
            if (quotaPerUsd > 0L) {
                quotaPerUsd.toDouble()
            } else {
                DEFAULT_NEW_API_QUOTA_PER_USD.toDouble()
            }

        fun formatQuotaUsd(
            quotaUnits: Long,
            quotaPerUsd: Long = DEFAULT_NEW_API_QUOTA_PER_USD,
        ): String {
            val usd = quotaUnits.toDouble() / resolveQuotaPerUsd(quotaPerUsd)
            if (usd == 0.0) {
                return formatBalance(CurrencyPreference.USD, "0")
            }
            // Keep two-decimal display rule: sub-cent non-zero amounts → <$0.01
            if (abs(usd) < 0.01) {
                return if (usd < 0.0) "-<$0.01" else "<$0.01"
            }
            return formatBalance(CurrencyPreference.USD, usd.toString())
        }

        fun formatRemainingBalance(
            totalAvailable: Long,
            quotaPerUsd: Long = DEFAULT_NEW_API_QUOTA_PER_USD,
        ): String = formatQuotaUsd(totalAvailable, quotaPerUsd)

        fun formatUsedBalance(
            totalUsed: Long,
            quotaPerUsd: Long = DEFAULT_NEW_API_QUOTA_PER_USD,
        ): String = formatQuotaUsd(totalUsed, quotaPerUsd)

        fun toSnapshot(
            data: NewApiTokenUsageDto,
            updatedAtEpochMs: Long = currentTimeMillis(),
            quotaPerUsd: Long = DEFAULT_NEW_API_QUOTA_PER_USD,
            nowEpochMs: Long = updatedAtEpochMs,
        ): QuotaSnapshot {
            val rate = resolveQuotaPerUsd(quotaPerUsd)
            val granted = data.totalGrantedLong
            val used = data.totalUsedLong
            val available = data.totalAvailableLong
            val remainingDisplay = if (data.unlimitedQuota) {
                NEW_API_UNLIMITED_REMAINING_DISPLAY
            } else {
                formatRemainingBalance(
                    totalAvailable = available,
                    quotaPerUsd = quotaPerUsd,
                )
            }
            val usedDisplay = formatUsedBalance(used, quotaPerUsd)
            val emptyLimited = isEmptyLimitedQuota(
                unlimited = data.unlimitedQuota,
                totalGranted = granted,
                totalUsed = used,
                totalAvailable = available,
            )
            val percent = usedPercent(
                totalGranted = granted,
                totalUsed = used,
                unlimited = data.unlimitedQuota,
                totalAvailable = available,
            )
            return QuotaSnapshot(
                platformId = PlatformIds.NEW_API,
                platformName = PlatformRegistry.displayName(PlatformIds.NEW_API),
                windows = listOf(
                    QuotaWindow(kind = QuotaWindowKind.BALANCE),
                    QuotaWindow(kind = QuotaWindowKind.TOKEN, usedPercent = percent),
                ),
                primaryDisplay = remainingDisplay,
                updatedAtEpochMs = updatedAtEpochMs,
                currency = CurrencyPreference.USD,
                totalBalance = (available.toDouble() / rate).toString(),
                unlimitedQuota = data.unlimitedQuota,
                usedDisplay = usedDisplay,
                emptyLimitedQuota = emptyLimited,
                tokenExpired = isTokenExpired(data.expiresAt, nowEpochMs = nowEpochMs),
                quotaOverspent = available < 0L,
            )
        }
    }
}

@Serializable
data class NewApiUsageEnvelope(
    val code: Boolean? = null,
    val success: Boolean? = null,
    val message: String? = null,
    val data: NewApiTokenUsageDto? = null,
) {
    val isOk: Boolean
        get() = code == true || success == true
}

@Serializable
data class NewApiTokenUsageDto(
    val `object`: String? = null,
    val name: String? = null,
    /** Double so forks that emit JSON numbers as floats still decode. */
    @SerialName("total_granted") val totalGranted: Double = 0.0,
    @SerialName("total_used") val totalUsed: Double = 0.0,
    @SerialName("total_available") val totalAvailable: Double = 0.0,
    @SerialName("unlimited_quota") val unlimitedQuota: Boolean = false,
    /** Unix seconds (docs); some forks use milliseconds. `0` / negative = never. */
    @SerialName("expires_at") val expiresAt: Long = 0L,
) {
    val totalGrantedLong: Long get() = totalGranted.toLong()
    val totalUsedLong: Long get() = totalUsed.toLong()
    val totalAvailableLong: Long get() = totalAvailable.toLong()
}
