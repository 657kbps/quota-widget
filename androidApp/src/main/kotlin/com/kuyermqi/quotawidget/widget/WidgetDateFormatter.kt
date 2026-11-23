package com.kuyermqi.quotawidget.widget

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Local-calendar bucket of a reset instant; widget strings pick the wording. */
sealed interface ResetMoment {
    data class Today(val time: String) : ResetMoment
    data class Tomorrow(val time: String) : ResetMoment
    data class Later(val date: String, val time: String) : ResetMoment
}

object WidgetDateFormatter {
    private val updatedFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("M 月 d 日 HH:mm")
    private val timeFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("HH:mm")
    private val dateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("M 月 d 日")

    fun formatUpdatedAt(epochMs: Long): String {
        if (epochMs <= 0L) return "尚未更新"
        return Instant.ofEpochMilli(epochMs)
            .atZone(ZoneId.systemDefault())
            .format(updatedFormatter)
    }

    /** Buckets [resetAtEpochMs] by local calendar day relative to [nowEpochMs]. */
    fun resetMoment(resetAtEpochMs: Long, nowEpochMs: Long): ResetMoment {
        val zone = ZoneId.systemDefault()
        val reset = Instant.ofEpochMilli(resetAtEpochMs).atZone(zone)
        val today = Instant.ofEpochMilli(nowEpochMs).atZone(zone).toLocalDate()
        val time = reset.format(timeFormatter)
        return when (reset.toLocalDate()) {
            today -> ResetMoment.Today(time)
            today.plusDays(1) -> ResetMoment.Tomorrow(time)
            else -> ResetMoment.Later(date = reset.format(dateFormatter), time = time)
        }
    }
}
