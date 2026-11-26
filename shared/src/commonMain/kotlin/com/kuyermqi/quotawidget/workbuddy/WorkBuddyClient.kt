package com.kuyermqi.quotawidget.workbuddy

import com.kuyermqi.quotawidget.domain.HttpStatusException
import com.kuyermqi.quotawidget.domain.QuotaSnapshot
import com.kuyermqi.quotawidget.domain.QuotaUnavailableException
import com.kuyermqi.quotawidget.domain.QuotaWindow
import com.kuyermqi.quotawidget.domain.QuotaWindowKind
import com.kuyermqi.quotawidget.domain.SessionExpiredException
import com.kuyermqi.quotawidget.platform.PlatformIds
import com.kuyermqi.quotawidget.platform.PlatformRegistry
import com.kuyermqi.quotawidget.session.asObjectOrNull
import com.kuyermqi.quotawidget.session.arrayOrNull
import com.kuyermqi.quotawidget.session.booleanOrNull
import com.kuyermqi.quotawidget.session.doubleOrNull
import com.kuyermqi.quotawidget.session.epochMsOrNull
import com.kuyermqi.quotawidget.session.intOrNull
import com.kuyermqi.quotawidget.session.longOrNull
import com.kuyermqi.quotawidget.session.objectOrNull
import com.kuyermqi.quotawidget.session.stringOrNull
import com.kuyermqi.quotawidget.settings.SessionPlatformSettings
import com.kuyermqi.quotawidget.util.currentTimeMillis
import com.kuyermqi.quotawidget.util.parseDateTimeToEpochMs
import com.kuyermqi.quotawidget.util.secureRandomHex
import com.kuyermqi.quotawidget.util.suspendRunCatching
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/** The two WorkBuddy builds the app signs in to. */
enum class WorkBuddyRegion(
    val platformId: String,
    val endpoint: String,
    val authPlatform: String,
    val appVersion: String,
    val appName: String,
) {
    CHINA(
        platformId = PlatformIds.WORKBUDDY,
        endpoint = "https://copilot.tencent.com",
        authPlatform = "workbuddy",
        appVersion = "5.9.3",
        appName = "WorkBuddy",
    ),
    INTERNATIONAL(
        platformId = PlatformIds.WORKBUDDY_AI,
        endpoint = "https://www.workbuddy.ai",
        authPlatform = "workbuddy-ai",
        appVersion = "5.9.3",
        appName = "workbuddy-ai",
    ),
    ;

    companion object {
        fun forPlatform(platformId: String): WorkBuddyRegion? =
            entries.firstOrNull { it.platformId == platformId }
    }
}

data class WorkBuddyLoginSession(
    val state: String,
    val authUrl: String,
)

data class WorkBuddyToken(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochMs: Long,
    val refreshExpiresAtEpochMs: Long,
    val domain: String,
)

data class WorkBuddyAccount(
    val uid: String,
    val nickname: String,
    val phoneNumber: String,
    val enterpriseId: String,
    val enterpriseName: String,
    val type: String,
)

class WorkBuddyClient(
    private val httpClient: HttpClient,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    },
) {
    /** Creates the server-side sign-in state whose page is opened in the WebView. */
    suspend fun createLoginSession(region: WorkBuddyRegion): WorkBuddyLoginSession {
        val data = call(
            region = region,
            method = HttpMethod.Post,
            path = "/v2/plugin/auth/state?platform=${region.authPlatform}",
            headers = mapOf(
                "X-No-Authorization" to "true",
                "X-No-User-Id" to "true",
                "X-No-Enterprise-Id" to "true",
                "X-No-Department-Info" to "true",
            ),
            body = "{}",
        ) ?: throw IllegalStateException("${region.appName} 未返回登录地址")
        val state = data.stringOrNull("state").orEmpty()
        val authUrl = data.stringOrNull("authUrl").orEmpty()
        if (state.isBlank() || authUrl.isBlank()) {
            throw IllegalStateException("${region.appName} 未返回登录地址")
        }
        val separator = if (authUrl.contains('?')) '&' else '?'
        val loginSessionId = randomHex(16)
        return WorkBuddyLoginSession(
            state = state,
            authUrl = "$authUrl${separator}version=${region.appVersion}" +
                "&loginSessionId=$loginSessionId",
        )
    }

    /**
     * One token poll. Returns null while WorkBuddy says the browser sign-in
     * has not finished yet (code 11217).
     */
    suspend fun pollLoginToken(region: WorkBuddyRegion, state: String): WorkBuddyToken? {
        val data = try {
            call(region, HttpMethod.Get, "/v2/plugin/auth/token?state=${encodeQuery(state)}")
        } catch (e: WorkBuddyApiException) {
            if (e.code == 11217) return null
            throw e
        } ?: return null
        return data.toToken().takeIf { it.accessToken.isNotBlank() }
    }

    suspend fun fetchLoginAccount(
        region: WorkBuddyRegion,
        state: String,
        token: WorkBuddyToken,
    ): WorkBuddyAccount? {
        val data = try {
            call(
                region = region,
                method = HttpMethod.Get,
                path = "/v2/plugin/login/account?state=${encodeQuery(state)}",
                headers = authHeaders(token) + ("X-Domain" to token.domain.ifBlank { region.host() }),
            )
        } catch (e: WorkBuddyApiException) {
            if (e.code == 11217 || e.code == 12151) return null
            throw e
        } ?: return null
        val account = data.toAccount()
        return withEnterprise(region, token, account)
    }

    private suspend fun withEnterprise(
        region: WorkBuddyRegion,
        token: WorkBuddyToken,
        account: WorkBuddyAccount,
    ): WorkBuddyAccount {
        val type = account.type.trim().lowercase()
        val needsName = account.enterpriseId.isNotBlank() && account.enterpriseName.isBlank()
        if (!needsName &&
            (account.enterpriseId.isNotBlank() || type.isBlank() || type == "personal")
        ) {
            return account
        }
        val list = suspendRunCatching {
            call(
                region = region,
                method = HttpMethod.Get,
                path = "/v2/plugin/accounts",
                headers = enterpriseAuthHeaders(token) +
                    ("X-Domain" to token.domain.ifBlank { region.host() }),
            )
        }.getOrNull() ?: return account
        val mine = list.arrayOrNull("accounts")
            ?.mapNotNull { it.asObjectOrNull() }
            ?.filter {
                it.stringOrNull("uid") == account.uid &&
                    !it.stringOrNull("enterpriseId").isNullOrBlank()
            }
            .orEmpty()
        if (mine.isEmpty()) return account
        val picked = if (account.enterpriseId.isNotBlank()) {
            mine.firstOrNull { it.stringOrNull("enterpriseId") == account.enterpriseId }
        } else {
            val sameType = mine.filter { it.stringOrNull("type").orEmpty().trim().lowercase() == type }
            when {
                sameType.size == 1 -> sameType.first()
                mine.count { it.booleanOrNull("lastLogin") == true } == 1 ->
                    mine.first { it.booleanOrNull("lastLogin") == true }
                mine.size == 1 -> mine.first()
                else -> null
            }
        } ?: return account
        return account.copy(
            enterpriseId = picked.stringOrNull("enterpriseId").orEmpty(),
            enterpriseName = picked.stringOrNull("enterpriseName").orEmpty(),
        )
    }

    suspend fun fetchQuota(
        region: WorkBuddyRegion,
        settings: SessionPlatformSettings,
        save: suspend (SessionPlatformSettings) -> Unit,
    ): QuotaSnapshot {
        var current = settings
        if (current.isExpiring()) {
            current = refresh(region, current, save)
        }
        val summary = try {
            meter(region, current, "/billing/meter/get-user-resource-summary")
        } catch (e: SessionExpiredException) {
            current = refresh(region, current, save)
            meter(region, current, "/billing/meter/get-user-resource-summary")
        }
        var window = summary?.toCreditWindow()
        if (window == null) {
            val enterprise = meter(region, current, "/billing/meter/get-enterprise-user-usage")
            window = enterprise?.toEnterpriseWindow()
        }
        if (window == null) {
            throw QuotaUnavailableException("${region.appName} 未返回可用的额度信息")
        }
        val summaryPlan = summary?.let {
            if (it.booleanOrNull("IsPaidUser") == true) "Pro" else "Free"
        }
        val plan = summaryPlan ?: current.plan.ifBlank { "Free" }
        if (plan.isNotBlank() && plan != current.plan) {
            current = current.copy(plan = plan)
            save(current)
        }
        val display = window.display
        return QuotaSnapshot(
            platformId = region.platformId,
            platformName = PlatformRegistry.displayName(region.platformId),
            windows = window.usedPercent?.let { percent ->
                listOf(
                    QuotaWindow(
                        kind = QuotaWindowKind.MONTHLY,
                        usedPercent = percent,
                        resetInSec = window.resetInSec,
                    ),
                )
            }.orEmpty(),
            primaryDisplay = display,
            updatedAtEpochMs = currentTimeMillis(),
        )
    }

    suspend fun refresh(
        region: WorkBuddyRegion,
        settings: SessionPlatformSettings,
        save: suspend (SessionPlatformSettings) -> Unit,
    ): SessionPlatformSettings {
        if (settings.refreshToken.isBlank()) {
            throw SessionExpiredException("${region.appName} 登录已过期，请重新登录")
        }
        val data = try {
            call(
                region = region,
                method = HttpMethod.Post,
                path = "/v2/plugin/auth/token/refresh",
                headers = mapOf(
                    "X-Refresh-Token" to settings.refreshToken,
                    "X-Auth-Refresh-Source" to "plugin",
                    "X-Domain" to settings.domain.ifBlank { region.host() },
                ) + enterpriseHeaders(settings),
                body = "{}",
            )
        } catch (e: HttpStatusException) {
            if (e.statusCode == 401 || e.statusCode == 403) {
                throw SessionExpiredException("${region.appName} 登录已过期，请重新登录")
            }
            throw e
        } catch (e: WorkBuddyApiException) {
            if (e.code == 401 || e.code == 1001 || e.code == 11001) {
                throw SessionExpiredException("${region.appName} 登录已过期，请重新登录")
            }
            throw e
        } ?: throw IllegalStateException("${region.appName} 刷新登录失败")
        val token = data.toToken()
        if (token.accessToken.isBlank()) {
            throw SessionExpiredException("${region.appName} 登录已过期，请重新登录")
        }
        val next = settings.copy(
            accessToken = token.accessToken,
            refreshToken = token.refreshToken.ifBlank { settings.refreshToken },
            expiresAtEpochMs = token.expiresAtEpochMs,
            domain = token.domain.ifBlank { settings.domain },
        )
        save(next)
        return next
    }

    private suspend fun meter(
        region: WorkBuddyRegion,
        settings: SessionPlatformSettings,
        path: String,
    ): JsonObject? {
        val endpoint = endpointOf(region, settings)
        val response = try {
            httpClient.request(endpoint + path) {
                method = HttpMethod.Post
                contentType(ContentType.Application.Json)
                header("Accept", "application/json")
                header("User-Agent", "WorkBuddy/${region.appVersion}")
                header("Authorization", "Bearer ${settings.accessToken}")
                header("X-User-Id", settings.uid)
                header("X-Domain", settings.domain.ifBlank { region.host() })
                header("X-Product", "SaaS")
                header("X-IDE-Type", "WorkBuddy")
                header("X-Requested-With", "XMLHttpRequest")
                enterpriseHeaders(settings).forEach { (name, value) -> header(name, value) }
                setBody("{}")
            }
        } catch (e: ResponseException) {
            val status = e.response.status.value
            if (status == 401 || status == 403) {
                throw SessionExpiredException("${region.appName} 登录已过期，请重新登录")
            }
            throw HttpStatusException(
                statusCode = status,
                message = "${region.appName} 查询额度失败: HTTP $status",
                cause = e,
            )
        }
        return response.bodyAsText().toEnvelopeData(region.displayName())
    }

    private suspend fun call(
        region: WorkBuddyRegion,
        method: HttpMethod,
        path: String,
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
    ): JsonObject? {
        val response = try {
            httpClient.request(region.endpoint + path) {
                this.method = method
                if (body != null) {
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }
                header("Accept", "application/json")
                header("User-Agent", "WorkBuddy/${region.appVersion}")
                headers.forEach { (name, value) -> header(name, value) }
            }
        } catch (e: ResponseException) {
            throw HttpStatusException(
                statusCode = e.response.status.value,
                message = "WorkBuddy HTTP ${e.response.status.value}",
                cause = e,
            )
        }
        val text = response.bodyAsText()
        if (response.status != HttpStatusCode.OK) {
            throw HttpStatusException(
                statusCode = response.status.value,
                message = "WorkBuddy HTTP ${response.status.value}",
            )
        }
        return text.toEnvelopeData(region.displayName())
    }

    private fun JsonObject.toToken(): WorkBuddyToken {
        val now = currentTimeMillis()
        val access = stringOrNull("accessToken").orEmpty()
        val refresh = stringOrNull("refreshToken").orEmpty()
        val expiresIn = longOrNull("expiresIn") ?: 0L
        val refreshExpiresIn = longOrNull("refreshExpiresIn") ?: 0L
        return WorkBuddyToken(
            accessToken = access,
            refreshToken = refresh,
            expiresAtEpochMs = epochMsOrNull("expiresAt")?.takeIf { it > 0 }
                ?: if (expiresIn > 0) now + expiresIn * 1000L else 0L,
            refreshExpiresAtEpochMs = epochMsOrNull("refreshExpiresAt")?.takeIf { it > 0 }
                ?: if (refreshExpiresIn > 0) now + refreshExpiresIn * 1000L else 0L,
            domain = stringOrNull("domain").orEmpty(),
        )
    }

    private fun JsonObject.toAccount(): WorkBuddyAccount =
        WorkBuddyAccount(
            uid = stringOrNull("uid").orEmpty(),
            nickname = stringOrNull("nickname").orEmpty(),
            phoneNumber = stringOrNull("phoneNumber").orEmpty(),
            enterpriseId = stringOrNull("enterpriseId").orEmpty(),
            enterpriseName = stringOrNull("enterpriseName").orEmpty(),
            type = stringOrNull("type").orEmpty(),
        )

    private fun JsonObject.toCreditWindow(): CreditWindow? {
        var total = 0.0
        var used = 0.0
        var prepaid = 0.0
        var resetAt = 0L
        val groups = arrayOrNull("CreditGroups")
            ?.mapNotNull { it.asObjectOrNull() }
            ?.filter { it["CycleTotalCapacity"] != null && it.stringOrNull("CycleTotalCapacity").orEmpty() != "" }
            .orEmpty()
        for (group in groups) {
            total += group.doubleOrNull("CycleTotalCapacity") ?: 0.0
            used += group.doubleOrNull("CycleUsedCapacity") ?: 0.0
        }
        for (packageJson in arrayOrNull("Packages").orEmpty()) {
            val pkg = packageJson.asObjectOrNull() ?: continue
            val packageTotal = pkg.doubleOrNull("CycleTotalCapacity")
            if (packageTotal == null) {
                prepaid += pkg.doubleOrNull("CycleRemainCapacity") ?: 0.0
                continue
            }
            if (groups.isNotEmpty()) continue
            total += packageTotal
            used += pkg.doubleOrNull("CycleUsedCapacity") ?: 0.0
            val end = pkg.epochMsOrNull("CycleEndTime") ?: 0L
            if (end > resetAt) resetAt = end
        }
        if (groups.isNotEmpty()) {
            val subscriptionCode = stringOrNull("SubscriptionPackageCode").orEmpty()
            if (subscriptionCode.isNotBlank()) {
                for (packageJson in arrayOrNull("Packages").orEmpty()) {
                    val pkg = packageJson.asObjectOrNull() ?: continue
                    if (pkg.stringOrNull("PackageCode") == subscriptionCode) {
                        resetAt = pkg.epochMsOrNull("CycleEndTime") ?: 0L
                        break
                    }
                }
            }
        }
        if (total <= 0.0) {
            if (prepaid > 0.0) {
                return CreditWindow(
                    usedPercent = null,
                    resetInSec = null,
                    display = "剩余 ${formatCompact(prepaid)} credits",
                )
            }
            return null
        }
        if (prepaid > 0.0) total += prepaid
        val resetIn = resetAt.takeIf { it > currentTimeMillis() }
            ?.let { (it - currentTimeMillis()) / 1000L }
        return CreditWindow(
            usedPercent = (used / total * 100.0).coerceIn(0.0, 100.0),
            resetInSec = resetIn,
            display = "${formatCompact(used)} / ${formatCompact(total)} credits",
        )
    }

    private fun JsonObject.toEnterpriseWindow(): CreditWindow? {
        val limit = doubleOrNull("limitNum") ?: return null
        if (limit <= 0.0) return null
        val used = doubleOrNull("credit") ?: 0.0
        val resetAt = epochMsOrNull("cycleResetTime")
        return CreditWindow(
            usedPercent = (used / limit * 100.0).coerceIn(0.0, 100.0),
            resetInSec = resetAt?.takeIf { it > currentTimeMillis() }
                ?.let { (it - currentTimeMillis()) / 1000L },
            display = "${formatCompact(used)} / ${formatCompact(limit)} credits",
        )
    }

    private fun endpointOf(region: WorkBuddyRegion, settings: SessionPlatformSettings): String {
        val domain = settings.domain.trim().lowercase()
        if (domain.isBlank() || domain in IOA_HOSTS) return region.endpoint
        return if (CLOUD_HOSTED.any { it.matches(domain) }) "https://$domain" else region.endpoint
    }

    private fun authHeaders(token: WorkBuddyToken): Map<String, String> = buildMap {
        put("Authorization", "Bearer ${token.accessToken}")
        put("X-No-User-Id", "true")
        put("X-No-Enterprise-Id", "true")
        if (token.domain.isNotBlank()) put("X-Domain", token.domain)
    }

    private fun enterpriseAuthHeaders(token: WorkBuddyToken): Map<String, String> = buildMap {
        put("Authorization", "Bearer ${token.accessToken}")
        if (token.domain.isNotBlank()) put("X-Domain", token.domain)
    }

    private fun WorkBuddyRegion.host(): String = endpoint.substringAfter("://")

    private fun WorkBuddyRegion.displayName(): String =
        if (this == WorkBuddyRegion.CHINA) "WorkBuddy" else "WorkBuddy AI"

    private fun String.toEnvelopeData(displayName: String): JsonObject? {
        val root = runCatching { json.parseToJsonElement(this).jsonObject }.getOrElse {
            throw IllegalStateException("$displayName 返回了无法解析的数据", it)
        }
        val code = root.intOrNull("code") ?: 0
        if (code != 0) {
            throw WorkBuddyApiException(code, root.stringOrNull("msg").orEmpty())
        }
        return root.objectOrNull("data")
    }

    private fun formatCompact(value: Double): String {
        val rounded = kotlin.math.round(value * 100.0) / 100.0
        return if (rounded == kotlin.math.floor(rounded)) {
            rounded.toLong().toString()
        } else {
            rounded.toString().trimEnd('0').trimEnd('.')
        }
    }

    private fun SessionPlatformSettings.isExpiring(): Boolean {
        if (accessToken.isBlank() || refreshToken.isBlank()) return true
        if (expiresAtEpochMs <= 0L) return false
        return currentTimeMillis() >= expiresAtEpochMs - 60_000L
    }

    private fun enterpriseHeaders(settings: SessionPlatformSettings): Map<String, String> =
        if (settings.enterpriseId.isBlank()) {
            emptyMap()
        } else {
            mapOf(
                "X-Enterprise-Id" to settings.enterpriseId,
                "X-Tenant-Id" to settings.enterpriseId,
            )
        }

    private fun encodeQuery(value: String): String = buildString {
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

    private fun randomHex(bytes: Int): String = secureRandomHex(bytes)

    private data class CreditWindow(
        val usedPercent: Double?,
        val resetInSec: Long?,
        val display: String,
    )

    private companion object {
        const val HEX = "0123456789ABCDEF"
        val CLOUD_HOSTED = listOf(
            Regex("^[a-z0-9-]+\\.sso\\.copilot\\.tencent\\.com$"),
            Regex("^[a-z0-9-]+\\.copilot\\.qq\\.com$"),
            Regex("^[a-z0-9-]+\\.sso\\.codebuddy\\.cn$"),
        )
        val IOA_HOSTS = setOf("tencent.sso.copilot.tencent.com", "tencent.sso.codebuddy.cn")
    }
}

internal class WorkBuddyApiException(
    val code: Int,
    message: String,
) : Exception("WorkBuddy API error $code: $message")
