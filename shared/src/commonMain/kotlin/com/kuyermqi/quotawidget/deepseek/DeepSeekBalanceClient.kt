package com.kuyermqi.quotawidget.deepseek

import com.kuyermqi.quotawidget.domain.BalanceSnapshot
import com.kuyermqi.quotawidget.domain.CurrencyPreference
import com.kuyermqi.quotawidget.domain.QuotaUnavailableException
import com.kuyermqi.quotawidget.domain.formatBalance
import com.kuyermqi.quotawidget.domain.httpFailure
import com.kuyermqi.quotawidget.platform.PlatformIds
import com.kuyermqi.quotawidget.util.currentTimeMillis
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class DeepSeekBalanceClient(
    private val httpClient: HttpClient,
) {
    suspend fun fetchBalance(
        apiKey: String,
        preferredCurrency: CurrencyPreference,
    ): BalanceSnapshot {
        val response: DeepSeekBalanceResponse = try {
            httpClient.get(BALANCE_URL) {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                accept(ContentType.Application.Json)
            }.body()
        } catch (e: ResponseException) {
            val status = e.response.status.value
            throw httpFailure(
                statusCode = status,
                sessionExpiredMessage = "DeepSeek API Key 无效或已失效，请检查配置",
                failureMessage = "查询 DeepSeek 余额失败: HTTP $status",
                cause = e,
            )
        }

        val selected = selectBalance(response.balanceInfos, preferredCurrency)
            ?: throw QuotaUnavailableException("DeepSeek 未返回余额信息")

        val currency = CurrencyPreference.fromStorage(selected.currency)
        return BalanceSnapshot(
            platformId = PlatformIds.DEEPSEEK,
            platformName = "DeepSeek",
            currency = currency,
            totalBalance = selected.totalBalance,
            formattedBalance = formatBalance(currency, selected.totalBalance),
            updatedAtEpochMs = currentTimeMillis(),
        )
    }

    companion object {
        private const val BALANCE_URL = "https://api.deepseek.com/user/balance"

        internal fun selectBalance(
            infos: List<BalanceInfoDto>,
            preferred: CurrencyPreference,
        ): BalanceInfoDto? {
            if (infos.isEmpty()) return null
            val preferredMatch = infos.find { it.currency.equals(preferred.name, ignoreCase = true) }
            if (preferredMatch != null) return preferredMatch
            val cny = infos.find { it.currency.equals("CNY", ignoreCase = true) }
            if (cny != null) return cny
            val usd = infos.find { it.currency.equals("USD", ignoreCase = true) }
            if (usd != null) return usd
            return infos.first()
        }
    }
}

@Serializable
data class DeepSeekBalanceResponse(
    @SerialName("is_available") val isAvailable: Boolean = false,
    @SerialName("balance_infos") val balanceInfos: List<BalanceInfoDto> = emptyList(),
)

@Serializable
data class BalanceInfoDto(
    val currency: String,
    @SerialName("total_balance") val totalBalance: String,
    @SerialName("granted_balance") val grantedBalance: String = "0",
    @SerialName("topped_up_balance") val toppedUpBalance: String = "0",
)
