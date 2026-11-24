package com.kuyermqi.quotawidget.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.kuyermqi.quotawidget.R
import com.kuyermqi.quotawidget.domain.QuotaWindow
import com.kuyermqi.quotawidget.domain.QuotaWindowKind
import com.kuyermqi.quotawidget.domain.UsageDisplayMode
import com.kuyermqi.quotawidget.domain.UsageProgressStyle
import com.kuyermqi.quotawidget.domain.UsageResetStyle
import com.kuyermqi.quotawidget.domain.UsageWindowKind

/** One selectable authentication method in an [AuthMethodChoiceList]. */
data class AuthMethodOption(
    val title: String,
    val subtitle: String,
    val configured: Boolean = false,
    val onClick: () -> Unit,
)

/** Shared logged-out status line with an optional error below it. */
@Composable
fun ColumnScope.AuthStatusLine(
    status: String,
    error: String? = null,
) {
    Text(
        text = status,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
    if (!error.isNullOrBlank()) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = error,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

/** Shared account/method title and plan line. */
@Composable
fun ColumnScope.AuthSummaryHeader(
    title: String,
    subtitle: String? = null,
) {
    if (title.isNotBlank()) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
    if (!subtitle.isNullOrBlank()) {
        if (title.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
        }
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Transparent bottom-right secondary actions (logout, change-auth) inside an account card. */
@Composable
private fun ColumnScope.AuthCardSecondaryActions(
    logoutLabel: String,
    onLogout: () -> Unit,
    changeLabel: String?,
    onChangeAuthMethod: (() -> Unit)?,
    isBusy: Boolean,
) {
    Spacer(modifier = Modifier.height(4.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(
            onClick = onLogout,
            enabled = !isBusy,
            contentPadding = PaddingValues(horizontal = 8.dp),
        ) {
            Text(logoutLabel)
        }
        if (changeLabel != null && onChangeAuthMethod != null) {
            TextButton(
                onClick = onChangeAuthMethod,
                enabled = !isBusy,
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                Text(
                    text = changeLabel,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** Card wrapper that visually separates account details from widget settings. */
@Composable
internal fun ColumnScope.AuthAccountCard(
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = appCardColor()),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            content()
        }
    }
}

/** Shared configured-account layout: summary, optional extra content, secondary actions, prefs, save. */
@Composable
fun ColumnScope.AuthConfiguredPanel(
    title: String,
    subtitle: String? = null,
    changeLabel: String? = null,
    onChangeAuthMethod: (() -> Unit)? = null,
    extraContent: @Composable ColumnScope.() -> Unit = {},
    windows: List<QuotaWindow>,
    widgetWindowKind: UsageWindowKind,
    onWidgetWindowKindChange: (UsageWindowKind) -> Unit,
    usageDisplayMode: UsageDisplayMode,
    onUsageDisplayModeChange: (UsageDisplayMode) -> Unit,
    usageProgressStyle: UsageProgressStyle,
    onUsageProgressStyleChange: (UsageProgressStyle) -> Unit,
    usageResetStyle: UsageResetStyle,
    onUsageResetStyleChange: (UsageResetStyle) -> Unit,
    windowKindChoices: List<UsageWindowKind>,
    showWindowKindPicker: Boolean = true,
    overviewKinds: List<QuotaWindowKind>,
    isBusy: Boolean,
    isDirty: Boolean,
    logoutLabel: String,
    saveLabel: String,
    saveBusy: Boolean = isBusy,
    onLogout: () -> Unit,
    onSave: () -> Unit,
    platformName: String = "",
    allowWindowFallback: Boolean = true,
    updatedAtEpochMs: Long = 0L,
) {
    AuthAccountCard {
        AuthSummaryHeader(
            title = title,
            subtitle = subtitle,
        )
        extraContent()
        AuthCardSecondaryActions(
            logoutLabel = logoutLabel,
            onLogout = onLogout,
            changeLabel = changeLabel,
            onChangeAuthMethod = onChangeAuthMethod,
            isBusy = isBusy,
        )
    }
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
        windowKindChoices = windowKindChoices,
        showWindowKindPicker = showWindowKindPicker,
        overviewKinds = overviewKinds,
        platformName = platformName,
        allowWindowFallback = allowWindowFallback,
        updatedAtEpochMs = updatedAtEpochMs,
    )
    AuthSaveAction(
        isBusy = saveBusy,
        isDirty = isDirty,
        saveLabel = saveLabel,
        onSave = onSave,
    )
}

/**
 * Shared auth sub-panel switcher: horizontal slide between panels, without animating
 * the container height.
 */
@Composable
fun <T> AuthPanelSwitcher(
    targetState: T,
    label: String,
    isForward: (initial: T, target: T) -> Boolean,
    content: @Composable ColumnScope.(T) -> Unit,
) {
    AnimatedContent(
        targetState = targetState,
        transitionSpec = {
            val direction = if (isForward(initialState, targetState)) {
                AnimatedContentTransitionScope.SlideDirection.Left
            } else {
                AnimatedContentTransitionScope.SlideDirection.Right
            }
            val enter = slideIntoContainer(
                towards = direction,
                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
            )
            val exit = slideOutOfContainer(
                towards = direction,
                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
            )
            ((enter + fadeIn(tween(180))) togetherWith (exit + fadeOut(tween(180)))).using(null)
        },
        label = label,
        contentAlignment = Alignment.TopStart,
        modifier = Modifier.fillMaxWidth(),
    ) { panel ->
        Column(modifier = Modifier.fillMaxWidth()) {
            content(panel)
        }
    }
}

/** Shared auth-method chooser used by session platforms and OpenCode Go. */
@Composable
fun ColumnScope.AuthMethodChoiceList(
    options: List<AuthMethodOption>,
    canCancelEdit: Boolean,
    isBusy: Boolean,
    backLabel: String,
    configuredLabel: String,
    onBack: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = appCardColor()),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, option ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    )
                }
                AuthChoiceRow(
                    title = option.title,
                    subtitle = option.subtitle,
                    configured = option.configured,
                    configuredLabel = configuredLabel,
                    enabled = !isBusy,
                    onClick = option.onClick,
                )
            }
            if (canCancelEdit) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    AuthBackButton(
                        isBusy = isBusy,
                        onBack = onBack,
                        backLabel = backLabel,
                    )
                }
            }
        }
    }
}

/** Shared save action for a configured account. */
@Composable
private fun ColumnScope.AuthSaveAction(
    isBusy: Boolean,
    isDirty: Boolean,
    saveLabel: String,
    onSave: () -> Unit,
) {
    Spacer(modifier = Modifier.height(12.dp))
    SaveAction(
        label = saveLabel,
        enabled = isDirty,
        busy = isBusy,
        onClick = onSave,
    )
}

/** Outlined pill back action used by auth panels. */
@Composable
private fun AuthBackButton(
    isBusy: Boolean,
    onBack: () -> Unit,
    backLabel: String,
) {
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
        Text(backLabel)
    }
}

/** Shared bottom-right auth form actions. */
@Composable
fun AuthFormActions(
    isBusy: Boolean,
    onBack: () -> Unit,
    backLabel: String,
    primaryLabel: String,
    primaryEnabled: Boolean,
    onPrimary: () -> Unit,
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
            AuthBackButton(
                isBusy = isBusy,
                onBack = onBack,
                backLabel = backLabel,
            )
            Button(
                onClick = onPrimary,
                enabled = primaryEnabled && !isBusy,
                modifier = Modifier.height(40.dp),
                contentPadding = PaddingValues(horizontal = 20.dp),
            ) {
                Text(primaryLabel)
            }
        }
    }
}

/** Shared API-key editor used by session platforms and OpenCode Go. */
@Composable
fun ColumnScope.ApiKeyAuthEditor(
    title: String,
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    hasApiKey: Boolean,
    configuredLabel: String,
    apiKeyLabel: String,
    apiKeyHint: String,
    isBusy: Boolean,
    backLabel: String,
    saveLabel: String,
    onBack: () -> Unit,
    onSave: () -> Unit,
    saveEnabled: Boolean = apiKey.isNotBlank(),
) {
    AuthAccountCard {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (hasApiKey) {
            Text(
                text = configuredLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        OutlinedTextField(
            value = apiKey,
            onValueChange = onApiKeyChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(apiKeyLabel) },
            supportingText = { Text(apiKeyHint) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            enabled = !isBusy,
        )
        Spacer(modifier = Modifier.height(12.dp))
        AuthFormActions(
            isBusy = isBusy,
            onBack = onBack,
            backLabel = backLabel,
            primaryLabel = saveLabel,
            primaryEnabled = saveEnabled,
            onPrimary = onSave,
        )
    }
}

@Composable
private fun AuthChoiceRow(
    title: String,
    subtitle: String,
    configured: Boolean,
    configuredLabel: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier.padding(start = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (configured) {
                Text(
                    text = configuredLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
