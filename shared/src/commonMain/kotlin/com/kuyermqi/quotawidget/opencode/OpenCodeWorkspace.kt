package com.kuyermqi.quotawidget.opencode

data class OpenCodeWorkspace(
    val id: String,
    val name: String = "",
    val slug: String? = null,
) {
    val displayName: String
        get() = name.ifBlank { id }
}
