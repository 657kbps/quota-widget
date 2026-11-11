package com.kuyermqi.quotawidget.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.kuyermqi.quotawidget.R
import com.kuyermqi.quotawidget.domain.QuotaWindow
import com.kuyermqi.quotawidget.domain.ServiceRegion
import com.kuyermqi.quotawidget.domain.UsageDisplayMode
import com.kuyermqi.quotawidget.domain.UsageProgressStyle
import com.kuyermqi.quotawidget.domain.UsageWindowKind
import com.kuyermqi.quotawidget.domain.availableUsageWindowKinds
import com.kuyermqi.quotawidget.domain.presentUsageOverviewWindowKinds

/**
 * API-key based usage platform editor (Kimi Code, Zhipu Coding Plan):
 * key, domestic/overseas site, widget window and display prefs.
 */
@Composable
fun ColumnScope.ApiKeyUsageConfigContent(
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    apiKeyHint: String,
    region: ServiceRegion,
    onRegionChange: (ServiceRegion) -> Unit,
    windows: List<QuotaWindow>,
    widgetWindowKind: UsageWindowKind,
    onWidgetWindowKindChange: (UsageWindowKind) -> Unit,
    usageDisplayMode: UsageDisplayMode,
    onUsageDisplayModeChange: (UsageDisplayMode) -> Unit,
    usageProgressStyle: UsageProgressStyle,
    onUsageProgressStyleChange: (UsageProgressStyle) -> Unit,
    regionHint: String,
    isDirty: Boolean,
    isSaving: Boolean,
    saveError: String?,
    onSave: () -> Unit,
) {
    OutlinedTextField(
        value = apiKey,
        onValueChange = onApiKeyChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.api_key_label)) },
        supportingText = { Text(apiKeyHint) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        enabled = !isSaving,
    )
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = stringResource(R.string.service_region_label),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ServiceRegion.entries.forEach { option ->
            FilterChip(
                selected = region == option,
                onClick = { onRegionChange(option) },
                enabled = !isSaving,
                label = { Text(stringResource(serviceRegionLabelRes(option))) },
            )
        }
    }
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = regionHint,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(16.dp))

    val availableWindowKinds = availableUsageWindowKinds(windows)
    UsageDisplayPrefsSection(
        windows = windows,
        widgetWindowKind = widgetWindowKind,
        onWidgetWindowKindChange = onWidgetWindowKindChange,
        usageDisplayMode = usageDisplayMode,
        onUsageDisplayModeChange = onUsageDisplayModeChange,
        usageProgressStyle = usageProgressStyle,
        onUsageProgressStyleChange = onUsageProgressStyleChange,
        enabled = !isSaving,
        windowKindChoices = availableWindowKinds,
        showWindowKindPicker = availableWindowKinds.size >= 2,
        overviewKinds = presentUsageOverviewWindowKinds(windows),
    )
    if (!saveError.isNullOrBlank()) {
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = saveError,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
    Spacer(modifier = Modifier.height(12.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isSaving) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
            )
            Spacer(modifier = Modifier.width(12.dp))
        }
        Button(
            onClick = onSave,
            enabled = isDirty && !isSaving,
        ) {
            Text(stringResource(R.string.action_save))
        }
    }
}

private fun serviceRegionLabelRes(region: ServiceRegion): Int = when (region) {
    ServiceRegion.CN -> R.string.service_region_cn
    ServiceRegion.GLOBAL -> R.string.service_region_global
}
