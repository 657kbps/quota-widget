package com.kuyermqi.quotawidget.ui

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kuyermqi.quotawidget.R
import com.kuyermqi.quotawidget.domain.ALLOWED_REFRESH_INTERVAL_MINUTES
import com.kuyermqi.quotawidget.domain.AppSettings
import com.kuyermqi.quotawidget.domain.DarkThemeMode
import com.kuyermqi.quotawidget.domain.ThemeColorMode
import com.kuyermqi.quotawidget.settings.PlatformSettingsRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AppSettingsScreen(
    settingsRepository: PlatformSettingsRepository,
    onBack: () -> Unit,
    onRefreshIntervalChanged: (Int) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val appSettings by settingsRepository.observeAppSettings()
        .collectAsStateWithLifecycle(initialValue = AppSettings())
    var showColorPicker by remember { mutableStateOf(false) }

    if (showColorPicker) {
        HsvColorPickerDialog(
            initialColorArgb = appSettings.customSeedColorArgb,
            onDismiss = { showColorPicker = false },
            onConfirm = { argb ->
                showColorPicker = false
                scope.launch {
                    settingsRepository.saveAppSettings(
                        appSettings.copy(
                            themeColorMode = ThemeColorMode.Custom,
                            customSeedColorArgb = argb,
                        ),
                    )
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_expand_more),
                            contentDescription = stringResource(R.string.app_settings_back),
                            modifier = Modifier
                                .size(24.dp)
                                .rotate(90f),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .imePadding(),
            contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item(key = "dark_mode") {
                SettingsSection(title = stringResource(R.string.app_settings_dark_mode)) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = appSettings.darkThemeMode == DarkThemeMode.Dark,
                            onClick = {
                                scope.launch {
                                    settingsRepository.saveAppSettings(
                                        appSettings.copy(darkThemeMode = DarkThemeMode.Dark),
                                    )
                                }
                            },
                            label = { Text(stringResource(R.string.app_settings_dark_mode_on)) },
                        )
                        FilterChip(
                            selected = appSettings.darkThemeMode == DarkThemeMode.Light,
                            onClick = {
                                scope.launch {
                                    settingsRepository.saveAppSettings(
                                        appSettings.copy(darkThemeMode = DarkThemeMode.Light),
                                    )
                                }
                            },
                            label = { Text(stringResource(R.string.app_settings_dark_mode_off)) },
                        )
                        FilterChip(
                            selected = appSettings.darkThemeMode == DarkThemeMode.FollowSystem,
                            onClick = {
                                scope.launch {
                                    settingsRepository.saveAppSettings(
                                        appSettings.copy(darkThemeMode = DarkThemeMode.FollowSystem),
                                    )
                                }
                            },
                            label = { Text(stringResource(R.string.app_settings_dark_mode_system)) },
                        )
                    }
                }
            }

            item(key = "theme_color") {
                SettingsSection(title = stringResource(R.string.app_settings_theme_color)) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = appSettings.themeColorMode == ThemeColorMode.FollowSystem,
                            onClick = {
                                scope.launch {
                                    settingsRepository.saveAppSettings(
                                        appSettings.copy(themeColorMode = ThemeColorMode.FollowSystem),
                                    )
                                }
                            },
                            label = { Text(stringResource(R.string.app_settings_theme_color_system)) },
                        )
                        FilterChip(
                            selected = appSettings.themeColorMode == ThemeColorMode.Custom,
                            onClick = {
                                scope.launch {
                                    settingsRepository.saveAppSettings(
                                        appSettings.copy(themeColorMode = ThemeColorMode.Custom),
                                    )
                                }
                            },
                            label = { Text(stringResource(R.string.app_settings_theme_color_custom)) },
                        )
                    }
                    if (appSettings.themeColorMode == ThemeColorMode.Custom) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.app_settings_theme_color_preview),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(appSettings.customSeedColorArgb))
                                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                    .pointerInput(Unit) {
                                        detectTapGestures { showColorPicker = true }
                                    },
                            )
                        }
                    }
                }
            }

            item(key = "refresh_interval") {
                SettingsSection(title = stringResource(R.string.app_settings_refresh_interval)) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ALLOWED_REFRESH_INTERVAL_MINUTES.forEach { minutes ->
                            FilterChip(
                                selected = appSettings.refreshIntervalMinutes == minutes,
                                onClick = {
                                    scope.launch {
                                        settingsRepository.saveAppSettings(
                                            appSettings.copy(refreshIntervalMinutes = minutes),
                                        )
                                        onRefreshIntervalChanged(minutes)
                                    }
                                },
                                label = { Text(refreshIntervalLabel(minutes)) },
                            )
                        }
                    }
                }
            }

            item(key = "check_updates_on_launch") {
                SettingsSection(title = stringResource(R.string.app_settings_check_updates_on_launch)) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = appSettings.checkForUpdatesOnLaunch,
                            onClick = {
                                scope.launch {
                                    settingsRepository.saveAppSettings(
                                        appSettings.copy(checkForUpdatesOnLaunch = true),
                                    )
                                }
                            },
                            label = { Text(stringResource(R.string.app_settings_check_updates_on)) },
                        )
                        FilterChip(
                            selected = !appSettings.checkForUpdatesOnLaunch,
                            onClick = {
                                scope.launch {
                                    settingsRepository.saveAppSettings(
                                        appSettings.copy(checkForUpdatesOnLaunch = false),
                                    )
                                }
                            },
                            label = { Text(stringResource(R.string.app_settings_check_updates_off)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun refreshIntervalLabel(minutes: Int): String {
    return when {
        minutes < 60 -> stringResource(R.string.app_settings_refresh_interval_minutes, minutes)
        minutes % 60 == 0 -> stringResource(R.string.app_settings_refresh_interval_hours, minutes / 60)
        else -> stringResource(R.string.app_settings_refresh_interval_minutes, minutes)
    }
}

@Composable
private fun HsvColorPickerDialog(
    initialColorArgb: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val initialHsv = remember(initialColorArgb) {
        FloatArray(3).also { AndroidColor.colorToHSV(initialColorArgb, it) }
    }
    var hue by remember(initialColorArgb) { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember(initialColorArgb) { mutableFloatStateOf(initialHsv[1]) }
    var value by remember(initialColorArgb) { mutableFloatStateOf(initialHsv[2]) }

    val previewColor = remember(hue, saturation, value) {
        Color(AndroidColor.HSVToColor(floatArrayOf(hue, saturation, value)))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.app_settings_color_picker_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .pointerInput(hue) {
                            detectTapGestures { offset ->
                                updateSaturationValue(offset, size.width.toFloat(), size.height.toFloat()) { s, v ->
                                    saturation = s
                                    value = v
                                }
                            }
                        }
                        .pointerInput(hue) {
                            detectDragGestures { change, _ ->
                                change.consume()
                                updateSaturationValue(
                                    change.position,
                                    size.width.toFloat(),
                                    size.height.toFloat(),
                                ) { s, v ->
                                    saturation = s
                                    value = v
                                }
                            }
                        },
                ) {
                    Canvas(modifier = Modifier.matchParentSize()) {
                        val pureHue = Color(AndroidColor.HSVToColor(floatArrayOf(hue, 1f, 1f)))
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(Color.White, pureHue),
                            ),
                        )
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black),
                            ),
                        )
                        val markerX = saturation * size.width
                        val markerY = (1f - value) * size.height
                        drawCircle(
                            color = Color.White,
                            radius = 8.dp.toPx(),
                            center = Offset(markerX, markerY),
                        )
                        drawCircle(
                            color = Color.Black,
                            radius = 6.dp.toPx(),
                            center = Offset(markerX, markerY),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.app_settings_color_picker_hue),
                    style = MaterialTheme.typography.labelLarge,
                )
                Slider(
                    value = hue,
                    onValueChange = { hue = it },
                    valueRange = 0f..360f,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(previewColor)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(AndroidColor.HSVToColor(floatArrayOf(hue, saturation, value))) },
            ) {
                Text(stringResource(R.string.app_settings_color_picker_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.app_settings_color_picker_cancel))
            }
        },
    )
}

private fun updateSaturationValue(
    offset: Offset,
    width: Float,
    height: Float,
    onUpdate: (saturation: Float, value: Float) -> Unit,
) {
    if (width <= 0f || height <= 0f) return
    val s = (offset.x / width).coerceIn(0f, 1f)
    val v = (1f - offset.y / height).coerceIn(0f, 1f)
    onUpdate(s, v)
}
