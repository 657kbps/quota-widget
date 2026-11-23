package com.kuyermqi.quotawidget.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.kuyermqi.quotawidget.R
import com.kuyermqi.quotawidget.platform.PlatformIds
import com.kuyermqi.quotawidget.platform.PlatformRegistry

/** Localized platform name resource; [homeList] keeps the longer home-list brand name for Zhipu. */
@StringRes
fun platformNameRes(platformId: String, homeList: Boolean = false): Int? = when (platformId) {
    PlatformIds.DEEPSEEK -> R.string.platform_name_deepseek
    PlatformIds.OPENCODE_GO -> R.string.platform_name_opencode_go
    PlatformIds.CODEX -> R.string.platform_name_codex
    PlatformIds.NEW_API -> R.string.platform_name_new_api
    PlatformIds.MAGPIE -> R.string.platform_name_magpie
    PlatformIds.KIMI_CODE -> R.string.platform_name_kimi_code
    PlatformIds.ZHIPU ->
        if (homeList) R.string.platform_name_zhipu_glm else R.string.platform_name_zhipu
    PlatformIds.WORKBUDDY -> R.string.platform_name_workbuddy
    PlatformIds.WORKBUDDY_AI -> R.string.platform_name_workbuddy_ai
    PlatformIds.CLINE -> R.string.platform_name_cline
    PlatformIds.COMMAND_CODE -> R.string.platform_name_command_code
    PlatformIds.ANTIGRAVITY -> R.string.platform_name_antigravity
    else -> null
}

@Composable
fun platformDisplayName(platformId: String, homeList: Boolean = false): String {
    val resId = platformNameRes(platformId, homeList)
    return if (resId != null) stringResource(resId) else PlatformRegistry.displayName(platformId)
}
