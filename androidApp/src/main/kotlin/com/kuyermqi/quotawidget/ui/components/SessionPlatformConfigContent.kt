package com.kuyermqi.quotawidget.ui.components

import androidx.activity.compose.BackHandler
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
import com.kuyermqi.quotawidget.domain.QuotaWindowKind
import com.kuyermqi.quotawidget.domain.UsageDisplayMode
import com.kuyermqi.quotawidget.domain.UsageProgressStyle
import com.kuyermqi.quotawidget.domain.UsageResetStyle
import com.kuyermqi.quotawidget.domain.UsageWindowKind
import com.kuyermqi.quotawidget.domain.availableUsageWindowKinds
import com.kuyermqi.quotawidget.settings.SessionPlatformSettings

/** Auth sub-panel for providers that support both browser login and an API key. */
enum class SessionAuthPanel {
    SUMMARY,
    CHOOSE,
    API_KEY,
}

@Composable
fun ColumnScope.SessionPlatformConfigContent(
    settings: SessionPlatformSettings,
    supportsApiKey: Boolean,
    authPanel: SessionAuthPanel,
    draftApiKey: String,
    onDraftApiKeyChange: (String) -> Unit,
    onAuthPanelChange: (SessionAuthPanel) -> Unit,
    windows: List<QuotaWindow>,
    widgetWindowKind: UsageWindowKind,
    onWidgetWindowKindChange: (UsageWindowKind) -> Unit,
    usageDisplayMode: UsageDisplayMode,
    onUsageDisplayModeChange: (UsageDisplayMode) -> Unit,
    usageProgressStyle: UsageProgressStyle,
    onUsageProgressStyleChange: (UsageProgressStyle) -> Unit,
    usageResetStyle: UsageResetStyle,
    onUsageResetStyleChange: (UsageResetStyle) -> Unit,
    overviewKinds: List<QuotaWindowKind>,
    notice: String? = null,
    isConfigured: Boolean,
    isBusy: Boolean,
    isSaving: Boolean,
    isDirty: Boolean,
    error: String?,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onSave: () -> Unit,
    platformName: String = "",
    updatedAtEpochMs: Long = 0L,
) {
    val showTopError = !error.isNullOrBlank() && (supportsApiKey || isConfigured)
    if (showTopError) {
        Text(
            text = error,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(modifier = Modifier.height(8.dp))
    }

    if (!supportsApiKey) {
        if (isConfigured) {
            SessionConfiguredContent(
                settings = settings,
                windows = windows,
                widgetWindowKind = widgetWindowKind,
                onWidgetWindowKindChange = onWidgetWindowKindChange,
                usageDisplayMode = usageDisplayMode,
                onUsageDisplayModeChange = onUsageDisplayModeChange,
                usageProgressStyle = usageProgressStyle,
                onUsageProgressStyleChange = onUsageProgressStyleChange,
                usageResetStyle = usageResetStyle,
                onUsageResetStyleChange = onUsageResetStyleChange,
                overviewKinds = overviewKinds,
                notice = notice,
                isBusy = isBusy,
                isSaving = isSaving,
                isDirty = isDirty,
                onLogout = onLogout,
                onSave = onSave,
                platformName = platformName,
                updatedAtEpochMs = updatedAtEpochMs,
            )
        } else {
            SessionLoggedOutContent(
                notice = notice,
                error = error,
                isBusy = isBusy,
                isSaving = isSaving,
                onLogin = onLogin,
                platformName = platformName,
                windows = windows,
                widgetWindowKind = widgetWindowKind,
                onWidgetWindowKindChange = onWidgetWindowKindChange,
                usageDisplayMode = usageDisplayMode,
                onUsageDisplayModeChange = onUsageDisplayModeChange,
                usageProgressStyle = usageProgressStyle,
                onUsageProgressStyleChange = onUsageProgressStyleChange,
                usageResetStyle = usageResetStyle,
                onUsageResetStyleChange = onUsageResetStyleChange,
                overviewKinds = overviewKinds,
                isDirty = isDirty,
                onSave = onSave,
                updatedAtEpochMs = updatedAtEpochMs,
            )
        }
        return
    }

    BackHandler(
        enabled = authPanel == SessionAuthPanel.API_KEY ||
            (authPanel == SessionAuthPanel.CHOOSE && isConfigured),
    ) {
        onAuthPanelChange(
            if (authPanel == SessionAuthPanel.API_KEY) {
                SessionAuthPanel.CHOOSE
            } else {
                SessionAuthPanel.SUMMARY
            },
        )
    }

    AuthPanelSwitcher(
        targetState = authPanel,
        label = "sessionAuthPanel",
        isForward = { initial, target ->
            target == SessionAuthPanel.API_KEY ||
                (initial == SessionAuthPanel.SUMMARY && target == SessionAuthPanel.CHOOSE)
        },
    ) { panel ->
        when (panel) {
            SessionAuthPanel.SUMMARY -> if (isConfigured) {
                SessionConfiguredContent(
                    settings = settings,
                    windows = windows,
                    widgetWindowKind = widgetWindowKind,
                    onWidgetWindowKindChange = onWidgetWindowKindChange,
                    usageDisplayMode = usageDisplayMode,
                    onUsageDisplayModeChange = onUsageDisplayModeChange,
                    usageProgressStyle = usageProgressStyle,
                    onUsageProgressStyleChange = onUsageProgressStyleChange,
                    usageResetStyle = usageResetStyle,
                    onUsageResetStyleChange = onUsageResetStyleChange,
                    overviewKinds = overviewKinds,
                    notice = notice,
                    isBusy = isBusy,
                    isSaving = isSaving,
                    isDirty = isDirty,
                    onChangeAuthMethod = { onAuthPanelChange(SessionAuthPanel.CHOOSE) },
                    onLogout = onLogout,
                    onSave = onSave,
                    platformName = platformName,
                    updatedAtEpochMs = updatedAtEpochMs,
                )
            } else {
                SessionAuthChoice(
                    settings = settings,
                    isConfigured = isConfigured,
                    isBusy = isBusy,
                    isSaving = isSaving,
                    onAuthPanelChange = onAuthPanelChange,
                    onLogin = onLogin,
                    platformName = platformName,
                    windows = windows,
                    widgetWindowKind = widgetWindowKind,
                    onWidgetWindowKindChange = onWidgetWindowKindChange,
                    usageDisplayMode = usageDisplayMode,
                    onUsageDisplayModeChange = onUsageDisplayModeChange,
                    usageProgressStyle = usageProgressStyle,
                    onUsageProgressStyleChange = onUsageProgressStyleChange,
                    usageResetStyle = usageResetStyle,
                    onUsageResetStyleChange = onUsageResetStyleChange,
                    overviewKinds = overviewKinds,
                    isDirty = isDirty,
                    onSave = onSave,
                    updatedAtEpochMs = updatedAtEpochMs,
                )
            }
            SessionAuthPanel.CHOOSE -> SessionAuthChoice(
                settings = settings,
                isConfigured = isConfigured,
                isBusy = isBusy,
                isSaving = isSaving,
                onAuthPanelChange = onAuthPanelChange,
                onLogin = onLogin,
                platformName = platformName,
                windows = windows,
                widgetWindowKind = widgetWindowKind,
                onWidgetWindowKindChange = onWidgetWindowKindChange,
                usageDisplayMode = usageDisplayMode,
                onUsageDisplayModeChange = onUsageDisplayModeChange,
                usageProgressStyle = usageProgressStyle,
                onUsageProgressStyleChange = onUsageProgressStyleChange,
                usageResetStyle = usageResetStyle,
                onUsageResetStyleChange = onUsageResetStyleChange,
                overviewKinds = overviewKinds,
                isDirty = isDirty,
                onSave = onSave,
                updatedAtEpochMs = updatedAtEpochMs,
            )
            SessionAuthPanel.API_KEY -> ApiKeyAuthEditor(
                title = stringResource(R.string.session_auth_api_title),
                apiKey = draftApiKey,
                onApiKeyChange = onDraftApiKeyChange,
                hasApiKey = settings.apiKey.isNotBlank(),
                configuredLabel = stringResource(R.string.session_auth_configured),
                apiKeyLabel = stringResource(R.string.session_api_key_label),
                apiKeyHint = stringResource(R.string.session_api_key_hint),
                isBusy = isBusy,
                backLabel = stringResource(R.string.session_auth_back),
                saveLabel = stringResource(R.string.session_auth_save_and_use),
                onBack = { onAuthPanelChange(SessionAuthPanel.CHOOSE) },
                onSave = onSave,
                saveEnabled = draftApiKey.isNotBlank() && !isBusy,
            )
        }
    }
}

@Composable
private fun ColumnScope.SessionLoggedOutContent(
    notice: String?,
    error: String?,
    isBusy: Boolean,
    isSaving: Boolean,
    onLogin: () -> Unit,
    platformName: String,
    windows: List<QuotaWindow>,
    widgetWindowKind: UsageWindowKind,
    onWidgetWindowKindChange: (UsageWindowKind) -> Unit,
    usageDisplayMode: UsageDisplayMode,
    onUsageDisplayModeChange: (UsageDisplayMode) -> Unit,
    usageProgressStyle: UsageProgressStyle,
    onUsageProgressStyleChange: (UsageProgressStyle) -> Unit,
    usageResetStyle: UsageResetStyle,
    onUsageResetStyleChange: (UsageResetStyle) -> Unit,
    overviewKinds: List<QuotaWindowKind>,
    isDirty: Boolean,
    onSave: () -> Unit,
    updatedAtEpochMs: Long,
) {
    AuthAccountCard {
        AuthStatusLine(
            status = stringResource(R.string.session_not_logged_in),
            error = error,
        )
        SessionNotice(notice)
        SessionActions(
            isBusy = isBusy,
            onLogin = onLogin,
        )
    }
    Spacer(modifier = Modifier.height(12.dp))
    SessionWidgetPrefs(
        windows = windows,
        widgetWindowKind = widgetWindowKind,
        onWidgetWindowKindChange = onWidgetWindowKindChange,
        usageDisplayMode = usageDisplayMode,
        onUsageDisplayModeChange = onUsageDisplayModeChange,
        usageProgressStyle = usageProgressStyle,
        onUsageProgressStyleChange = onUsageProgressStyleChange,
        usageResetStyle = usageResetStyle,
        onUsageResetStyleChange = onUsageResetStyleChange,
        overviewKinds = overviewKinds,
        isBusy = isBusy,
        isSaving = isSaving,
        isDirty = isDirty,
        onSave = onSave,
        platformName = platformName,
        updatedAtEpochMs = updatedAtEpochMs,
    )
}

@Composable
private fun ColumnScope.SessionAuthChoice(
    settings: SessionPlatformSettings,
    isConfigured: Boolean,
    isBusy: Boolean,
    isSaving: Boolean,
    onAuthPanelChange: (SessionAuthPanel) -> Unit,
    onLogin: () -> Unit,
    platformName: String,
    windows: List<QuotaWindow>,
    widgetWindowKind: UsageWindowKind,
    onWidgetWindowKindChange: (UsageWindowKind) -> Unit,
    usageDisplayMode: UsageDisplayMode,
    onUsageDisplayModeChange: (UsageDisplayMode) -> Unit,
    usageProgressStyle: UsageProgressStyle,
    onUsageProgressStyleChange: (UsageProgressStyle) -> Unit,
    usageResetStyle: UsageResetStyle,
    onUsageResetStyleChange: (UsageResetStyle) -> Unit,
    overviewKinds: List<QuotaWindowKind>,
    isDirty: Boolean,
    onSave: () -> Unit,
    updatedAtEpochMs: Long,
) {
    AuthMethodChoiceList(
        options = listOf(
            AuthMethodOption(
                title = stringResource(R.string.session_auth_web_title),
                subtitle = stringResource(R.string.session_auth_web_hint),
                configured = settings.accessToken.isNotBlank() &&
                    settings.refreshToken.isNotBlank(),
                onClick = onLogin,
            ),
            AuthMethodOption(
                title = stringResource(R.string.session_auth_api_title),
                subtitle = stringResource(R.string.session_api_key_hint),
                configured = settings.apiKey.isNotBlank(),
                onClick = { onAuthPanelChange(SessionAuthPanel.API_KEY) },
            ),
        ),
        canCancelEdit = isConfigured,
        isBusy = isBusy,
        backLabel = stringResource(R.string.session_auth_back),
        configuredLabel = stringResource(R.string.session_auth_configured),
        onBack = { onAuthPanelChange(SessionAuthPanel.SUMMARY) },
    )
    if (!isConfigured) {
        Spacer(modifier = Modifier.height(12.dp))
        SessionWidgetPrefs(
            windows = windows,
            widgetWindowKind = widgetWindowKind,
            onWidgetWindowKindChange = onWidgetWindowKindChange,
            usageDisplayMode = usageDisplayMode,
            onUsageDisplayModeChange = onUsageDisplayModeChange,
            usageProgressStyle = usageProgressStyle,
            onUsageProgressStyleChange = onUsageProgressStyleChange,
            usageResetStyle = usageResetStyle,
            onUsageResetStyleChange = onUsageResetStyleChange,
            overviewKinds = overviewKinds,
            isBusy = isBusy,
            isSaving = isSaving,
            isDirty = isDirty,
            onSave = onSave,
            platformName = platformName,
            updatedAtEpochMs = updatedAtEpochMs,
        )
    }
}

/** Widget settings shown before an account is connected, saved with the draft prefs. */
@Composable
private fun ColumnScope.SessionWidgetPrefs(
    windows: List<QuotaWindow>,
    widgetWindowKind: UsageWindowKind,
    onWidgetWindowKindChange: (UsageWindowKind) -> Unit,
    usageDisplayMode: UsageDisplayMode,
    onUsageDisplayModeChange: (UsageDisplayMode) -> Unit,
    usageProgressStyle: UsageProgressStyle,
    onUsageProgressStyleChange: (UsageProgressStyle) -> Unit,
    usageResetStyle: UsageResetStyle,
    onUsageResetStyleChange: (UsageResetStyle) -> Unit,
    overviewKinds: List<QuotaWindowKind>,
    isBusy: Boolean,
    isSaving: Boolean,
    isDirty: Boolean,
    onSave: () -> Unit,
    platformName: String,
    updatedAtEpochMs: Long,
) {
    val windowKindChoices = availableUsageWindowKinds(windows)
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
        windowKindChoices = windowKindChoices,
        showWindowKindPicker = windowKindChoices.size >= 2,
        overviewKinds = overviewKinds,
        platformName = platformName,
        updatedAtEpochMs = updatedAtEpochMs,
    )
    Spacer(modifier = Modifier.height(12.dp))
    SaveAction(
        label = stringResource(R.string.session_save_prefs),
        enabled = isDirty,
        busy = isSaving,
        onClick = onSave,
    )
}

@Composable
private fun ColumnScope.SessionConfiguredContent(
    settings: SessionPlatformSettings,
    windows: List<QuotaWindow>,
    widgetWindowKind: UsageWindowKind,
    onWidgetWindowKindChange: (UsageWindowKind) -> Unit,
    usageDisplayMode: UsageDisplayMode,
    onUsageDisplayModeChange: (UsageDisplayMode) -> Unit,
    usageProgressStyle: UsageProgressStyle,
    onUsageProgressStyleChange: (UsageProgressStyle) -> Unit,
    usageResetStyle: UsageResetStyle,
    onUsageResetStyleChange: (UsageResetStyle) -> Unit,
    overviewKinds: List<QuotaWindowKind>,
    notice: String? = null,
    isBusy: Boolean,
    isSaving: Boolean,
    isDirty: Boolean,
    onChangeAuthMethod: (() -> Unit)? = null,
    onLogout: () -> Unit,
    onSave: () -> Unit,
    platformName: String = "",
    updatedAtEpochMs: Long = 0L,
) {
    val usingApiKey = settings.apiKey.isNotBlank() && settings.accountLabel.isBlank()
    val title = if (usingApiKey) {
        stringResource(
            R.string.auth_current_method,
            stringResource(R.string.session_api_key_label),
        )
    } else {
        stringResource(
            R.string.session_account_logged_in,
            settings.accountLabel.ifBlank { stringResource(R.string.session_account_unknown) },
        )
    }
    val subtitle = settings.plan
        .takeIf { it.isNotBlank() }
        ?.let { stringResource(R.string.session_plan, it) }
    AuthConfiguredPanel(
        title = title,
        subtitle = subtitle,
        changeLabel = if (onChangeAuthMethod != null) {
            stringResource(R.string.session_auth_change)
        } else {
            null
        },
        onChangeAuthMethod = onChangeAuthMethod,
        extraContent = { SessionNotice(notice) },
        windows = windows,
        widgetWindowKind = widgetWindowKind,
        onWidgetWindowKindChange = onWidgetWindowKindChange,
        usageDisplayMode = usageDisplayMode,
        onUsageDisplayModeChange = onUsageDisplayModeChange,
        usageProgressStyle = usageProgressStyle,
        onUsageProgressStyleChange = onUsageProgressStyleChange,
        usageResetStyle = usageResetStyle,
        onUsageResetStyleChange = onUsageResetStyleChange,
        windowKindChoices = availableUsageWindowKinds(windows),
        overviewKinds = overviewKinds,
        isBusy = isBusy,
        isDirty = isDirty,
        saveBusy = isSaving,
        logoutLabel = stringResource(R.string.session_logout),
        saveLabel = stringResource(R.string.session_save_prefs),
        onLogout = onLogout,
        onSave = onSave,
        platformName = platformName,
        updatedAtEpochMs = updatedAtEpochMs,
    )
}

@Composable
private fun ColumnScope.SessionNotice(notice: String?) {
    if (!notice.isNullOrBlank()) {
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = notice,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ColumnScope.SessionActions(
    isBusy: Boolean,
    onLogin: () -> Unit,
) {
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
            Text(stringResource(R.string.session_login))
        }
    }
}
