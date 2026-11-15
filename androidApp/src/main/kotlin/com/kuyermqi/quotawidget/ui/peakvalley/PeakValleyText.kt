package com.kuyermqi.quotawidget.ui.peakvalley

import android.content.res.Resources
import com.kuyermqi.quotawidget.R
import com.kuyermqi.quotawidget.deepseek.peakvalley.SHANGHAI_ZONE
import com.kuyermqi.quotawidget.deepseek.peakvalley.peakValleyCountdown
import java.time.Instant
import java.time.format.DateTimeFormatter

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val dateTimeFormatter = DateTimeFormatter.ofPattern("M 月 d 日 HH:mm")
private val fullDateTimeFormatter = DateTimeFormatter.ofPattern("yyyy 年 M 月 d 日 HH:mm")

fun formatPeakValleyTarget(
    epochMs: Long,
    nowEpochMs: Long,
    tomorrowLabel: String,
): String {
    val target = Instant.ofEpochMilli(epochMs).atZone(SHANGHAI_ZONE)
    val now = Instant.ofEpochMilli(nowEpochMs).atZone(SHANGHAI_ZONE)
    return when {
        target.toLocalDate() == now.toLocalDate() -> target.format(timeFormatter)
        target.toLocalDate() == now.toLocalDate().plusDays(1) ->
            "$tomorrowLabel ${target.format(timeFormatter)}"
        else -> target.format(dateTimeFormatter)
    }
}

fun formatPeakValleyTimestamp(epochMs: Long): String =
    Instant.ofEpochMilli(epochMs).atZone(SHANGHAI_ZONE).format(fullDateTimeFormatter)

fun formatPeakValleyCountdown(resources: Resources, remainingMs: Long): String {
    val countdown = peakValleyCountdown(remainingMs)
    return when {
        countdown.days > 0 && countdown.hours > 0 ->
            resources.getString(
                R.string.peak_valley_countdown_days_hours,
                countdown.days,
                countdown.hours,
            )
        countdown.days > 0 ->
            resources.getString(R.string.peak_valley_countdown_days, countdown.days)
        countdown.hours > 0 && countdown.minutes > 0 ->
            resources.getString(
                R.string.peak_valley_countdown_hours_minutes,
                countdown.hours,
                countdown.minutes,
            )
        countdown.hours > 0 ->
            resources.getString(R.string.peak_valley_countdown_hours, countdown.hours)
        countdown.totalMinutes > 0 ->
            resources.getString(R.string.peak_valley_countdown_minutes, countdown.totalMinutes)
        else -> resources.getString(R.string.peak_valley_countdown_zero)
    }
}
