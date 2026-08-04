package com.kuyermqi.quotawidget.codex

import com.kuyermqi.quotawidget.deepseek.createHttpClient
import com.kuyermqi.quotawidget.domain.SessionExpiredException
import com.kuyermqi.quotawidget.util.currentTimeMillis
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.random.Random

/**
 * Codex CLI OAuth (PKCE) — same client as sub2api / CLIProxyAPI / OpenCode.
 */
class CodexOAuth(
    private val httpClient: HttpClient = createHttpClient(),
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    },
) {
    fun createPkceSession(): CodexPkceSession {
        val verifier = generateCodeVerifier()
        val challenge = generateCodeChallenge(verifier)
        val state = generateHex(32)
        return CodexPkceSession(
            state = state,
            codeVerifier = verifier,
            codeChallenge = challenge,
            authorizeUrl = buildAuthorizeUrl(state, challenge),
        )
    }

    suspend fun exchangeCode(
        code: String,
        codeVerifier: String,
        redirectUri: String = REDIRECT_URI,
    ): CodexTokenBundle {
        val response = try {
            httpClient.submitForm(
                url = TOKEN_URL,
                formParameters = Parameters.build {
                    append("grant_type", "authorization_code")
                    append("client_id", CLIENT_ID)
                    append("code", code)
                    append("redirect_uri", redirectUri)
                    append("code_verifier", codeVerifier)
                },
            )
        } catch (e: ClientRequestException) {
            throw IllegalStateException("OAuth token exchange failed: ${e.response.status}", e)
        }
        val body = response.bodyAsText()
        val token = json.decodeFromString(CodexTokenResponse.serializer(), body)
        return token.toBundle()
    }

    suspend fun refresh(refreshToken: String): CodexTokenBundle {
        val response = try {
            httpClient.submitForm(
                url = TOKEN_URL,
                formParameters = Parameters.build {
                    append("grant_type", "refresh_token")
                    append("client_id", CLIENT_ID)
                    append("refresh_token", refreshToken)
                    append("scope", REFRESH_SCOPES)
                },
            )
        } catch (e: ClientRequestException) {
            val status = e.response.status
            if (status == HttpStatusCode.Unauthorized || status == HttpStatusCode.Forbidden) {
                throw SessionExpiredException("Codex 登录已失效，请重新登录")
            }
            val errBody = runCatching { e.response.bodyAsText() }.getOrNull().orEmpty()
            if (errBody.contains("refresh_token_reused", ignoreCase = true) ||
                errBody.contains("invalid_grant", ignoreCase = true)
            ) {
                throw SessionExpiredException("Codex 登录已失效，请重新登录")
            }
            throw IllegalStateException("OAuth token refresh failed: $status", e)
        }
        val body = response.bodyAsText()
        val token = json.decodeFromString(CodexTokenResponse.serializer(), body)
        return token.toBundle(fallbackRefreshToken = refreshToken)
    }

    fun close() {
        httpClient.close()
    }

    companion object {
        const val CLIENT_ID = "app_EMoamEEZ73f0CkXaXp7hrann"
        const val AUTHORIZE_URL = "https://auth.openai.com/oauth/authorize"
        const val TOKEN_URL = "https://auth.openai.com/oauth/token"
        const val REDIRECT_URI = "http://localhost:1455/auth/callback"
        const val DEFAULT_SCOPES = "openid email profile offline_access"
        const val REFRESH_SCOPES = "openid profile email"
        const val ORIGINATOR = "quota-widget"

        /** Refresh when access token expires within this window. */
        const val REFRESH_SKEW_MS = 5 * 60 * 1000L

        fun buildAuthorizeUrl(state: String, codeChallenge: String): String {
            val params = listOf(
                "response_type" to "code",
                "client_id" to CLIENT_ID,
                "redirect_uri" to REDIRECT_URI,
                "scope" to DEFAULT_SCOPES,
                "state" to state,
                "code_challenge" to codeChallenge,
                "code_challenge_method" to "S256",
                "id_token_add_organizations" to "true",
                "codex_cli_simplified_flow" to "true",
                "originator" to ORIGINATOR,
                "prompt" to "login",
            ).joinToString("&") { (k, v) ->
                "${encodeQuery(k)}=${encodeQuery(v)}"
            }
            return "$AUTHORIZE_URL?$params"
        }

        fun generateCodeVerifier(): String = generateHex(64)

        fun generateCodeChallenge(verifier: String): String {
            val digest = sha256(verifier.encodeToByteArray())
            return base64UrlNoPad(digest)
        }

        fun parseJwtClaims(jwt: String): CodexJwtClaims {
            val parts = jwt.split('.')
            require(parts.size >= 2) { "invalid JWT" }
            val payloadJson = decodeBase64Url(parts[1]).decodeToString()
            return parseClaimsJson(payloadJson)
        }

        fun parseClaimsJson(payloadJson: String): CodexJwtClaims {
            val root = Json { ignoreUnknownKeys = true }.parseToJsonElement(payloadJson).jsonObject
            val email = root["email"]?.jsonPrimitive?.content
            val auth = root["https://api.openai.com/auth"]?.jsonObject
            val profile = root["https://api.openai.com/profile"]?.jsonObject
            return CodexJwtClaims(
                email = email
                    ?: profile?.get("email")?.jsonPrimitive?.content
                    ?: auth?.get("email")?.jsonPrimitive?.content,
                chatgptAccountId = auth?.string("chatgpt_account_id"),
                chatgptUserId = auth?.string("chatgpt_user_id") ?: auth?.string("user_id"),
                chatgptPlanType = auth?.string("chatgpt_plan_type"),
            )
        }

        fun isAccessTokenExpiringSoon(
            expiresAtEpochMs: Long,
            nowEpochMs: Long = currentTimeMillis(),
            skewMs: Long = REFRESH_SKEW_MS,
        ): Boolean {
            if (expiresAtEpochMs <= 0L) return true
            return nowEpochMs >= expiresAtEpochMs - skewMs
        }

        private fun JsonObject.string(key: String): String? =
            this[key]?.jsonPrimitive?.content

        private fun generateHex(byteCount: Int): String {
            val bytes = ByteArray(byteCount)
            Random.Default.nextBytes(bytes)
            return bytes.joinToString("") { b ->
                (b.toInt() and 0xff).toString(16).padStart(2, '0')
            }
        }

        private fun encodeQuery(value: String): String =
            buildString(value.length) {
                for (ch in value) {
                    when {
                        ch.isLetterOrDigit() || ch in "-._~" -> append(ch)
                        else -> append('%').append(
                            ch.code.toString(16).uppercase().padStart(2, '0'),
                        )
                    }
                }
            }

        @OptIn(ExperimentalEncodingApi::class)
        private fun base64UrlNoPad(bytes: ByteArray): String =
            Base64.UrlSafe.encode(bytes).trimEnd('=')

        @OptIn(ExperimentalEncodingApi::class)
        private fun decodeBase64Url(value: String): ByteArray {
            val padded = when (value.length % 4) {
                2 -> "$value=="
                3 -> "$value="
                else -> value
            }
            return Base64.UrlSafe.decode(padded)
        }

        /** Portable SHA-256 without expecting a platform crypto API. */
        private fun sha256(input: ByteArray): ByteArray {
            // Use Kotlin's experimental Digests if available — fall back via expect would be ideal;
            // Ktor/OkHttp path uses JVM MessageDigest on Android via actual. For commonMain tests
            // we implement a thin expect/actual. See CodexSha256.
            return codexSha256(input)
        }
    }
}

data class CodexPkceSession(
    val state: String,
    val codeVerifier: String,
    val codeChallenge: String,
    val authorizeUrl: String,
)

data class CodexTokenBundle(
    val accessToken: String,
    val refreshToken: String,
    val idToken: String,
    val expiresAtEpochMs: Long,
    val accountId: String,
    val email: String,
    val planType: String,
)

data class CodexJwtClaims(
    val email: String? = null,
    val chatgptAccountId: String? = null,
    val chatgptUserId: String? = null,
    val chatgptPlanType: String? = null,
)

@Serializable
internal data class CodexTokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("id_token") val idToken: String? = null,
    @SerialName("expires_in") val expiresIn: Long = 3600,
    @SerialName("token_type") val tokenType: String? = null,
)

private fun CodexTokenResponse.toBundle(
    fallbackRefreshToken: String? = null,
): CodexTokenBundle {
    val id = idToken.orEmpty()
    val claims = if (id.isNotBlank()) {
        runCatching { CodexOAuth.parseJwtClaims(id) }.getOrNull()
    } else {
        null
    }
    val accessClaims = runCatching { CodexOAuth.parseJwtClaims(accessToken) }.getOrNull()
    val accountId = claims?.chatgptAccountId
        ?: accessClaims?.chatgptAccountId
        ?: ""
    require(accessToken.isNotBlank()) { "missing access_token" }
    val refresh = refreshToken?.takeIf { it.isNotBlank() } ?: fallbackRefreshToken.orEmpty()
    require(refresh.isNotBlank()) { "missing refresh_token" }
    require(accountId.isNotBlank()) { "missing chatgpt_account_id in token" }
    return CodexTokenBundle(
        accessToken = accessToken,
        refreshToken = refresh,
        idToken = id,
        expiresAtEpochMs = currentTimeMillis() + expiresIn.coerceAtLeast(60) * 1000L,
        accountId = accountId,
        email = claims?.email ?: accessClaims?.email ?: "",
        planType = claims?.chatgptPlanType ?: accessClaims?.chatgptPlanType ?: "",
    )
}
