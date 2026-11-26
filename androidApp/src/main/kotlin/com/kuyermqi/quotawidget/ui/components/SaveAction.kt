package com.kuyermqi.quotawidget.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kuyermqi.quotawidget.R

/** Holds the save action registered by the current provider form. */
@Stable
class SaveActionState {
    internal var owner by mutableStateOf<Any?>(null)
        private set
    internal var label by mutableStateOf("")
        private set
    internal var enabled by mutableStateOf(false)
        private set
    internal var busy by mutableStateOf(false)
        private set
    internal var onClick by mutableStateOf<(() -> Unit)?>(null)
        private set

    internal fun update(
        owner: Any,
        label: String,
        enabled: Boolean,
        busy: Boolean,
        onClick: () -> Unit,
    ) {
        this.owner = owner
        this.label = label
        this.enabled = enabled
        this.busy = busy
        this.onClick = onClick
    }

    internal fun clear(owner: Any) {
        if (this.owner !== owner) return
        this.owner = null
        enabled = false
        busy = false
        onClick = null
    }
}

internal val LocalSaveActionState = compositionLocalOf<SaveActionState?> { null }

/**
 * Registers a save action for the enclosing provider page, which shows it as a FAB.
 * Falls back to an inline button outside such a page.
 */
@Suppress("FunctionNaming")
@Composable
fun SaveAction(
    label: String,
    enabled: Boolean,
    busy: Boolean,
    onClick: () -> Unit,
    topSpacing: Dp = 0.dp,
) {
    val state = LocalSaveActionState.current
    if (state == null) {
        if (topSpacing > 0.dp) {
            Spacer(modifier = Modifier.height(topSpacing))
        }
        InlineSaveAction(
            label = label,
            enabled = enabled,
            busy = busy,
            onClick = onClick,
        )
        return
    }
    val owner = remember { Any() }
    SideEffect { state.update(owner, label, enabled, busy, onClick) }
    DisposableEffect(owner) {
        onDispose { state.clear(owner) }
    }
}

/** Floating action button mirroring the save action registered by the page's form. */
@Composable
internal fun SaveActionFab(state: SaveActionState) {
    AnimatedVisibility(
        visible = state.onClick != null,
        enter = fadeIn() + scaleIn(initialScale = 0.8f),
        exit = fadeOut() + scaleOut(targetScale = 0.8f),
    ) {
        val scheme = MaterialTheme.colorScheme
        val active = state.enabled
        val clickable = active && !state.busy
        ExtendedFloatingActionButton(
            onClick = { if (clickable) state.onClick?.invoke() },
            icon = {
                if (state.busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = LocalContentColor.current,
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.ic_save),
                        contentDescription = null,
                    )
                }
            },
            text = { Text(text = state.label) },
            containerColor = if (active) scheme.primaryContainer else scheme.surfaceContainerHighest,
            contentColor = if (active) scheme.onPrimaryContainer else scheme.onSurface.copy(alpha = 0.38f),
        )
    }
}

@Composable
private fun InlineSaveAction(
    label: String,
    enabled: Boolean,
    busy: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
            )
            Spacer(modifier = Modifier.width(12.dp))
        }
        Button(
            onClick = onClick,
            enabled = enabled && !busy,
        ) {
            Text(text = label)
        }
    }
}
