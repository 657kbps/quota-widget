package com.kuyermqi.quotawidget.deepseek

import com.kuyermqi.quotawidget.domain.BalanceSnapshot
import com.kuyermqi.quotawidget.domain.CurrencyPreference
import com.kuyermqi.quotawidget.domain.formatBalance
import com.kuyermqi.quotawidget.platform.PlatformIds
import com.kuyermqi.quotawidget.util.currentTimeMillis
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class DeepSeekBalanceClient(
    private val httpClient: HttpClient = createHttpClient(),
) {
    suspend fun fetchBalance(
        apiKey: String,
        preferredCurrency: CurrencyPreference,
    ): BalanceSnapshot {
        val response: DeepSeekBalanceResponse = httpClient.get(BALANCE_URL) {
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            accept(ContentType.Application.Json)
        }.body()

        val selected = selectBalance(response.balanceInfos, preferredCurrency)
            ?: throw IllegalStateException("余额信息为空")

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

    fun close() {
        httpClient.close()
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
