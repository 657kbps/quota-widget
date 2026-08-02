package com.kuyermqi.quotawidget.update

data class SemVer(
    val major: Int,
    val minor: Int,
    val patch: Int,
) : Comparable<SemVer> {
    override fun compareTo(other: SemVer): Int {
        val majorCmp = major.compareTo(other.major)
        if (majorCmp != 0) return majorCmp
        val minorCmp = minor.compareTo(other.minor)
        if (minorCmp != 0) return minorCmp
        return patch.compareTo(other.patch)
    }

    companion object {
        fun parse(raw: String): SemVer {
            val normalized = raw.trim().removePrefix("v").removePrefix("V")
            val core = normalized.substringBefore('-').substringBefore('+')
            val parts = core.split('.')
            fun part(index: Int): Int =
                parts.getOrNull(index)?.takeWhile { it.isDigit() }?.toIntOrNull() ?: 0
            return SemVer(major = part(0), minor = part(1), patch = part(2))
        }
    }
}

fun normalizeVersionName(raw: String): String =
    raw.trim().removePrefix("v").removePrefix("V").substringBefore('-').substringBefore('+')
