package com.kuyermqi.quotawidget.widget

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object WidgetDateFormatter {
    private val formatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("M 月 d 日 HH:mm")

    fun formatUpdatedAt(epochMs: Long): String {
        if (epochMs <= 0L) return "尚未更新"
        return Instant.ofEpochMilli(epochMs)
            .atZone(ZoneId.systemDefault())
            .format(formatter)
    }
}
