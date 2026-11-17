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
        assertNotNull(PlatformRegistry.find(PlatformIds.KIMI_CODE))
        assertNotNull(PlatformRegistry.find(PlatformIds.ZHIPU))
        assertEquals("DeepSeek", PlatformRegistry.displayName(PlatformIds.DEEPSEEK))
        assertEquals("OpenCode Go", PlatformRegistry.displayName(PlatformIds.OPENCODE_GO))
        assertEquals("Codex", PlatformRegistry.displayName(PlatformIds.CODEX))
        assertEquals("NewAPI", PlatformRegistry.displayName(PlatformIds.NEW_API))
        assertEquals("Magpie", PlatformRegistry.displayName(PlatformIds.MAGPIE))
        assertEquals("Kimi Code", PlatformRegistry.displayName(PlatformIds.KIMI_CODE))
        assertEquals("Zhipu", PlatformRegistry.displayName(PlatformIds.ZHIPU))
        assertEquals("Zhipu GLM", PlatformRegistry.find(PlatformIds.ZHIPU)?.homeListName)
    }

    @Test
    fun registry_isSortedByDisplayName() {
        val names = PlatformRegistry.platforms.map { it.displayName }
        assertEquals(names.sortedBy { it.lowercase() }, names)
    }

    @Test
    fun registryContainsSessionPlatforms() {
        assertEquals("WorkBuddy", PlatformRegistry.displayName(PlatformIds.WORKBUDDY))
        assertEquals("WorkBuddy 国际版", PlatformRegistry.displayName(PlatformIds.WORKBUDDY_AI))
        assertEquals("Cline Pass", PlatformRegistry.displayName(PlatformIds.CLINE))
        assertEquals("Command Code", PlatformRegistry.displayName(PlatformIds.COMMAND_CODE))
        assertEquals("Antigravity", PlatformRegistry.displayName(PlatformIds.ANTIGRAVITY))
        PlatformIds.sessionPlatforms.forEach { platformId ->
            assertNotNull(PlatformRegistry.find(platformId))
        }
    }
}
