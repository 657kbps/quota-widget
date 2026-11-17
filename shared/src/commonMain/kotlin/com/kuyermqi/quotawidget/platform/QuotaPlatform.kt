package com.kuyermqi.quotawidget.platform

/**
 * Extensible quota/balance platform contract.
 * Add new platforms by implementing this and registering in [PlatformRegistry].
 */
interface QuotaPlatform {
    val id: String
    val displayName: String

    /** Label shown in the home platform list; defaults to [displayName]. */
    val homeListName: String get() = displayName
}

object PlatformIds {
    const val DEEPSEEK = "deepseek"
    const val OPENCODE_GO = "opencode_go"
    const val CODEX = "codex"
    const val NEW_API = "new_api"
    const val MAGPIE = "magpie"
    const val KIMI_CODE = "kimi_code"
    const val ZHIPU = "zhipu"
    const val WORKBUDDY = "workbuddy"
    const val WORKBUDDY_AI = "workbuddy_ai"
    const val CLINE = "cline"
    const val COMMAND_CODE = "command_code"
    const val ANTIGRAVITY = "antigravity"

    /** Platforms whose login state is kept in one generic [SessionPlatformSettings]. */
    val sessionPlatforms: List<String> = listOf(
        WORKBUDDY,
        WORKBUDDY_AI,
        CLINE,
        COMMAND_CODE,
        ANTIGRAVITY,
    )
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
        object : QuotaPlatform {
            override val id = PlatformIds.NEW_API
            override val displayName = "NewAPI"
        },
        object : QuotaPlatform {
            override val id = PlatformIds.MAGPIE
            override val displayName = "Magpie"
        },
        object : QuotaPlatform {
            override val id = PlatformIds.KIMI_CODE
            override val displayName = "Kimi Code"
        },
        object : QuotaPlatform {
            override val id = PlatformIds.ZHIPU
            override val displayName = "Zhipu"
            override val homeListName = "Zhipu GLM"
        },
        object : QuotaPlatform {
            override val id = PlatformIds.WORKBUDDY
            override val displayName = "WorkBuddy"
        },
        object : QuotaPlatform {
            override val id = PlatformIds.WORKBUDDY_AI
            override val displayName = "WorkBuddy 国际版"
        },
        object : QuotaPlatform {
            override val id = PlatformIds.CLINE
            override val displayName = "Cline Pass"
        },
        object : QuotaPlatform {
            override val id = PlatformIds.COMMAND_CODE
            override val displayName = "Command Code"
        },
        object : QuotaPlatform {
            override val id = PlatformIds.ANTIGRAVITY
            override val displayName = "Antigravity"
        },
    ).sortedBy { it.displayName.lowercase() }

    fun find(id: String): QuotaPlatform? = platforms.find { it.id == id }

    fun displayName(id: String): String =
        find(id)?.displayName ?: id
}
