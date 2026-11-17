package com.kuyermqi.quotawidget.util

expect fun currentTimeMillis(): Long

/**
 * Parses the timestamp formats provider APIs send: ISO-8601 with a `Z` or
 * numeric offset, and `yyyy-MM-dd HH:mm:ss` in China Standard Time.
 */
expect fun parseDateTimeToEpochMs(value: String): Long?
