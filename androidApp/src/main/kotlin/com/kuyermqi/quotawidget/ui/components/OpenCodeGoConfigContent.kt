package com.kuyermqi.quotawidget.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.kuyermqi.quotawidget.R
import com.kuyermqi.quotawidget.domain.QuotaWindow
import com.kuyermqi.quotawidget.domain.QuotaWindowKind
import com.kuyermqi.quotawidget.domain.UsageDisplayMode
import com.kuyermqi.quotawidget.domain.UsageProgressStyle
import com.kuyermqi.quotawidget.domain.UsageWindowKind
import com.kuyermqi.quotawidget.opencode.OpenCodeWorkspace
import com.kuyermqi.quotawidget.settings.OpenCodeAuthMethod

enum class OpenCodeAuthPanel {
    SUMMARY,
    CHOOSE,
    COOKIE,
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
    onWorkspaceSelected: (OpenCodeWorkspace) -> Unit,
    onAuthPanelChange: (OpenCodeAuthPanel) -> Unit,
    onLogin: () -> Unit,
    onReauth: () -> Unit,
    onLogout: () -> Unit,
    onSave: (OpenCodeAuthMethod?) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        if (!errorMessage.isNullOrBlank()) {
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            AnimatedContent(
                targetState = authPanel,
                transitionSpec = {
                    val forward = targetState == OpenCodeAuthPanel.COOKIE ||
                        targetState == OpenCodeAuthPanel.API_KEY ||
                        (initialState == OpenCodeAuthPanel.SUMMARY &&
                            targetState == OpenCodeAuthPanel.CHOOSE)
                    val direction = if (forward) {
                        AnimatedContentTransitionScope.SlideDirection.Left
                    } else {
                        AnimatedContentTransitionScope.SlideDirection.Right
                    }
                    val enter = slideIntoContainer(
                        towards = direction,
                        animationSpec = tween(
                            durationMillis = 280,
                            easing = FastOutSlowInEasing,
                        ),
                    )
                    val exit = slideOutOfContainer(
                        towards = direction,
                        animationSpec = tween(
                            durationMillis = 280,
                            easing = FastOutSlowInEasing,
                        ),
                    )
                    val transition = (enter + fadeIn(tween(180))) togetherWith
                        (exit + fadeOut(tween(180)))
                    transition.using(
                        SizeTransform(
                            clip = true,
                            sizeAnimationSpec = { _, _ ->
                                tween<IntSize>(
                                    durationMillis = 280,
                                    easing = FastOutSlowInEasing,
                                )
                            },
                        ),
                    )
                },
                label = "opencodeAuthPanel",
                contentAlignment = Alignment.TopStart,
                modifier = Modifier
                    .width(maxWidth)
                    .clipToBounds(),
            ) { panel ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    when (panel) {
                        OpenCodeAuthPanel.SUMMARY -> AuthSummary(
                            activeAuthMethod = activeAuthMethod,
                            needsReauth = needsReauth,
                            hasCookieSession = hasCookieSession,
                            isBusy = isBusy,
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
                            onWorkspaceSelected = onWorkspaceSelected,
                            onAuthPanelChange = onAuthPanelChange,
                            onReauth = onReauth,
                            onLogout = onLogout,
                            onSave = onSave,
                        )
                        OpenCodeAuthPanel.CHOOSE -> AuthChoiceList(
                            hasCookieSession = hasCookieSession,
                            hasApiKey = hasApiKey,
                            canCancelEdit = isConfigured,
                            isBusy = isBusy,
                            onAuthPanelChange = onAuthPanelChange,
                        )
                        OpenCodeAuthPanel.COOKIE -> CookieAuthEditor(
                            activeAuthMethod = activeAuthMethod,
                            hasCookieSession = hasCookieSession,
                            isBusy = isBusy,
                            workspaces = workspaces,
                            selectedWorkspaceId = selectedWorkspaceId,
                            selectedWorkspaceName = selectedWorkspaceName,
                            onAuthPanelChange = onAuthPanelChange,
                            onUseExistingSession = { onSave(OpenCodeAuthMethod.COOKIE) },
                            onLogin = onLogin,
                        )
                        OpenCodeAuthPanel.API_KEY -> ApiKeyAuthEditor(
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
    }
}

@Composable
private fun ColumnScope.AuthSummary(
    activeAuthMethod: OpenCodeAuthMethod,
    needsReauth: Boolean,
    hasCookieSession: Boolean,
    isBusy: Boolean,
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
    onWorkspaceSelected: (OpenCodeWorkspace) -> Unit,
    onAuthPanelChange: (OpenCodeAuthPanel) -> Unit,
    onReauth: () -> Unit,
    onLogout: () -> Unit,
    onSave: (OpenCodeAuthMethod?) -> Unit,
) {
    val methodLabel = when (activeAuthMethod) {
        OpenCodeAuthMethod.COOKIE -> stringResource(R.string.opencode_auth_web_title)
        OpenCodeAuthMethod.API_KEY -> stringResource(R.string.opencode_api_key_label)
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.opencode_auth_current, methodLabel),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        TextButton(
            onClick = { onAuthPanelChange(OpenCodeAuthPanel.CHOOSE) },
            enabled = !isBusy,
        ) {
            Text(stringResource(R.string.opencode_auth_change))
        }
    }
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
    Spacer(modifier = Modifier.height(16.dp))
    UsageDisplayPrefsSection(
        windows = windows,
        widgetWindowKind = widgetWindowKind,
        onWidgetWindowKindChange = onWidgetWindowKindChange,
        usageDisplayMode = usageDisplayMode,
        onUsageDisplayModeChange = onUsageDisplayModeChange,
        usageProgressStyle = usageProgressStyle,
        onUsageProgressStyleChange = onUsageProgressStyleChange,
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
        TextButton(
            onClick = onLogout,
            enabled = !isBusy,
        ) {
            Text(stringResource(R.string.opencode_logout))
        }
        Button(
            onClick = { onSave(null) },
            enabled = isDirty && !isBusy,
        ) {
            Text(stringResource(R.string.action_save))
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun ColumnScope.AuthChoiceList(
    hasCookieSession: Boolean,
    hasApiKey: Boolean,
    canCancelEdit: Boolean,
    isBusy: Boolean,
    onAuthPanelChange: (OpenCodeAuthPanel) -> Unit,
) {
    if (canCancelEdit) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
        ) {
            TextButton(
                onClick = { onAuthPanelChange(OpenCodeAuthPanel.SUMMARY) },
                enabled = !isBusy,
                contentPadding = PaddingValues(horizontal = 6.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.opencode_auth_back))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
    AuthChoiceCard(
        title = stringResource(R.string.opencode_auth_web_title),
        subtitle = stringResource(R.string.opencode_auth_web_hint),
        configured = hasCookieSession,
        enabled = !isBusy,
        onClick = { onAuthPanelChange(OpenCodeAuthPanel.COOKIE) },
    )
    Spacer(modifier = Modifier.height(8.dp))
    AuthChoiceCard(
        title = stringResource(R.string.opencode_auth_api_title),
        subtitle = stringResource(R.string.opencode_api_key_hint),
        configured = hasApiKey,
        enabled = !isBusy,
        onClick = { onAuthPanelChange(OpenCodeAuthPanel.API_KEY) },
    )
}

@Composable
private fun ColumnScope.CookieAuthEditor(
    activeAuthMethod: OpenCodeAuthMethod,
    hasCookieSession: Boolean,
    isBusy: Boolean,
    workspaces: List<OpenCodeWorkspace>,
    selectedWorkspaceId: String,
    selectedWorkspaceName: String,
    onAuthPanelChange: (OpenCodeAuthPanel) -> Unit,
    onUseExistingSession: () -> Unit,
    onLogin: () -> Unit,
) {
    if (hasCookieSession) {
        val accountName = workspaces
            .find { it.id == selectedWorkspaceId }
            ?.displayName
            ?: selectedWorkspaceName.ifBlank { selectedWorkspaceId }
        Text(
            text = if (accountName.isNotBlank()) {
                stringResource(R.string.opencode_auth_logged_in_account, accountName)
            } else {
                stringResource(R.string.opencode_auth_logged_in)
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(12.dp))
        if (activeAuthMethod == OpenCodeAuthMethod.COOKIE) {
            AuthFormActions(
                isBusy = isBusy,
                onBack = { onAuthPanelChange(OpenCodeAuthPanel.CHOOSE) },
                primaryLabel = stringResource(R.string.opencode_auth_switch_account),
                primaryEnabled = true,
                onPrimary = onLogin,
            )
        } else {
            AuthFormActions(
                isBusy = isBusy,
                onBack = { onAuthPanelChange(OpenCodeAuthPanel.CHOOSE) },
                primaryLabel = stringResource(R.string.opencode_auth_use_account),
                primaryEnabled = true,
                onPrimary = onUseExistingSession,
                secondaryLabel = stringResource(R.string.opencode_auth_switch_account),
                onSecondary = onLogin,
            )
        }
    } else {
        Text(
            text = stringResource(R.string.opencode_auth_web_title),
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.opencode_status_logged_out),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(12.dp))
        AuthFormActions(
            isBusy = isBusy,
            onBack = { onAuthPanelChange(OpenCodeAuthPanel.CHOOSE) },
            primaryLabel = stringResource(R.string.opencode_login),
            primaryEnabled = true,
            onPrimary = onLogin,
        )
    }
}

@Composable
private fun ColumnScope.ApiKeyAuthEditor(
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    hasApiKey: Boolean,
    isBusy: Boolean,
    onAuthPanelChange: (OpenCodeAuthPanel) -> Unit,
    onSave: (OpenCodeAuthMethod?) -> Unit,
) {
    Text(
        text = stringResource(R.string.opencode_auth_api_title),
        style = MaterialTheme.typography.titleSmall,
    )
    Spacer(modifier = Modifier.height(8.dp))
    if (hasApiKey) {
        Text(
            text = stringResource(R.string.opencode_auth_configured),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
    OutlinedTextField(
        value = apiKey,
        onValueChange = onApiKeyChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.opencode_api_key_label)) },
        supportingText = { Text(stringResource(R.string.opencode_api_key_hint)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        enabled = !isBusy,
    )
    Spacer(modifier = Modifier.height(12.dp))
    AuthFormActions(
        isBusy = isBusy,
        onBack = { onAuthPanelChange(OpenCodeAuthPanel.CHOOSE) },
        primaryLabel = stringResource(R.string.opencode_auth_save_and_use),
        primaryEnabled = apiKey.isNotBlank(),
        onPrimary = { onSave(OpenCodeAuthMethod.API_KEY) },
    )
}

@Composable
private fun AuthFormActions(
    isBusy: Boolean,
    onBack: () -> Unit,
    primaryLabel: String,
    primaryEnabled: Boolean,
    onPrimary: () -> Unit,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End,
    ) {
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
            OutlinedButton(
                onClick = onBack,
                enabled = !isBusy,
                modifier = Modifier.height(40.dp),
                contentPadding = PaddingValues(horizontal = 20.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.opencode_auth_back))
            }
            Button(
                onClick = onPrimary,
                enabled = primaryEnabled && !isBusy,
                modifier = Modifier.height(40.dp),
                contentPadding = PaddingValues(horizontal = 20.dp),
            ) {
                Text(primaryLabel)
            }
        }
        if (secondaryLabel != null && onSecondary != null) {
            TextButton(
                onClick = onSecondary,
                enabled = !isBusy,
            ) {
                Text(secondaryLabel)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun AuthChoiceCard(
    title: String,
    subtitle: String,
    configured: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        ListItem(
            headlineContent = {
                Text(text = title)
            },
            supportingContent = {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (configured) {
                        Text(
                            text = stringResource(R.string.opencode_auth_configured),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Icon(
                        painter = painterResource(R.drawable.ic_expand_more),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.rotate(-90f),
                    )
                }
            },
        )
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
