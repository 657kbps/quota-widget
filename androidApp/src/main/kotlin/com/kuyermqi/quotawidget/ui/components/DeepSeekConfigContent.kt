package com.kuyermqi.quotawidget.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.kuyermqi.quotawidget.R
import com.kuyermqi.quotawidget.domain.CurrencyPreference

@Composable
fun ColumnScope.DeepSeekConfigContent(
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    currency: CurrencyPreference,
    onCurrencyChange: (CurrencyPreference) -> Unit,
    balance: String,
    updatedAtEpochMs: Long,
    isDirty: Boolean,
    isSaving: Boolean,
    saveError: String?,
    onSave: () -> Unit,
) {
    AuthAccountCard {
        OutlinedTextField(
            value = apiKey,
            onValueChange = onApiKeyChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.api_key_label)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            enabled = !isSaving,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.deepseek_currency_label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(6.dp))
        SegmentedOptionRow(
            options = CurrencyPreference.entries,
            selectedOption = currency,
            labelOf = { it.name },
            onOptionSelected = onCurrencyChange,
            enabled = !isSaving,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.deepseek_currency_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(modifier = Modifier.height(12.dp))
    DeepSeekWidgetPreviewCard(
        balance = balance,
        currency = currency,
        updatedAtEpochMs = updatedAtEpochMs,
    )
    if (!saveError.isNullOrBlank()) {
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = saveError,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
    SaveAction(
        label = stringResource(R.string.action_save),
        enabled = isDirty,
        busy = isSaving,
        onClick = onSave,
        topSpacing = 12.dp,
    )
}
