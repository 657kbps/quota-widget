package com.kuyermqi.quotawidget.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.kuyermqi.quotawidget.QuotaWidgetApp
import com.kuyermqi.quotawidget.deepseek.peakvalley.DeepSeekPeakValleyCalculator
import com.kuyermqi.quotawidget.deepseek.peakvalley.HolidayCalendarTable
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

/**
 * Recalculates the standard DeepSeek widget's peak/off-peak mirror and schedules
 * the next local recompute. Uses inexact AlarmManager delivery only; Doze may
 * delay it, so every wake recomputes from the current wall clock.
 */
object DeepSeekPeakValleyCoordinator {
    private const val TAG = "QuotaRefresh"
    private const val ALARM_REQUEST_CODE = 7301
    private const val COUNTDOWN_TICK_MS = 30L * 60L * 1000L
    private const val MIN_ALARM_DELAY_MS = 1_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun requestRefresh(context: Context, reason: String) {
        val appContext = context.applicationContext
        scope.launch {
            try {
                refresh(appContext, reason)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Log.e(TAG, "peak-valley refresh failed reason=$reason", error)
            }
        }
    }

    suspend fun refresh(context: Context, reason: String) {
        try {
            refreshInternal(context, reason)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            Log.e(TAG, "peak-valley refresh failed reason=$reason", error)
        }
    }

    private suspend fun refreshInternal(context: Context, reason: String) {
        val app = context.applicationContext as QuotaWidgetApp
        WidgetGlanceState.glanceWriteMutex.withLock {
            val enabled = app.settingsRepository.isDeepSeekPeakValleyEnabled()
            val manager = GlanceAppWidgetManager(context)
            val ids = manager.getGlanceIds(DeepSeekBalanceWidget::class.java)
            val table = if (enabled) {
                app.holidayCalendarRepository.table()
            } else {
                HolidayCalendarTable.EMPTY
            }
            val now = System.currentTimeMillis()
            Log.i(TAG, "peak-valley refresh reason=$reason enabled=$enabled widgets=${ids.size}")

            for (id in ids) {
                updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) { prefs ->
                    prefs.toMutablePreferences().apply {
                        DeepSeekPeakValleyGlanceState.write(
                            prefs = this,
                            enabled = enabled,
                            table = table,
                            computedAtEpochMs = now,
                        )
                    }
                }
                DeepSeekBalanceWidget().update(context, id)
            }

            if (!enabled || ids.isEmpty()) {
                cancelAlarm(context)
                return@withLock
            }
            scheduleNext(context, table, now)
        }
    }

    private fun scheduleNext(context: Context, table: HolidayCalendarTable, now: Long) {
        val snapshot = DeepSeekPeakValleyCalculator(table).snapshotAt(Instant.ofEpochMilli(now))
        val target = minOf(
            snapshot.nextEvaluationEpochMs,
            now + COUNTDOWN_TICK_MS,
        ).coerceAtLeast(now + MIN_ALARM_DELAY_MS)
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            target,
            alarmIntent(context),
        )
        Log.i(TAG, "peak-valley alarm target=$target snapshot=${snapshot.tier}")
    }

    private fun cancelAlarm(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        alarmManager.cancel(alarmIntent(context))
    }

    private fun alarmIntent(context: Context): PendingIntent {
        val intent = Intent(context, DeepSeekPeakValleyAlarmReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
