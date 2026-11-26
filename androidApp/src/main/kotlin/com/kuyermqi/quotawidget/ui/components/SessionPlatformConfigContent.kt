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
import com.kuyermqi.quotawidget.platform.PlatformIds
import com.kuyermqi.quotawidget.settings.SessionAuthMethod
import com.kuyermqi.quotawidget.settings.SessionPlatformSettings

/** Auth sub-panel for providers that support both browser login and an API key. */
enum class SessionAuthPanel {
    SUMMARY,
    CHOOSE,
    API_KEY,
}

data class SessionUsagePrefsState(
    val windows: List<QuotaWindow>,
    val widgetWindowKind: UsageWindowKind,
    val onWidgetWindowKindChange: (UsageWindowKind) -> Unit,
    val usageDisplayMode: UsageDisplayMode,
    val onUsageDisplayModeChange: (UsageDisplayMode) -> Unit,
    val usageProgressStyle: UsageProgressStyle,
    val onUsageProgressStyleChange: (UsageProgressStyle) -> Unit,
    val usageResetStyle: UsageResetStyle,
    val onUsageResetStyleChange: (UsageResetStyle) -> Unit,
    val overviewKinds: List<QuotaWindowKind>,
    val updatedAtEpochMs: Long = 0L,
)

data class SessionPlatformConfigState(
    val platformId: String,
    val platformName: String,
    val settings: SessionPlatformSettings,
    val supportsApiKey: Boolean,
    val authPanel: SessionAuthPanel,
    val draftApiKey: String,
    val onDraftApiKeyChange: (String) -> Unit,
    val onAuthPanelChange: (SessionAuthPanel) -> Unit,
    val usagePrefs: SessionUsagePrefsState,
    val notice: String? = null,
    val isConfigured: Boolean,
    val isBusy: Boolean,
    val isSaving: Boolean,
    val isDirty: Boolean,
    val error: String?,
    val onLogin: () -> Unit,
    val onLogout: () -> Unit,
    val onSave: () -> Unit,
)

@Suppress("FunctionNaming")
@Composable
fun ColumnScope.SessionPlatformConfigContent(
    state: SessionPlatformConfigState,
) {
    val showTopError = !state.error.isNullOrBlank() && (state.supportsApiKey || state.isConfigured)
    if (showTopError) {
        Text(
            text = state.error,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(modifier = Modifier.height(8.dp))
    }

    if (!state.supportsApiKey) {
        if (state.isConfigured) {
            SessionConfiguredContent(state = state)
        } else {
            SessionLoggedOutContent(state = state)
        }
        return
    }

    BackHandler(
        enabled = state.authPanel == SessionAuthPanel.API_KEY ||
            (state.authPanel == SessionAuthPanel.CHOOSE && state.isConfigured),
    ) {
        state.onAuthPanelChange(
            if (state.authPanel == SessionAuthPanel.API_KEY) {
                SessionAuthPanel.CHOOSE
            } else {
                SessionAuthPanel.SUMMARY
            },
        )
    }

    AuthPanelSwitcher(
        targetState = state.authPanel,
        label = "sessionAuthPanel",
        isForward = { initial, target ->
            target == SessionAuthPanel.API_KEY ||
                (initial == SessionAuthPanel.SUMMARY && target == SessionAuthPanel.CHOOSE)
        },
    ) { panel ->
        when (panel) {
            SessionAuthPanel.SUMMARY -> if (state.isConfigured) {
                SessionConfiguredContent(
                    state = state,
                    onChangeAuthMethod = { state.onAuthPanelChange(SessionAuthPanel.CHOOSE) },
                )
            } else {
                SessionAuthChoice(state = state)
            }
            SessionAuthPanel.CHOOSE -> SessionAuthChoice(state = state)
            SessionAuthPanel.API_KEY -> ApiKeyAuthEditor(
                title = stringResource(R.string.session_auth_api_title),
                apiKey = state.draftApiKey,
                onApiKeyChange = state.onDraftApiKeyChange,
                hasApiKey = state.settings.apiKey.isNotBlank(),
                configuredLabel = stringResource(R.string.session_auth_configured),
                apiKeyLabel = stringResource(R.string.session_api_key_label),
                apiKeyHint = stringResource(R.string.session_api_key_hint),
                isBusy = state.isBusy,
                backLabel = stringResource(R.string.session_auth_back),
                saveLabel = stringResource(R.string.session_auth_save_and_use),
                onBack = { state.onAuthPanelChange(SessionAuthPanel.CHOOSE) },
                onSave = state.onSave,
                saveEnabled = state.draftApiKey.isNotBlank() && !state.isBusy,
            )
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun ColumnScope.SessionLoggedOutContent(
    state: SessionPlatformConfigState,
) {
    AuthAccountCard {
        AuthStatusLine(
            status = stringResource(R.string.session_not_logged_in),
            error = state.error,
        )
        SessionNotice(state.notice)
        SessionActions(
            isBusy = state.isBusy,
            onLogin = state.onLogin,
        )
    }
    Spacer(modifier = Modifier.height(12.dp))
    SessionWidgetPrefs(state = state)
}

@Suppress("FunctionNaming")
@Composable
private fun ColumnScope.SessionAuthChoice(
    state: SessionPlatformConfigState,
) {
    AuthMethodChoiceList(
        options = listOf(
            AuthMethodOption(
                title = stringResource(R.string.session_auth_web_title),
                subtitle = stringResource(R.string.session_auth_web_hint),
                configured = state.settings.accessToken.isNotBlank() &&
                    state.settings.refreshToken.isNotBlank(),
                onClick = state.onLogin,
            ),
            AuthMethodOption(
                title = stringResource(R.string.session_auth_api_title),
                subtitle = stringResource(R.string.session_api_key_hint),
                configured = state.settings.apiKey.isNotBlank(),
                onClick = { state.onAuthPanelChange(SessionAuthPanel.API_KEY) },
            ),
        ),
        canCancelEdit = state.isConfigured,
        isBusy = state.isBusy,
        backLabel = stringResource(R.string.session_auth_back),
        configuredLabel = stringResource(R.string.session_auth_configured),
        onBack = { state.onAuthPanelChange(SessionAuthPanel.SUMMARY) },
    )
    if (!state.isConfigured) {
        Spacer(modifier = Modifier.height(12.dp))
        SessionWidgetPrefs(state = state)
    }
}

/** Widget settings shown before an account is connected, saved with the draft prefs. */
@Suppress("FunctionNaming")
@Composable
private fun ColumnScope.SessionWidgetPrefs(
    state: SessionPlatformConfigState,
) {
    val prefs = state.usagePrefs
    val windowKindChoices = availableUsageWindowKinds(prefs.windows)
    UsageDisplayPrefsSection(
        windows = prefs.windows,
        widgetWindowKind = prefs.widgetWindowKind,
        onWidgetWindowKindChange = prefs.onWidgetWindowKindChange,
        usageDisplayMode = prefs.usageDisplayMode,
        onUsageDisplayModeChange = prefs.onUsageDisplayModeChange,
        usageProgressStyle = prefs.usageProgressStyle,
        onUsageProgressStyleChange = prefs.onUsageProgressStyleChange,
        usageResetStyle = prefs.usageResetStyle,
        onUsageResetStyleChange = prefs.onUsageResetStyleChange,
        enabled = !state.isBusy,
        windowKindChoices = windowKindChoices,
        showWindowKindPicker = windowKindChoices.size >= 2,
        overviewKinds = prefs.overviewKinds,
        platformName = state.platformName,
        updatedAtEpochMs = prefs.updatedAtEpochMs,
    )
    SaveAction(
        label = stringResource(R.string.session_save_prefs),
        enabled = state.isDirty,
        busy = state.isSaving,
        onClick = state.onSave,
        topSpacing = 12.dp,
    )
}

@Suppress("FunctionNaming")
@Composable
private fun ColumnScope.SessionConfiguredContent(
    state: SessionPlatformConfigState,
    onChangeAuthMethod: (() -> Unit)? = null,
) {
    val settings = state.settings
    val prefs = state.usagePrefs
    val effectiveMethod = settings.authMethod ?: when {
        state.platformId == PlatformIds.CLINE && settings.apiKey.isNotBlank() ->
            SessionAuthMethod.API_KEY
        state.platformId == PlatformIds.COMMAND_CODE -> SessionAuthMethod.API_KEY
        else -> SessionAuthMethod.WEB
    }
    val methodLabel = when (effectiveMethod) {
        SessionAuthMethod.API_KEY -> stringResource(R.string.session_api_key_label)
        SessionAuthMethod.WEB -> if (state.platformId == PlatformIds.ANTIGRAVITY) {
            stringResource(R.string.auth_method_google_oauth)
        } else {
            stringResource(R.string.session_auth_web_title)
        }
    }
    val title = stringResource(R.string.auth_current_method, methodLabel)
    val subtitle = stringResource(
        R.string.session_account_logged_in,
        settings.accountLabel.ifBlank { stringResource(R.string.session_account_unknown) },
    )
    val detail = settings.plan
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
        extraContent = {
            AuthDetailLine(detail)
            SessionNotice(state.notice)
        },
        windows = prefs.windows,
        widgetWindowKind = prefs.widgetWindowKind,
        onWidgetWindowKindChange = prefs.onWidgetWindowKindChange,
        usageDisplayMode = prefs.usageDisplayMode,
        onUsageDisplayModeChange = prefs.onUsageDisplayModeChange,
        usageProgressStyle = prefs.usageProgressStyle,
        onUsageProgressStyleChange = prefs.onUsageProgressStyleChange,
        usageResetStyle = prefs.usageResetStyle,
        onUsageResetStyleChange = prefs.onUsageResetStyleChange,
        windowKindChoices = availableUsageWindowKinds(prefs.windows),
        overviewKinds = prefs.overviewKinds,
        isBusy = state.isBusy,
        isDirty = state.isDirty,
        saveBusy = state.isSaving,
        logoutLabel = stringResource(R.string.session_logout),
        saveLabel = stringResource(R.string.session_save_prefs),
        onLogout = state.onLogout,
        onSave = state.onSave,
        platformName = state.platformName,
        updatedAtEpochMs = prefs.updatedAtEpochMs,
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
