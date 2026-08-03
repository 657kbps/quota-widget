package com.kuyermqi.quotawidget.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kuyermqi.quotawidget.R
import com.kuyermqi.quotawidget.domain.OpenCodeUsageDisplayMode
import com.kuyermqi.quotawidget.domain.OpenCodeWidgetWindowKind
import com.kuyermqi.quotawidget.domain.QuotaWindow
import com.kuyermqi.quotawidget.domain.QuotaWindowKind
import com.kuyermqi.quotawidget.domain.displayUsageFillFraction
import com.kuyermqi.quotawidget.domain.formatOpenCodeUsagePercent
import com.kuyermqi.quotawidget.domain.isUsageNearLimitForDisplay
import com.kuyermqi.quotawidget.domain.opencode.openCodeWindowLabelRes
import com.kuyermqi.quotawidget.opencode.OpenCodeWorkspace
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColumnScope.OpenCodeGoConfigContent(
    isLoggedIn: Boolean,
    isBusy: Boolean,
    isDirty: Boolean,
    errorMessage: String?,
    windows: List<QuotaWindow>,
    workspaces: List<OpenCodeWorkspace>,
    selectedWorkspaceId: String,
    selectedWorkspaceName: String,
    isLoadingWorkspaces: Boolean,
    workspacesError: String?,
    widgetWindowKind: OpenCodeWidgetWindowKind,
    onWidgetWindowKindChange: (OpenCodeWidgetWindowKind) -> Unit,
    usageDisplayMode: OpenCodeUsageDisplayMode,
    onUsageDisplayModeChange: (OpenCodeUsageDisplayMode) -> Unit,
    onWorkspaceSelected: (OpenCodeWorkspace) -> Unit,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onSave: () -> Unit,
) {
    Spacer(modifier = Modifier.height(if (isLoggedIn) 4.dp else 8.dp))
    if (!isLoggedIn) {
        Text(
            text = stringResource(R.string.opencode_status_logged_out),
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
        WorkspacePicker(
            workspaces = workspaces,
            selectedWorkspaceId = selectedWorkspaceId,
            selectedWorkspaceName = selectedWorkspaceName,
            isLoading = isLoadingWorkspaces,
            errorMessage = workspacesError,
            enabled = !isBusy,
            onWorkspaceSelected = onWorkspaceSelected,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.opencode_widget_window_label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WindowChip(
                selected = widgetWindowKind == OpenCodeWidgetWindowKind.ROLLING,
                label = stringResource(R.string.opencode_window_rolling),
                enabled = !isBusy,
                onClick = { onWidgetWindowKindChange(OpenCodeWidgetWindowKind.ROLLING) },
            )
            WindowChip(
                selected = widgetWindowKind == OpenCodeWidgetWindowKind.WEEKLY,
                label = stringResource(R.string.opencode_window_weekly),
                enabled = !isBusy,
                onClick = { onWidgetWindowKindChange(OpenCodeWidgetWindowKind.WEEKLY) },
            )
            WindowChip(
                selected = widgetWindowKind == OpenCodeWidgetWindowKind.MONTHLY,
                label = stringResource(R.string.opencode_window_monthly),
                enabled = !isBusy,
                onClick = { onWidgetWindowKindChange(OpenCodeWidgetWindowKind.MONTHLY) },
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.opencode_usage_display_label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WindowChip(
                selected = usageDisplayMode == OpenCodeUsageDisplayMode.USED,
                label = stringResource(R.string.opencode_usage_display_used),
                enabled = !isBusy,
                onClick = { onUsageDisplayModeChange(OpenCodeUsageDisplayMode.USED) },
            )
            WindowChip(
                selected = usageDisplayMode == OpenCodeUsageDisplayMode.REMAINING,
                label = stringResource(R.string.opencode_usage_display_remaining),
                enabled = !isBusy,
                onClick = { onUsageDisplayModeChange(OpenCodeUsageDisplayMode.REMAINING) },
            )
        }
        if (windows.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    // Distinct inset from PlatformConfigItem's surfaceContainer.
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                ) {
                    UsageBar(
                        label = stringResource(
                            openCodeWindowLabelRes(
                                QuotaWindowKind.FIVE_HOUR,
                                usageDisplayMode,
                            ),
                        ),
                        window = windows.find { it.kind == QuotaWindowKind.FIVE_HOUR },
                        usageDisplayMode = usageDisplayMode,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    UsageBar(
                        label = stringResource(
                            openCodeWindowLabelRes(
                                QuotaWindowKind.WEEKLY,
                                usageDisplayMode,
                            ),
                        ),
                        window = windows.find { it.kind == QuotaWindowKind.WEEKLY },
                        usageDisplayMode = usageDisplayMode,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    UsageBar(
                        label = stringResource(
                            openCodeWindowLabelRes(
                                QuotaWindowKind.MONTHLY,
                                usageDisplayMode,
                            ),
                        ),
                        window = windows.find { it.kind == QuotaWindowKind.MONTHLY },
                        usageDisplayMode = usageDisplayMode,
                    )
                }
            }
        }
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
                Text(stringResource(R.string.opencode_logout))
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
                Text(stringResource(R.string.opencode_login))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkspacePicker(
    workspaces: List<OpenCodeWorkspace>,
    selectedWorkspaceId: String,
    selectedWorkspaceName: String,
    isLoading: Boolean,
    errorMessage: String?,
    enabled: Boolean,
    onWorkspaceSelected: (OpenCodeWorkspace) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = workspaces
        .find { it.id == selectedWorkspaceId }
        ?.displayName
        ?: selectedWorkspaceName.ifBlank { selectedWorkspaceId }

    Text(
        text = stringResource(R.string.opencode_workspace_label),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(6.dp))
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled && !isLoading) expanded = it },
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            enabled = enabled && !isLoading,
            trailingIcon = {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                }
            },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
            singleLine = true,
        )
        ExposedDropdownMenu(
            expanded = expanded && workspaces.isNotEmpty(),
            onDismissRequest = { expanded = false },
        ) {
            workspaces.forEach { workspace ->
                DropdownMenuItem(
                    text = { Text(workspace.displayName) },
                    onClick = {
                        expanded = false
                        if (workspace.id != selectedWorkspaceId) {
                            onWorkspaceSelected(workspace)
                        }
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
    if (!errorMessage.isNullOrBlank()) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = errorMessage,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun WindowChip(
    selected: Boolean,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        label = { Text(label) },
    )
}

@Composable
private fun UsageBar(
    label: String,
    window: QuotaWindow?,
    usageDisplayMode: OpenCodeUsageDisplayMode,
) {
    val used = window?.usedPercent ?: 0.0
    val progress = displayUsageFillFraction(used, usageDisplayMode)
    val scheme = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurface,
            )
            Text(
                text = formatOpenCodeUsagePercent(used, usageDisplayMode),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurface,
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
            color = if (isUsageNearLimitForDisplay(used, usageDisplayMode)) {
                scheme.error
            } else {
                scheme.primary
            },
            trackColor = scheme.onSurface.copy(alpha = 0.18f),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = formatResetLabel(window?.resetInSec),
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurface.copy(alpha = 0.72f),
        )
    }
}

@Composable
private fun formatResetLabel(resetInSec: Long?): String {
    if (resetInSec == null || resetInSec < 0) {
        return stringResource(R.string.opencode_usage_resets_unknown)
    }
    val totalMinutes = (resetInSec / 60.0).roundToInt().coerceAtLeast(0)
    val days = totalMinutes / (60 * 24)
    val hours = (totalMinutes % (60 * 24)) / 60
    val minutes = totalMinutes % 60
    val text = when {
        days > 0 -> "$days 天 $hours 小时"
        hours > 0 -> "$hours 小时 $minutes 分"
        else -> "$minutes 分钟"
    }
    return stringResource(R.string.opencode_usage_resets_in, text)
}
