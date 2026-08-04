package com.kuyermqi.quotawidget.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kuyermqi.quotawidget.R
import com.kuyermqi.quotawidget.domain.QuotaWindow
import com.kuyermqi.quotawidget.domain.UsageDisplayMode
import com.kuyermqi.quotawidget.domain.UsageProgressStyle
import com.kuyermqi.quotawidget.domain.UsageWindowKind
import com.kuyermqi.quotawidget.domain.availableUsageWindowKinds
import com.kuyermqi.quotawidget.domain.presentCodexOverviewWindowKinds

@Composable
fun ColumnScope.CodexConfigContent(
    isLoggedIn: Boolean,
    isBusy: Boolean,
    isDirty: Boolean,
    errorMessage: String?,
    email: String,
    planType: String,
    windows: List<QuotaWindow>,
    widgetWindowKind: UsageWindowKind,
    onWidgetWindowKindChange: (UsageWindowKind) -> Unit,
    usageDisplayMode: UsageDisplayMode,
    onUsageDisplayModeChange: (UsageDisplayMode) -> Unit,
    usageProgressStyle: UsageProgressStyle,
    onUsageProgressStyleChange: (UsageProgressStyle) -> Unit,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onSave: () -> Unit,
) {
    Spacer(modifier = Modifier.height(if (isLoggedIn) 4.dp else 8.dp))
    if (!isLoggedIn) {
        Text(
            text = stringResource(R.string.codex_status_logged_out),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
    if (!errorMessage.isNullOrBlank()) {
        if (!isLoggedIn) {
            Spacer(modifier = Modifier.height(8.dp))
        }
        Text(
            text = errorMessage,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
    if (isLoggedIn) {
        if (!errorMessage.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
        }
        val accountLine = buildString {
            if (email.isNotBlank()) append(email)
            if (planType.isNotBlank()) {
                if (isNotEmpty()) append(" · ")
                append(planType)
            }
        }
        if (accountLine.isNotBlank()) {
            Text(
                text = accountLine,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
        val availableWindowKinds = availableUsageWindowKinds(windows)
        UsageDisplayPrefsSection(
            windows = windows,
            widgetWindowKind = widgetWindowKind,
            onWidgetWindowKindChange = onWidgetWindowKindChange,
            usageDisplayMode = usageDisplayMode,
            onUsageDisplayModeChange = onUsageDisplayModeChange,
            usageProgressStyle = usageProgressStyle,
            onUsageProgressStyleChange = onUsageProgressStyleChange,
            enabled = !isBusy,
            windowKindChoices = availableWindowKinds,
            showWindowKindPicker = availableWindowKinds.size >= 2,
            overviewKinds = presentCodexOverviewWindowKinds(windows),
        )
    }
    Spacer(modifier = Modifier.height(12.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isBusy) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        if (isLoggedIn) {
            TextButton(
                onClick = onLogout,
                enabled = !isBusy,
            ) {
                Text(stringResource(R.string.codex_logout))
            }
            Button(
                onClick = onSave,
                enabled = isDirty && !isBusy,
            ) {
                Text(stringResource(R.string.action_save))
            }
        } else {
            Button(
                onClick = onLogin,
                enabled = !isBusy,
            ) {
                Text(stringResource(R.string.codex_login))
            }
        }
    }
}
