package com.kuyermqi.quotawidget.platform

/**
 * Extensible quota/balance platform contract.
 * Add new platforms by implementing this and registering in [PlatformRegistry].
 */
interface QuotaPlatform {
    val id: String
    val displayName: String
}

object PlatformIds {
    const val DEEPSEEK = "deepseek"
    const val OPENCODE_GO = "opencode_go"
    const val CODEX = "codex"
}

object PlatformRegistry {
    val platforms: List<QuotaPlatform> = listOf(
        object : QuotaPlatform {
            override val id = PlatformIds.DEEPSEEK
            override val displayName = "DeepSeek"
        },
        object : QuotaPlatform {
            override val id = PlatformIds.OPENCODE_GO
            override val displayName = "OpenCode Go"
        },
        object : QuotaPlatform {
            override val id = PlatformIds.CODEX
            override val displayName = "Codex"
        },
    )

    fun find(id: String): QuotaPlatform? = platforms.find { it.id == id }

    fun displayName(id: String): String =
        find(id)?.displayName ?: id
}
