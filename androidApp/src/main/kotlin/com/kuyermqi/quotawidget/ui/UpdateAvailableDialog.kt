package com.kuyermqi.quotawidget.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.kuyermqi.quotawidget.R

@Composable
fun UpdateAvailableDialog(
    versionName: String,
    onIgnoreVersion: () -> Unit,
    onNeverPrompt: () -> Unit,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.update_available_title)) },
        text = {
            Text(stringResource(R.string.update_available_message, versionName))
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center,
            ) {
                TextButton(onClick = onDownload) {
                    Text(stringResource(R.string.update_download))
                }
                TextButton(onClick = onIgnoreVersion) {
                    Text(stringResource(R.string.update_ignore_version))
                }
                TextButton(onClick = onNeverPrompt) {
                    Text(stringResource(R.string.update_never_prompt))
                }
            }
        },
    )
}
