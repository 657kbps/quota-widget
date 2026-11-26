package com.kuyermqi.quotawidget.network

/**
 * The parts of an http(s) URL needed to judge an origin and whether it is
 * routable from the public internet. Only what the security policy needs:
 * absolute scheme, host, and an explicitly written port.
 */
internal data class HttpUrlParts(
    val scheme: String,
    val host: String,
    val explicitPort: Int?,
) {
    val isSecure: Boolean
        get() = scheme == "https"

    val effectivePort: Int
        get() = explicitPort ?: if (isSecure) HTTPS_PORT else HTTP_PORT

    val isLoopbackHost: Boolean
        get() = host == "localhost" ||
            host.endsWith(".localhost") ||
            host == "::1" ||
            host == "0:0:0:0:0:0:0:1" ||
            (ipv4Octets()?.get(0) == 127)

    val isPrivateHost: Boolean
        get() {
            val octets = ipv4Octets()
            if (octets != null) {
                return octets[0] == 10 ||
                    (octets[0] == 172 && octets[1] in 16..31) ||
                    (octets[0] == 192 && octets[1] == 168) ||
                    (octets[0] == 169 && octets[1] == 254)
            }
            if (!host.contains(':')) return false
            // fc00::/7 unique local addresses and fe80::/10 link local addresses.
            val firstGroup = host.substringBefore(':').toIntOrNull(radix = 16) ?: return false
            return firstGroup and 0xFE00 == 0xFC00 || firstGroup and 0xFFC0 == 0xFE80
        }

    /** Null when [host] is not a dotted-quad IPv4 literal. */
    private fun ipv4Octets(): List<Int>? {
        val segments = host.split('.')
        if (segments.size != 4) return null
        val octets = segments.map { it.toIntOrNull() ?: return null }
        return octets.takeIf { values -> values.all { it in 0..255 } }
    }
}

private val SCHEME_PREFIX = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:")

internal const val HTTP_PORT = 80
internal const val HTTPS_PORT = 443

/**
 * Parses an absolute http(s) URL, or returns null when it is not one. Written by
 * hand so the policy stays identical on every platform and never guesses a
 * scheme for schemeless input.
 */
internal fun parseHttpUrl(raw: String): HttpUrlParts? {
    val value = raw.trim()
    val schemeEnd = value.indexOf("://")
    if (schemeEnd <= 0) return null
    val scheme = value.substring(0, schemeEnd).lowercase()
    if (scheme != "http" && scheme != "https") return null
    val afterScheme = value.substring(schemeEnd + 3)
    val authorityLength = afterScheme.indexOfFirst { it == '/' || it == '?' || it == '#' }
        .let { if (it < 0) afterScheme.length else it }
    // User info is not part of the origin and must not be mistaken for a host.
    val authority = afterScheme.substring(0, authorityLength).substringAfterLast('@')
    if (authority.isEmpty()) return null

    val host: String
    val portText: String?
    if (authority.startsWith("[")) {
        val close = authority.indexOf(']')
        if (close <= 1) return null
        host = authority.substring(1, close)
        val suffix = authority.substring(close + 1)
        if (suffix.isNotEmpty() && !suffix.startsWith(':')) return null
        portText = suffix.removePrefix(":").takeIf { it.isNotEmpty() }
    } else {
        val colon = authority.lastIndexOf(':')
        if (authority.indexOf(':') != colon) return null
        host = if (colon < 0) authority else authority.substring(0, colon)
        portText = if (colon < 0) null else authority.substring(colon + 1)
    }

    val normalizedHost = host.lowercase()
    if (!isPlausibleHost(normalizedHost)) return null
    val port = when {
        portText == null -> null
        else -> portText.toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
    }
    // Writing the default port out is the same origin as omitting it.
    val defaultPort = if (scheme == "https") HTTPS_PORT else HTTP_PORT
    return HttpUrlParts(
        scheme = scheme,
        host = normalizedHost,
        explicitPort = port?.takeIf { it != defaultPort },
    )
}

private fun isPlausibleHost(host: String): Boolean {
    if (host.isEmpty()) return false
    return host.all { ch ->
        ch in 'a'..'z' || ch in '0'..'9' || ch == '.' || ch == '-' || ch == '_' || ch == ':'
    }
}

/**
 * Resolves a `Location` header against the URL that produced it. Redirect
 * targets are frequently root-relative or relative to the current path.
 */
internal fun resolveRedirectUrl(currentUrl: String, location: String): String? {
    val target = location.trim()
    if (target.isEmpty()) return null
    // Any scheme prefix means the server sent an absolute URL; whether that
    // scheme is acceptable is decided later, by [classifyRedirect].
    if (SCHEME_PREFIX.containsMatchIn(target)) return target

    val schemeEnd = currentUrl.indexOf("://")
    if (schemeEnd <= 0) return null
    val scheme = currentUrl.substring(0, schemeEnd)
    if (target.startsWith("//")) return "$scheme:$target"

    val afterScheme = currentUrl.substring(schemeEnd + 3)
    val authorityLength = afterScheme.indexOfFirst { it == '/' || it == '?' || it == '#' }
        .let { if (it < 0) afterScheme.length else it }
    val authority = afterScheme.substring(0, authorityLength)
    if (authority.isEmpty()) return null
    if (target.startsWith("/")) return "$scheme://$authority$target"

    val currentPath = if (authorityLength < afterScheme.length) {
        afterScheme.substring(authorityLength).substringBefore('?').substringBefore('#')
    } else {
        "/"
    }
    val directory = currentPath.substringBeforeLast('/', missingDelimiterValue = "/")
        .ifEmpty { "/" }
    val separator = if (directory.endsWith('/')) "" else "/"
    return "$scheme://$authority$directory$separator$target"
}
