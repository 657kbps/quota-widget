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
import com.kuyermqi.quotawidget.domain.QuotaWindowKind
import com.kuyermqi.quotawidget.domain.UsageDisplayMode
import com.kuyermqi.quotawidget.domain.UsageProgressStyle
import com.kuyermqi.quotawidget.domain.UsageWindowKind

@Composable
fun ColumnScope.NewApiConfigContent(
    baseUrl: String,
    onBaseUrlChange: (String) -> Unit,
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    quotaPerUsd: String,
    onQuotaPerUsdChange: (String) -> Unit,
    windows: List<QuotaWindow>,
    unlimitedQuota: Boolean,
    emptyLimitedQuota: Boolean,
    tokenExpired: Boolean,
    quotaOverspent: Boolean,
    usageDisplayMode: UsageDisplayMode,
    onUsageDisplayModeChange: (UsageDisplayMode) -> Unit,
    usageProgressStyle: UsageProgressStyle,
    onUsageProgressStyleChange: (UsageProgressStyle) -> Unit,
    isDirty: Boolean,
    isSaving: Boolean,
    saveError: String?,
    onSave: () -> Unit,
) {
    OutlinedTextField(
        value = baseUrl,
        onValueChange = onBaseUrlChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.new_api_base_url_label)) },
        placeholder = { Text(stringResource(R.string.new_api_base_url_hint)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        enabled = !isSaving,
    )
    Spacer(modifier = Modifier.height(12.dp))
    OutlinedTextField(
        value = apiKey,
        onValueChange = onApiKeyChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.new_api_api_key_label)) },
        supportingText = { Text(stringResource(R.string.new_api_usage_display_hint)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        enabled = !isSaving,
    )
    Spacer(modifier = Modifier.height(12.dp))
    OutlinedTextField(
        value = quotaPerUsd,
        onValueChange = onQuotaPerUsdChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.new_api_quota_per_usd_label)) },
        supportingText = { Text(stringResource(R.string.new_api_quota_per_usd_hint)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        enabled = !isSaving,
    )
    Spacer(modifier = Modifier.height(16.dp))
    UsageDisplayPrefsSection(
        windows = windows,
        widgetWindowKind = UsageWindowKind.TOKEN,
        onWidgetWindowKindChange = {},
        usageDisplayMode = usageDisplayMode,
        onUsageDisplayModeChange = onUsageDisplayModeChange,
        usageProgressStyle = usageProgressStyle,
        onUsageProgressStyleChange = onUsageProgressStyleChange,
        enabled = !isSaving,
        windowKindChoices = emptyList(),
        showWindowKindPicker = false,
        overviewKinds = if (unlimitedQuota) emptyList() else listOf(QuotaWindowKind.TOKEN),
        showResetLabels = false,
        showProgressStyle = !unlimitedQuota,
        forceZeroProgressPreview = emptyLimitedQuota,
    )
    if (tokenExpired) {
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.new_api_token_expired),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
    if (quotaOverspent) {
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.new_api_overspent_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
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
            Text(stringResource(R.string.new_api_save))
        }
    }
}
