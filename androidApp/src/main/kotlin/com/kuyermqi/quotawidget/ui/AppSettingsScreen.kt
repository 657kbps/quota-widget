package com.kuyermqi.quotawidget.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalToggleButton
import androidx.compose.material3.FilledTonalToggleButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kuyermqi.quotawidget.R
import com.kuyermqi.quotawidget.domain.AppLanguage
import com.kuyermqi.quotawidget.domain.AppSettings
import com.kuyermqi.quotawidget.domain.DarkThemeMode
import com.kuyermqi.quotawidget.domain.ThemeColorMode
import com.kuyermqi.quotawidget.locale.AppLocale
import com.kuyermqi.quotawidget.settings.PlatformSettingsRepository
import com.kuyermqi.quotawidget.ui.components.PageHeader
import com.kuyermqi.quotawidget.ui.components.ThemeColorPickerDialog
import com.kuyermqi.quotawidget.ui.components.appCardColor
import com.kuyermqi.quotawidget.ui.theme.AppNightMode
import com.kuyermqi.quotawidget.update.UpdateAvailability
import com.kuyermqi.quotawidget.update.UpdateCheckOutcome
import kotlinx.coroutines.launch

private const val DARK_LUMINANCE_THRESHOLD = 0.5f

@Suppress("FunctionNaming")
@Composable
fun AppSettingsScreen(
    settingsRepository: PlatformSettingsRepository,
    onBack: () -> Unit,
    onRefreshIntervalChanged: (Int) -> Unit,
    onCheckForUpdates: suspend (String) -> UpdateCheckOutcome,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val initialSettings = remember(context) {
        AppSettings(
            darkThemeMode = AppNightMode.storedMode(context),
            appLanguage = AppLocale.current(),
        )
    }
    val appSettings by settingsRepository.observeAppSettings()
        .collectAsStateWithLifecycle(initialValue = initialSettings)
    var showColorPicker by remember { mutableStateOf(false) }

    val versionName = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }

    var isCheckingUpdate by remember { mutableStateOf(false) }
    var availableUpdate by remember { mutableStateOf<UpdateAvailability?>(null) }
    val upToDateMessage = stringResource(R.string.app_settings_check_updates_latest)
    val checkFailedMessage = stringResource(R.string.app_settings_check_updates_failed)

    fun save(updated: AppSettings) {
        scope.launch { settingsRepository.saveAppSettings(updated) }
    }

    if (showColorPicker) {
        ThemeColorPickerDialog(
            initialColorArgb = appSettings.customSeedColorArgb,
            onDismiss = { showColorPicker = false },
            onConfirm = { argb ->
                showColorPicker = false
                save(
                    appSettings.copy(
                        themeColorMode = ThemeColorMode.Custom,
                        customSeedColorArgb = argb,
                    ),
                )
            },
        )
    }

    availableUpdate?.let { update ->
        UpdateAvailableDialog(
            versionName = update.versionName,
            onIgnoreVersion = {
                scope.launch { settingsRepository.setUpdateIgnoredVersion(update.versionName) }
                availableUpdate = null
            },
            onDownload = {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, update.releaseUrl.toUri()))
                }
                availableUpdate = null
            },
            onDismiss = { availableUpdate = null },
        )
    }

    val scheme = MaterialTheme.colorScheme
    val cardColor = appCardColor()
    val chooseColorRowColor = if (scheme.surface.luminance() < DARK_LUMINANCE_THRESHOLD) {
        scheme.surfaceContainerHigh
    } else {
        scheme.surfaceContainer
    }

    Scaffold(containerColor = scheme.surfaceContainerLow) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding),
        ) {
            PageHeader(
                title = stringResource(R.string.app_settings_title),
                onBack = onBack,
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                AppearanceSettingsSection(
                    appSettings = appSettings,
                    cardColor = cardColor,
                    chooseColorRowColor = chooseColorRowColor,
                    onSave = ::save,
                    onChooseCustomColor = { showColorPicker = true },
                )
                WidgetSettingsSection(
                    appSettings = appSettings,
                    cardColor = cardColor,
                    onSave = ::save,
                    onRefreshIntervalChanged = onRefreshIntervalChanged,
                )
                AppInfoSettingsSection(
                    appSettings = appSettings,
                    cardColor = cardColor,
                    updateCheckState = UpdateCheckRowState(
                        versionName = versionName,
                        isChecking = isCheckingUpdate,
                        onCheckClick = {
                            if (!isCheckingUpdate) {
                                isCheckingUpdate = true
                                scope.launch {
                                    val outcome = onCheckForUpdates(versionName)
                                    isCheckingUpdate = false
                                    when (outcome) {
                                        is UpdateCheckOutcome.Available -> availableUpdate = outcome.availability
                                        UpdateCheckOutcome.UpToDate ->
                                            Toast.makeText(context, upToDateMessage, Toast.LENGTH_SHORT).show()
                                        UpdateCheckOutcome.Failed ->
                                            Toast.makeText(context, checkFailedMessage, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                    ),
                    onSave = ::save,
                )
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Suppress("FunctionNaming")
@Composable
internal fun SettingsSection(
    title: String,
    cardColor: Color,
    content: @Composable ColumnScope.() -> Unit,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
    )
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(28.dp),
        color = cardColor,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            content = content,
        )
    }
}

@Suppress("FunctionNaming")
@Composable
internal fun SettingsExpandingRow(
    title: String,
    value: String,
    valueDotColor: Color? = null,
    content: @Composable () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "settingsChevron",
    )

    Column {
        SettingsRow(
            title = title,
            value = value,
            valueDotColor = valueDotColor,
            showChevron = true,
            chevronRotation = chevronRotation,
            modifier = Modifier.clickable { expanded = !expanded },
        )
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(animationSpec = tween(220)) + fadeIn(tween(160)),
            exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(tween(120)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 16.dp),
            ) {
                content()
            }
        }
    }
}

@Suppress("FunctionNaming")
@Composable
internal fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    value: String? = null,
    valueDotColor: Color? = null,
    showChevron: Boolean = false,
    chevronRotation: Float = 0f,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (summary != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (value != null) {
            if (valueDotColor != null) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(valueDotColor)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (showChevron) {
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(chevronRotation),
            )
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.width(12.dp))
            trailing()
        }
    }
}

@Suppress("FunctionNaming")
@Composable
internal fun SettingsRowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 20.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
    )
}

@Suppress("FunctionNaming")
@Composable
internal fun <T> ButtonGroupChoice(
    options: List<T>,
    selected: T,
    labelOf: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    ButtonGroup(
        overflowIndicator = { menuState ->
            ButtonGroupDefaults.OverflowIndicator(menuState = menuState)
        },
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, option ->
            customItem(
                buttonGroupContent = {
                    val isSelected = option == selected
                    val shapes = when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    }
                    FilledTonalToggleButton(
                        checked = isSelected,
                        onCheckedChange = { onSelect(option) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 40.dp),
                        shapes = shapes,
                        colors = FilledTonalToggleButtonDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            checkedContainerColor = MaterialTheme.colorScheme.primary,
                            checkedContentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        icon = if (isSelected) {
                            {
                                Icon(
                                    painter = painterResource(R.drawable.ic_check),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        } else {
                            null
                        },
                    ) {
                        Text(
                            text = labelOf(option),
                            maxLines = 1,
                        )
                    }
                },
                menuContent = { menuState ->
                    DropdownMenuItem(
                        text = { Text(text = labelOf(option)) },
                        onClick = {
                            onSelect(option)
                            menuState.dismiss()
                        },
                    )
                },
            )
        }
    }
}

@Suppress("FunctionNaming")
@Composable
internal fun CheckUpdateButton(
    isChecking: Boolean,
    onClick: () -> Unit,
) {
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(isChecking) {
        if (isChecking) {
            rotation.snapTo(0f)
            rotation.animateTo(
                targetValue = 360f,
                animationSpec = infiniteRepeatable(tween(durationMillis = 900, easing = LinearEasing)),
            )
        }
    }

    TextButton(
        onClick = onClick,
        enabled = !isChecking,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_refresh),
            contentDescription = null,
            modifier = Modifier
                .size(18.dp)
                .rotate(rotation.value),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.app_settings_check_updates_action),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Suppress("FunctionNaming")
@Composable
internal fun AppLanguageOptionsList(
    selected: AppLanguage,
    onSelect: (AppLanguage) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        AppLanguage.entries.forEach { language ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .selectable(
                        selected = language == selected,
                        role = Role.RadioButton,
                        onClick = { onSelect(language) },
                    )
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = language == selected,
                    onClick = null,
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = appLanguageLabel(language),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
internal fun appLanguageLabel(language: AppLanguage): String = stringResource(
    when (language) {
        AppLanguage.FollowSystem -> R.string.app_language_system
        AppLanguage.SimplifiedChinese -> R.string.app_language_simplified_chinese
        AppLanguage.TraditionalChinese -> R.string.app_language_traditional_chinese
        AppLanguage.English -> R.string.app_language_english
        AppLanguage.Japanese -> R.string.app_language_japanese
        AppLanguage.German -> R.string.app_language_german
        AppLanguage.French -> R.string.app_language_french
        AppLanguage.Russian -> R.string.app_language_russian
    },
)

@Composable
internal fun themeColorValue(mode: ThemeColorMode): String = when (mode) {
    ThemeColorMode.FollowSystem -> stringResource(R.string.app_settings_theme_color_system)
    ThemeColorMode.Custom -> stringResource(R.string.app_settings_theme_color_custom)
}

@Composable
internal fun darkModeLabel(mode: DarkThemeMode): String = stringResource(
    when (mode) {
        DarkThemeMode.FollowSystem -> R.string.app_settings_dark_mode_system
        DarkThemeMode.Light -> R.string.app_settings_dark_mode_light
        DarkThemeMode.Dark -> R.string.app_settings_dark_mode_dark
    },
)

@Composable
internal fun refreshIntervalLabel(minutes: Int): String {
    return when {
        minutes < 60 -> stringResource(R.string.app_settings_refresh_interval_minutes, minutes)
        minutes % 60 == 0 -> stringResource(R.string.app_settings_refresh_interval_hours, minutes / 60)
        else -> stringResource(R.string.app_settings_refresh_interval_minutes, minutes)
    }
}
