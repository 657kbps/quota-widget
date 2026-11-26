package com.kuyermqi.quotawidget.settings

import com.kuyermqi.quotawidget.domain.AppSettings
import com.kuyermqi.quotawidget.domain.RefreshIconPhase
import com.kuyermqi.quotawidget.domain.UsageDisplayMode
import com.kuyermqi.quotawidget.domain.UsageProgressStyle
import com.kuyermqi.quotawidget.domain.UsageResetStyle
import com.kuyermqi.quotawidget.domain.UsageWindowKind
import com.kuyermqi.quotawidget.domain.WidgetDisplayState

/**
 * Widget card knobs of one platform. A null [windowKind] / [usageResetStyle] means that
 * platform's card has no such knob, so the Glance sync leaves that key alone.
 */
data class WidgetCardSettings(
    val usageDisplayMode: UsageDisplayMode,
    val usageProgressStyle: UsageProgressStyle,
    val windowKind: UsageWindowKind? = null,
    val usageResetStyle: UsageResetStyle? = null,
)

data class WidgetPlatformRenderState(
    val display: WidgetDisplayState,
    val phase: RefreshIconPhase,
    val card: WidgetCardSettings?,
)

/** Everything a widget update renders, decoded from a single settings read. */
data class WidgetRenderSnapshot(
    val appSettings: AppSettings,
    val platforms: Map<String, WidgetPlatformRenderState>,
) {
    companion object {
        internal fun of(
            platformIds: Set<String>,
            appSettings: AppSettings,
            renderState: (String) -> WidgetPlatformRenderState,
        ): WidgetRenderSnapshot = WidgetRenderSnapshot(
            appSettings = appSettings,
            platforms = platformIds.associateWith(renderState),
        )
    }
}