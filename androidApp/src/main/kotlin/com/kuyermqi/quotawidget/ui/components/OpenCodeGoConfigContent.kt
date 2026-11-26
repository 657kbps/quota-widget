package com.kuyermqi.quotawidget.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.ExposedDropdownMenu
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
import com.kuyermqi.quotawidget.domain.UsageProgressStyle
import com.kuyermqi.quotawidget.domain.UsageResetStyle
import com.kuyermqi.quotawidget.domain.UsageWindowKind
import com.kuyermqi.quotawidget.opencode.OpenCodeWorkspace
import com.kuyermqi.quotawidget.settings.OpenCodeAuthMethod

enum class OpenCodeAuthPanel {
    SUMMARY,
    CHOOSE,
    API_KEY,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColumnScope.OpenCodeGoConfigContent(
    authPanel: OpenCodeAuthPanel,
    activeAuthMethod: OpenCodeAuthMethod,
    isConfigured: Boolean,
    needsReauth: Boolean,
    hasCookieSession: Boolean,
    hasApiKey: Boolean,
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    isBusy: Boolean,
    isSaving: Boolean,
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
    usageProgressStyle: UsageProgressStyle,
    onUsageProgressStyleChange: (UsageProgressStyle) -> Unit,
    usageResetStyle: UsageResetStyle,
    onUsageResetStyleChange: (UsageResetStyle) -> Unit,
    onWorkspaceSelected: (OpenCodeWorkspace) -> Unit,
    onAuthPanelChange: (OpenCodeAuthPanel) -> Unit,
    onLogin: () -> Unit,
    onReauth: () -> Unit,
    onLogout: () -> Unit,
    onSave: (OpenCodeAuthMethod?) -> Unit,
    updatedAtEpochMs: Long = 0L,
) {
    BackHandler(
        enabled = authPanel == OpenCodeAuthPanel.API_KEY ||
            (authPanel == OpenCodeAuthPanel.CHOOSE && isConfigured),
    ) {
        onAuthPanelChange(
            if (authPanel == OpenCodeAuthPanel.API_KEY) {
                OpenCodeAuthPanel.CHOOSE
            } else {
                OpenCodeAuthPanel.SUMMARY
            },
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        val showTopError = !errorMessage.isNullOrBlank()
        if (showTopError) {
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        AuthPanelSwitcher(
            targetState = authPanel,
            label = "opencodeAuthPanel",
            isForward = { initial, target ->
                target == OpenCodeAuthPanel.API_KEY ||
                    (initial == OpenCodeAuthPanel.SUMMARY && target == OpenCodeAuthPanel.CHOOSE)
            },
        ) { panel ->
            when (panel) {
                OpenCodeAuthPanel.SUMMARY -> AuthSummary(
                    activeAuthMethod = activeAuthMethod,
                    needsReauth = needsReauth,
                    hasCookieSession = hasCookieSession,
                    isBusy = isBusy,
                    isSaving = isSaving,
                    isDirty = isDirty,
                    windows = windows,
                    workspaces = workspaces,
                    selectedWorkspaceId = selectedWorkspaceId,
                    selectedWorkspaceName = selectedWorkspaceName,
                    isLoadingWorkspaces = isLoadingWorkspaces,
                    workspacesError = workspacesError,
                    widgetWindowKind = widgetWindowKind,
                    onWidgetWindowKindChange = onWidgetWindowKindChange,
                    usageDisplayMode = usageDisplayMode,
                    onUsageDisplayModeChange = onUsageDisplayModeChange,
                    usageProgressStyle = usageProgressStyle,
                    onUsageProgressStyleChange = onUsageProgressStyleChange,
                    usageResetStyle = usageResetStyle,
                    onUsageResetStyleChange = onUsageResetStyleChange,
                    onWorkspaceSelected = onWorkspaceSelected,
                    onAuthPanelChange = onAuthPanelChange,
                    onReauth = onReauth,
                    onLogout = onLogout,
                    onSave = onSave,
                    updatedAtEpochMs = updatedAtEpochMs,
                )
                OpenCodeAuthPanel.CHOOSE -> {
                    AuthChoiceList(
                        hasCookieSession = hasCookieSession,
                        hasApiKey = hasApiKey,
                        canCancelEdit = isConfigured,
                        isBusy = isBusy,
                        onAuthPanelChange = onAuthPanelChange,
                        onLogin = onLogin,
                        windows = windows,
                        widgetWindowKind = widgetWindowKind,
                        onWidgetWindowKindChange = onWidgetWindowKindChange,
                        usageDisplayMode = usageDisplayMode,
                        onUsageDisplayModeChange = onUsageDisplayModeChange,
                        usageProgressStyle = usageProgressStyle,
                        onUsageProgressStyleChange = onUsageProgressStyleChange,
                        usageResetStyle = usageResetStyle,
                        onUsageResetStyleChange = onUsageResetStyleChange,
                        isSaving = isSaving,
                        isDirty = isDirty,
                        onSavePrefs = { onSave(null) },
                        updatedAtEpochMs = updatedAtEpochMs,
                    )
                }
                OpenCodeAuthPanel.API_KEY -> OpenCodeApiKeyAuthEditor(
                    apiKey = apiKey,
                    onApiKeyChange = onApiKeyChange,
                    hasApiKey = hasApiKey,
                    isBusy = isBusy,
                    onAuthPanelChange = onAuthPanelChange,
                    onSave = onSave,
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.AuthSummary(
    activeAuthMethod: OpenCodeAuthMethod,
    needsReauth: Boolean,
    hasCookieSession: Boolean,
    isBusy: Boolean,
    isSaving: Boolean,
    isDirty: Boolean,
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
    usageProgressStyle: UsageProgressStyle,
    onUsageProgressStyleChange: (UsageProgressStyle) -> Unit,
    usageResetStyle: UsageResetStyle,
    onUsageResetStyleChange: (UsageResetStyle) -> Unit,
    onWorkspaceSelected: (OpenCodeWorkspace) -> Unit,
    onAuthPanelChange: (OpenCodeAuthPanel) -> Unit,
    onReauth: () -> Unit,
    onLogout: () -> Unit,
    onSave: (OpenCodeAuthMethod?) -> Unit,
    updatedAtEpochMs: Long = 0L,
) {
    val methodLabel = when (activeAuthMethod) {
        OpenCodeAuthMethod.COOKIE -> stringResource(R.string.opencode_auth_web_title)
        OpenCodeAuthMethod.API_KEY -> stringResource(R.string.opencode_api_key_label)
    }
    AuthConfiguredPanel(
        title = stringResource(R.string.auth_current_method, methodLabel),
        changeLabel = stringResource(R.string.opencode_auth_change),
        onChangeAuthMethod = { onAuthPanelChange(OpenCodeAuthPanel.CHOOSE) },
        extraContent = {
            if (needsReauth) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.opencode_reauth_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = onReauth,
                        enabled = !isBusy,
                    ) {
                        Text(stringResource(R.string.opencode_reauth))
                    }
                }
            }
            if (activeAuthMethod == OpenCodeAuthMethod.COOKIE && hasCookieSession) {
                Spacer(modifier = Modifier.height(8.dp))
                WorkspacePicker(
                    workspaces = workspaces,
                    selectedWorkspaceId = selectedWorkspaceId,
                    selectedWorkspaceName = selectedWorkspaceName,
                    isLoading = isLoadingWorkspaces,
                    errorMessage = workspacesError,
                    enabled = !isBusy,
                    onWorkspaceSelected = onWorkspaceSelected,
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
        windowKindChoices = listOf(
            UsageWindowKind.ROLLING,
            UsageWindowKind.WEEKLY,
            UsageWindowKind.MONTHLY,
        ),
        overviewKinds = listOf(
            QuotaWindowKind.FIVE_HOUR,
            QuotaWindowKind.WEEKLY,
            QuotaWindowKind.MONTHLY,
        ),
        isBusy = isBusy,
        isDirty = isDirty,
        saveBusy = isSaving,
        logoutLabel = stringResource(R.string.opencode_logout),
        saveLabel = stringResource(R.string.action_save),
        onLogout = onLogout,
        onSave = { onSave(null) },
        platformName = stringResource(R.string.platform_name_opencode_go),
        allowWindowFallback = false,
        updatedAtEpochMs = updatedAtEpochMs,
    )
}

@Composable
private fun ColumnScope.AuthChoiceList(
    hasCookieSession: Boolean,
    hasApiKey: Boolean,
    canCancelEdit: Boolean,
    isBusy: Boolean,
    onAuthPanelChange: (OpenCodeAuthPanel) -> Unit,
    onLogin: () -> Unit,
    windows: List<QuotaWindow>,
    widgetWindowKind: UsageWindowKind,
    onWidgetWindowKindChange: (UsageWindowKind) -> Unit,
    usageDisplayMode: UsageDisplayMode,
    onUsageDisplayModeChange: (UsageDisplayMode) -> Unit,
    usageProgressStyle: UsageProgressStyle,
    onUsageProgressStyleChange: (UsageProgressStyle) -> Unit,
    usageResetStyle: UsageResetStyle,
    onUsageResetStyleChange: (UsageResetStyle) -> Unit,
    isSaving: Boolean,
    isDirty: Boolean,
    onSavePrefs: () -> Unit,
    updatedAtEpochMs: Long,
) {
    AuthMethodChoiceList(
        options = listOf(
            AuthMethodOption(
                title = stringResource(R.string.opencode_auth_web_title),
                subtitle = stringResource(R.string.opencode_auth_web_hint),
                configured = hasCookieSession,
                onClick = onLogin,
            ),
            AuthMethodOption(
                title = stringResource(R.string.opencode_auth_api_title),
                subtitle = stringResource(R.string.opencode_api_key_hint),
                configured = hasApiKey,
                onClick = { onAuthPanelChange(OpenCodeAuthPanel.API_KEY) },
            ),
        ),
        canCancelEdit = canCancelEdit,
        isBusy = isBusy,
        backLabel = stringResource(R.string.opencode_auth_back),
        configuredLabel = stringResource(R.string.opencode_auth_configured),
        onBack = { onAuthPanelChange(OpenCodeAuthPanel.SUMMARY) },
    )
    if (!canCancelEdit) {
        Spacer(modifier = Modifier.height(12.dp))
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
            platformName = stringResource(R.string.platform_name_opencode_go),
            allowWindowFallback = false,
            updatedAtEpochMs = updatedAtEpochMs,
        )
        SaveAction(
            label = stringResource(R.string.action_save),
            enabled = isDirty,
            busy = isSaving,
            onClick = onSavePrefs,
            topSpacing = 12.dp,
        )
    }
}

@Composable
private fun ColumnScope.OpenCodeApiKeyAuthEditor(
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    hasApiKey: Boolean,
    isBusy: Boolean,
    onAuthPanelChange: (OpenCodeAuthPanel) -> Unit,
    onSave: (OpenCodeAuthMethod?) -> Unit,
) {
    ApiKeyAuthEditor(
        title = stringResource(R.string.opencode_auth_api_title),
        apiKey = apiKey,
        onApiKeyChange = onApiKeyChange,
        hasApiKey = hasApiKey,
        configuredLabel = stringResource(R.string.opencode_auth_configured),
        apiKeyLabel = stringResource(R.string.opencode_api_key_label),
        apiKeyHint = stringResource(R.string.opencode_api_key_hint),
        isBusy = isBusy,
        backLabel = stringResource(R.string.opencode_auth_back),
        saveLabel = stringResource(R.string.opencode_auth_save_and_use),
        onBack = { onAuthPanelChange(OpenCodeAuthPanel.CHOOSE) },
        onSave = { onSave(OpenCodeAuthMethod.API_KEY) },
    )
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
