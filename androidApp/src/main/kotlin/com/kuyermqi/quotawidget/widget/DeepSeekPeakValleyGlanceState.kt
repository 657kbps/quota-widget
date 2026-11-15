package com.kuyermqi.quotawidget.widget

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.kuyermqi.quotawidget.deepseek.peakvalley.DeepSeekPeakValleyCalculator
import com.kuyermqi.quotawidget.deepseek.peakvalley.HolidayCalendarTable
import com.kuyermqi.quotawidget.deepseek.peakvalley.PeakValleySnapshot
import java.time.Instant

/**
 * Non-sensitive Glance mirror used by the DeepSeek standard widget. The widget
 * composes the current tier from these calendar facts plus the wall clock, so a
 * balance sync can never publish a stale computed tier.
 */
object DeepSeekPeakValleyGlanceState {
    val enabledKey = booleanPreferencesKey("qw_peak_valley_enabled")
    val tableKey = stringPreferencesKey("qw_peak_valley_table")
    val recomputeKey = longPreferencesKey("qw_peak_valley_recompute")

    fun Preferences.isPeakValleyEnabled(): Boolean = this[enabledKey] ?: true

    fun Preferences.peakValleyTable(): HolidayCalendarTable =
        HolidayCalendarTable.fromPayload(this[tableKey])

    fun Preferences.peakValleySnapshot(nowEpochMs: Long): PeakValleySnapshot? {
        if (!isPeakValleyEnabled()) return null
        val table = peakValleyTable()
        return DeepSeekPeakValleyCalculator(table).snapshotAt(
            Instant.ofEpochMilli(nowEpochMs),
        )
    }

    fun write(
        prefs: MutablePreferences,
        enabled: Boolean,
        table: HolidayCalendarTable?,
        computedAtEpochMs: Long,
    ) {
        prefs[enabledKey] = enabled
        if (enabled && table != null) {
            prefs[tableKey] = table.toPayload()
        } else {
            prefs.remove(tableKey)
        }
        prefs[recomputeKey] = maxOf((prefs[recomputeKey] ?: 0L) + 1L, computedAtEpochMs)
    }
}
