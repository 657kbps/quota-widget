package com.kuyermqi.quotawidget.opencode

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class GoPageUsageParserTest {
    @Test
    fun parse_fromEmbeddedUsageObjects() {
        val html = """
            <html><body>
            <script>
            state = {
              rollingUsage: { status: "ok", resetInSec: 1800, usagePercent: 12.5 },
              weeklyUsage: { status: "ok", resetInSec: 86400, usagePercent: 40 },
              monthlyUsage: { status: "ok", resetInSec: 604800, usagePercent: 55 }
            }
            </script>
            </body></html>
        """.trimIndent()

        val dto = GoPageUsageParser.parse(html)
        assertNotNull(dto)
        assertEquals(12.5, dto.rollingUsage?.usagePercent)
        assertEquals(1800L, dto.rollingUsage?.resetInSec)
        assertEquals(40.0, dto.weeklyUsage?.usagePercent)
        assertEquals(55.0, dto.monthlyUsage?.usagePercent)
    }

    @Test
    fun parse_fromSerovalRoot() {
        val payload = """
            ((self.${'$'}R = self.${'$'}R || {})["server-fn:1"] = [],
            (${'$'}R => ${'$'}R[0] = {
              rollingUsage: ${'$'}R[1] = { status: "ok", resetInSec: 10, usagePercent: 3 },
              weeklyUsage: ${'$'}R[2] = { status: "ok", resetInSec: 20, usagePercent: 4 },
              monthlyUsage: ${'$'}R[3] = { status: "ok", resetInSec: 30, usagePercent: 5 }
            })(${'$'}R["server-fn:1"]))
        """.trimIndent().replace("\n", "")
        val hexLen = payload.encodeToByteArray().size.toString(16).padStart(8, '0')
        val framed = ";0x$hexLen;$payload"

        val dto = GoPageUsageParser.parse(framed)
        assertNotNull(dto)
        assertEquals(3.0, dto.rollingUsage?.usagePercent)
        assertEquals(4.0, dto.weeklyUsage?.usagePercent)
        assertEquals(5.0, dto.monthlyUsage?.usagePercent)
    }
}
