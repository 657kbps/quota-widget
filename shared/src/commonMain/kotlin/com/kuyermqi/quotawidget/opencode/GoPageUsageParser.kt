package com.kuyermqi.quotawidget.opencode

/**
 * Extracts Lite usage windows from the authenticated `/workspace/{id}/go` HTML/SSR payload.
 *
 * OpenCode's `queryLiteSubscription` server fn calls `withActor(fn)` without the workspace id, so a
 * standalone `/_server` RPC runs as `account`/`public` and throws
 * `actor of type "…" is not associated with a workspace`. The full document request works because
 * layout `session.get` seeds the actor for that request's SSR.
 */
object GoPageUsageParser {
    fun parse(html: String): LiteSubscriptionDto? {
        SerovalServerFnDecoder.decodeLiteSubscription(html)?.let { return it }
        SerovalServerFnDecoder.decodeLiteSubscriptionFromAnyRoot(html)?.let { return it }

        val rolling = usageNearKey(html, "rollingUsage")
        val weekly = usageNearKey(html, "weeklyUsage")
        val monthly = usageNearKey(html, "monthlyUsage")
        return LiteSubscriptionDto(
            rollingUsage = rolling,
            weeklyUsage = weekly,
            monthlyUsage = monthly,
        ).takeIf {
            it.rollingUsage != null || it.weeklyUsage != null || it.monthlyUsage != null
        }
    }

    private fun usageNearKey(html: String, key: String): LiteUsageDto? {
        var searchFrom = 0
        while (true) {
            val keyIdx = html.indexOf(key, searchFrom)
            if (keyIdx < 0) return null
            val slice = html.substring(keyIdx, (keyIdx + 480).coerceAtMost(html.length))
            val percent = USAGE_PERCENT.find(slice)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
            if (percent != null) {
                val reset = RESET_IN_SEC.find(slice)?.groupValues?.getOrNull(1)?.toLongOrNull()
                val status = STATUS.find(slice)?.groupValues?.getOrNull(1)
                return LiteUsageDto(status = status, usagePercent = percent, resetInSec = reset)
            }
            searchFrom = keyIdx + key.length
        }
    }

    private val USAGE_PERCENT =
        Regex("""usagePercent["'\s:=]+([0-9]+(?:\.[0-9]+)?)""")
    private val RESET_IN_SEC =
        Regex("""resetInSec["'\s:=]+([0-9]+)""")
    private val STATUS =
        Regex("""status["'\s:=]+["']([^"']+)["']""")
}
