package com.kuyermqi.quotawidget.opencode

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OpenCodeGoClientTest {
    @Test
    fun extractWorkspaceId_fromWorkspaceUrl() {
        assertEquals(
            "wrk_01ABC",
            OpenCodeGoClient.extractWorkspaceId("https://opencode.ai/workspace/wrk_01ABC"),
        )
        assertEquals(
            "wrk_01ABC",
            OpenCodeGoClient.extractWorkspaceId("https://opencode.ai/workspace/wrk_01ABC/"),
        )
        assertEquals(
            "wrk_01ABC",
            OpenCodeGoClient.extractWorkspaceId("https://opencode.ai/workspace/wrk_01ABC/go"),
        )
        assertEquals(
            "wrk_01ABC",
            OpenCodeGoClient.extractWorkspaceId("https://opencode.ai/workspace/wrk_01ABC/go?x=1"),
        )
        assertTrue(OpenCodeGoClient.isLoginSuccessUrl("https://opencode.ai/workspace/wrk_xyz"))
        assertTrue(OpenCodeGoClient.isLoginSuccessUrl("https://opencode.ai/workspace/wrk_xyz/go"))
        assertFalse(OpenCodeGoClient.isLoginSuccessUrl("https://opencode.ai/auth"))
        assertNull(OpenCodeGoClient.extractWorkspaceId("https://opencode.ai/auth"))
    }

    @Test
    fun extractAuthCookie_fromHeader() {
        assertEquals(
            "secret-value",
            OpenCodeGoClient.extractAuthCookie("foo=1; auth=secret-value; bar=2"),
        )
        assertNull(OpenCodeGoClient.extractAuthCookie("foo=1; bar=2"))
        assertEquals(
            "auth=secret-value; foo=1",
            OpenCodeGoClient.cookieHeaderFor("auth=secret-value; foo=1"),
        )
        assertEquals("auth=token", OpenCodeGoClient.cookieHeaderFor("token"))
    }

    @Test
    fun findServerFnId_fromCreateServerReference() {
        val legacy = """export const q = createServerReference(fn, "abc123/lite", "default");"""
        assertEquals("abc123/lite#default", OpenCodeGoClient.findServerFnIdInText(legacy))

        val hash = """
            const queryLiteSubscription_query = createServerReference("c7389bd0e731f80f49593e5ee53835475f4e28594dd6bd83eb229bab753498cd");
            const other = createServerReference("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        """.trimIndent()
        assertEquals(
            "c7389bd0e731f80f49593e5ee53835475f4e28594dd6bd83eb229bab753498cd",
            OpenCodeGoClient.findServerFnIdInText(hash),
        )
    }

    @Test
    fun encodeServerFnArgs_serovalJsonShape() {
        val encoded = OpenCodeGoClient.encodeServerFnArgs("wrk_01ABC")
        assertTrue(encoded.contains("\"s\":\"wrk_01ABC\""))
        assertTrue(encoded.startsWith("""{"t":{"t":9"""))
        assertTrue(OpenCodeGoClient.encodeEmptyServerFnArgs().contains("\"a\":[]"))
    }

    @Test
    fun findWorkspacesServerFnId_fromCreateServerReference() {
        val js = """
            const getWorkspaces_query = createServerReference("def39973159c7f0483d8793a822b8dbb10d067e12c65455fcb4608459ba0234f");
            const getWorkspaces = query(getWorkspaces_query, "workspaces");
        """.trimIndent()
        assertEquals(
            "def39973159c7f0483d8793a822b8dbb10d067e12c65455fcb4608459ba0234f",
            OpenCodeGoClient.findWorkspacesServerFnId(js),
        )
    }

    @Test
    fun findWorkspacesServerFnId_fromMinifiedWorkspaceChunk() {
        val js = """
            const getWorkspaces_query = createServerReference("def39973159c7f0483d8793a822b8dbb10d067e12c65455fcb4608459ba0234f");
            const getWorkspaces = query(getWorkspaces_query, "workspaces");
        """.replace("\n", "")
        assertEquals(
            "def39973159c7f0483d8793a822b8dbb10d067e12c65455fcb4608459ba0234f",
            OpenCodeGoClient.findWorkspacesServerFnId(js),
        )
    }

    @Test
    fun findWorkspacesServerFnId_ignoresBlackSubscribeWorkspaces() {
        val js = """
            const getWorkspaces_query = createServerReference("e9cc590d6b3cc20debdfdcef27f0801696ac84f47188c42cb4348d0401f3eb44");
            const getWorkspaces = query(getWorkspaces_query, "black.subscribe.workspaces");
            const getWorkspaces_query2 = createServerReference("def39973159c7f0483d8793a822b8dbb10d067e12c65455fcb4608459ba0234f");
            const getWorkspaces2 = query(getWorkspaces_query2, "workspaces");
        """.trimIndent()
        assertEquals(
            "def39973159c7f0483d8793a822b8dbb10d067e12c65455fcb4608459ba0234f",
            OpenCodeGoClient.findWorkspacesServerFnId(js),
        )
    }

    @Test
    fun looksLikeLoginPage_whenNoUsageData() {
        assertTrue(
            OpenCodeGoClient.looksLikeLoginPage(
                """<html><a href="/auth/authorize">Sign in</a></html>""",
            ),
        )
        assertFalse(
            OpenCodeGoClient.looksLikeLoginPage(
                """;0x00000010;(${'$'}R => ${'$'}R[0] = { rollingUsage: {} })""",
            ),
        )
    }
}
