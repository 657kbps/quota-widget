package com.kuyermqi.quotawidget.ui.components

import com.kuyermqi.quotawidget.R
import com.kuyermqi.quotawidget.platform.PlatformIds

/** Provider logo and whether it is monochrome enough to tint with the theme. */
data class ProviderLogo(
    val drawableRes: Int,
    val monochrome: Boolean = false,
)

internal fun providerLogo(platformId: String): ProviderLogo? = when (platformId) {
    PlatformIds.DEEPSEEK -> ProviderLogo(R.drawable.ic_provider_deepseek)
    PlatformIds.OPENCODE_GO -> ProviderLogo(R.drawable.ic_provider_opencode, monochrome = true)
    PlatformIds.CODEX -> ProviderLogo(R.drawable.ic_provider_codex)
    PlatformIds.NEW_API -> ProviderLogo(R.drawable.ic_provider_newapi)
    PlatformIds.MAGPIE -> ProviderLogo(R.drawable.ic_provider_magpie, monochrome = true)
    PlatformIds.KIMI_CODE -> ProviderLogo(R.drawable.ic_provider_kimi, monochrome = true)
    PlatformIds.ZHIPU -> ProviderLogo(R.drawable.ic_provider_zhipu)
    PlatformIds.WORKBUDDY -> ProviderLogo(R.drawable.ic_provider_workbuddy)
    PlatformIds.WORKBUDDY_AI -> ProviderLogo(R.drawable.ic_provider_workbuddy)
    PlatformIds.CLINE -> ProviderLogo(R.drawable.ic_provider_cline, monochrome = true)
    PlatformIds.COMMAND_CODE -> ProviderLogo(R.drawable.ic_provider_commandcode, monochrome = true)
    PlatformIds.ANTIGRAVITY -> ProviderLogo(R.drawable.ic_provider_antigravity)
    else -> null
}
