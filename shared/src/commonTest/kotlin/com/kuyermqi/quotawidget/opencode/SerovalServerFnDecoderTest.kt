package com.kuyermqi.quotawidget.opencode

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SerovalServerFnDecoderTest {
    @Test
    fun decodeLiteSubscription_fromSerovalChunk() {
        val payload = """
            ((self.${'$'}R = self.${'$'}R || {})["server-fn:3"] = [],
            (${'$'}R => ${'$'}R[0] = {
                mine: !0,
                useBalance: !1,
                region: ${'$'}R[1] = ["us", "eu", "sg", "cn"],
                rollingUsage: ${'$'}R[2] = {
                    status: "ok",
                    resetInSec: 18000,
                    usagePercent: 19.5
                },
                weeklyUsage: ${'$'}R[3] = {
                    status: "ok",
                    resetInSec: 6567,
                    usagePercent: 30
                },
                monthlyUsage: ${'$'}R[4] = {
                    status: "ok",
                    resetInSec: 2676695,
                    usagePercent: 12
                }
            })(${'$'}R["server-fn:3"]))
        """.trimIndent()
        val hexLen = payload.encodeToByteArray().size.toString(16).padStart(8, '0')
        val body = ";0x$hexLen;$payload"

        val dto = SerovalServerFnDecoder.decodeLiteSubscription(body)
        assertNotNull(dto)
        assertEquals(19.5, dto.rollingUsage?.usagePercent)
        assertEquals(18000L, dto.rollingUsage?.resetInSec)
        assertEquals("ok", dto.rollingUsage?.status)
        assertEquals(30.0, dto.weeklyUsage?.usagePercent)
        assertEquals(12.0, dto.monthlyUsage?.usagePercent)

        val windows = dto.toWindows()
        assertEquals(3, windows.size)
    }

    @Test
    fun decodeLiteSubscription_nullRoot() {
        val payload = """(${'$'}R => ${'$'}R[0] = null)(${'$'}R["server-fn:1"])"""
        val hexLen = payload.encodeToByteArray().size.toString(16).padStart(8, '0')
        assertNull(SerovalServerFnDecoder.decodeLiteSubscription(";0x$hexLen;$payload"))
    }

    @Test
    fun decodeWorkspaces_fromSerovalArray() {
        val payload = """
            ((self.${'$'}R = self.${'$'}R || {})["server-fn:1"] = [],
            (${'$'}R => ${'$'}R[0] = ${'$'}R[1] = [
              ${'$'}R[2] = { id: "wrk_01AAA", name: "Personal", slug: "personal" },
              ${'$'}R[3] = { id: "wrk_01BBB", name: "Team", slug: "team" }
            ])(${'$'}R["server-fn:1"]))
        """.trimIndent().replace("\n", "")
        val hexLen = payload.encodeToByteArray().size.toString(16).padStart(8, '0')
        val list = SerovalServerFnDecoder.decodeWorkspaces(";0x$hexLen;$payload")
        assertNotNull(list)
        assertEquals(2, list.size)
        assertEquals("wrk_01AAA", list[0].id)
        assertEquals("Personal", list[0].name)
        assertEquals("personal", list[0].slug)
        assertEquals("Team", list[1].name)
    }
}
