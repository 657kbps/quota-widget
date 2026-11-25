package com.kuyermqi.quotawidget.cline

import com.kuyermqi.quotawidget.domain.HttpStatusException
import com.kuyermqi.quotawidget.domain.QuotaSnapshot
import com.kuyermqi.quotawidget.domain.QuotaWindow
import com.kuyermqi.quotawidget.domain.QuotaWindowKind
import com.kuyermqi.quotawidget.domain.SessionExpiredException
import com.kuyermqi.quotawidget.domain.formatUsagePrimaryDisplay
import com.kuyermqi.quotawidget.platform.PlatformIds
import com.kuyermqi.quotawidget.platform.PlatformRegistry
import com.kuyermqi.quotawidget.session.asObjectOrNull
import com.kuyermqi.quotawidget.session.arrayOrNull
import com.kuyermqi.quotawidget.session.doubleOrNull
import com.kuyermqi.quotawidget.session.epochMsOrNull
import com.kuyermqi.quotawidget.session.intOrNull
import com.kuyermqi.quotawidget.session.objectOrNull
import com.kuyermqi.quotawidget.session.stringOrNull
import com.kuyermqi.quotawidget.settings.SessionPlatformSettings
import com.kuyermqi.quotawidget.util.currentTimeMillis
import com.kuyermqi.quotawidget.util.suspendRunCatching
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

data class ClineDeviceSession(
    val deviceCode: String,
    val verificationUrl: String,
    val intervalSeconds: Int,
    val expiresInSeconds: Int,
)

sealed interface ClineDevicePoll {
    data object Pending : ClineDevicePoll
    data object SlowDown : ClineDevicePoll
    data class Ready(val accessToken: String, val refreshToken: String) : ClineDevicePoll
}

data class ClineAuthResult(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochMs: Long,
    val uid: String,
    val email: String,
    val name: String,
    val plan: String,
)

class ClineClient(
    private val httpClient: HttpClient,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    },
) {
    suspend fun createDeviceSession(): ClineDeviceSession {
        val response = try {
            httpClient.submitForm(
                url = WORKOS_DEVICE,
                formParameters = Parameters.build {
                    append("client_id", WORKOS_CLIENT)
                },
            )
        } catch (e: ClientRequestException) {
            throw IllegalStateException("Cline 登录请求失败: HTTP ${e.response.status.value}", e)
        }
        val body = response.bodyAsText()
        val data = json.parseToJsonElement(body).jsonObject
        val deviceCode = data.stringOrNull("device_code").orEmpty()
        val verification = data.stringOrNull("verification_uri_complete")
            ?: data.stringOrNull("verification_uri")
        if (deviceCode.isBlank() || verification.isNullOrBlank()) {
            throw IllegalStateException("Cline 登录响应不完整")
        }
        return ClineDeviceSession(
            deviceCode = deviceCode,
            verificationUrl = verification,
            intervalSeconds = data.intOrNull("interval") ?: 5,
            expiresInSeconds = data.intOrNull("expires_in") ?: 300,
        )
    }

    suspend fun pollDeviceToken(session: ClineDeviceSession): ClineDevicePoll {
        val response = try {
            httpClient.submitForm(
                url = WORKOS_TOKEN,
                formParameters = Parameters.build {
                    append("grant_type", DEVICE_GRANT)
                    append("device_code", session.deviceCode)
                    append("client_id", WORKOS_CLIENT)
                },
            )
        } catch (e: ClientRequestException) {
            val error = suspendRunCatching {
                json.parseToJsonElement(e.response.bodyAsText()).jsonObject.stringOrNull("error")
            }.getOrNull()
            return when (error) {
                "authorization_pending" -> ClineDevicePoll.Pending
                "slow_down" -> ClineDevicePoll.SlowDown
                null -> throw IllegalStateException(
                    "Cline 登录轮询失败: HTTP ${e.response.status.value}",
                    e,
                )
                else -> throw IllegalStateException("Cline 登录失败: $error")
            }
        }
        val data = json.parseToJsonElement(response.bodyAsText()).jsonObject
        data.stringOrNull("error")?.let { error ->
            return when (error) {
                "authorization_pending" -> ClineDevicePoll.Pending
                "slow_down" -> ClineDevicePoll.SlowDown
                else -> throw IllegalStateException("Cline 登录失败: $error")
            }
        }
        val access = data.stringOrNull("access_token").orEmpty()
        if (access.isBlank()) return ClineDevicePoll.Pending
        return ClineDevicePoll.Ready(
            accessToken = access,
            refreshToken = data.stringOrNull("refresh_token").orEmpty(),
        )
    }

    suspend fun register(workos: ClineDevicePoll.Ready): ClineAuthResult {
        val data = clineApi(
            path = "/auth/register",
            method = "POST",
            body = buildJsonObject {
                put("accessToken", workos.accessToken)
                put("refreshToken", workos.refreshToken)
            },
        )
        return data.toAuthResult(fallbackRefresh = workos.refreshToken)
    }

    suspend fun fetchQuota(
        settings: SessionPlatformSettings,
        save: suspend (SessionPlatformSettings) -> Unit,
    ): QuotaSnapshot {
        var current = settings
        if (current.apiKey.isBlank() && current.isExpiring()) {
            current = refresh(current, save)
        }
        return try {
            loadOnce(current, save)
        } catch (e: SessionExpiredException) {
            // A lapsed token is retried at most once; a second lapse means the session is gone.
            if (current.apiKey.isNotBlank() || current.refreshToken.isBlank()) throw e
            loadOnce(refresh(current, save), save)
        }
    }

    private suspend fun loadOnce(
        current: SessionPlatformSettings,
        save: suspend (SessionPlatformSettings) -> Unit,
    ): QuotaSnapshot {
        var limitsError: String? = null
        val me = clineApi("/users/me", bearer = bearerOf(current))
        val balance = try {
            fetchBalance(me, current)
        } catch (e: SessionExpiredException) {
            throw e
        } catch (e: Exception) {
            null
        }
        val limits = try {
            clineApi("/users/me/plan/usage-limits", bearer = bearerOf(current))
        } catch (e: SessionExpiredException) {
            throw e
        } catch (e: Exception) {
            if (e is HttpStatusException && e.statusCode == 404) {
                null
            } else {
                limitsError = "ClinePass 限额读取失败"
                null
            }
        }
        val windows = limits.toWindows()
        val plan = me.stringOrNull("plan")
            ?: me.stringOrNull("planType")
            ?: me.stringOrNull("planName")
            ?: me.stringOrNull("membership")
            ?: me.stringOrNull("tier")
            ?: ""
        val balanceText = balance?.toBalanceText()
        val display = balanceText
            ?: if (windows.isNotEmpty()) formatUsagePrimaryDisplay(windows) else plan
        val email = me.stringOrNull("email").orEmpty()
        val uid = me.stringOrNull("clineUserId")
            ?: me.stringOrNull("id")
            ?: me.stringOrNull("subject")
            ?: me.stringOrNull("userId")
            ?: current.uid
        val next = current.copy(
            uid = uid,
            email = email.ifBlank { current.email },
            accountId = current.accountId.ifBlank { email.ifBlank { uid } },
            plan = plan.ifBlank { current.plan },
        )
        if (next != current) save(next)
        return QuotaSnapshot(
            platformId = PlatformIds.CLINE,
            platformName = PlatformRegistry.displayName(PlatformIds.CLINE),
            windows = windows,
            primaryDisplay = display,
            updatedAtEpochMs = currentTimeMillis(),
        ).also {
            if (limitsError != null) {
                // Surfaced by the home row through an empty display only when nothing loaded.
            }
        }
    }

    suspend fun refresh(
        settings: SessionPlatformSettings,
        save: suspend (SessionPlatformSettings) -> Unit,
    ): SessionPlatformSettings {
        if (settings.refreshToken.isBlank()) {
            throw SessionExpiredException("Cline 登录已过期，请重新登录")
        }
        val data = try {
            clineApi(
                path = "/auth/refresh",
                method = "POST",
                body = buildJsonObject {
                    put("refreshToken", settings.refreshToken)
                    put("grantType", "refresh_token")
                },
                lapsedStatuses = setOf(400, 401, 403),
            )
        } catch (e: SessionExpiredException) {
            throw e
        }
        val auth = data.toAuthResult(fallbackRefresh = settings.refreshToken)
        val next = settings.copy(
            accessToken = auth.accessToken,
            refreshToken = auth.refreshToken,
            expiresAtEpochMs = auth.expiresAtEpochMs,
            uid = auth.uid.ifBlank { settings.uid },
            email = auth.email.ifBlank { settings.email },
            accountId = settings.accountId.ifBlank {
                auth.email.ifBlank { auth.uid }
            },
            plan = auth.plan.ifBlank { settings.plan },
        )
        save(next)
        return next
    }

    private suspend fun fetchBalance(me: JsonObject, settings: SessionPlatformSettings): JsonObject? {
        if (settings.apiKey.isNotBlank()) return null
        val ids = listOfNotNull(
            settings.accountId.takeIf { it.isNotBlank() },
            me.stringOrNull("subject"),
            me.stringOrNull("id"),
            settings.uid.takeIf { it.isNotBlank() },
        ).distinct()
        var last: Exception? = null
        for (id in ids) {
            try {
                return clineApi("/users/${encodePath(id)}/balance", bearer = bearerOf(settings))
            } catch (e: SessionExpiredException) {
                throw e
            } catch (e: Exception) {
                last = e
            }
        }
        if (last != null) return null
        return null
    }

    private suspend fun clineApi(
        path: String,
        method: String = "GET",
        body: JsonObject? = null,
        lapsedStatuses: Set<Int> = setOf(401, 403),
        bearer: String = "",
    ): JsonObject {
        val headers = clientHeaders() + buildMap {
            if (bearer.isNotBlank()) put(HttpHeaders.Authorization, "Bearer $bearer")
        }
        val response = try {
            if (method == "POST") {
                httpClient.post(API + path) {
                    headers.forEach { (name, value) -> header(name, value) }
                    contentType(ContentType.Application.Json)
                    setBody(body?.toString() ?: "{}")
                }
            } else {
                httpClient.get(API + path) {
                    headers.forEach { (name, value) -> header(name, value) }
                }
            }
        } catch (e: ResponseException) {
            val status = e.response.status.value
            if (status in lapsedStatuses) {
                throw SessionExpiredException("Cline 登录已过期，请重新登录")
            }
            throw HttpStatusException(
                statusCode = status,
                message = "Cline 请求失败: HTTP $status",
                cause = e,
            )
        }
        val text = response.bodyAsText()
        val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrElse {
            throw IllegalStateException("Cline 返回了无法解析的数据", it)
        }
        if (root["success"] != null && root["success"].toString() != "true") {
            throw IllegalStateException(
                "Cline 请求失败: ${root.stringOrNull("error").orEmpty()}",
            )
        }
        return root.objectOrNull("data") ?: root
    }

    private fun bearerOf(settings: SessionPlatformSettings): String = when {
        settings.apiKey.isNotBlank() -> settings.apiKey
        settings.accessToken.isBlank() -> ""
        settings.accessToken.startsWith("workos:", ignoreCase = true) -> settings.accessToken
        else -> "workos:${settings.accessToken}"
    }

    private fun JsonObject?.toWindows(): List<QuotaWindow> {
        val limits = this?.arrayOrNull("limits") ?: return emptyList()
        val mapped = linkedMapOf<QuotaWindowKind, QuotaWindow>()
        for (item in limits) {
            val limit = item.asObjectOrNull() ?: continue
            val kind = when (limit.stringOrNull("type")) {
                "five_hour" -> QuotaWindowKind.FIVE_HOUR
                "weekly" -> QuotaWindowKind.WEEKLY
                "monthly" -> QuotaWindowKind.MONTHLY
                else -> continue
            }
            val percent = limit.doubleOrNull("percentUsed")?.coerceIn(0.0, 100.0) ?: 0.0
            val resetAt = limit.epochMsOrNull("resetsAt")
            mapped[kind] = QuotaWindow(
                kind = kind,
                usedPercent = percent,
                resetInSec = resetAt?.takeIf { it > currentTimeMillis() }
                    ?.let { (it - currentTimeMillis()) / 1000L },
            )
        }
        return listOfNotNull(
            mapped[QuotaWindowKind.FIVE_HOUR],
            mapped[QuotaWindowKind.WEEKLY],
            mapped[QuotaWindowKind.MONTHLY],
        )
    }

    private fun JsonObject?.toBalanceText(): String? {
        val micro = this?.doubleOrNull("balance") ?: return null
        if (micro < 0) return null
        val dollars = micro / 1_000_000.0
        return if (dollars == 0.0 || dollars >= 0.01) {
            "$${formatTwo(dollars)}"
        } else {
            "$" + (kotlin.math.round(dollars * 10_000.0) / 10_000.0).toString()
        }
    }

    private fun JsonObject?.toAuthResult(fallbackRefresh: String): ClineAuthResult {
        val data = this ?: throw IllegalStateException("Cline 返回数据为空")
        val access = data.stringOrNull("accessToken").orEmpty()
        val refresh = data.stringOrNull("refreshToken") ?: fallbackRefresh
        if (access.isBlank() || refresh.isBlank()) {
            throw IllegalStateException("Cline 登录响应缺少令牌")
        }
        val userInfo = data.objectOrNull("userInfo")
        return ClineAuthResult(
            accessToken = access,
            refreshToken = refresh,
            expiresAtEpochMs = data.epochMsOrNull("expiresAt") ?: 0L,
            uid = userInfo?.stringOrNull("clineUserId")
                ?: userInfo?.stringOrNull("subject")
                ?: "",
            email = userInfo?.stringOrNull("email").orEmpty(),
            name = userInfo?.stringOrNull("name").orEmpty(),
            plan = data.stringOrNull("plan").orEmpty(),
        )
    }

    private fun clientHeaders(): Map<String, String> = mapOf(
        "HTTP-Referer" to "https://cline.bot",
        "X-Title" to "Cline",
        "User-Agent" to "Cline/$CLIENT_VERSION",
        "X-IS-MULTIROOT" to "false",
        "X-CLIENT-TYPE" to "cline-cli",
        "X-CLIENT-VERSION" to CLIENT_VERSION,
        "X-PLATFORM" to "cli",
        "X-PLATFORM-VERSION" to CLIENT_VERSION,
        "X-CORE-VERSION" to CLIENT_CORE_VERSION,
    )

    private fun SessionPlatformSettings.isExpiring(): Boolean {
        if (accessToken.isBlank()) return refreshToken.isNotBlank()
        if (expiresAtEpochMs <= 0L) return false
        return currentTimeMillis() >= expiresAtEpochMs - 5 * 60 * 1000L
    }

    private fun formatTwo(value: Double): String {
        val cents = kotlin.math.round(value * 100.0).toLong()
        return "${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"
    }

    private fun encodePath(value: String): String =
        value.replace("%", "%25").replace("/", "%2F")

    private companion object {
        const val API = "https://api.cline.bot/api/v1"
        const val WORKOS_DEVICE = "https://api.workos.com/user_management/authorize/device"
        const val WORKOS_TOKEN = "https://api.workos.com/user_management/authenticate"
        const val WORKOS_CLIENT = "client_01K3A541FN8TA3EPPHTD2325AR"
        const val DEVICE_GRANT = "urn:ietf:params:oauth:grant-type:device_code"
        const val CLIENT_VERSION = "3.0.68"
        const val CLIENT_CORE_VERSION = "0.0.90"
    }
}
