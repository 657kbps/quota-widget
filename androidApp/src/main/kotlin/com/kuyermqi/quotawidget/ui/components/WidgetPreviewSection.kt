package com.kuyermqi.quotawidget.ui.components

import android.os.Build
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kuyermqi.quotawidget.R
import com.kuyermqi.quotawidget.domain.QuotaSnapshot
import com.kuyermqi.quotawidget.domain.QuotaWindow
import com.kuyermqi.quotawidget.domain.QuotaWindowKind
import com.kuyermqi.quotawidget.domain.UsageDisplayMode
import com.kuyermqi.quotawidget.domain.UsageProgressStyle
import com.kuyermqi.quotawidget.domain.UsageResetStyle
import com.kuyermqi.quotawidget.domain.UsageWindowKind
import com.kuyermqi.quotawidget.domain.formatUsageDisplayPercent
import com.kuyermqi.quotawidget.domain.formatUsageResetCountdown
import com.kuyermqi.quotawidget.domain.usage.overviewWindowLabel
import com.kuyermqi.quotawidget.widget.WidgetDateFormatter
import com.kuyermqi.quotawidget.widget.compactBalanceTitleFontSize
import com.kuyermqi.quotawidget.widget.usage.progressTrackColorArgb
import kotlin.math.roundToInt

private const val COMPACT_PILL_PREVIEW_MAX_PERCENT_SP = 22f

internal val LocalShowWidgetUpdatedAt = staticCompositionLocalOf { true }

internal class WidgetTabScrollContext(
    val scrollState: ScrollState,
    private val viewportCoordinatesProvider: () -> LayoutCoordinates?,
) {
    val viewportCoordinates: LayoutCoordinates?
        get() = viewportCoordinatesProvider()
}

internal val LocalWidgetTabScrollContext = staticCompositionLocalOf<WidgetTabScrollContext?> { null }

internal data class WidgetTabHeaderState<T>(
    val tabs: List<T>,
    val selectedTab: T,
    val tabLabelOf: (T) -> String,
    val onTabSelected: (T) -> Unit,
)

internal data class UsageLayoutPreviewModel(
    val platformName: String,
    val windows: List<QuotaWindow>,
    val widgetWindowKind: UsageWindowKind,
    val usageDisplayMode: UsageDisplayMode,
    val usageProgressStyle: UsageProgressStyle,
    val usageResetStyle: UsageResetStyle,
    val overviewKinds: List<QuotaWindowKind>,
    val showResetLabels: Boolean,
    val showProgressStyle: Boolean,
    val forceZeroProgressPreview: Boolean,
    val allowWindowFallback: Boolean,
    val updatedAtEpochMs: Long,
    val newApiSnapshot: QuotaSnapshot?,
)

@Suppress("FunctionNaming")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T> WidgetTabPreviewCard(
    tabState: WidgetTabHeaderState<T>,
    widgetModifier: (T) -> Modifier,
    settingsContent: @Composable ColumnScope.(T) -> Unit = {},
    previewContent: @Composable (T) -> Unit,
) {
    val tabScrollContext = LocalWidgetTabScrollContext.current
    var tabRowCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var scrollToTopTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(scrollToTopTrigger) {
        if (scrollToTopTrigger == 0) return@LaunchedEffect
        repeat(2) { withFrameNanos { } }
        val context = tabScrollContext ?: return@LaunchedEffect
        val viewport = context.viewportCoordinates ?: return@LaunchedEffect
        val tabRow = tabRowCoords ?: return@LaunchedEffect
        if (!viewport.isAttached || !tabRow.isAttached) return@LaunchedEffect
        val target = (context.scrollState.value + (tabRow.positionInRoot().y - viewport.positionInRoot().y))
            .roundToInt()
            .coerceIn(0, context.scrollState.maxValue)
        if (target != context.scrollState.value) {
            context.scrollState.animateScrollTo(target)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = appCardColor()),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (tabState.tabs.size >= 2) {
                val selectedIndex = tabState.tabs.indexOf(tabState.selectedTab).coerceAtLeast(0)
                PrimaryTabRow(
                    selectedTabIndex = selectedIndex,
                    modifier = Modifier.onGloballyPositioned { tabRowCoords = it },
                    containerColor = Color.Transparent,
                    divider = {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        )
                    },
                ) {
                    tabState.tabs.forEachIndexed { index, tab ->
                        Tab(
                            selected = index == selectedIndex,
                            onClick = {
                                if (tab != tabState.selectedTab) {
                                    tabState.onTabSelected(tab)
                                    scrollToTopTrigger++
                                }
                            },
                            text = {
                                Text(
                                    text = tabState.tabLabelOf(tab),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                        )
                    }
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                settingsContent(tabState.selectedTab)
                WidgetPreviewStage(
                    modifier = widgetModifier(tabState.selectedTab),
                    content = { previewContent(tabState.selectedTab) },
                )
            }
        }
    }
}

@Suppress("FunctionNaming")
@Composable
internal fun WidgetPreviewStage(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val wallpaperColors = rememberWallpaperBackdropColors()
    val fallbackStageColor = MaterialTheme.colorScheme.surfaceContainerLow
    val stageModifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(16.dp))
        .let { base ->
            if (!wallpaperColors.isNullOrEmpty()) {
                val gradientColors = if (wallpaperColors.size == 1) {
                    listOf(
                        wallpaperColors[0],
                        lerp(wallpaperColors[0], fallbackStageColor, 0.35f),
                    )
                } else {
                    wallpaperColors
                }
                base.background(
                    brush = Brush.linearGradient(
                        colors = gradientColors,
                        start = Offset.Zero,
                        end = Offset(900f, 600f),
                    ),
                )
            } else {
                base.background(fallbackStageColor)
            }
        }
        .padding(horizontal = 12.dp, vertical = 14.dp)

    val widgetCornerRadius = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dimensionResource(android.R.dimen.system_app_widget_background_radius)
    } else {
        16.dp
    }
    val widgetBackgroundColor = rememberWidgetBackgroundColor()

    Box(
        modifier = stageModifier,
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(widgetCornerRadius),
            color = widgetBackgroundColor,
        ) {
            CompositionLocalProvider(LocalTextStyle provides TextStyle.Default) {
                content()
            }
        }
    }
}

internal fun Modifier.usageLayoutWidgetSize(
    layout: WidgetConfigTab,
    overviewKindsCount: Int,
): Modifier = when (layout) {
    WidgetConfigTab.STANDARD -> this
        .widthIn(max = 184.dp)
        .fillMaxWidth()
        .aspectRatio(1f)
    WidgetConfigTab.COMPACT -> this
        .widthIn(max = 184.dp)
        .fillMaxWidth()
        .height(92.dp)
    WidgetConfigTab.OVERVIEW -> this
        .widthIn(max = 184.dp)
        .fillMaxWidth()
        .height(if (overviewKindsCount >= 3) 208.dp else 184.dp)
}

@Suppress("FunctionNaming")
@Composable
internal fun UsageLayoutPreviewContent(
    layout: WidgetConfigTab,
    model: UsageLayoutPreviewModel,
) {
    val updatedLabel = previewUpdatedLabel(model.updatedAtEpochMs, compact = false)
    val compactUpdatedLabel = previewUpdatedLabel(model.updatedAtEpochMs, compact = true)
    when (layout) {
        WidgetConfigTab.STANDARD -> StandardWidgetPreview(
            model = model,
            updatedLabel = updatedLabel,
            compactUpdatedLabel = compactUpdatedLabel,
        )
        WidgetConfigTab.COMPACT -> CompactWidgetPreview(
            windows = model.windows,
            widgetWindowKind = model.widgetWindowKind,
            usageDisplayMode = model.usageDisplayMode,
            usageResetStyle = model.usageResetStyle,
            showResetLabels = model.showResetLabels,
            forceZeroProgressPreview = model.forceZeroProgressPreview,
            allowWindowFallback = model.allowWindowFallback,
            updatedAtEpochMs = model.updatedAtEpochMs,
            updatedLabel = compactUpdatedLabel,
        )
        WidgetConfigTab.OVERVIEW -> OverviewWidgetPreview(
            platformName = model.platformName,
            windows = model.windows,
            overviewKinds = model.overviewKinds,
            usageDisplayMode = model.usageDisplayMode,
            usageProgressStyle = model.usageProgressStyle,
            usageResetStyle = model.usageResetStyle,
            showResetLabels = model.showResetLabels,
            forceZeroProgressPreview = model.forceZeroProgressPreview,
            updatedAtEpochMs = model.updatedAtEpochMs,
            updatedLabel = updatedLabel,
        )
    }
}

@Composable
internal fun previewLinearTrackColor(): Color {
    val scheme = MaterialTheme.colorScheme
    return Color(
        progressTrackColorArgb(
            surfaceVariantArgb = scheme.surfaceVariant.toArgb(),
            onSurfaceArgb = scheme.onSurface.toArgb(),
        ),
    )
}

@Composable
internal fun previewUpdatedLabel(
    updatedAtEpochMs: Long,
    compact: Boolean = false,
): String {
    if (!LocalShowWidgetUpdatedAt.current) return ""
    val context = LocalContext.current
    return when {
        updatedAtEpochMs > 0L && compact -> stringResource(
            R.string.widget_circular_updated_at,
            WidgetDateFormatter.formatUpdatedAt(context, updatedAtEpochMs, circular = true),
        )
        updatedAtEpochMs > 0L -> stringResource(
            R.string.widget_updated_at,
            WidgetDateFormatter.formatUpdatedAt(context, updatedAtEpochMs),
        )
        compact -> stringResource(R.string.widget_circular_updated_sample)
        else -> stringResource(R.string.usage_preview_updated_sample)
    }
}

@Composable
private fun CompactWidgetPreview(
    windows: List<QuotaWindow>,
    widgetWindowKind: UsageWindowKind,
    usageDisplayMode: UsageDisplayMode,
    usageResetStyle: UsageResetStyle,
    showResetLabels: Boolean,
    forceZeroProgressPreview: Boolean,
    allowWindowFallback: Boolean,
    updatedAtEpochMs: Long,
    updatedLabel: String,
) {
    val context = LocalContext.current
    val resolvedKind = resolvePreviewWindowKind(windows, widgetWindowKind, allowWindowFallback)
    val window = windows.find { it.kind == resolvedKind.toQuotaWindowKind() }
    val used = if (forceZeroProgressPreview) 0.0 else window?.usedPercent
    val mode = if (forceZeroProgressPreview) UsageDisplayMode.USED else usageDisplayMode
    val percentText = used?.let { formatUsageDisplayPercent(it, mode) }
        ?: stringResource(R.string.usage_unavailable)

    val nowMs = remember(updatedAtEpochMs, window?.resetInSec) { System.currentTimeMillis() }
    val resetAtMs = if (showResetLabels && used != null && usageResetStyle != UsageResetStyle.HIDDEN) {
        resolvePreviewResetEpochMs(window, updatedAtEpochMs, nowMs)
    } else {
        null
    }
    val pillText = resetAtMs
        ?.takeIf { usageResetStyle == UsageResetStyle.PILL }
        ?.let { formatUsageResetCountdown(it - nowMs) }
    val resetAtLabel = if (pillText == null) {
        resetAtMs
            ?.let { WidgetDateFormatter.resetMoment(context, it, nowMs) }
            ?.let { formatResetAtLabel(it) }
    } else {
        null
    }
    val footerText = resetAtLabel ?: updatedLabel
    val scheme = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            val baseCompactPercentSize = compactBalanceTitleFontSize(percentText)
            val compactPercentSize = if (pillText != null) {
                minOf(COMPACT_PILL_PREVIEW_MAX_PERCENT_SP, baseCompactPercentSize.value).sp
            } else {
                baseCompactPercentSize
            }
            if (pillText != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = percentText,
                        style = previewTextStyle(compactPercentSize, FontWeight.Bold),
                        color = scheme.onSurface,
                        maxLines = 1,
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    PreviewResetPill(text = pillText, compact = true)
                }
            } else {
                Text(
                    text = percentText,
                    style = previewTextStyle(compactPercentSize, FontWeight.Bold),
                    color = scheme.onSurface,
                    maxLines = 1,
                )
            }
            if (footerText.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = footerText,
                    style = previewTextStyle(10.sp, lineHeight = 12.sp),
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        Box(
            modifier = Modifier.size(34.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_refresh),
                contentDescription = null,
                tint = scheme.primary,
                modifier = Modifier.size(30.dp),
            )
        }
    }
}

@Suppress("FunctionNaming")
@Composable
internal fun PreviewResetPill(
    text: String,
    compact: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val corner = if (compact) 6.dp else 7.dp
    val padH = if (compact) 5.dp else 6.dp
    val padV = if (compact) 2.dp else 3.dp
    val fontSize = if (compact) 10.sp else 11.sp
    val lineHeight = if (compact) 12.sp else 13.sp
    Surface(
        shape = RoundedCornerShape(corner),
        color = scheme.secondaryContainer,
    ) {
        Text(
            text = text,
            style = previewTextStyle(fontSize, FontWeight.Medium, lineHeight = lineHeight),
            color = scheme.onSecondaryContainer,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = padH, vertical = padV),
        )
    }
}

@Composable
private fun OverviewWidgetPreview(
    platformName: String,
    windows: List<QuotaWindow>,
    overviewKinds: List<QuotaWindowKind>,
    usageDisplayMode: UsageDisplayMode,
    usageProgressStyle: UsageProgressStyle,
    usageResetStyle: UsageResetStyle,
    showResetLabels: Boolean,
    forceZeroProgressPreview: Boolean,
    updatedAtEpochMs: Long,
    updatedLabel: String,
) {
    val resources = LocalResources.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 14.dp, top = 14.dp, end = 14.dp, bottom = 22.dp),
    ) {
        if (platformName.isNotBlank()) {
            PreviewWidgetHeader(title = platformName)
            Spacer(modifier = Modifier.height(10.dp))
        }
        Spacer(modifier = Modifier.weight(1f))
        overviewKinds.forEachIndexed { index, kind ->
            if (index > 0) Spacer(modifier = Modifier.height(10.dp))
            UsageWindowBar(
                label = overviewWindowLabel(resources, kind),
                window = windows.find { it.kind == kind },
                usageDisplayMode = usageDisplayMode,
                usageProgressStyle = usageProgressStyle,
                showResetLabel = showResetLabels &&
                    usageResetStyle != UsageResetStyle.HIDDEN,
                updatedAtEpochMs = updatedAtEpochMs,
                forceZeroProgress = forceZeroProgressPreview,
            )
        }
        if (updatedLabel.isNotBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = updatedLabel,
                style = previewTextStyle(12.sp, lineHeight = 14.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}
