package com.kuyermqi.quotawidget.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.kuyermqi.quotawidget.R
import com.kuyermqi.quotawidget.domain.QuotaWindow
import com.kuyermqi.quotawidget.domain.UsageDisplayMode
import com.kuyermqi.quotawidget.domain.UsageProgressStyle
import com.kuyermqi.quotawidget.domain.UsageWindowKind
import com.kuyermqi.quotawidget.domain.availableUsageWindowKinds
import com.kuyermqi.quotawidget.domain.formatUsagePrimaryDisplay
import com.kuyermqi.quotawidget.domain.presentCodexOverviewWindowKinds
import com.kuyermqi.quotawidget.magpie.MagpieProvider

@Composable
fun ColumnScope.MagpieConfigContent(
    baseUrl: String,
    onBaseUrlChange: (String) -> Unit,
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    providers: List<MagpieProvider>,
    selectedProviderId: String,
    onProviderSelected: (String) -> Unit,
    windows: List<QuotaWindow>,
    widgetWindowKind: UsageWindowKind,
    onWidgetWindowKindChange: (UsageWindowKind) -> Unit,
    usageDisplayMode: UsageDisplayMode,
    onUsageDisplayModeChange: (UsageDisplayMode) -> Unit,
    usageProgressStyle: UsageProgressStyle,
    onUsageProgressStyleChange: (UsageProgressStyle) -> Unit,
    isSaving: Boolean,
    isDirty: Boolean,
    saveError: String?,
    onSave: () -> Unit,
) {
    OutlinedTextField(
        value = baseUrl,
        onValueChange = onBaseUrlChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.magpie_base_url_label)) },
        placeholder = { Text(stringResource(R.string.magpie_base_url_hint)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        enabled = !isSaving,
    )
    Spacer(modifier = Modifier.height(12.dp))
    OutlinedTextField(
        value = apiKey,
        onValueChange = onApiKeyChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.magpie_api_key_label)) },
        supportingText = { Text(stringResource(R.string.magpie_api_key_hint)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        enabled = !isSaving,
    )
    if (providers.size >= 2) {
        Spacer(modifier = Modifier.height(16.dp))
        MagpieProviderPicker(
            providers = providers,
            selectedProviderId = selectedProviderId,
            usageDisplayMode = usageDisplayMode,
            enabled = !isSaving,
            onProviderSelected = onProviderSelected,
        )
    }
    Spacer(modifier = Modifier.height(16.dp))
    val windowKindChoices = availableUsageWindowKinds(windows).ifEmpty {
        listOf(UsageWindowKind.ROLLING, UsageWindowKind.WEEKLY, UsageWindowKind.MONTHLY)
    }
    UsageDisplayPrefsSection(
        windows = windows,
        widgetWindowKind = widgetWindowKind,
        onWidgetWindowKindChange = onWidgetWindowKindChange,
        usageDisplayMode = usageDisplayMode,
        onUsageDisplayModeChange = onUsageDisplayModeChange,
        usageProgressStyle = usageProgressStyle,
        onUsageProgressStyleChange = onUsageProgressStyleChange,
        enabled = !isSaving,
        windowKindChoices = windowKindChoices,
        showWindowKindPicker = windowKindChoices.size >= 2,
        overviewKinds = presentCodexOverviewWindowKinds(windows),
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

/**
 * Material 3 exposed dropdown, matching the workspace picker in the OpenCode Go
 * card: one line for the chosen provider, the provider's usage underneath.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MagpieProviderPicker(
    providers: List<MagpieProvider>,
    selectedProviderId: String,
    usageDisplayMode: UsageDisplayMode,
    enabled: Boolean,
    onProviderSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = providers.find { it.id == selectedProviderId } ?: providers.first()

    Text(
        text = stringResource(R.string.magpie_provider_label),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(6.dp))
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
    ) {
        OutlinedTextField(
            value = providerLabelOf(selected),
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
            singleLine = true,
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            providers.forEach { provider ->
                val isSelected = provider.id == selected.id
                val selectedColor = MaterialTheme.colorScheme.primary
                val selectedIcon: (@Composable () -> Unit)? = if (isSelected) {
                    {
                        Icon(
                            painter = painterResource(R.drawable.ic_check),
                            contentDescription = null,
                            tint = selectedColor,
                        )
                    }
                } else {
                    null
                }
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                text = providerLabelOf(provider),
                                color = if (isSelected) selectedColor else Color.Unspecified,
                            )
                            Text(
                                text = formatUsagePrimaryDisplay(provider.windows, usageDisplayMode),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    onClick = {
                        expanded = false
                        if (!isSelected) onProviderSelected(provider.id)
                    },
                    trailingIcon = selectedIcon,
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

@Composable
private fun providerLabelOf(provider: MagpieProvider): String =
    if (provider.accountCount > 1) {
        stringResource(R.string.magpie_provider_accounts, provider.displayName, provider.accountCount)
    } else {
        provider.displayName
    }
