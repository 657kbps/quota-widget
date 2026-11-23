package com.kuyermqi.quotawidget.widget

import android.content.Context
import androidx.annotation.StringRes
import com.kuyermqi.quotawidget.R
import com.kuyermqi.quotawidget.locale.AppLocale
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
    fun formatUpdatedAt(context: Context, epochMs: Long): String {
        if (epochMs <= 0L) return context.getString(R.string.widget_updated_never)
        return Instant.ofEpochMilli(epochMs)
            .atZone(ZoneId.systemDefault())
            .format(formatter(context, R.string.widget_date_time_pattern))
    }

    /** Buckets [resetAtEpochMs] by local calendar day relative to [nowEpochMs]. */
    fun resetMoment(context: Context, resetAtEpochMs: Long, nowEpochMs: Long): ResetMoment {
        val zone = ZoneId.systemDefault()
        val reset = Instant.ofEpochMilli(resetAtEpochMs).atZone(zone)
        val today = Instant.ofEpochMilli(nowEpochMs).atZone(zone).toLocalDate()
        val time = reset.format(formatter(context, R.string.widget_time_pattern))
        return when (reset.toLocalDate()) {
            today -> ResetMoment.Today(time)
            today.plusDays(1) -> ResetMoment.Tomorrow(time)
            else -> ResetMoment.Later(
                date = reset.format(formatter(context, R.string.widget_date_pattern)),
                time = time,
            )
        }
    }

    private fun formatter(context: Context, @StringRes patternRes: Int): DateTimeFormatter =
        DateTimeFormatter.ofPattern(
            context.getString(patternRes),
            AppLocale.localeOf(context),
        )
}
