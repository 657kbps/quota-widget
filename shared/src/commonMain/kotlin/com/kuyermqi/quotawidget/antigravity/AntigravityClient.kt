package com.kuyermqi.quotawidget.antigravity

import com.kuyermqi.quotawidget.codex.codexSha256
import com.kuyermqi.quotawidget.domain.HttpStatusException
import com.kuyermqi.quotawidget.domain.NeutralUsageLabels
import com.kuyermqi.quotawidget.domain.QuotaSnapshot
import com.kuyermqi.quotawidget.domain.QuotaUnavailableException
import com.kuyermqi.quotawidget.domain.QuotaWindow
import com.kuyermqi.quotawidget.domain.QuotaWindowKind
import com.kuyermqi.quotawidget.domain.SessionExpiredException
import com.kuyermqi.quotawidget.domain.formatUsagePrimaryDisplay
import com.kuyermqi.quotawidget.platform.PlatformIds
import com.kuyermqi.quotawidget.platform.PlatformRegistry
import com.kuyermqi.quotawidget.session.asObjectOrNull
import com.kuyermqi.quotawidget.session.arrayOrNull
import com.kuyermqi.quotawidget.session.booleanOrNull
import com.kuyermqi.quotawidget.session.doubleOrNull
import com.kuyermqi.quotawidget.session.epochMsOrNull
import com.kuyermqi.quotawidget.session.objectOrNull
import com.kuyermqi.quotawidget.session.stringOrNull
import com.kuyermqi.quotawidget.settings.SessionPlatformSettings
import com.kuyermqi.quotawidget.util.currentTimeMillis
import com.kuyermqi.quotawidget.util.secureRandomBytes
import com.kuyermqi.quotawidget.util.suspendRunCatching
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.Parameters
import io.ktor.http.contentType
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

data class AntigravityLoginSession(
    val state: String,
    val codeVerifier: String,
    val authorizeUrl: String,
    val redirectUri: String,
)

data class AntigravityAuthResult(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochMs: Long,
    val email: String,
)

class AntigravityClient(
    private val httpClient: HttpClient,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    },
) {
    suspend fun exchangeCode(
        code: String,
        codeVerifier: String,
        redirectUri: String,
    ): AntigravityAuthResult {
        val response = try {
            httpClient.submitForm(
                url = TOKEN_URL,
                formParameters = Parameters.build {
                    append("grant_type", "authorization_code")
                    append("code", code)
                    append("client_id", CLIENT_ID)
                    append("client_secret", CLIENT_SECRET)
                    append("redirect_uri", redirectUri)
                    append("code_verifier", codeVerifier)
                },
            )
        } catch (e: ClientRequestException) {
            throw SessionExpiredException("Antigravity 授权失败，请重新登录")
        }
        val data = json.parseToJsonElement(response.bodyAsText()).jsonObject
        val access = data.stringOrNull("access_token").orEmpty()
        val refresh = data.stringOrNull("refresh_token").orEmpty()
        if (access.isBlank() || refresh.isBlank()) {
            throw IllegalStateException("Antigravity 登录响应缺少令牌")
        }
        val expiresIn = data["expires_in"]?.toString()?.toLongOrNull() ?: 3600L
        return AntigravityAuthResult(
            accessToken = access,
            refreshToken = refresh,
            expiresAtEpochMs = currentTimeMillis() + expiresIn * 1000L,
            email = fetchEmail(access).orEmpty(),
        )
    }

    suspend fun finishLogin(
        auth: AntigravityAuthResult,
        previous: SessionPlatformSettings,
        save: suspend (SessionPlatformSettings) -> Unit,
    ): SessionPlatformSettings {
        val base = previous.copy(
            accessToken = auth.accessToken,
            refreshToken = auth.refreshToken,
            expiresAtEpochMs = auth.expiresAtEpochMs,
            apiKey = "",
            email = auth.email,
            accountId = auth.email,
            plan = "",
            projectId = "",
            uid = "",
            enterpriseId = "",
            domain = "",
        )
        val project = try {
            loadProject(auth.accessToken, existingProject = "")
        } catch (e: SessionExpiredException) {
            throw e
        } catch (e: Exception) {
            ProjectInfo("", "")
        }
        val next = base.copy(projectId = project.id, plan = project.plan)
        save(next)
        return next
    }

    suspend fun fetchQuota(
        settings: SessionPlatformSettings,
        save: suspend (SessionPlatformSettings) -> Unit,
    ): QuotaSnapshot {
        var current = settings
        if (current.isExpiring()) {
            current = refresh(current, save)
        }
        val project = try {
            loadProject(current.accessToken, current.projectId)
        } catch (e: SessionExpiredException) {
            current = refresh(current, save)
            loadProject(current.accessToken, current.projectId)
        }
        val next = current.copy(
            projectId = project.id.ifBlank { current.projectId },
            plan = project.plan.ifBlank { current.plan },
        )
        if (next != current) {
            save(next)
            current = next
        }
        val windows = try {
            fetchQuotaWindows(current.accessToken, current.projectId)
        } catch (e: SessionExpiredException) {
            current = refresh(current, save)
            fetchQuotaWindows(current.accessToken, current.projectId)
        }
        if (windows.isEmpty()) {
            throw QuotaUnavailableException("Antigravity 未返回可用的额度信息")
        }
        return QuotaSnapshot(
            platformId = PlatformIds.ANTIGRAVITY,
            platformName = PlatformRegistry.displayName(PlatformIds.ANTIGRAVITY),
            windows = windows,
            primaryDisplay = formatUsagePrimaryDisplay(windows, NeutralUsageLabels),
            updatedAtEpochMs = currentTimeMillis(),
        )
    }

    suspend fun refresh(
        settings: SessionPlatformSettings,
        save: suspend (SessionPlatformSettings) -> Unit,
    ): SessionPlatformSettings {
        if (settings.refreshToken.isBlank()) {
            throw SessionExpiredException("Antigravity 登录已过期，请重新登录")
        }
        val response = try {
            httpClient.submitForm(
                url = TOKEN_URL,
                formParameters = Parameters.build {
                    append("grant_type", "refresh_token")
                    append("refresh_token", settings.refreshToken)
                    append("client_id", CLIENT_ID)
                    append("client_secret", CLIENT_SECRET)
                },
            )
        } catch (e: ResponseException) {
            val status = e.response.status.value
            if (status == 400 || status == 401) {
                throw SessionExpiredException("Antigravity 登录已过期，请重新登录")
            }
            throw HttpStatusException(
                statusCode = status,
                message = "Antigravity 刷新登录失败: HTTP $status",
                cause = e,
            )
        }
        val data = json.parseToJsonElement(response.bodyAsText()).jsonObject
        val access = data.stringOrNull("access_token").orEmpty()
        if (access.isBlank()) {
            throw SessionExpiredException("Antigravity 登录已过期，请重新登录")
        }
        val expiresIn = data["expires_in"]?.toString()?.toLongOrNull() ?: 3600L
        val next = settings.copy(
            accessToken = access,
            refreshToken = data.stringOrNull("refresh_token") ?: settings.refreshToken,
            expiresAtEpochMs = currentTimeMillis() + expiresIn * 1000L,
        )
        save(next)
        return next
    }

    private suspend fun fetchEmail(accessToken: String): String? {
        val response = suspendRunCatching {
            httpClient.get(WHO_URL) {
                header(HttpHeaders.Authorization, "Bearer $accessToken")
            }
        }.getOrNull() ?: return null
        return suspendRunCatching {
            json.parseToJsonElement(response.bodyAsText()).jsonObject.stringOrNull("email")
        }.getOrNull()
    }

    private suspend fun loadProject(accessToken: String, existingProject: String): ProjectInfo {
        val load = try {
            call(PROD, "loadCodeAssist", accessToken)
        } catch (e: SessionExpiredException) {
            throw e
        } catch (e: Exception) {
            if (existingProject.isNotBlank()) return ProjectInfo(existingProject, "")
            throw e
        }
        val tier = load.objectOrNull("paidTier")
            ?: load.objectOrNull("currentTier")
        val plan = tier?.stringOrNull("name").orEmpty()
        val own = companionProject(load["cloudaicompanionProject"])
        if (own.isNotBlank()) return ProjectInfo(own, plan)
        if (existingProject.isNotBlank()) return ProjectInfo(existingProject, plan)
        val allowed = load.arrayOrNull("allowedTiers")
            ?.mapNotNull { it.asObjectOrNull() }
            .orEmpty()
        val defaultTier = allowed.firstOrNull { it["isDefault"]?.toString() == "true" }
            ?: allowed.firstOrNull()
        var tierId = defaultTier?.stringOrNull("id").orEmpty()
        if (tierId.isBlank() || tierId == "legacy-tier") tierId = "free-tier"
        val answer = onboard(accessToken, tierId)
        if (answer.isBlank()) {
            val reason = load.arrayOrNull("ineligibleTiers")
                ?.mapNotNull { it.asObjectOrNull() }
                ?.firstNotNullOfOrNull { it.stringOrNull("reasonMessage") }
            throw IllegalStateException(
                reason?.let { "Antigravity 无法为当前 Google 账号开通: $it" }
                    ?: "Antigravity 未返回 Code Assist 项目，请先在 Antigravity 中登录该账号",
            )
        }
        return ProjectInfo(answer, plan.ifBlank { defaultTier?.stringOrNull("name").orEmpty() })
    }

    private suspend fun onboard(accessToken: String, tierId: String): String {
        repeat(5) {
            val body = buildJsonObject {
                put("tierId", tierId)
                put(
                    "metadata",
                    buildJsonObject { put("ideType", "ANTIGRAVITY") },
                )
            }
            val answer = call(DAILY, "onboardUser", accessToken, body)
            if (answer["done"]?.toString() == "true") {
                val response = answer.objectOrNull("response")
                return companionProject(response?.get("cloudaicompanionProject"))
            }
            delay(500)
        }
        return ""
    }

    private suspend fun fetchQuotaWindows(accessToken: String, projectId: String): List<QuotaWindow> {
        val windows = mutableListOf<QuotaWindow>()
        val summary = try {
            call(
                base = DAILY,
                method = "retrieveUserQuotaSummary",
                accessToken = accessToken,
                body = buildJsonObject { put("project", projectId) },
            )
        } catch (e: SessionExpiredException) {
            throw e
        } catch (e: Exception) {
            null
        }
        val buckets = mutableMapOf<QuotaWindowKind, QuotaWindow>()
        for (group in summary?.arrayOrNull("groups").orEmpty()) {
            val groupObject = group.asObjectOrNull() ?: continue
            for (bucket in groupObject.arrayOrNull("buckets").orEmpty()) {
                val item = bucket.asObjectOrNull() ?: continue
                if (item.booleanOrNull("disabled") == true) continue
                val kind = when (item.stringOrNull("window")?.lowercase()) {
                    "5h" -> QuotaWindowKind.FIVE_HOUR
                    "weekly" -> QuotaWindowKind.WEEKLY
                    else -> continue
                }
                val remaining = item.doubleOrNull("remainingFraction") ?: continue
                val resetAt = item.epochMsOrNull("resetTime")
                val candidate = QuotaWindow(
                    kind = kind,
                    usedPercent = ((1.0 - remaining) * 100.0).coerceIn(0.0, 100.0),
                    resetInSec = resetAt?.takeIf { it > currentTimeMillis() }
                        ?.let { (it - currentTimeMillis()) / 1000L },
                )
                val previous = buckets[kind]
                if (previous == null || (candidate.usedPercent ?: 0.0) > (previous.usedPercent ?: 0.0)) {
                    buckets[kind] = candidate
                }
            }
        }
        windows += listOfNotNull(
            buckets[QuotaWindowKind.FIVE_HOUR],
            buckets[QuotaWindowKind.WEEKLY],
        )
        if (windows.isNotEmpty()) return windows
        val models = try {
            call(
                base = DAILY,
                method = "fetchAvailableModels",
                accessToken = accessToken,
                body = buildJsonObject { put("project", projectId) },
            )
        } catch (e: SessionExpiredException) {
            throw e
        } catch (e: Exception) {
            null
        }
        var tightest: QuotaWindow? = null
        val modelMap = models?.objectOrNull("models") ?: JsonObject(emptyMap())
        for ((_, value) in modelMap) {
            val model = value.asObjectOrNull() ?: continue
            val quota = model.objectOrNull("quotaInfo") ?: continue
            val remaining = quota.doubleOrNull("remainingFraction") ?: continue
            val resetAt = quota.epochMsOrNull("resetTime")
            val candidate = QuotaWindow(
                kind = QuotaWindowKind.FIVE_HOUR,
                usedPercent = ((1.0 - remaining) * 100.0).coerceIn(0.0, 100.0),
                resetInSec = resetAt?.takeIf { it > currentTimeMillis() }
                    ?.let { (it - currentTimeMillis()) / 1000L },
            )
            if (tightest == null || (candidate.usedPercent ?: 0.0) > (tightest.usedPercent ?: 0.0)) {
                tightest = candidate
            }
        }
        return listOfNotNull(tightest)
    }

    private suspend fun call(
        base: String,
        method: String,
        accessToken: String,
        body: JsonObject = buildJsonObject { put("metadata", buildJsonObject { put("ideType", "ANTIGRAVITY") }) },
    ): JsonObject {
        val response = try {
            httpClient.request("$base/v1internal:$method") {
                this.method = HttpMethod.Post
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $accessToken")
                header(HttpHeaders.UserAgent, USER_AGENT)
                setBody(body.toString())
            }
        } catch (e: ResponseException) {
            val status = e.response.status.value
            if (status == 401 || status == 403) {
                throw SessionExpiredException("Antigravity 登录已过期，请重新登录")
            }
            throw HttpStatusException(
                statusCode = status,
                message = "Antigravity 请求失败: HTTP $status",
                cause = e,
            )
        }
        val text = response.bodyAsText()
        return runCatching { json.parseToJsonElement(text).jsonObject }.getOrElse {
            throw IllegalStateException("Antigravity 返回了无法解析的数据", it)
        }
    }

    private fun companionProject(value: Any?): String = when (value) {
        is kotlinx.serialization.json.JsonPrimitive -> if (value.isString) value.content else ""
        is JsonObject -> value.stringOrNull("id").orEmpty()
        else -> ""
    }

    private fun SessionPlatformSettings.isExpiring(): Boolean {
        if (accessToken.isBlank()) return true
        if (expiresAtEpochMs <= 0L) return false
        return currentTimeMillis() >= expiresAtEpochMs - 2 * 60 * 1000L
    }

    private data class ProjectInfo(val id: String, val plan: String)

    companion object {
        @OptIn(ExperimentalEncodingApi::class)
        fun createLoginSession(): AntigravityLoginSession {
            val verifier = Base64.UrlSafe.encode(secureRandomBytes(32)).trimEnd('=')
            val challenge = Base64.UrlSafe.encode(
                codexSha256(verifier.encodeToByteArray()),
            ).trimEnd('=')
            val state = Base64.UrlSafe.encode(secureRandomBytes(24)).trimEnd('=')
            val redirect = REDIRECT_URI
            val url = buildString {
                append(AUTH_URL)
                append("?client_id=").append(percentEncode(CLIENT_ID))
                append("&redirect_uri=").append(percentEncode(redirect))
                append("&response_type=code")
                append("&scope=").append(percentEncode(SCOPES.joinToString(" ")))
                append("&access_type=offline")
                append("&prompt=consent")
                append("&state=").append(percentEncode(state))
                append("&code_challenge=").append(percentEncode(challenge))
                append("&code_challenge_method=S256")
            }
            return AntigravityLoginSession(
                state = state,
                codeVerifier = verifier,
                authorizeUrl = url,
                redirectUri = redirect,
            )
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

        private const val CLIENT_ID = "1071006060591-tmhssin2h21lcre235vtolojh4g403ep.apps.googleusercontent.com"
        private const val CLIENT_SECRET = "GOCSPX-" + "K58FWR486LdLJ1mLB8sXC4z6qDAf"
        private const val AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth"
        private const val TOKEN_URL = "https://oauth2.googleapis.com/token"
        private const val WHO_URL = "https://www.googleapis.com/oauth2/v2/userinfo?alt=json"
        private const val PROD = "https://cloudcode-pa.googleapis.com"
        private const val DAILY = "https://daily-cloudcode-pa.googleapis.com"
        private const val REDIRECT_URI = "http://localhost:53683/oauth-callback"
        private const val USER_AGENT =
            "antigravity/hub/2.32.1 (aidev_client; os_type=android; arch=arm64; cl=1021296516)"
        private const val HEX = "0123456789ABCDEF"
        private val SCOPES = listOf(
            "https://www.googleapis.com/auth/cloud-platform",
            "https://www.googleapis.com/auth/userinfo.email",
            "https://www.googleapis.com/auth/userinfo.profile",
            "https://www.googleapis.com/auth/cclog",
            "https://www.googleapis.com/auth/experimentsandconfigs",
            "https://www.googleapis.com/auth/aicode",
        )
    }
}
