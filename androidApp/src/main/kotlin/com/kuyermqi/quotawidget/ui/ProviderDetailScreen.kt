package com.kuyermqi.quotawidget.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kuyermqi.quotawidget.R
import com.kuyermqi.quotawidget.ui.components.LocalSaveActionState
import com.kuyermqi.quotawidget.ui.components.PageHeader
import com.kuyermqi.quotawidget.ui.components.ProviderLogo
import com.kuyermqi.quotawidget.ui.components.SaveActionFab
import com.kuyermqi.quotawidget.ui.components.SaveActionState

/** Single-provider page; [content] is the provider's existing configuration form. */
@Composable
internal fun ProviderDetailScreen(
    title: String,
    logo: ProviderLogo?,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val saveAction = remember { SaveActionState() }
    var showUnsavedDialog by remember { mutableStateOf(false) }

    val requestBack: () -> Unit = {
        when {
            saveAction.busy -> Unit
            saveAction.enabled -> showUnsavedDialog = true
            else -> onBack()
        }
    }

    BackHandler(onBack = requestBack)

    CompositionLocalProvider(LocalSaveActionState provides saveAction) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            floatingActionButton = { SaveActionFab(saveAction) },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding)
                    .imePadding(),
            ) {
                PageHeader(
                    title = title,
                    onBack = requestBack,
                    logo = logo,
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
                    content = content,
                )
            }
        }
        if (showUnsavedDialog) {
            SaveChangesDialog(
                onSave = {
                    showUnsavedDialog = false
                    if (!saveAction.busy) saveAction.onClick?.invoke()
                },
                onDontSave = {
                    showUnsavedDialog = false
                    onBack()
                },
                onDismiss = { showUnsavedDialog = false },
            )
        }
    }
}

@Composable
private fun SaveChangesDialog(
    onSave: () -> Unit,
    onDontSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.unsaved_changes_title)) },
        text = { Text(text = stringResource(R.string.unsaved_changes_message)) },
        confirmButton = {
            TextButton(onClick = onSave) {
                Text(text = stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDontSave) {
                Text(text = stringResource(R.string.unsaved_changes_dont_save))
            }
        },
    )
}
