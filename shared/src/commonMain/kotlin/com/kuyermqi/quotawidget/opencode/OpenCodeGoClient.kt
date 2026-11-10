package com.kuyermqi.quotawidget.opencode

import com.kuyermqi.quotawidget.deepseek.createHttpClient
import com.kuyermqi.quotawidget.domain.QuotaSnapshot
import com.kuyermqi.quotawidget.domain.QuotaWindow
import com.kuyermqi.quotawidget.domain.SessionExpiredException
import com.kuyermqi.quotawidget.domain.formatUsagePrimaryDisplay
import com.kuyermqi.quotawidget.domain.formatUsageRemainingRolling
import com.kuyermqi.quotawidget.platform.PlatformIds
import com.kuyermqi.quotawidget.platform.PlatformRegistry
import com.kuyermqi.quotawidget.util.currentTimeMillis
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * OpenCode Console client for Go subscription usage.
 *
 * The console moved from `/workspace/{id}` SSR pages to a `/console/{id}` SPA backed by
 * `/console/api` JSON endpoints. Console requests use the `__Host-console_session` cookie;
 * legacy `auth` cookies still go through [OpenCodeLegacyClient]. API-key requests use
 * `GET /zen/go/v1/usage` instead of a browser session.
 */
class OpenCodeGoClient(
    private val httpClient: HttpClient = createHttpClient(),
) {
    private val legacyClient = OpenCodeLegacyClient(httpClient)

    suspend fun listWorkspaces(authCookie: String): List<OpenCodeWorkspace> {
        if (!hasConsoleSessionCookie(authCookie)) {
            return legacyClient.listWorkspaces(authCookie)
        }
        return try {
            listConsoleWorkspaces(authCookie)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!hasLegacySessionCookie(authCookie)) throw e
            legacyClient.listWorkspaces(authCookie)
        }
    }

    private suspend fun listConsoleWorkspaces(authCookie: String): List<OpenCodeWorkspace> {
        val body = getJson(
            url = "$CONSOLE_API_BASE/orgs",
            cookieHeader = cookieHeaderFor(authCookie),
            referer = CONSOLE_BASE_URL,
        )
        val workspaces = try {
            json.decodeFromString(ListSerializer, body)
        } catch (e: Exception) {
            throw IllegalStateException(
                "无法解析 OpenCode Workspace 列表 (prefix=${body.take(120).replace('\n', ' ')})",
                e,
            )
        }
        return workspaces
            .filter { it.id.isNotBlank() }
            .map { OpenCodeWorkspace(id = it.id, name = it.name.orEmpty()) }
    }

    suspend fun fetchQuota(workspaceId: String, authCookie: String): QuotaSnapshot {
        if (!hasConsoleSessionCookie(authCookie)) {
            return legacyClient.fetchQuota(workspaceId, authCookie)
        }
        return try {
            fetchConsoleQuota(workspaceId, authCookie)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!hasLegacySessionCookie(authCookie)) throw e
            legacyClient.fetchQuota(workspaceId, authCookie)
        }
    }

    private suspend fun fetchConsoleQuota(workspaceId: String, authCookie: String): QuotaSnapshot {
        val body = getJson(
            url = "$CONSOLE_API_BASE/go/status?fresh=true",
            cookieHeader = cookieHeaderFor(authCookie),
            referer = goPageUrl(workspaceId),
            workspaceId = workspaceId,
        )
        val windows = try {
            OpenCodeGoStatusParser.parse(body, nowEpochMs = currentTimeMillis())
        } catch (e: Exception) {
            throw IllegalStateException(
                "无法解析 OpenCode Go 额度 (prefix=${body.take(160).replace('\n', ' ')})",
                e,
            )
        }
        return snapshotFromWindows(windows)
    }

    suspend fun fetchQuotaByApiKey(apiKey: String): QuotaSnapshot {
        val token = apiKey.trim()
        require(token.isNotEmpty()) { "OpenCode API Key 不能为空" }
        val body = getApiJson(token)
        val windows = try {
            OpenCodeGoApiUsageParser.parse(body, nowEpochMs = currentTimeMillis())
        } catch (e: Exception) {
            throw IllegalStateException(
                "无法解析 OpenCode Go API 额度 (prefix=${body.take(160).replace('\n', ' ')})",
                e,
            )
        }
        return snapshotFromWindows(windows)
    }

    private fun snapshotFromWindows(windows: List<QuotaWindow>): QuotaSnapshot {
        if (windows.isEmpty()) {
            throw IllegalStateException("OpenCode Go 未开通或额度不可用")
        }
        return QuotaSnapshot(
            platformId = PlatformIds.OPENCODE_GO,
            platformName = PlatformRegistry.displayName(PlatformIds.OPENCODE_GO),
            windows = windows,
            primaryDisplay = formatUsageRemainingRolling(windows)
                ?: formatUsagePrimaryDisplay(windows),
            updatedAtEpochMs = currentTimeMillis(),
        )
    }

    fun close() {
        httpClient.close()
    }

    private suspend fun getJson(
        url: String,
        cookieHeader: String,
        referer: String,
        workspaceId: String? = null,
    ): String {
        val response = try {
            httpClient.get(url) {
                header(HttpHeaders.Cookie, cookieHeader)
                header(HttpHeaders.Accept, "application/json")
                header(HttpHeaders.UserAgent, BROWSER_UA)
                header(HttpHeaders.Referrer, referer)
                if (workspaceId != null) {
                    header(HEADER_WORKSPACE_ID, workspaceId)
                }
            }
        } catch (e: ResponseException) {
            when (e.response.status) {
                HttpStatusCode.Unauthorized,
                HttpStatusCode.Forbidden,
                -> throw SessionExpiredException("OpenCode 会话已失效")
                HttpStatusCode.NotFound -> throw IllegalStateException(
                    "OpenCode 接口不存在或 Workspace 无效",
                    e,
                )
                else -> throw IllegalStateException(
                    "OpenCode 请求失败 HTTP ${e.response.status.value}: ${e.message}",
                    e,
                )
            }
        }
        val body = response.bodyAsText()
        val trimmed = body.trimStart()
        if (trimmed.startsWith("<") || AUTH_ERROR_BODY.containsMatchIn(body)) {
            throw SessionExpiredException("OpenCode 会话已失效")
        }
        return body
    }

    private suspend fun getApiJson(apiKey: String): String {
        val response = try {
            httpClient.get(USAGE_API_URL) {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                header(HttpHeaders.Accept, "application/json")
                header(HttpHeaders.UserAgent, BROWSER_UA)
            }
        } catch (e: ResponseException) {
            when (e.response.status) {
                HttpStatusCode.Unauthorized,
                HttpStatusCode.Forbidden,
                -> throw IllegalStateException("OpenCode API Key 无效或已过期", e)
                HttpStatusCode.NotFound -> throw IllegalStateException(
                    "OpenCode Go 用量接口不存在",
                    e,
                )
                else -> throw IllegalStateException(
                    "OpenCode Go 用量请求失败 HTTP ${e.response.status.value}: ${e.message}",
                    e,
                )
            }
        }
        val body = response.bodyAsText()
        if (body.trimStart().startsWith("<")) {
            throw IllegalStateException("OpenCode Go 用量接口返回了非 JSON 响应")
        }
        return body
    }

    companion object {
        const val BASE_URL = "https://opencode.ai"
        const val CONSOLE_BASE_URL = "$BASE_URL/console"
        const val CONSOLE_API_BASE = "$CONSOLE_BASE_URL/api"
        const val LOGIN_URL = "$CONSOLE_BASE_URL/login"
        const val USAGE_API_URL = "$BASE_URL/zen/go/v1/usage"

        private const val HEADER_WORKSPACE_ID = "x-org-id"
        private const val BROWSER_UA =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/125.0.0.0 Mobile Safari/537.36"

        private val AUTH_ERROR_BODY = Regex(""""_tag"\s*:\s*"Unauthorized"""")

        private val WORKSPACE_PATH =
            Regex("""/(?:console|workspace)/((?:wrk|org)_[A-Za-z0-9_-]+)(?:/|$)""")

        private val CONSOLE_SESSION_COOKIE_NAMES = listOf(
            "__Host-console_session",
            "console_session",
        )
        private val LEGACY_SESSION_COOKIE_NAMES = listOf("auth", "__Host-auth")
        private val AUTH_COOKIE_NAMES = CONSOLE_SESSION_COOKIE_NAMES + LEGACY_SESSION_COOKIE_NAMES

        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        fun hasConsoleSessionCookie(cookieHeader: String): Boolean =
            hasCookie(cookieHeader, CONSOLE_SESSION_COOKIE_NAMES)

        fun hasLegacySessionCookie(cookieHeader: String): Boolean =
            hasCookie(cookieHeader, LEGACY_SESSION_COOKIE_NAMES)

        private fun hasCookie(cookieHeader: String, names: List<String>): Boolean =
            cookieHeader.split(';').any { part ->
                val trimmed = part.trim()
                names.any { name -> trimmed.startsWith("$name=", ignoreCase = true) }
            }

        fun goPageUrl(workspaceId: String): String = "$CONSOLE_BASE_URL/$workspaceId/go"

        fun extractWorkspaceId(url: String): String? =
            WORKSPACE_PATH.find(url)?.groupValues?.getOrNull(1)

        fun isLoginSuccessUrl(url: String): Boolean =
            extractWorkspaceId(url) != null

        fun extractAuthCookie(cookieHeader: String?): String? {
            if (cookieHeader.isNullOrBlank()) return null
            // Prefer the last session entry when CookieManager returns duplicates.
            return cookieHeader
                .split(';')
                .map { it.trim() }
                .filter { cookie ->
                    AUTH_COOKIE_NAMES.any { name ->
                        cookie.startsWith("$name=", ignoreCase = true)
                    }
                }
                .map { it.substringAfter('=') }
                .lastOrNull()
                ?.takeIf { it.isNotBlank() }
        }

        /** Prefer the full CookieManager header when available. */
        fun cookieHeaderFor(authCookieOrHeader: String): String {
            val trimmed = authCookieOrHeader.trim()
            if (extractAuthCookie(trimmed) != null) {
                return trimmed
            }
            return "auth=$trimmed"
        }
    }
}

@Serializable
private data class OpenCodeWorkspaceDto(
    val id: String = "",
    val name: String? = null,
)

private val ListSerializer =
    kotlinx.serialization.builtins.ListSerializer(OpenCodeWorkspaceDto.serializer())
