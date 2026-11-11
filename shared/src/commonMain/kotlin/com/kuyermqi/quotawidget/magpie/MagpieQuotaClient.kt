package com.kuyermqi.quotawidget.magpie

import com.kuyermqi.quotawidget.deepseek.createHttpClient
import com.kuyermqi.quotawidget.domain.QuotaSnapshot
import com.kuyermqi.quotawidget.domain.QuotaWindow
import com.kuyermqi.quotawidget.domain.QuotaWindowKind
import com.kuyermqi.quotawidget.domain.QuotaUnavailableException
import com.kuyermqi.quotawidget.domain.SessionExpiredException
import com.kuyermqi.quotawidget.domain.UsageDisplayMode
import com.kuyermqi.quotawidget.domain.formatUsagePrimaryDisplay
import com.kuyermqi.quotawidget.opencode.parseIsoInstantMillis
import com.kuyermqi.quotawidget.platform.PlatformIds
import com.kuyermqi.quotawidget.platform.PlatformRegistry
import com.kuyermqi.quotawidget.util.currentTimeMillis
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Reads magpie's own usage cards, `GET /v1/magpie/quotas` (see
 * usemagpie.ai/docs/reference). The endpoint needs a gateway key and
 * "Share on local network" enabled on the gateway.
 *
 * Each card is one signed-in account or key. Cards are grouped by provider
 * ([providersOf]); the widget shows one provider, picked by the user and
 * defaulting to the first the gateway returns.
 */
class MagpieQuotaClient(
    private val httpClient: HttpClient = createHttpClient(),
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    },
) {
    /**
     * Providers the gateway reports, in first-seen order, limited to the ones a
     * percent widget can show (some cards carry neither allowance windows nor
     * per-kind percentages).
     */
    suspend fun fetchProviders(
        baseUrl: String,
        apiKey: String,
    ): List<MagpieProvider> {
        val response = try {
            httpClient.get(quotasUrl(baseUrl)) {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                accept(ContentType.Application.Json)
            }
        } catch (e: ResponseException) {
            throw e.toMagpieError()
        }
        val body = response.bodyAsText()
        if (body.isBlank()) {
            throw IllegalStateException("Magpie 返回为空")
        }
        val envelope = try {
            json.decodeFromString(MagpieQuotaEnvelope.serializer(), body)
        } catch (e: Exception) {
            throw IllegalStateException("Magpie 返回无法解析", e)
        }
        return providersOf(envelope.data.orEmpty())
    }

    fun close() {
        httpClient.close()
    }

    private suspend fun ResponseException.toMagpieError(): Exception {
        val serverMessage = runCatching {
            json.decodeFromString(
                MagpieErrorEnvelope.serializer(),
                response.bodyAsText(),
            ).error?.message
        }.getOrNull()?.takeIf { it.isNotBlank() }
        return when (response.status) {
            HttpStatusCode.Unauthorized ->
                SessionExpiredException(
                    serverMessage?.let { "Magpie 网关密钥无效：$it" }
                        ?: "Magpie 网关密钥无效或已失效，请检查配置",
                )
            HttpStatusCode.Forbidden ->
                IllegalStateException(
                    serverMessage
                        ?: "Magpie 拒绝访问额度接口，请检查网关密钥的额度权限",
                )
            else -> IllegalStateException(
                serverMessage ?: "查询 Magpie 额度失败: ${response.status}",
            )
        }
    }

    companion object {
        fun normalizeBaseUrl(baseUrl: String): String =
            baseUrl.trim().trimEnd('/')

        fun quotasUrl(baseUrl: String): String =
            "${normalizeBaseUrl(baseUrl)}/v1/magpie/quotas"

        /**
         * Window kinds a percent widget can show. Per-model token pools carry
         * no 5h/week/month meaning and are skipped; credit pools reset monthly,
         * so they map to the monthly slot.
         */
        fun windowKindOf(name: String?): QuotaWindowKind? {
            val lower = name?.trim()?.lowercase() ?: return null
            return when {
                // Credit pools reset monthly ("Credits", "Total credits");
                // "Extra credits" has no reset and is only a top-up bucket.
                lower.contains("extra") -> null
                lower.contains("credit") -> QuotaWindowKind.MONTHLY
                lower.contains("5 hour") ||
                    lower.contains("5-hour") ||
                    lower.contains("5h") -> QuotaWindowKind.FIVE_HOUR
                lower.contains("7 day") ||
                    lower.contains("7-day") ||
                    lower.contains("7d") ||
                    lower.contains("week") -> QuotaWindowKind.WEEKLY
                lower.contains("month") -> QuotaWindowKind.MONTHLY
                else -> null
            }
        }

        /**
         * Groups cards by gateway provider, keeping first-seen order and
         * dropping providers without any displayable window.
         */
        fun providersOf(
            cards: List<MagpieQuotaCard>,
            nowEpochMs: Long = currentTimeMillis(),
        ): List<MagpieProvider> {
            val byProvider = LinkedHashMap<String, MutableList<MagpieQuotaCard>>()
            for (card in cards) {
                val id = card.provider?.trim().orEmpty()
                if (id.isEmpty()) continue
                byProvider.getOrPut(id) { mutableListOf() } += card
            }
            return byProvider.mapNotNull { (id, providerCards) ->
                val windows = mergeWindows(providerCards, nowEpochMs)
                if (windows.isEmpty()) return@mapNotNull null
                MagpieProvider(
                    id = id,
                    name = providerCards.firstNotNullOfOrNull { it.name?.takeIf(String::isNotBlank) }
                        .orEmpty(),
                    accountCount = providerCards.size,
                    plans = providerCards
                        .mapNotNull { it.plan?.takeIf(String::isNotBlank) }
                        .distinct(),
                    windows = windows,
                )
            }
        }

        /**
         * One window per kind for the provider: the tightest account wins
         * ("用量也要聚合计算"), since a provider's accounts hold separate
         * allowance pools and the closest-to-limit one is the actionable number.
         */
        fun mergeWindows(
            cards: List<MagpieQuotaCard>,
            nowEpochMs: Long = currentTimeMillis(),
        ): List<QuotaWindow> {
            val tightest = mutableMapOf<QuotaWindowKind, QuotaWindow>()
            for (card in cards) {
                for (window in windowsOf(card, nowEpochMs)) {
                    val current = tightest[window.kind]
                    if (current == null || window.usedPercent!! > current.usedPercent!!) {
                        tightest[window.kind] = window
                    }
                }
            }
            return WINDOW_ORDER.mapNotNull { tightest[it] }
        }

        /**
         * One account's windows. A card can report several entries per kind
         * ("Gemini · 7 days" / "Claude & GPT · 7 days", or "Total credits" /
         * "Credits"); the highest-priority entry wins, then the tightest.
         */
        fun windowsOf(
            card: MagpieQuotaCard,
            nowEpochMs: Long = currentTimeMillis(),
        ): List<QuotaWindow> {
            val best = mutableMapOf<QuotaWindowKind, Pair<Int, QuotaWindow>>()
            for (window in card.windows.orEmpty()) {
                val used = window.used ?: continue
                val kind = windowKindOf(window.name) ?: continue
                val priority = windowPriority(window.name)
                val candidate = QuotaWindow(
                    kind = kind,
                    usedPercent = used.coerceAtLeast(0.0),
                    resetInSec = resetInSec(window.resetsAt, nowEpochMs),
                )
                val current = best[kind]
                val better = current == null ||
                    priority > current.first ||
                    (priority == current.first && candidate.usedPercent!! > current.second.usedPercent!!)
                if (better) best[kind] = priority to candidate
            }
            return WINDOW_ORDER.mapNotNull { best[it]?.second }
        }

        /**
         * "Total credits" already includes "Credits" and "Extra credits";
         * preferring it avoids reporting a partial pool as the whole allowance.
         */
        fun windowPriority(name: String?): Int {
            val lower = name?.trim()?.lowercase() ?: return 0
            return when {
                !lower.contains("credit") -> 0
                lower.contains("total") -> 2
                lower.contains("extra") -> 0
                else -> 1
            }
        }

        /**
         * The saved provider when the gateway still reports it, otherwise the
         * first one the gateway returned.
         */
        fun selectProvider(
            providers: List<MagpieProvider>,
            selectedId: String?,
        ): MagpieProvider? =
            providers.find { it.id == selectedId } ?: providers.firstOrNull()

        fun toSnapshot(
            windows: List<QuotaWindow>,
            usageDisplayMode: UsageDisplayMode = UsageDisplayMode.USED,
            updatedAtEpochMs: Long = currentTimeMillis(),
        ): QuotaSnapshot {
            if (windows.isEmpty()) {
                throw QuotaUnavailableException(
                    "Magpie 网关未返回 5 小时/每周/每月 额度窗口，" +
                        "请确认网关已连接订阅账号或带限额的 Key",
                )
            }
            return QuotaSnapshot(
                platformId = PlatformIds.MAGPIE,
                platformName = PlatformRegistry.displayName(PlatformIds.MAGPIE),
                windows = windows,
                primaryDisplay = formatUsagePrimaryDisplay(windows, usageDisplayMode),
                updatedAtEpochMs = updatedAtEpochMs,
            )
        }

        private val WINDOW_ORDER = listOf(
            QuotaWindowKind.FIVE_HOUR,
            QuotaWindowKind.WEEKLY,
            QuotaWindowKind.MONTHLY,
        )

        private fun resetInSec(resetsAt: String?, nowEpochMs: Long): Long? {
            val resetsAtMs = parseIsoInstantMillis(resetsAt) ?: return null
            return ((resetsAtMs - nowEpochMs) / 1000L).coerceAtLeast(0L)
        }
    }
}

@Serializable
data class MagpieQuotaEnvelope(
    val `object`: String? = null,
    val data: List<MagpieQuotaCard>? = null,
)

/** One account or key as the gateway reports it; one card per signed-in account. */
@Serializable
data class MagpieQuotaCard(
    val provider: String? = null,
    val name: String? = null,
    val kind: String? = null,
    val plan: String? = null,
    val user: String? = null,
    val windows: List<MagpieQuotaWindow>? = null,
    val readAt: String? = null,
)

/**
 * One allowance window. [used] and [remaining] are percentages for the
 * subscription windows ("Gemini · 7 days"); credit windows carry [amount] and
 * [limit] instead and are skipped when aggregating.
 */
@Serializable
data class MagpieQuotaWindow(
    val name: String? = null,
    val used: Double? = null,
    val remaining: Double? = null,
    val resetsAt: String? = null,
    val display: String? = null,
    val amount: Double? = null,
    val limit: Double? = null,
    val unit: String? = null,
)

@Serializable
data class MagpieErrorEnvelope(
    val error: MagpieError? = null,
)

@Serializable
data class MagpieError(
    val message: String? = null,
    val type: String? = null,
)
