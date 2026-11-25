package com.kuyermqi.quotawidget.commandcode

import com.kuyermqi.quotawidget.domain.HttpStatusException
import com.kuyermqi.quotawidget.domain.QuotaSnapshot
import com.kuyermqi.quotawidget.domain.QuotaUnavailableException
import com.kuyermqi.quotawidget.domain.QuotaWindow
import com.kuyermqi.quotawidget.domain.QuotaWindowKind
import com.kuyermqi.quotawidget.domain.SessionExpiredException
import com.kuyermqi.quotawidget.domain.formatUsagePrimaryDisplay
import com.kuyermqi.quotawidget.platform.PlatformIds
import com.kuyermqi.quotawidget.platform.PlatformRegistry
import com.kuyermqi.quotawidget.session.asObjectOrNull
import com.kuyermqi.quotawidget.session.doubleOrNull
import com.kuyermqi.quotawidget.session.epochMsOrNull
import com.kuyermqi.quotawidget.session.objectOrNull
import com.kuyermqi.quotawidget.session.stringOrNull
import com.kuyermqi.quotawidget.settings.SessionPlatformSettings
import com.kuyermqi.quotawidget.util.currentTimeMillis
import com.kuyermqi.quotawidget.util.suspendRunCatching
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.random.Random

data class CommandCodeCallback(
    val apiKey: String,
    val state: String,
    val userId: String,
    val userName: String,
    val keyName: String,
)

data class CommandCodeIdentity(
    val email: String,
    val userName: String,
    val plan: String,
)

class CommandCodeClient(
    private val httpClient: HttpClient,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    },
) {
    suspend fun fetchIdentity(apiKey: String): CommandCodeIdentity {
        var who: JsonObject? = null
        var lastError: Exception? = null
        for (attempt in 0 until 3) {
            try {
                who = accountJson("/alpha/whoami", apiKey)
                break
            } catch (e: HttpStatusException) {
                if (e.statusCode == 401 || e.statusCode == 403) {
                    throw SessionExpiredException("Command Code 登录已过期，请重新登录")
                }
                lastError = e
                if (attempt < 2) delay(1_000L)
            }
        }
        val root = who ?: throw (lastError ?: IllegalStateException("Command Code 登录失败"))
        val user = root.objectOrNull("user") ?: root
        val subscription = suspendRunCatching { fetchSubscription(apiKey) }.getOrNull()
        return CommandCodeIdentity(
            email = user.stringOrNull("email").orEmpty(),
            userName = user.stringOrNull("userName")
                ?: user.stringOrNull("name").orEmpty(),
            plan = subscription?.first.orEmpty(),
        )
    }

    suspend fun fetchQuota(
        settings: SessionPlatformSettings,
        save: suspend (SessionPlatformSettings) -> Unit,
    ): QuotaSnapshot {
        if (settings.apiKey.isBlank()) {
            throw SessionExpiredException("Command Code 尚未登录")
        }
        val credits = try {
            accountJson("/alpha/billing/credits", settings.apiKey)
        } catch (e: HttpStatusException) {
            if (e.statusCode == 401 || e.statusCode == 403) {
                throw SessionExpiredException("Command Code 登录已过期，请重新登录")
            }
            throw e
        }
        val subscription = suspendRunCatching { fetchSubscription(settings.apiKey) }.getOrNull()
        val parsed = parseCredits(credits, subscription)
        if (parsed.windows.isEmpty()) {
            if (parsed.balance <= 0.0) {
                throw QuotaUnavailableException("Command Code 未返回可用的额度信息")
            }
            val next = settings.copy(plan = parsed.plan)
            if (next != settings) save(next)
            return QuotaSnapshot(
                platformId = PlatformIds.COMMAND_CODE,
                platformName = PlatformRegistry.displayName(PlatformIds.COMMAND_CODE),
                windows = emptyList(),
                primaryDisplay = "$${formatMoney(parsed.balance)}",
                updatedAtEpochMs = currentTimeMillis(),
            )
        }
        val next = settings.copy(plan = parsed.plan.ifBlank { settings.plan })
        if (next != settings) save(next)
        val primary = formatUsagePrimaryDisplay(parsed.windows)
        return QuotaSnapshot(
            platformId = PlatformIds.COMMAND_CODE,
            platformName = PlatformRegistry.displayName(PlatformIds.COMMAND_CODE),
            windows = parsed.windows,
            primaryDisplay = primary,
            updatedAtEpochMs = currentTimeMillis(),
        )
    }

    private suspend fun fetchSubscription(apiKey: String): Pair<String, JsonObject?> {
        val body = accountJson("/alpha/billing/subscriptions", apiKey)
        val data = body.objectOrNull("data")
        val planId = data?.stringOrNull("planId")
            ?: body.stringOrNull("planId")
            ?: ""
        val status = data?.stringOrNull("status") ?: body.stringOrNull("status").orEmpty()
        val plan = if (status == "canceled" || status == "incomplete_expired") {
            "No plan"
        } else {
            planName(planId).ifBlank { if (planId.isBlank()) "No plan" else planId }
        }
        return plan to (data ?: body)
    }

    private suspend fun accountJson(path: String, apiKey: String): JsonObject {
        val response = try {
            httpClient.request(API + path) {
                method = HttpMethod.Get
                header("Accept", "application/json")
                header("Authorization", "Bearer $apiKey")
                header("x-api-key", apiKey)
            }
        } catch (e: ResponseException) {
            throw HttpStatusException(
                statusCode = e.response.status.value,
                message = "Command Code HTTP ${e.response.status.value}",
                cause = e,
            )
        }
        if (response.status != HttpStatusCode.OK) {
            throw HttpStatusException(
                statusCode = response.status.value,
                message = "Command Code HTTP ${response.status.value}",
            )
        }
        val text = response.bodyAsText()
        return runCatching { json.parseToJsonElement(text).jsonObject }.getOrElse {
            throw IllegalStateException("Command Code 返回了无法解析的数据", it)
        }
    }

    companion object {
        private const val HEX = "0123456789ABCDEF"
        const val STATE_EXTRA = "extra_command_code_state"
        private const val API = "https://api.commandcode.ai"
        private const val STUDIO = "https://commandcode.ai"
        private const val DEFAULT_CALLBACK_PORT = 53682

        fun newState(): String {
            val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_"
            return buildString(32) { repeat(32) { append(chars[Random.nextInt(chars.length)]) } }
        }

        fun createLoginUrl(state: String, port: Int = DEFAULT_CALLBACK_PORT): String {
            val callback = percentEncode("http://127.0.0.1:$port/callback")
            val encodedState = percentEncode(state)
            return "$STUDIO/studio/auth/cli?callback=$callback&state=$encodedState&mode=redirect"
        }

        fun parseLoginCallback(url: String): CommandCodeCallback? {
            val query = url.substringAfter('?', missingDelimiterValue = "")
            if (query.isBlank()) return null
            val params = query.split('&').mapNotNull { part ->
                val index = part.indexOf('=')
                if (index <= 0) null else {
                    decode(part.substring(0, index)) to decode(part.substring(index + 1))
                }
            }.toMap()
            val key = params["apiKey"].orEmpty()
            if (key.isBlank()) return null
            return CommandCodeCallback(
                apiKey = key,
                state = params["state"].orEmpty(),
                userId = params["userId"].orEmpty(),
                userName = params["userName"].orEmpty(),
                keyName = params["keyName"].orEmpty(),
            )
        }

        internal fun parseCredits(
            creditsResponse: JsonObject,
            subscription: Pair<String, JsonObject?>?,
            nowEpochMs: Long = currentTimeMillis(),
        ): CommandCodeParsedCredits {
            val creditDetail = creditsResponse.objectOrNull("credits") ?: JsonObject(emptyMap())
            val windowLimits = creditsResponse.objectOrNull("windowLimits")
                ?: creditDetail.objectOrNull("windowLimits")
            val planId = creditDetail.stringOrNull("planId")
                ?: creditsResponse.stringOrNull("planId")
                ?: subscription?.second?.stringOrNull("planId")
                ?: ""
            val plan = planName(planId).ifBlank { subscription?.first.orEmpty() }
            val windows = buildWindows(
                credits = creditDetail,
                windowLimits = windowLimits,
                subscription = subscription?.second,
                planId = planId,
                nowEpochMs = nowEpochMs,
            )
            val balance = listOf(
                creditDetail.doubleOrNull("monthlyCredits") ?: 0.0,
                creditDetail.doubleOrNull("purchasedCredits") ?: 0.0,
                creditDetail.doubleOrNull("freeCredits") ?: 0.0,
            ).sum()
            return CommandCodeParsedCredits(
                plan = plan,
                windows = windows,
                balance = balance,
            )
        }

        private fun buildWindows(
            credits: JsonObject,
            windowLimits: JsonObject?,
            subscription: JsonObject?,
            planId: String,
            nowEpochMs: Long,
        ): List<QuotaWindow> {
            val windows = mutableListOf<QuotaWindow>()
            windowLimits?.objectOrNull("fiveHour")
                ?.toLimitWindow(QuotaWindowKind.FIVE_HOUR, nowEpochMs)
                ?.let(windows::add)
            windowLimits?.objectOrNull("weekly")
                ?.toLimitWindow(QuotaWindowKind.WEEKLY, nowEpochMs)
                ?.let(windows::add)
            val monthlyCredits = credits.doubleOrNull("monthlyCredits") ?: 0.0
            val purchased = credits.doubleOrNull("purchasedCredits") ?: 0.0
            val free = credits.doubleOrNull("freeCredits") ?: 0.0
            val monthlyPlan = planOf(planId).second
            if (monthlyPlan > 0.0 || monthlyCredits > 0.0 || purchased > 0.0 || free > 0.0) {
                val left = monthlyCredits + purchased + free
                val units = planOf(planId).third
                val pool = if (units) {
                    maxOf(monthlyPlan, monthlyCredits)
                } else {
                    maxOf(monthlyPlan, monthlyCredits) + left - monthlyCredits
                }
                if (pool > 0.0) {
                    val used = if (units) {
                        (pool - monthlyCredits).coerceAtLeast(0.0)
                    } else {
                        (pool - left).coerceAtLeast(0.0)
                    }
                    val resetAt = subscription?.epochMsOrNull("currentPeriodEnd")
                    windows.add(
                        QuotaWindow(
                            kind = QuotaWindowKind.MONTHLY,
                            usedPercent = (used / pool * 100.0).coerceIn(0.0, 100.0),
                            resetInSec = resetAt?.takeIf { it > nowEpochMs }
                                ?.let { (it - nowEpochMs) / 1000L },
                        ),
                    )
                }
            }
            return windows
        }

        private fun JsonObject.toLimitWindow(
            kind: QuotaWindowKind,
            nowEpochMs: Long,
        ): QuotaWindow? {
            val used = doubleOrNull("used") ?: return null
            val cap = doubleOrNull("cap") ?: return null
            if (cap <= 0.0) return null
            val resetAt = epochMsOrNull("resetAt")
            return QuotaWindow(
                kind = kind,
                usedPercent = (used.coerceAtLeast(0.0) / cap * 100.0).coerceIn(0.0, 100.0),
                resetInSec = resetAt?.takeIf { it > nowEpochMs }
                    ?.let { (it - nowEpochMs) / 1000L },
            )
        }

        private fun planName(planId: String): String = planOf(planId).first

        private fun planOf(planId: String): Triple<String, Double, Boolean> {
            val value = planId.lowercase().replace('_', '-')
            return when {
                value.startsWith("individual-provider") -> Triple("Provider", 0.0, false)
                value.startsWith("individual-pro-v1") -> Triple("Pro", 80.0, true)
                value.startsWith("individual-goat") -> Triple("GOAT", 70.0, true)
                value.startsWith("individual-ultra") -> Triple("Ultra", 300.0, false)
                value.startsWith("individual-max") -> Triple("Max", 150.0, false)
                value.startsWith("individual-pro") -> Triple("Pro", 30.0, true)
                value.startsWith("individual-go") -> Triple("Go", 10.0, true)
                value.startsWith("teams-pro") -> Triple("Teams Pro", 40.0, false)
                else -> Triple("", 0.0, false)
            }
        }

        private fun formatMoney(value: Double): String {
            val rounded = kotlin.math.round(value * 100.0) / 100.0
            val cents = kotlin.math.round(rounded * 100.0).toLong()
            return "${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"
        }

        private fun percentEncode(value: String): String = buildString {
            for (byte in value.encodeToByteArray()) {
                val code = byte.toInt() and 0xFF
                val char = code.toChar()
                if (char.isLetterOrDigit() || char in "-._~") {
                    append(char)
                } else {
                    append('%')
                    append(HEX[code shr 4])
                    append(HEX[code and 0x0F])
                }
            }
        }

        private fun decode(value: String): String {
            if (!value.contains('%') && !value.contains('+')) return value
            val bytes = ArrayList<Byte>(value.length)
            var index = 0
            while (index < value.length) {
                val char = value[index]
                if (char == '%' && index + 2 < value.length) {
                    val code = value.substring(index + 1, index + 3).toIntOrNull(16)
                    if (code != null) {
                        bytes.add(code.toByte())
                        index += 3
                        continue
                    }
                }
                if (char == '+') {
                    bytes.add(' '.code.toByte())
                } else {
                    bytes.add(char.code.toByte())
                }
                index += 1
            }
            return bytes.toByteArray().decodeToString()
        }
    }
}

internal data class CommandCodeParsedCredits(
    val plan: String,
    val windows: List<QuotaWindow>,
    val balance: Double,
)
