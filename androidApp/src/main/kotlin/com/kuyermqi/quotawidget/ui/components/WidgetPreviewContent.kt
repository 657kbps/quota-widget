package com.kuyermqi.quotawidget.ui.components

import android.app.WallpaperManager
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.kuyermqi.quotawidget.R
import com.kuyermqi.quotawidget.domain.CurrencyPreference
import com.kuyermqi.quotawidget.domain.QuotaWindow
import com.kuyermqi.quotawidget.domain.UsageDisplayMode
import com.kuyermqi.quotawidget.domain.UsageProgressStyle
import com.kuyermqi.quotawidget.domain.UsageWindowKind
import com.kuyermqi.quotawidget.domain.formatBalance
import com.kuyermqi.quotawidget.domain.displayUsageFillFraction
import com.kuyermqi.quotawidget.domain.formatUsageDisplayPercent
import com.kuyermqi.quotawidget.domain.formatUsageResetCountdown
import com.kuyermqi.quotawidget.domain.isUsageNearLimitForDisplay
import com.kuyermqi.quotawidget.domain.resetAtEpochMs
import com.kuyermqi.quotawidget.domain.resolveCodexUsageSummaryWindowKind
import com.kuyermqi.quotawidget.widget.ResetMoment
import com.kuyermqi.quotawidget.widget.WidgetDateFormatter
import com.kuyermqi.quotawidget.widget.balanceTitleFontSize
import com.kuyermqi.quotawidget.widget.compactBalanceTitleFontSize
import com.kuyermqi.quotawidget.widget.usage.deepenedCapsuleFillColor

private enum class DeepSeekPreviewLayout {
    STANDARD,
    COMPACT,
}

@Composable
internal fun DeepSeekWidgetPreviewCard(
    balance: String,
    currency: CurrencyPreference,
    updatedAtEpochMs: Long,
) {
    var selectedLayout by remember { mutableStateOf(DeepSeekPreviewLayout.STANDARD) }
    val previewBalance = balance.ifBlank {
        formatBalance(currency, "12.50")
    }
    val labelStandard = stringResource(R.string.usage_preview_layout_standard)
    val labelCompact = stringResource(R.string.usage_preview_layout_compact)
    val updatedLabel = previewUpdatedLabel(updatedAtEpochMs, compact = false)
    val compactUpdatedLabel = previewUpdatedLabel(updatedAtEpochMs, compact = true)

    WidgetTabPreviewCard(
        tabState = WidgetTabHeaderState(
            tabs = DeepSeekPreviewLayout.entries,
            selectedTab = selectedLayout,
            tabLabelOf = { layout ->
                when (layout) {
                    DeepSeekPreviewLayout.STANDARD -> labelStandard
                    DeepSeekPreviewLayout.COMPACT -> labelCompact
                }
            },
            onTabSelected = { selectedLayout = it },
        ),
        widgetModifier = { layout ->
            when (layout) {
                DeepSeekPreviewLayout.STANDARD -> Modifier
                    .widthIn(max = 184.dp)
                    .fillMaxWidth()
                    .aspectRatio(1f)
                DeepSeekPreviewLayout.COMPACT -> Modifier
                    .widthIn(max = 184.dp)
                    .fillMaxWidth()
                    .height(92.dp)
            }
        },
    ) { layout ->
        when (layout) {
            DeepSeekPreviewLayout.STANDARD ->
                DeepSeekStandardBalancePreview(previewBalance, updatedLabel)
            DeepSeekPreviewLayout.COMPACT ->
                DeepSeekCompactBalancePreview(previewBalance, compactUpdatedLabel)
        }
    }
}

@Composable
private fun DeepSeekStandardBalancePreview(
    balance: String,
    updatedLabel: String,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 14.dp, top = 14.dp, end = 14.dp, bottom = 22.dp),
    ) {
        PreviewWidgetHeader(title = stringResource(R.string.platform_name_deepseek))
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = balance,
            style = previewTextStyle(balanceTitleFontSize(balance), FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        if (updatedLabel.isNotBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = updatedLabel,
                style = previewTextStyle(12.sp, lineHeight = 14.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun DeepSeekCompactBalancePreview(
    balance: String,
    updatedLabel: String,
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = balance,
                style = previewTextStyle(compactBalanceTitleFontSize(balance), FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            if (updatedLabel.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = updatedLabel,
                    style = previewTextStyle(10.sp, lineHeight = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(30.dp),
            )
        }
    }
}

@Composable
internal fun PreviewWidgetHeader(title: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = previewTextStyle(15.sp, FontWeight.Medium, lineHeight = 18.sp),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier.size(28.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_refresh),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
internal fun UsageWindowBar(
    label: String,
    window: QuotaWindow?,
    usageDisplayMode: UsageDisplayMode,
    usageProgressStyle: UsageProgressStyle,
    showResetLabel: Boolean,
    updatedAtEpochMs: Long,
    forceZeroProgress: Boolean = false,
) {
    val used = if (forceZeroProgress) 0.0 else window?.usedPercent
    val mode = if (forceZeroProgress) UsageDisplayMode.USED else usageDisplayMode
    val progress = used?.let { displayUsageFillFraction(it, mode) } ?: 0f
    val percentText = used?.let { formatUsageDisplayPercent(it, mode) }
        ?: stringResource(R.string.usage_unavailable)
    val nearLimit = used?.let { isUsageNearLimitForDisplay(it, mode) } ?: false
    val nowMs = remember(updatedAtEpochMs, window?.resetInSec) { System.currentTimeMillis() }
    val resetText = if (showResetLabel && used != null) {
        resolvePreviewResetEpochMs(window, updatedAtEpochMs, nowMs)
            ?.let { formatUsageResetCountdown(it - nowMs) }
    } else {
        null
    }
    val labelText = if (resetText != null) "$label · $resetText" else label
    val scheme = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth()) {
        when (usageProgressStyle.toLinearFallback()) {
            UsageProgressStyle.CAPSULE -> {
                val shape = RoundedCornerShape(8.dp)
                val fillColor = Color(
                    if (nearLimit) {
                        deepenedCapsuleFillColor(
                            containerArgb = scheme.errorContainer.toArgb(),
                            accentArgb = scheme.error.toArgb(),
                        )
                    } else {
                        deepenedCapsuleFillColor(
                            containerArgb = scheme.primaryContainer.toArgb(),
                            accentArgb = scheme.primary.toArgb(),
                        )
                    },
                )
                val trackColor = scheme.surface
                val contentColor = if (nearLimit) {
                    scheme.onErrorContainer
                } else {
                    scheme.onSurface
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .clip(shape)
                        .background(trackColor),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .background(fillColor),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = labelText,
                            style = capsulePreviewTextStyle(FontWeight.Medium),
                            color = contentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = percentText,
                            style = capsulePreviewTextStyle(FontWeight.Bold),
                            color = contentColor,
                            maxLines = 1,
                        )
                    }
                }
            }
            UsageProgressStyle.BAR,
            UsageProgressStyle.RING,
            UsageProgressStyle.RING_SEGMENTED,
            UsageProgressStyle.GAUGE,
            UsageProgressStyle.GAUGE_SEGMENTED,
            -> {
                val barFill = if (nearLimit) scheme.error else scheme.primary
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = labelText,
                        style = previewTextStyle(12.sp, FontWeight.Medium, lineHeight = 14.sp),
                        color = scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = percentText,
                        style = previewTextStyle(12.sp, FontWeight.Bold, lineHeight = 14.sp),
                        color = scheme.onSurface,
                        maxLines = 1,
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                    color = barFill,
                    trackColor = previewLinearTrackColor(),
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
            }
        }
    }
}

internal fun resolvePreviewWindowKind(
    windows: List<QuotaWindow>,
    preferred: UsageWindowKind,
    allowFallback: Boolean,
): UsageWindowKind {
    if (!allowFallback) return preferred
    return resolveCodexUsageSummaryWindowKind(windows, preferred) ?: preferred
}

internal fun resolvePreviewResetEpochMs(
    window: QuotaWindow?,
    updatedAtEpochMs: Long,
    nowMs: Long,
): Long? {
    val w = window ?: return null
    if (updatedAtEpochMs > 0L) {
        return w.resetAtEpochMs(updatedAtEpochMs)?.takeIf { it > nowMs }
    }
    val seconds = w.resetInSec?.takeIf { it > 0L } ?: return null
    return nowMs + seconds * 1_000L
}

@Composable
internal fun formatResetAtLabel(moment: ResetMoment, circular: Boolean = false): String = when (moment) {
    is ResetMoment.Today -> stringResource(
        if (circular) R.string.widget_circular_resets_at else R.string.widget_resets_at,
        moment.time,
    )
    is ResetMoment.Tomorrow -> stringResource(
        if (circular) R.string.widget_circular_resets_at_tomorrow else R.string.widget_resets_at_tomorrow,
        moment.time,
    )
    is ResetMoment.Later -> stringResource(
        if (circular) R.string.widget_circular_resets_at_date else R.string.widget_resets_at_date,
        moment.date,
        moment.time,
    )
}

internal fun previewTextStyle(
    fontSize: TextUnit,
    fontWeight: FontWeight = FontWeight.Normal,
    lineHeight: TextUnit = fontSize,
): TextStyle = TextStyle(
    fontSize = fontSize,
    lineHeight = lineHeight,
    fontWeight = fontWeight,
    letterSpacing = 0.sp,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

/** Matches overview widget comfortable density (12.sp inside 28.dp bar). */
private fun capsulePreviewTextStyle(weight: FontWeight): TextStyle = TextStyle(
    fontSize = 12.sp,
    lineHeight = 12.sp,
    fontWeight = weight,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

/**
 * Mirrors Glance Material3 [androidx.glance.material3.ColorProviders] widgetBackground
 * tone adjustment on [secondaryContainer].
 */
@Composable
internal fun rememberWidgetBackgroundColor(): Color {
    val secondaryContainer = MaterialTheme.colorScheme.secondaryContainer
    return remember(secondaryContainer) {
        val hct = FloatArray(3)
        ColorUtils.colorToM3HCT(secondaryContainer.toArgb(), hct)
        val adjustment = if (hct[2] > 50f) 5f else -10f
        val adjustedTone = (hct[2] + adjustment).coerceIn(0f, 100f)
        Color(ColorUtils.M3HCTToColor(hct[0], hct[1], adjustedTone))
    }
}

/**
 * Reads the current system wallpaper's extracted palette via permission-free
 * [WallpaperManager.getWallpaperColors] (API 27+) so the preview stage reflects
 * the user's wallpaper tones without requesting storage permissions.
 */
@Composable
internal fun rememberWallpaperBackdropColors(): List<Color>? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) return null
    val context = LocalContext.current
    val colors by produceState<List<Color>?>(initialValue = null, context) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                WallpaperManager.getInstance(context)
                    .getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
            }.getOrNull()?.let { wc ->
                buildList {
                    add(Color(wc.primaryColor.toArgb()))
                    wc.secondaryColor?.let { add(Color(it.toArgb())) }
                    wc.tertiaryColor?.let { add(Color(it.toArgb())) }
                }
            }
        }
    }
    return colors
}
