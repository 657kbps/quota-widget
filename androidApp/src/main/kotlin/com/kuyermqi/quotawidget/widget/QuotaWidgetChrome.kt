package com.kuyermqi.quotawidget.widget

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
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
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.CircularProgressIndicator
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import com.kuyermqi.quotawidget.MainActivity
import com.kuyermqi.quotawidget.QuotaWidgetApp
import com.kuyermqi.quotawidget.R
import com.kuyermqi.quotawidget.domain.RefreshIconPhase
import com.kuyermqi.quotawidget.domain.WidgetDisplayState
import com.kuyermqi.quotawidget.locale.AppLocale
import com.kuyermqi.quotawidget.platform.PlatformRegistry
import com.kuyermqi.quotawidget.ui.platformNameRes
import com.kuyermqi.quotawidget.widget.WidgetGlanceState.toAppLanguage
import com.kuyermqi.quotawidget.widget.WidgetGlanceState.toAppThemeSettings
import com.kuyermqi.quotawidget.widget.WidgetGlanceState.toDisplayState
import com.kuyermqi.quotawidget.widget.WidgetGlanceState.toRefreshPhase
import com.kuyermqi.quotawidget.widget.WidgetGlanceState.usageDisplayModeFor
import com.kuyermqi.quotawidget.worker.BalanceRefreshWorker

/** App Context from [provideGlance], not Glance [androidx.glance.LocalContext]. */
private val LocalWidgetAndroidContext = staticCompositionLocalOf<Context> {
    error("LocalWidgetAndroidContext not provided")
}

internal fun GlanceModifier.clickableNoRipple(onClick: Action): GlanceModifier =
    clickable(onClick = onClick, rippleOverride = R.drawable.widget_no_ripple)

internal fun GlanceModifier.systemWidgetBackgroundCornerRadius(): GlanceModifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        cornerRadius(android.R.dimen.system_app_widget_background_radius)
    } else {
        cornerRadius(16.dp)
    }

private const val TAG = "QuotaRefresh"

internal suspend fun GlanceAppWidget.providePlatformGlance(
    context: Context,
    id: GlanceId,
    platformId: String,
    content: @Composable (
        state: WidgetDisplayState,
        refreshPhase: RefreshIconPhase,
        openApp: Action,
        platformTitle: String,
    ) -> Unit,
) {
    val app = context.applicationContext as QuotaWidgetApp
    val repoDisplay = WidgetGlanceState.syncFromRepository(
        context,
        id,
        app.settingsRepository,
        platformId,
    )
    maybeRefreshIfConfigured(context, repoDisplay, platformId)
    Log.i(TAG, "provideGlance id=$id platform=$platformId repo=${repoDisplay::class.simpleName}")
    provideContent {
        val prefs = currentState<Preferences>()
        val appLanguage = prefs.toAppLanguage()
        val systemLocales = context.resources.configuration.locales
        val primarySystemLocale = if (systemLocales.isEmpty) null else systemLocales[0]
        val localizedContext = remember(context, appLanguage, primarySystemLocale) {
            AppLocale.wrap(context, appLanguage)
        }
        CompositionLocalProvider(LocalWidgetAndroidContext provides localizedContext) {
            val glanceState = prefs.toDisplayState()
            val state = if (
                prefs[WidgetGlanceState.statusKey] == null &&
                repoDisplay !is WidgetDisplayState.NotConfigured
            ) {
                repoDisplay
            } else {
                glanceState
            }.withLocalizedSummary(localizedContext, prefs.usageDisplayModeFor(platformId))
            val refreshPhase = prefs.toRefreshPhase()
            val themeColors = colorProvidersFor(localizedContext, prefs.toAppThemeSettings())
            val platformTitle = platformNameRes(platformId)?.let { contextString(it) }
                ?: PlatformRegistry.displayName(platformId)
            val openApp = actionStartActivity<MainActivity>(
                actionParametersOf(
                    ActionParameters.Key<String>(MainActivity.EXTRA_FOCUS_PLATFORM_ID)
                        to platformId,
                ),
            )
            if (themeColors != null) {
                GlanceTheme(colors = themeColors) {
                    content(state, refreshPhase, openApp, platformTitle)
                }
            } else {
                GlanceTheme {
                    content(state, refreshPhase, openApp, platformTitle)
                }
            }
        }
    }
}

internal fun maybeRefreshIfConfigured(
    context: Context,
    state: WidgetDisplayState,
    platformId: String,
) {
    if (state !is WidgetDisplayState.Loading) return
    Log.i(TAG, "provideGlance enqueue refresh; platform=$platformId configured but no quota yet")
    WorkManager.getInstance(context).enqueueUniqueWork(
        BalanceRefreshWorker.UNIQUE_WORK_NAME + "_bootstrap",
        ExistingWorkPolicy.KEEP,
        BalanceRefreshWork.oneTime(),
    )
}

internal fun enqueueBootstrapRefresh(context: Context) {
    Log.i(TAG, "widget onEnabled; enqueue bootstrap refresh")
    WorkManager.getInstance(context).enqueueUniqueWork(
        BalanceRefreshWorker.UNIQUE_WORK_NAME + "_bootstrap",
        ExistingWorkPolicy.KEEP,
        BalanceRefreshWork.oneTime(),
    )
}

@Composable
internal fun contextString(@StringRes resId: Int, vararg formatArgs: Any): String =
    LocalWidgetAndroidContext.current.getString(resId, *formatArgs)

@Composable
internal fun widgetContext(): Context = LocalWidgetAndroidContext.current

@Composable
internal fun updatedAtLabel(epochMs: Long, compact: Boolean = false): String = contextString(
    if (compact) R.string.widget_circular_updated_at else R.string.widget_updated_at,
    WidgetDateFormatter.formatUpdatedAt(widgetContext(), epochMs, circular = compact),
)

/** Fixed size used by the in-app widget previews; live widgets use [balanceTitleSize]. */
internal fun balanceTitleFontSize(formattedBalance: String): TextUnit =
    when {
        formattedBalance.length <= 6 -> 32.sp
        formattedBalance.length <= 7 -> 28.sp
        formattedBalance.length <= 8 -> 24.sp
        formattedBalance.length <= 10 -> 22.sp
        formattedBalance.length <= 12 -> 20.sp
        else -> 18.sp
    }

internal fun compactBalanceTitleFontSize(formattedBalance: String): TextUnit =
    when {
        formattedBalance.length <= 6 -> 26.sp
        formattedBalance.length <= 7 -> 24.sp
        formattedBalance.length <= 8 -> 22.sp
        formattedBalance.length <= 10 -> 20.sp
        formattedBalance.length <= 12 -> 18.sp
        else -> 16.sp
    }

@Composable
internal fun balanceTitleSize(text: String, compact: Boolean): TextUnit {
    val size = LocalSize.current
    val horizontalReserve = if (compact) 64.dp else 28.dp
    val verticalReserve = if (compact) 20.dp else 80.dp
    return fittedTitleFontSize(
        text = text,
        maxWidth = (size.width - horizontalReserve).coerceAtLeast(40.dp),
        maxHeight = (size.height - verticalReserve).coerceAtLeast(24.dp),
        minSp = if (compact) 16f else 18f,
        maxSp = if (compact) 32f else 40f,
    )
}

@Composable
internal fun BalanceBlock(
    title: String,
    subtitle: String?,
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
            maxLines = 1,
            modifier = GlanceModifier.clickableNoRipple(openApp),
        )
        if (!subtitle.isNullOrBlank()) {
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
}

@Composable
internal fun RefreshButton(
    platformId: String,
    phase: RefreshIconPhase,
    hitSize: Dp = 28.dp,
    iconSize: Dp = 24.dp,
    spinnerSize: Dp = 22.dp,
) {
    val refresh = actionRunCallback<RefreshBalanceAction>(
        parameters = actionParametersOf(
            RefreshBalanceAction.PLATFORM_ID_KEY to platformId,
        ),
    )
    Box(
        modifier = GlanceModifier
            .size(hitSize)
            .clickableNoRipple(refresh),
        contentAlignment = Alignment.Center,
    ) {
        when (phase) {
            RefreshIconPhase.Spinning,
            RefreshIconPhase.Settling,
            -> {
                CircularProgressIndicator(
                    color = GlanceTheme.colors.primary,
                    modifier = GlanceModifier
                        .size(spinnerSize)
                        .clickableNoRipple(refresh),
                )
            }
            RefreshIconPhase.Idle -> {
                Image(
                    provider = ImageProvider(R.drawable.ic_refresh),
                    contentDescription = contextString(R.string.widget_refresh_action),
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
                    modifier = GlanceModifier
                        .size(iconSize)
                        .clickableNoRipple(refresh),
                )
            }
        }
    }
}

@Composable
internal fun WidgetHeader(
    platformId: String,
    platformTitle: String,
    refreshPhase: RefreshIconPhase,
    openApp: Action,
) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = platformTitle,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            ),
            modifier = GlanceModifier
                .defaultWeight()
                .clickableNoRipple(openApp),
        )
        RefreshButton(platformId = platformId, phase = refreshPhase)
    }
}
