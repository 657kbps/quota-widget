package com.kuyermqi.quotawidget.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.kuyermqi.quotawidget.R
import com.kuyermqi.quotawidget.domain.ALLOWED_REFRESH_INTERVAL_MINUTES
import com.kuyermqi.quotawidget.domain.AppSettings
import com.kuyermqi.quotawidget.domain.DarkThemeMode
import com.kuyermqi.quotawidget.domain.ThemeColorMode
import kotlin.math.roundToInt

private const val CUSTOM_COLOR_EXPAND_MS = 200
private const val CUSTOM_COLOR_FADE_IN_MS = 160
private const val CUSTOM_COLOR_SHRINK_MS = 180
private const val CUSTOM_COLOR_FADE_OUT_MS = 120

private val DarkModeOptions = listOf(
    DarkThemeMode.FollowSystem,
    DarkThemeMode.Light,
    DarkThemeMode.Dark,
)

private val ThemeColorOptions = listOf(
    ThemeColorMode.FollowSystem,
    ThemeColorMode.Custom,
)

internal data class UpdateCheckRowState(
    val versionName: String,
    val isChecking: Boolean,
    val onCheckClick: () -> Unit,
)

@Suppress("FunctionNaming")
@Composable
internal fun AppearanceSettingsSection(
    appSettings: AppSettings,
    cardColor: Color,
    chooseColorRowColor: Color,
    onSave: (AppSettings) -> Unit,
    onChooseCustomColor: () -> Unit,
) {
    SettingsSection(
        title = stringResource(R.string.app_settings_section_appearance),
        cardColor = cardColor,
    ) {
        SettingsExpandingRow(
            title = stringResource(R.string.app_settings_dark_mode),
            value = darkModeLabel(appSettings.darkThemeMode),
        ) {
            ButtonGroupChoice(
                options = DarkModeOptions,
                selected = appSettings.darkThemeMode,
                labelOf = { mode ->
                    if (mode == DarkThemeMode.FollowSystem) {
                        stringResource(R.string.app_settings_system_short)
                    } else {
                        darkModeLabel(mode)
                    }
                },
                onSelect = { mode -> onSave(appSettings.copy(darkThemeMode = mode)) },
            )
        }
        SettingsRowDivider()
        SettingsExpandingRow(
            title = stringResource(R.string.app_settings_theme_color),
            value = themeColorValue(appSettings.themeColorMode),
            valueDotColor = if (appSettings.themeColorMode == ThemeColorMode.Custom) {
                Color(appSettings.customSeedColorArgb)
            } else {
                null
            },
        ) {
            ButtonGroupChoice(
                options = ThemeColorOptions,
                selected = appSettings.themeColorMode,
                labelOf = { mode ->
                    if (mode == ThemeColorMode.FollowSystem) {
                        stringResource(R.string.app_settings_system_short)
                    } else {
                        themeColorValue(mode)
                    }
                },
                onSelect = { mode -> onSave(appSettings.copy(themeColorMode = mode)) },
            )
            AnimatedVisibility(
                visible = appSettings.themeColorMode == ThemeColorMode.Custom,
                enter = expandVertically(tween(CUSTOM_COLOR_EXPAND_MS)) + fadeIn(tween(CUSTOM_COLOR_FADE_IN_MS)),
                exit = shrinkVertically(tween(CUSTOM_COLOR_SHRINK_MS)) + fadeOut(tween(CUSTOM_COLOR_FADE_OUT_MS)),
            ) {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(chooseColorRowColor)
                            .clickable(onClick = onChooseCustomColor)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(Color(appSettings.customSeedColorArgb))
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                    shape = CircleShape,
                                ),
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.app_settings_theme_color_choose),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Icon(
                            painter = painterResource(R.drawable.ic_chevron_right),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

@Suppress("FunctionNaming")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WidgetSettingsSection(
    appSettings: AppSettings,
    cardColor: Color,
    onSave: (AppSettings) -> Unit,
    onRefreshIntervalChanged: (Int) -> Unit,
) {
    val refreshIntervals = ALLOWED_REFRESH_INTERVAL_MINUTES
    val lastRefreshIndex = refreshIntervals.lastIndex
    var refreshIndex by rememberSaveable {
        mutableIntStateOf(
            refreshIntervals.indexOf(appSettings.refreshIntervalMinutes).coerceIn(0, lastRefreshIndex),
        )
    }
    val sliderState = rememberSliderState(
        value = refreshIndex.toFloat(),
        steps = (lastRefreshIndex - 1).coerceAtLeast(0),
        trackRange = 0f..lastRefreshIndex.toFloat(),
    )
    LaunchedEffect(appSettings.refreshIntervalMinutes) {
        val targetIndex = refreshIntervals.indexOf(appSettings.refreshIntervalMinutes)
            .coerceIn(0, lastRefreshIndex)
        refreshIndex = targetIndex
        if (sliderState.value.roundToInt() != targetIndex) {
            sliderState.value = targetIndex.toFloat()
        }
    }
    val refreshIntervalMinutes = refreshIntervals[refreshIndex]

    SettingsSection(
        title = stringResource(R.string.app_settings_section_widget),
        cardColor = cardColor,
    ) {
        SettingsExpandingRow(
            title = stringResource(R.string.app_settings_refresh_interval),
            value = refreshIntervalLabel(refreshIntervalMinutes),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Slider(
                    state = sliderState,
                    onValueChange = { value ->
                        refreshIndex = value.roundToInt().coerceIn(0, lastRefreshIndex)
                        sliderState.value = value
                    },
                    onValueChangeFinished = {
                        val minutes = refreshIntervals[refreshIndex]
                        if (minutes != appSettings.refreshIntervalMinutes) {
                            onSave(appSettings.copy(refreshIntervalMinutes = minutes))
                            onRefreshIntervalChanged(minutes)
                        }
                    },
                )
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = refreshIntervalLabel(refreshIntervals.first()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = refreshIntervalLabel(refreshIntervals.last()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        SettingsRowDivider()
        SettingsRow(
            title = stringResource(R.string.app_settings_show_updated_at),
            trailing = {
                Switch(
                    checked = appSettings.showWidgetUpdatedAt,
                    onCheckedChange = null,
                )
            },
            modifier = Modifier.toggleable(
                value = appSettings.showWidgetUpdatedAt,
                role = Role.Switch,
                onValueChange = { enabled -> onSave(appSettings.copy(showWidgetUpdatedAt = enabled)) },
            ),
        )
    }
}

@Suppress("FunctionNaming")
@Composable
internal fun AppInfoSettingsSection(
    appSettings: AppSettings,
    cardColor: Color,
    updateCheckState: UpdateCheckRowState,
    onSave: (AppSettings) -> Unit,
) {
    SettingsSection(
        title = stringResource(R.string.app_settings_section_app),
        cardColor = cardColor,
    ) {
        SettingsExpandingRow(
            title = stringResource(R.string.app_settings_language),
            value = appLanguageLabel(appSettings.appLanguage),
        ) {
            AppLanguageOptionsList(
                selected = appSettings.appLanguage,
                onSelect = { language -> onSave(appSettings.copy(appLanguage = language)) },
            )
        }
        SettingsRowDivider()
        SettingsRow(
            title = stringResource(R.string.app_settings_check_updates_on_launch),
            summary = stringResource(R.string.app_settings_check_updates_supporting),
            trailing = {
                Switch(
                    checked = appSettings.checkForUpdatesOnLaunch,
                    onCheckedChange = null,
                )
            },
            modifier = Modifier.toggleable(
                value = appSettings.checkForUpdatesOnLaunch,
                role = Role.Switch,
                onValueChange = { enabled -> onSave(appSettings.copy(checkForUpdatesOnLaunch = enabled)) },
            ),
        )
        SettingsRowDivider()
        SettingsRow(
            title = stringResource(R.string.app_settings_version),
            summary = updateCheckState.versionName,
            trailing = {
                CheckUpdateButton(
                    isChecking = updateCheckState.isChecking,
                    onClick = updateCheckState.onCheckClick,
                )
            },
        )
    }
}
