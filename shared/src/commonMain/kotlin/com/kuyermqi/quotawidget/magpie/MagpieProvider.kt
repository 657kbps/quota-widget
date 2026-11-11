package com.kuyermqi.quotawidget.magpie

import com.kuyermqi.quotawidget.domain.QuotaWindow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * One provider the gateway reports quotas for, merging every signed-in account
 * of that provider (two antigravity accounts become one entry).
 *
 * Persisted with [com.kuyermqi.quotawidget.settings.MagpieSettings] so the
 * picker can list providers before a refresh completes.
 */
@Serializable
data class MagpieProvider(
    /** Gateway provider id, e.g. "antigravity". */
    val id: String,
    val name: String = "",
    /** How many accounts of this provider the gateway reported. */
    val accountCount: Int = 1,
    /** Distinct plans across those accounts, in gateway order. */
    val plans: List<String> = emptyList(),
    /** One window per kind: the tightest across the provider's accounts. */
    val windows: List<QuotaWindow> = emptyList(),
) {
    val displayName: String
        get() = name.ifBlank { id }
}

private val providerJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

fun encodeMagpieProviders(providers: List<MagpieProvider>): String =
    providerJson.encodeToString(ListSerializer(MagpieProvider.serializer()), providers)

fun decodeMagpieProviders(raw: String?): List<MagpieProvider> {
    if (raw.isNullOrBlank()) return emptyList()
    return runCatching {
        providerJson.decodeFromString(ListSerializer(MagpieProvider.serializer()), raw)
    }.getOrDefault(emptyList())
}
