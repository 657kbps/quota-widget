package com.kuyermqi.quotawidget.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class PlatformRegistryTest {
    @Test
    fun registry_containsEverySupportedPlatform() {
        assertNotNull(PlatformRegistry.find(PlatformIds.DEEPSEEK))
        assertNotNull(PlatformRegistry.find(PlatformIds.OPENCODE_GO))
        assertNotNull(PlatformRegistry.find(PlatformIds.CODEX))
        assertNotNull(PlatformRegistry.find(PlatformIds.NEW_API))
        assertNotNull(PlatformRegistry.find(PlatformIds.MAGPIE))
        assertEquals("DeepSeek", PlatformRegistry.displayName(PlatformIds.DEEPSEEK))
        assertEquals("OpenCode Go", PlatformRegistry.displayName(PlatformIds.OPENCODE_GO))
        assertEquals("Codex", PlatformRegistry.displayName(PlatformIds.CODEX))
        assertEquals("NewAPI", PlatformRegistry.displayName(PlatformIds.NEW_API))
        assertEquals("Magpie", PlatformRegistry.displayName(PlatformIds.MAGPIE))
    }
}
