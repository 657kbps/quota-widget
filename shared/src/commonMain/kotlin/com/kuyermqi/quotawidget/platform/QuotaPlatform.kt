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
}

object PlatformRegistry {
    val platforms: List<QuotaPlatform> = listOf(
        object : QuotaPlatform {
            override val id = PlatformIds.DEEPSEEK
            override val displayName = "DeepSeek"
        },
    )

    fun find(id: String): QuotaPlatform? = platforms.find { it.id == id }
}
