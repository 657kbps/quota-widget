package com.kuyermqi.quotawidget.widget

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.CircularProgressIndicator
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.kuyermqi.quotawidget.MainActivity
import com.kuyermqi.quotawidget.QuotaWidgetApp
import com.kuyermqi.quotawidget.R
import com.kuyermqi.quotawidget.domain.RefreshIconPhase
import com.kuyermqi.quotawidget.domain.WidgetDisplayState
import com.kuyermqi.quotawidget.widget.WidgetGlanceState.toDisplayState
import com.kuyermqi.quotawidget.widget.WidgetGlanceState.toRefreshPhase
import com.kuyermqi.quotawidget.worker.BalanceRefreshWorker

/** clickable without the default Material ink / ripple. */
private fun GlanceModifier.clickableNoRipple(onClick: Action): GlanceModifier =
    clickable(onClick = onClick, rippleOverride = R.drawable.widget_no_ripple)

/** Match launcher/OEM widget corners on API 31+; fall back to AOSP-typical 16dp. */
private fun GlanceModifier.systemWidgetBackgroundCornerRadius(): GlanceModifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        cornerRadius(android.R.dimen.system_app_widget_background_radius)
    } else {
        cornerRadius(16.dp)
    }

class DeepSeekBalanceWidget : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as QuotaWidgetApp
        val repoDisplay = WidgetGlanceState.syncFromRepository(context, id, app.settingsRepository)
        maybeRefreshIfConfigured(context, app)
        Log.i(TAG, "provideGlance id=$id repoDisplay=${repoDisplay::class.simpleName}")
        provideContent {
            val prefs = currentState<Preferences>()
            val glanceState = prefs.toDisplayState()
            // currentState() can still be empty on the first composition after
            // updateAppWidgetState in the same provideGlance; fall back to repo
            // only when Glance prefs were never written for this session.
            val state = if (
                prefs[WidgetGlanceState.statusKey] == null &&
                repoDisplay !is WidgetDisplayState.NotConfigured
            ) {
                Log.w(
                    TAG,
                    "compose fallback: empty glance state, using repo=${repoDisplay::class.simpleName}",
                )
                repoDisplay
            } else {
                glanceState
            }
            Log.i(
                TAG,
                "compose glance=${glanceState::class.simpleName} " +
                    "effective=${state::class.simpleName} " +
                    "qw_status=${prefs[WidgetGlanceState.statusKey]}",
            )
            val refreshPhase = prefs.toRefreshPhase()
            GlanceTheme {
                WidgetContent(
                    state = state,
                    refreshPhase = refreshPhase,
                    openApp = actionStartActivity<MainActivity>(),
                )
            }
        }
    }

    companion object {
        private const val TAG = "QuotaRefresh"

        private suspend fun maybeRefreshIfConfigured(context: Context, app: QuotaWidgetApp) {
            val settings = app.settingsRepository.getDeepSeekSettings()
            if (settings.apiKey.isBlank()) return
            val state = app.settingsRepository.getWidgetState()
            if (state !is WidgetDisplayState.Loading) return
            Log.i(TAG, "provideGlance enqueue refresh; configured but no balance yet")
            val request = OneTimeWorkRequestBuilder<BalanceRefreshWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                BalanceRefreshWorker.UNIQUE_WORK_NAME + "_bootstrap",
                ExistingWorkPolicy.KEEP,
                request,
            )
        }
    }
}

@Composable
private fun WidgetContent(
    state: WidgetDisplayState,
    refreshPhase: RefreshIconPhase,
    openApp: Action,
) {
    // Rounded card: shape drawable + system radius (API 31+) / 16dp fallback.
    // Glance often does not bubble clicks from Text to parent containers — attach actions on leaves.
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .systemWidgetBackgroundCornerRadius()
            .clickableNoRipple(openApp),
    ) {
        Image(
            provider = ImageProvider(R.drawable.widget_rounded_bg),
            contentDescription = null,
            colorFilter = ColorFilter.tint(GlanceTheme.colors.widgetBackground),
            modifier = GlanceModifier.fillMaxSize(),
        )
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(start = 14.dp, top = 14.dp, end = 14.dp, bottom = 22.dp),
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "DeepSeek",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    modifier = GlanceModifier
                        .defaultWeight()
                        .clickableNoRipple(openApp),
                )
                RefreshButton(phase = refreshPhase)
            }

            Spacer(
                modifier = GlanceModifier
                    .defaultWeight()
                    .fillMaxWidth()
                    .clickableNoRipple(openApp),
            )

            when (state) {
                WidgetDisplayState.NotConfigured -> {
                    BalanceBlock(
                        title = "未配置",
                        subtitle = "点击配置 API Key",
                        titleSize = 32.sp,
                        openApp = openApp,
                    )
                }
                WidgetDisplayState.Loading -> {
                    BalanceBlock(
                        title = "刷新中…",
                        subtitle = "正在获取余额",
                        titleSize = 32.sp,
                        openApp = openApp,
                    )
                }
                is WidgetDisplayState.Success -> {
                    val balance = state.snapshot.formattedBalance
                    BalanceBlock(
                        title = balance,
                        subtitle = "更新于 ${WidgetDateFormatter.formatUpdatedAt(state.snapshot.updatedAtEpochMs)}",
                        titleSize = balanceTitleFontSize(balance),
                        openApp = openApp,
                    )
                }
                is WidgetDisplayState.Error -> {
                    BalanceBlock(
                        title = "获取失败",
                        subtitle = state.message,
                        titleSize = 32.sp,
                        openApp = openApp,
                    )
                }
            }
        }
    }
}

private fun balanceTitleFontSize(formattedBalance: String): TextUnit =
    when {
        formattedBalance.length <= 8 -> 32.sp
        formattedBalance.length <= 10 -> 28.sp
        formattedBalance.length <= 12 -> 24.sp
        else -> 20.sp
    }

@Composable
private fun BalanceBlock(
    title: String,
    subtitle: String,
    titleSize: TextUnit,
    openApp: Action,
) {
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        Text(
            text = title,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = titleSize,
                fontWeight = FontWeight.Bold,
            ),
            modifier = GlanceModifier.clickableNoRipple(openApp),
        )
        Spacer(GlanceModifier.height(2.dp))
        Text(
            text = subtitle,
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = 12.sp,
            ),
            modifier = GlanceModifier.clickableNoRipple(openApp),
        )
    }
}

@Composable
private fun RefreshButton(phase: RefreshIconPhase) {
    val refresh = actionRunCallback<RefreshBalanceAction>()
    // Keep hit target close to icon size so right inset matches left text padding.
    Box(
        modifier = GlanceModifier
            .size(28.dp)
            .clickableNoRipple(refresh),
        contentAlignment = Alignment.Center,
    ) {
        when (phase) {
            RefreshIconPhase.Spinning,
            RefreshIconPhase.Settling,
            -> {
                // Also attach action on the indicator; parent clickable is unreliable.
                CircularProgressIndicator(
                    color = GlanceTheme.colors.primary,
                    modifier = GlanceModifier
                        .size(22.dp)
                        .clickableNoRipple(refresh),
                )
            }
            RefreshIconPhase.Idle -> {
                Image(
                    provider = ImageProvider(R.drawable.ic_refresh),
                    contentDescription = "刷新余额",
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
                    modifier = GlanceModifier
                        .size(24.dp)
                        .clickableNoRipple(refresh),
                )
            }
        }
    }
}

class DeepSeekBalanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DeepSeekBalanceWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        Log.i("QuotaRefresh", "widget onEnabled; enqueue bootstrap refresh")
        val request = OneTimeWorkRequestBuilder<BalanceRefreshWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            BalanceRefreshWorker.UNIQUE_WORK_NAME + "_bootstrap",
            ExistingWorkPolicy.KEEP,
            request,
        )
    }
}
