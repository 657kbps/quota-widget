package com.kuyermqi.quotawidget.codex

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Diagnostic logs for Codex auth/usage. Uses [println] so messages show under
 * Android `System.out` / tag QuotaRefresh. Never log full tokens.
 */
internal object CodexDebugLog {
    private val json = Json { ignoreUnknownKeys = true }

    fun i(message: String) {
        println("QuotaRefresh Codex $message")
    }

    fun w(message: String) {
        println("QuotaRefresh Codex WARN $message")
    }

    fun tokenFp(value: String): String {
        if (value.isBlank()) return "empty"
        return "len=${value.length} tail=${value.takeLast(4)}"
    }

    fun summarizeOAuthErrorBody(body: String): String {
        if (body.isBlank()) return "empty"
        val code = extractErrorField(body, "code")
        val type = extractErrorField(body, "type")
        val message = extractErrorField(body, "message")
            ?: extractErrorField(body, "error_description")
        val parts = buildList {
            if (!code.isNullOrBlank()) add("code=$code")
            if (!type.isNullOrBlank()) add("type=$type")
            if (!message.isNullOrBlank()) add("message=${message.take(160)}")
        }
        if (parts.isNotEmpty()) return parts.joinToString(" ")
        return "body=${sanitizeForLog(body).take(240)}"
    }

    private fun extractErrorField(body: String, field: String): String? {
        return runCatching {
            val root = json.parseToJsonElement(body).jsonObject
            val direct = root[field]?.jsonPrimitive?.content
            if (!direct.isNullOrBlank()) return@runCatching direct
            val err = root["error"]?.jsonObject ?: return@runCatching null
            err[field]?.jsonPrimitive?.content
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun sanitizeForLog(raw: String): String {
        var s = raw
        // Redact JWT-shaped and long opaque token-like strings.
        s = JWT_REGEX.replace(s, "<jwt>")
        s = LONG_TOKEN_REGEX.replace(s, "<token>")
        return s.replace('\n', ' ')
    }

    private val JWT_REGEX = Regex("""eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+""")
    private val LONG_TOKEN_REGEX = Regex("""[A-Za-z0-9_-]{40,}""")
}
