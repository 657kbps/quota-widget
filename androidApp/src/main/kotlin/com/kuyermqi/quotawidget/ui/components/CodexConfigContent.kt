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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kuyermqi.quotawidget.R
import com.kuyermqi.quotawidget.domain.QuotaWindow
import com.kuyermqi.quotawidget.domain.UsageDisplayMode
import com.kuyermqi.quotawidget.domain.UsageProgressStyle
import com.kuyermqi.quotawidget.domain.UsageResetStyle
import com.kuyermqi.quotawidget.domain.UsageWindowKind
import com.kuyermqi.quotawidget.domain.availableUsageWindowKinds
import com.kuyermqi.quotawidget.domain.presentCodexOverviewWindowKinds
import com.kuyermqi.quotawidget.platform.PlatformIds
import com.kuyermqi.quotawidget.ui.platformDisplayName

@Composable
fun ColumnScope.CodexConfigContent(
    isLoggedIn: Boolean,
    isBusy: Boolean,
    isSaving: Boolean,
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
    usageResetStyle: UsageResetStyle,
    onUsageResetStyleChange: (UsageResetStyle) -> Unit,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onSave: () -> Unit,
    updatedAtEpochMs: Long = 0L,
) {
    if (!isLoggedIn) {
        AuthAccountCard {
            AuthStatusLine(
                status = stringResource(R.string.codex_status_logged_out),
                error = errorMessage,
            )
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
                Button(
                    onClick = onLogin,
                    enabled = !isBusy,
                ) {
                    Text(stringResource(R.string.codex_login))
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        val availableWindowKinds = availableUsageWindowKinds(windows)
        UsageDisplayPrefsSection(
            windows = windows,
            widgetWindowKind = widgetWindowKind,
            onWidgetWindowKindChange = onWidgetWindowKindChange,
            usageDisplayMode = usageDisplayMode,
            onUsageDisplayModeChange = onUsageDisplayModeChange,
            usageProgressStyle = usageProgressStyle,
            onUsageProgressStyleChange = onUsageProgressStyleChange,
            usageResetStyle = usageResetStyle,
            onUsageResetStyleChange = onUsageResetStyleChange,
            enabled = !isBusy,
            windowKindChoices = availableWindowKinds,
            showWindowKindPicker = availableWindowKinds.size >= 2,
            overviewKinds = presentCodexOverviewWindowKinds(windows),
            platformName = platformDisplayName(PlatformIds.CODEX),
            updatedAtEpochMs = updatedAtEpochMs,
        )
        SaveAction(
            label = stringResource(R.string.action_save),
            enabled = isDirty,
            busy = isSaving,
            onClick = onSave,
            topSpacing = 12.dp,
        )
        return
    }

    val accountLine = buildString {
        if (email.isNotBlank()) append(email)
        if (planType.isNotBlank()) {
            if (isNotEmpty()) append(" · ")
            append(planType)
        }
    }
    val availableWindowKinds = availableUsageWindowKinds(windows)
    AuthConfiguredPanel(
        title = accountLine,
        extraContent = {
            if (!errorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        windows = windows,
        widgetWindowKind = widgetWindowKind,
        onWidgetWindowKindChange = onWidgetWindowKindChange,
        usageDisplayMode = usageDisplayMode,
        onUsageDisplayModeChange = onUsageDisplayModeChange,
        usageProgressStyle = usageProgressStyle,
        onUsageProgressStyleChange = onUsageProgressStyleChange,
        usageResetStyle = usageResetStyle,
        onUsageResetStyleChange = onUsageResetStyleChange,
        windowKindChoices = availableWindowKinds,
        showWindowKindPicker = availableWindowKinds.size >= 2,
        overviewKinds = presentCodexOverviewWindowKinds(windows),
        isBusy = isBusy,
        isDirty = isDirty,
        saveBusy = isSaving,
        logoutLabel = stringResource(R.string.codex_logout),
        saveLabel = stringResource(R.string.action_save),
        onLogout = onLogout,
        onSave = onSave,
        platformName = platformDisplayName(PlatformIds.CODEX),
        updatedAtEpochMs = updatedAtEpochMs,
    )
}
