package com.kuyermqi.quotawidget.opencode

import com.kuyermqi.quotawidget.deepseek.createHttpClient
import com.kuyermqi.quotawidget.domain.QuotaSnapshot
import com.kuyermqi.quotawidget.domain.SessionExpiredException
import com.kuyermqi.quotawidget.domain.formatUsagePrimaryDisplay
import com.kuyermqi.quotawidget.domain.formatUsageRemainingRolling
import com.kuyermqi.quotawidget.platform.PlatformIds
import com.kuyermqi.quotawidget.platform.PlatformRegistry
import com.kuyermqi.quotawidget.util.currentTimeMillis
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType

class OpenCodeGoClient(
    private val httpClient: HttpClient = createHttpClient(),
) {
    private var cachedServerFnId: String? = null
    private var cachedWorkspacesServerFnId: String? = null

    suspend fun listWorkspaces(authCookie: String): List<OpenCodeWorkspace> {
        val cookieHeader = cookieHeaderFor(authCookie)
        val serverFnId = resolveWorkspacesServerFnId(cookieHeader)
        log("listWorkspaces serverFnId=$serverFnId")
        val body = invokeServerFn(
            serverFnId = serverFnId,
            cookieHeader = cookieHeader,
            argsJson = encodeEmptyServerFnArgs(),
            referer = "$BASE_URL/",
        )
        if (looksLikeAuthError(body) || looksLikeLoginPage(body)) {
            throw SessionExpiredException("OpenCode 会话已失效")
        }
        return SerovalServerFnDecoder.decodeWorkspaces(body)
            ?: throw IllegalStateException(
                "无法解析 Workspace 列表 (prefix=${body.take(160).replace('\n', ' ')})",
            )
    }

    suspend fun fetchQuota(workspaceId: String, authCookie: String): QuotaSnapshot {
        val cookieHeader = cookieHeaderFor(authCookie)
        log("fetchQuota workspace=$workspaceId cookieLen=${cookieHeader.length}")
        // Prefer the Go document: SSR runs session.get + lite query in one request, which is the
        // only reliable path. Standalone lite.subscription.get omits workspace in withActor and
        // fails with "not associated with a workspace" even for valid sessions.
        val html = fetchGoPage(workspaceId, cookieHeader)
        if (looksLikeLoginPage(html)) {
            throw SessionExpiredException("OpenCode 会话已失效")
        }
        var dto = GoPageUsageParser.parse(html)
        if (dto == null) {
            log("go page parse miss; trying _server fallback")
            dto = runCatching {
                val serverFnId = resolveServerFnId(workspaceId, cookieHeader)
                val body = invokeLiteSubscription(workspaceId, cookieHeader, serverFnId)
                if (looksLikeAuthError(body)) {
                    log(" _server auth-like error prefix=${body.take(120).replace('\n', ' ')}")
                    null
                } else {
                    SerovalServerFnDecoder.decodeLiteSubscription(body)
                }
            }.onFailure { log("_server fallback failed: ${it.message}") }.getOrNull()
        }
        if (dto == null) {
            throw IllegalStateException(
                "无法解析 OpenCode Go 额度 (pageLen=${html.length} prefix=${html.take(120).replace('\n', ' ')})",
            )
        }
        val windows = dto.toWindows()
        if (windows.isEmpty()) {
            throw IllegalStateException("无法解析 OpenCode Go 额度窗口")
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

    private suspend fun fetchGoPage(workspaceId: String, cookieHeader: String): String {
        val goUrl = "$BASE_URL/workspace/$workspaceId/go"
        return try {
            val response = httpClient.get(goUrl) {
                header(HttpHeaders.Cookie, cookieHeader)
                header(HttpHeaders.Accept, "text/html,application/xhtml+xml")
                header(HttpHeaders.UserAgent, BROWSER_UA)
                header(HttpHeaders.Referrer, "$BASE_URL/workspace/$workspaceId")
            }
            ensureNotAuthError(response)
            response.bodyAsText().also {
                log("GET go status=${response.status} bodyLen=${it.length}")
            }
        } catch (e: ClientRequestException) {
            when (e.response.status) {
                HttpStatusCode.Unauthorized,
                HttpStatusCode.Forbidden,
                -> throw SessionExpiredException("OpenCode 会话已失效")
                else -> throw e
            }
        }
    }

    fun close() {
        httpClient.close()
    }

    /**
     * SolidStart `query(...)` prefers GET (`fn.GET`) with seroval-JSON args in the query string.
     * Fall back to POST `/_server` if GET fails.
     *
     * Current console runtime uses single-hash ids: `createServerReference("…hex…")`
     * and encodes args via seroval `toJSON` (`{ t, f, m }`), not a bare JSON array.
     */
    private suspend fun invokeLiteSubscription(
        workspaceId: String,
        cookieHeader: String,
        serverFnId: String,
    ): String = invokeServerFn(
        serverFnId = serverFnId,
        cookieHeader = cookieHeader,
        argsJson = encodeServerFnArgs(workspaceId),
        referer = "$BASE_URL/workspace/$workspaceId/go",
        clearLiteCacheOnNotFound = true,
    )

    private suspend fun invokeServerFn(
        serverFnId: String,
        cookieHeader: String,
        argsJson: String?,
        referer: String,
        clearLiteCacheOnNotFound: Boolean = false,
    ): String {
        val instance = "server-fn:${nextInstance()}"
        val getUrl = buildString {
            append("$BASE_URL/_server?id=${urlEncode(serverFnId)}")
            if (!argsJson.isNullOrBlank()) {
                append("&args=${urlEncode(argsJson)}")
            }
        }

        val getBody = runCatching {
            val response = httpClient.get(getUrl) {
                header(HttpHeaders.Cookie, cookieHeader)
                header(HttpHeaders.Accept, "*/*")
                header(HttpHeaders.UserAgent, BROWSER_UA)
                header(HttpHeaders.Referrer, referer)
                header("Origin", BASE_URL)
                header("X-Server-Id", serverFnId)
                header("X-Server-Instance", instance)
            }
            log("GET _server status=${response.status}")
            ensureNotAuthError(response)
            response.bodyAsText()
        }.onFailure { log("GET _server failed: ${it.message}") }.getOrNull()

        if (getBody != null && looksLikeSerovalPayload(getBody) && !looksLikeAuthError(getBody)) {
            return getBody
        }
        if (getBody != null && looksLikeAuthError(getBody)) {
            throw SessionExpiredException("OpenCode 会话已失效")
        }
        if (getBody != null) {
            log("GET _server unexpected bodyPrefix=${getBody.take(160).replace('\n', ' ')}")
        }

        val postInstance = "server-fn:${nextInstance()}"
        return try {
            val response = httpClient.post("$BASE_URL/_server") {
                header(HttpHeaders.Cookie, cookieHeader)
                header(HttpHeaders.Accept, "*/*")
                header(HttpHeaders.UserAgent, BROWSER_UA)
                header(HttpHeaders.Referrer, referer)
                header("Origin", BASE_URL)
                header("X-Server-Id", serverFnId)
                header("X-Server-Instance", postInstance)
                contentType(ContentType.Application.Json)
                setBody(argsJson ?: encodeEmptyServerFnArgs())
            }
            log("POST _server status=${response.status}")
            ensureNotAuthError(response)
            val text = response.bodyAsText()
            if (looksLikeAuthError(text)) {
                throw SessionExpiredException("OpenCode 会话已失效")
            }
            if (!looksLikeSerovalPayload(text)) {
                throw IllegalStateException(
                    "OpenCode 接口返回异常 (prefix=${text.take(160).replace('\n', ' ')})",
                )
            }
            text
        } catch (e: ClientRequestException) {
            when (e.response.status) {
                HttpStatusCode.Unauthorized,
                HttpStatusCode.Forbidden,
                -> throw SessionExpiredException("OpenCode 会话已失效")
                HttpStatusCode.NotFound -> {
                    if (clearLiteCacheOnNotFound) cachedServerFnId = null
                    cachedWorkspacesServerFnId = null
                    throw IllegalStateException("OpenCode 接口已失效，请稍后重试")
                }
                else -> throw IllegalStateException(
                    "OpenCode 请求失败 HTTP ${e.response.status.value}: ${e.message}",
                    e,
                )
            }
        }
    }

    private suspend fun ensureNotAuthError(response: HttpResponse) {
        when (response.status) {
            HttpStatusCode.Unauthorized,
            HttpStatusCode.Forbidden,
            -> throw SessionExpiredException("OpenCode 会话已失效")
            else -> Unit
        }
        if (response.status.value >= 400) {
            val text = runCatching { response.bodyAsText() }.getOrDefault("")
            throw IllegalStateException(
                "OpenCode 额度请求失败 HTTP ${response.status.value}: ${text.take(120)}",
            )
        }
    }

    private suspend fun resolveServerFnId(workspaceId: String, cookieHeader: String): String {
        cachedServerFnId?.let { return it }
        val goUrl = "$BASE_URL/workspace/$workspaceId/go"
        val html = try {
            httpClient.get(goUrl) {
                header(HttpHeaders.Cookie, cookieHeader)
                header(HttpHeaders.Accept, "text/html,application/xhtml+xml")
                header(HttpHeaders.UserAgent, BROWSER_UA)
            }.bodyAsText()
        } catch (e: ClientRequestException) {
            when (e.response.status) {
                HttpStatusCode.Unauthorized,
                HttpStatusCode.Forbidden,
                -> throw SessionExpiredException("OpenCode 会话已失效")
                else -> throw e
            }
        }
        if (looksLikeLoginPage(html)) {
            throw SessionExpiredException("OpenCode 会话已失效")
        }
        findPreferredServerFnId(html)?.let {
            log("serverFnId from html=$it")
            cachedServerFnId = it
            return it
        }

        val queue = ArrayDeque<String>()
        val seen = mutableSetOf<String>()
        extractScriptUrls(html).map { resolveUrl(it) }.forEach { queue.addLast(it) }
        resolveEntryClientUrl()?.let { queue.addLast(it) }

        var fallback: String? = null
        var scanned = 0
        while (queue.isNotEmpty() && scanned < 60) {
            val absolute = queue.removeFirst()
            if (!seen.add(absolute) || !absolute.endsWith(".js")) continue
            scanned += 1
            val js = runCatching {
                httpClient.get(absolute) {
                    header(HttpHeaders.Cookie, cookieHeader)
                    header(HttpHeaders.Accept, "*/*")
                }.bodyAsText()
            }.getOrNull() ?: continue

            for (imp in JS_IMPORT_PATH.findAll(js).map { it.groupValues[1] }) {
                val next = resolveUrl("/_build/assets/$imp")
                if (next !in seen) queue.addLast(next)
            }

            QUERY_LITE_SUBSCRIPTION_REF.find(js)?.groupValues?.getOrNull(1)?.let {
                log("serverFnId queryLiteSubscription=$it url=$absolute scanned=$scanned")
                cachedServerFnId = it
                return it
            }
            findPreferredServerFnId(js)?.let {
                log("serverFnId from js=$it url=$absolute")
                cachedServerFnId = it
                return it
            }
            val ids = findAllServerFnIdsInText(js)
            if (ids.isEmpty()) continue
            if ("lite.subscription" in js || "queryLiteSubscription" in js || "rollingUsage" in js) {
                val preferred = ids.first()
                log("serverFnId relevant=$preferred url=$absolute")
                cachedServerFnId = preferred
                return preferred
            }
            if (fallback == null) fallback = ids.first()
        }
        log("discover scanned=$scanned fallback=${fallback != null}")
        return fallback?.also {
            log("serverFnId fallback=$it")
            cachedServerFnId = it
        } ?: throw IllegalStateException("无法定位 OpenCode Go 额度接口")
    }

    private suspend fun resolveWorkspacesServerFnId(cookieHeader: String): String {
        cachedWorkspacesServerFnId?.let { return it }
        val queue = ArrayDeque<String>()
        val seen = mutableSetOf<String>()
        fun enqueue(url: String, prefer: Boolean = false) {
            if (url in seen) return
            if (prefer) queue.addFirst(url) else queue.addLast(url)
        }
        resolveEntryClientUrl()?.let { enqueue(it) }
        // WorkspacePicker is a lazy route chunk (e.g. workspace-*.js), referenced as a bare
        // asset string from the router — not only via static `from "./…"` imports.
        var scanned = 0
        while (queue.isNotEmpty() && scanned < 120) {
            val absolute = queue.removeFirst()
            if (!seen.add(absolute) || !absolute.endsWith(".js")) continue
            scanned += 1
            val js = runCatching {
                httpClient.get(absolute) {
                    header(HttpHeaders.Cookie, cookieHeader)
                    header(HttpHeaders.Accept, "*/*")
                    header(HttpHeaders.UserAgent, BROWSER_UA)
                }.bodyAsText()
            }.getOrNull() ?: continue
            for (imp in JS_IMPORT_PATH.findAll(js).map { it.groupValues[1] }) {
                enqueue(resolveUrl("/_build/assets/$imp"))
            }
            for (name in JS_ASSET_CHUNK.findAll(js).map { it.groupValues[1] }) {
                val next = resolveUrl("/_build/assets/$name")
                enqueue(next, prefer = "workspace" in name.lowercase())
            }
            findWorkspacesServerFnId(js)?.let {
                log("workspaces serverFnId=$it url=$absolute scanned=$scanned")
                cachedWorkspacesServerFnId = it
                return it
            }
        }
        log("workspaces discover miss scanned=$scanned")
        throw IllegalStateException("无法定位 OpenCode Workspace 列表接口 (scanned=$scanned)")
    }

    private suspend fun resolveEntryClientUrl(): String? {
        val home = runCatching {
            httpClient.get("$BASE_URL/") {
                header(HttpHeaders.Accept, "text/html")
                header(HttpHeaders.UserAgent, BROWSER_UA)
            }.bodyAsText()
        }.getOrNull().orEmpty()
        return ENTRY_CLIENT_HREF.find(home)?.value?.let { resolveUrl(it) }
    }

    companion object {
        const val BASE_URL = "https://opencode.ai"
        const val LOGIN_URL = "$BASE_URL/auth"
        private const val BROWSER_UA =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/125.0.0.0 Mobile Safari/537.36"

        private var instanceCounter = 0

        private fun nextInstance(): Int {
            instanceCounter += 1
            return instanceCounter
        }

        private fun log(message: String) {
            println("OpenCodeGo: $message")
        }

        private val WORKSPACE_PATH =
            Regex("""/workspace/(wrk_[A-Za-z0-9]+)(?:/|$)""")

        /** Legacy two-arg SolidStart references: createServerReference(fn, "id", "name"). */
        private val CREATE_SERVER_REF_LEGACY =
            Regex("""createServerReference\s*\(\s*(?:[^,]+,\s*)?"([^"]+)"\s*,\s*"([^"]+)"\s*\)""")

        /** Current console: createServerReference("hexhash"). */
        private val CREATE_SERVER_REF_HASH =
            Regex("""createServerReference\s*\(\s*["']([a-fA-F0-9]{32,})["']\s*\)""")

        private val QUERY_LITE_SUBSCRIPTION_REF =
            Regex(
                """queryLiteSubscription_query\s*=\s*createServerReference\s*\(\s*["']([a-fA-F0-9]{32,})["']\s*\)""",
            )

        private val GET_WORKSPACES_REF =
            Regex(
                """getWorkspaces_query\s*=\s*createServerReference\s*\(\s*["']([a-fA-F0-9]{32,})["']\s*\)""",
            )

        private val WORKSPACES_QUERY_REF =
            Regex(
                """createServerReference\s*\(\s*["']([a-fA-F0-9]{32,})["']\s*\)\s*;\s*""" +
                    """(?:const|let|var)\s+\w+\s*=\s*query\s*\([^,]+,\s*["']workspaces["']\s*\)""",
            )

        private val SERVER_URL_ID =
            Regex("""/_server/?\?id=([^&"'\\s]+)(?:&name=([^&"'\\s]+))?""")

        private val SCRIPT_SRC =
            Regex("""<(?:script|link)[^>]+(?:src|href)=["']([^"']+)["'][^>]*>""", RegexOption.IGNORE_CASE)

        private val ENTRY_CLIENT_HREF =
            Regex("""/_build/assets/entry-client-[A-Za-z0-9_-]+\.js""")

        private val JS_IMPORT_PATH =
            Regex("""from\s*["']\./([^"']+\.js)["']""")

        /**
         * Vite/Solid lazy chunks, e.g. `"_build/assets/workspace-C-iqqx2n.js"`
         * (often without a leading slash) or bare `"name-hash.js"`.
         */
        private val JS_ASSET_CHUNK =
            Regex("""["'](?:/?_build/assets/)?([A-Za-z0-9_-]+-[A-Za-z0-9_-]{4,}\.js)["']""")

        fun extractWorkspaceId(url: String): String? =
            WORKSPACE_PATH.find(url)?.groupValues?.getOrNull(1)

        fun isLoginSuccessUrl(url: String): Boolean =
            extractWorkspaceId(url) != null

        fun extractAuthCookie(cookieHeader: String?): String? {
            if (cookieHeader.isNullOrBlank()) return null
            // Prefer the last auth= entry when CookieManager returns duplicates.
            return cookieHeader
                .split(';')
                .map { it.trim() }
                .filter { it.startsWith("auth=", ignoreCase = true) }
                .map { it.substringAfter('=') }
                .lastOrNull()
                ?.takeIf { it.isNotBlank() }
        }

        /** Prefer the full CookieManager header when available. */
        fun cookieHeaderFor(authCookieOrHeader: String): String {
            val trimmed = authCookieOrHeader.trim()
            if (trimmed.contains('=') && trimmed.contains("auth=", ignoreCase = true)) {
                return trimmed
            }
            if (trimmed.startsWith("auth=", ignoreCase = true)) {
                return trimmed
            }
            return "auth=$trimmed"
        }

        /**
         * SolidStart `Mo` / seroval `toJSONAsync` wire shape for a single string argument.
         * Verified against live `/_server` (bare JSON arrays are rejected).
         */
        internal fun encodeServerFnArgs(workspaceId: String): String {
            val escaped = workspaceId
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
            return """{"t":{"t":9,"i":0,"a":[{"t":1,"s":"$escaped"}],"o":0},"f":127,"m":[]}"""
        }

        /** Seroval `toJSON` shape for a zero-argument server function call. */
        internal fun encodeEmptyServerFnArgs(): String =
            """{"t":{"t":9,"i":0,"a":[],"o":0},"f":127,"m":[]}"""

        /**
         * Prefer the account-scoped `query(..., "workspaces")` hash.
         * Avoid `black.subscribe.workspaces` which also uses a `getWorkspaces_query` symbol.
         */
        internal fun findWorkspacesServerFnId(text: String): String? {
            WORKSPACES_QUERY_REF.find(text)?.groupValues?.getOrNull(1)?.let { return it }
            for (match in GET_WORKSPACES_REF.findAll(text)) {
                val id = match.groupValues.getOrNull(1) ?: continue
                val after = text.substring(match.range.last + 1).take(160)
                if (Regex("""query\s*\([^,]+,\s*["']workspaces["']\s*\)""").containsMatchIn(after) &&
                    "black.subscribe.workspaces" !in after
                ) {
                    return id
                }
            }
            return null
        }

        fun looksLikeLoginPage(text: String): Boolean {
            val lower = text.lowercase()
            return "rollingusage" !in lower &&
                "${'$'}r[0]" !in lower &&
                (
                    "/auth/authorize" in lower ||
                        """name="opencode:auth" content="false"""" in lower ||
                        "sign in" in lower
                    )
        }

        internal fun looksLikeAuthError(text: String): Boolean {
            val lower = text.lowercase()
            return "not associated with a workspace" in lower ||
                """["location","/auth/authorize"]""" in lower ||
                "/auth/authorize" in lower && "new response" in lower
        }

        internal fun looksLikeSerovalOrUsage(text: String): Boolean {
            val lower = text.lowercase()
            return lower.startsWith(";0x") ||
                "${'$'}r[0]" in lower ||
                "rollingusage" in lower
        }

        internal fun looksLikeSerovalPayload(text: String): Boolean {
            val lower = text.lowercase()
            return lower.startsWith(";0x") ||
                "${'$'}r[0]" in lower ||
                "rollingusage" in lower ||
                "wrk_" in lower
        }

        internal fun findPreferredServerFnId(text: String): String? =
            QUERY_LITE_SUBSCRIPTION_REF.find(text)?.groupValues?.getOrNull(1)
                ?: findAllServerFnIdsInText(text).firstOrNull { id ->
                    // Prefer legacy ids that still embed a path hint.
                    id.contains("lite", ignoreCase = true) ||
                        id.contains("subscription", ignoreCase = true)
                }

        internal fun findServerFnIdInText(text: String): String? =
            findPreferredServerFnId(text) ?: findAllServerFnIdsInText(text).firstOrNull()

        internal fun findAllServerFnIdsInText(text: String): List<String> {
            val found = linkedSetOf<String>()
            QUERY_LITE_SUBSCRIPTION_REF.findAll(text).forEach { match ->
                found += match.groupValues[1]
            }
            CREATE_SERVER_REF_HASH.findAll(text).forEach { match ->
                found += match.groupValues[1]
            }
            CREATE_SERVER_REF_LEGACY.findAll(text).forEach { match ->
                found += "${match.groupValues[1]}#${match.groupValues[2]}"
            }
            SERVER_URL_ID.findAll(text).forEach { match ->
                val id = decodeComponent(match.groupValues[1])
                val name = match.groupValues.getOrNull(2)?.takeIf { it.isNotBlank() }
                found += if (name != null) "$id#${decodeComponent(name)}" else id
            }
            return found.toList()
        }

        private fun extractScriptUrls(html: String): List<String> =
            SCRIPT_SRC.findAll(html)
                .map { it.groupValues[1] }
                .filter {
                    it.contains("_build") ||
                        it.endsWith(".js") ||
                        it.contains("assets/") ||
                        it.contains("chunk")
                }
                .distinct()
                .toList()

        private fun resolveUrl(url: String): String = when {
            url.startsWith("http://") || url.startsWith("https://") -> url
            url.startsWith("//") -> "https:$url"
            url.startsWith("/") -> "$BASE_URL$url"
            else -> "$BASE_URL/$url"
        }

        private fun decodeComponent(raw: String): String =
            raw.replace("%2F", "/", ignoreCase = true)
                .replace("%2f", "/")
                .replace("%23", "#", ignoreCase = true)

        private fun urlEncode(raw: String): String = buildString(raw.length * 2) {
            for (byte in raw.encodeToByteArray()) {
                val value = byte.toInt() and 0xFF
                val ch = value.toChar()
                when (ch) {
                    in 'A'..'Z', in 'a'..'z', in '0'..'9', '-', '_', '.', '~' -> append(ch)
                    else -> append('%').append(value.toString(16).uppercase().padStart(2, '0'))
                }
            }
        }
    }
}
