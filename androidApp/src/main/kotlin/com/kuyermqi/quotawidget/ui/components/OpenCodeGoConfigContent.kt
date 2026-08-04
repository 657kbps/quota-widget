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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
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
import com.kuyermqi.quotawidget.domain.QuotaWindow
import com.kuyermqi.quotawidget.domain.QuotaWindowKind
import com.kuyermqi.quotawidget.domain.UsageDisplayMode
import com.kuyermqi.quotawidget.domain.UsageWindowKind
import com.kuyermqi.quotawidget.opencode.OpenCodeWorkspace

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
    widgetWindowKind: UsageWindowKind,
    onWidgetWindowKindChange: (UsageWindowKind) -> Unit,
    usageDisplayMode: UsageDisplayMode,
    onUsageDisplayModeChange: (UsageDisplayMode) -> Unit,
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
        UsageDisplayPrefsSection(
            windows = windows,
            widgetWindowKind = widgetWindowKind,
            onWidgetWindowKindChange = onWidgetWindowKindChange,
            usageDisplayMode = usageDisplayMode,
            onUsageDisplayModeChange = onUsageDisplayModeChange,
            enabled = !isBusy,
            windowKindChoices = listOf(
                UsageWindowKind.ROLLING,
                UsageWindowKind.WEEKLY,
                UsageWindowKind.MONTHLY,
            ),
            showWindowKindPicker = true,
            overviewKinds = listOf(
                QuotaWindowKind.FIVE_HOUR,
                QuotaWindowKind.WEEKLY,
                QuotaWindowKind.MONTHLY,
            ),
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
