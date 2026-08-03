package com.kuyermqi.quotawidget.opencode

/**
 * Decodes SolidStart / seroval server-function response chunks into a value tree,
 * then maps the root object to [LiteSubscriptionDto].
 *
 * Wire format example:
 * ```
 * ;0x00000146;
 * ((self.$R = self.$R || {})["server-fn:3"] = [],
 * ($R => $R[0] = { rollingUsage: $R[2] = { ... }, ... })($R["server-fn:3"]))
 * ```
 */
object SerovalServerFnDecoder {
    fun decodeLiteSubscription(body: String): LiteSubscriptionDto? {
        val payload = firstChunkPayload(body) ?: return null
        val root = parseRootObject(payload) ?: return null
        if (root.isEmpty() && !payloadContainsNullRoot(payload)) {
            // Explicit null subscription (no Go plan).
            return null
        }
        if (root.isEmpty()) return null
        return dtoFromRoot(root)
    }

    /** Scans any `$R[n] = { … }` object in a page/payload for Lite usage fields. */
    fun decodeLiteSubscriptionFromAnyRoot(body: String): LiteSubscriptionDto? {
        val payload = firstChunkPayload(body) ?: body
        val marker = "${'$'}R["
        var searchFrom = 0
        while (true) {
            val idx = payload.indexOf(marker, searchFrom)
            if (idx < 0) return null
            var i = idx + marker.length
            while (i < payload.length && payload[i].isDigit()) i++
            if (i >= payload.length || payload[i] != ']') {
                searchFrom = idx + 1
                continue
            }
            i++
            while (i < payload.length && payload[i].isWhitespace()) i++
            if (i >= payload.length || payload[i] != '=') {
                searchFrom = idx + 1
                continue
            }
            i++
            while (i < payload.length && payload[i].isWhitespace()) i++
            if (payload.getOrNull(i) != '{') {
                searchFrom = idx + 1
                continue
            }
            val parser = JsLiteralParser(payload, i)
            val value = runCatching { parser.parseValue() }.getOrNull()
            @Suppress("UNCHECKED_CAST")
            val root = value as? Map<String, Any?>
            val dto = root?.let { dtoFromRoot(it) }
            if (dto != null) return dto
            searchFrom = idx + 1
        }
    }

    private fun dtoFromRoot(root: Map<String, Any?>): LiteSubscriptionDto? =
        LiteSubscriptionDto(
            rollingUsage = usageFrom(root["rollingUsage"]),
            weeklyUsage = usageFrom(root["weeklyUsage"]),
            monthlyUsage = usageFrom(root["monthlyUsage"]),
        ).takeIf {
            it.rollingUsage != null || it.weeklyUsage != null || it.monthlyUsage != null
        }

    /** Decodes `getWorkspaces` root array: `[{ id, name, slug? }, …]`. */
    fun decodeWorkspaces(body: String): List<OpenCodeWorkspace>? {
        val payload = firstChunkPayload(body) ?: return null
        val root = parseRootArray(payload) ?: return null
        val workspaces = root.mapNotNull { item ->
            @Suppress("UNCHECKED_CAST")
            val map = item as? Map<String, Any?> ?: return@mapNotNull null
            val id = map["id"] as? String ?: return@mapNotNull null
            if (id.isBlank()) return@mapNotNull null
            OpenCodeWorkspace(
                id = id,
                name = (map["name"] as? String).orEmpty(),
                slug = map["slug"] as? String,
            )
        }
        return workspaces
    }

    internal fun firstChunkPayload(body: String): String? {
        val bytes = body.encodeToByteArray()
        if (bytes.size < 12 || bytes[0] != ';'.code.toByte()) {
            // Some environments may omit the length header; treat whole body as payload.
            val trimmed = body.trim()
            return trimmed.takeIf {
                it.isNotEmpty() && (it.contains("\$R") || it.contains("rollingUsage") || it.contains("wrk_"))
            }
        }
        val head = bytes.copyOfRange(1, 11).decodeToString()
        if (!head.startsWith("0x")) return null
        val length = head.removePrefix("0x").toIntOrNull(16) ?: return null
        if (bytes.size < 12 + length) return null
        return bytes.copyOfRange(12, 12 + length).decodeToString()
    }

    private fun payloadContainsNullRoot(payload: String): Boolean =
        Regex("""[${'$'}]R\[0\]\s*=\s*null""").containsMatchIn(payload)

    private fun parseRootObject(payload: String): Map<String, Any?>? {
        val value = parseRootValue(payload) ?: return null
        @Suppress("UNCHECKED_CAST")
        return when (value) {
            is Map<*, *> -> value as? Map<String, Any?>
            else -> null
        }
    }

    private fun parseRootArray(payload: String): List<Any?>? {
        val value = parseRootValue(payload) ?: return null
        @Suppress("UNCHECKED_CAST")
        return value as? List<Any?>
    }

    private fun parseRootValue(payload: String): Any? {
        val marker = "${'$'}R[0]"
        var searchFrom = 0
        while (true) {
            val idx = payload.indexOf(marker, searchFrom)
            if (idx < 0) return null
            var i = idx + marker.length
            while (i < payload.length && payload[i].isWhitespace()) i++
            if (i < payload.length && payload[i] == '=') {
                i++
                while (i < payload.length && payload[i].isWhitespace()) i++
                when {
                    payload.startsWith("null", i) -> return emptyMap<String, Any?>()
                    payload.getOrNull(i) == '{' ||
                        payload.getOrNull(i) == '[' ||
                        payload.startsWith("\$R[", i) -> {
                        val parser = JsLiteralParser(payload, i)
                        return runCatching { parser.parseValue() }.getOrNull()
                    }
                }
            }
            searchFrom = idx + 1
        }
    }

    private fun usageFrom(value: Any?): LiteUsageDto? {
        @Suppress("UNCHECKED_CAST")
        val map = value as? Map<String, Any?> ?: return null
        val percent = when (val p = map["usagePercent"]) {
            is Number -> p.toDouble()
            else -> return null
        }
        val reset = when (val r = map["resetInSec"]) {
            is Number -> r.toLong()
            else -> null
        }
        val status = map["status"] as? String
        return LiteUsageDto(status = status, usagePercent = percent, resetInSec = reset)
    }

    /**
     * Minimal JS-literal parser for seroval server-fn payloads.
     * Supports objects, arrays, strings, numbers, `!0`/`!1`, null, and `$R[n] = value`.
     */
    private class JsLiteralParser(
        private val source: String,
        start: Int,
    ) {
        private var index = start

        fun parseValue(): Any? {
            skipWs()
            if (index >= source.length) error("Unexpected end")
            // $R[n] = value  (assignment expression yields the value)
            if (source.startsWith("\$R[", index)) {
                index += 3
                while (index < source.length && source[index] != ']') index++
                if (index < source.length && source[index] == ']') index++
                skipWs()
                if (index < source.length && source[index] == '=') {
                    index++
                    return parseValue()
                }
                // Bare $R[n] reference — treat as null (should already be assigned inline).
                return null
            }
            return when (val c = source[index]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' , '\'' -> parseString()
                '!' -> parseBangBool()
                in '0'..'9', '-', '+' -> parseNumber()
                else -> when {
                    source.startsWith("null", index) -> {
                        index += 4
                        null
                    }
                    source.startsWith("true", index) -> {
                        index += 4
                        true
                    }
                    source.startsWith("false", index) -> {
                        index += 5
                        false
                    }
                    source.startsWith("void", index) -> {
                        // void 0
                        index += 4
                        skipWs()
                        if (index < source.length && source[index] == '0') index++
                        null
                    }
                    else -> error("Unexpected '${c}' at $index")
                }
            }
        }

        private fun parseObject(): Map<String, Any?> {
            expect('{')
            val result = linkedMapOf<String, Any?>()
            skipWs()
            if (peek('}')) {
                index++
                return result
            }
            while (true) {
                skipWs()
                val key = parseKey()
                skipWs()
                expect(':')
                val value = parseValue()
                result[key] = value
                skipWs()
                when {
                    peek(',') -> {
                        index++
                        skipWs()
                        if (peek('}')) {
                            index++
                            break
                        }
                    }
                    peek('}') -> {
                        index++
                        break
                    }
                    else -> error("Expected ',' or '}' at $index")
                }
            }
            return result
        }

        private fun parseArray(): List<Any?> {
            expect('[')
            val result = mutableListOf<Any?>()
            skipWs()
            if (peek(']')) {
                index++
                return result
            }
            while (true) {
                result.add(parseValue())
                skipWs()
                when {
                    peek(',') -> {
                        index++
                        skipWs()
                        if (peek(']')) {
                            index++
                            break
                        }
                    }
                    peek(']') -> {
                        index++
                        break
                    }
                    else -> error("Expected ',' or ']' at $index")
                }
            }
            return result
        }

        private fun parseKey(): String {
            skipWs()
            return when {
                peek('"') || peek('\'') -> parseString()
                else -> {
                    val start = index
                    while (index < source.length) {
                        val c = source[index]
                        if (c.isLetterOrDigit() || c == '_' || c == '$') index++ else break
                    }
                    source.substring(start, index).also {
                        if (it.isEmpty()) error("Empty key at $start")
                    }
                }
            }
        }

        private fun parseString(): String {
            val quote = source[index]
            index++
            val sb = StringBuilder()
            while (index < source.length) {
                when (val c = source[index]) {
                    quote -> {
                        index++
                        return sb.toString()
                    }
                    '\\' -> {
                        index++
                        if (index >= source.length) break
                        when (val e = source[index]) {
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                val hex = source.substring(index + 1, index + 5)
                                sb.append(hex.toInt(16).toChar())
                                index += 4
                            }
                            else -> sb.append(e)
                        }
                        index++
                    }
                    else -> {
                        sb.append(c)
                        index++
                    }
                }
            }
            error("Unterminated string")
        }

        private fun parseBangBool(): Boolean {
            expect('!')
            return when {
                peek('0') -> {
                    index++
                    true
                }
                peek('1') -> {
                    index++
                    false
                }
                else -> error("Expected !0 or !1 at $index")
            }
        }

        private fun parseNumber(): Number {
            val start = index
            if (peek('+') || peek('-')) index++
            while (index < source.length && (source[index].isDigit() || source[index] == '.')) {
                index++
            }
            if (index < source.length && (source[index] == 'e' || source[index] == 'E')) {
                index++
                if (peek('+') || peek('-')) index++
                while (index < source.length && source[index].isDigit()) index++
            }
            val raw = source.substring(start, index)
            return raw.toLongOrNull() ?: raw.toDouble()
        }

        private fun skipWs() {
            while (index < source.length) {
                val c = source[index]
                if (c.isWhitespace() || c == '\n' || c == '\r') index++ else break
            }
        }

        private fun peek(c: Char): Boolean = index < source.length && source[index] == c

        private fun expect(c: Char) {
            skipWs()
            if (!peek(c)) error("Expected '$c' at $index")
            index++
        }
    }
}
