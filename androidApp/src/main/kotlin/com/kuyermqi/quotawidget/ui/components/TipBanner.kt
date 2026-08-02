package com.kuyermqi.quotawidget.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.kuyermqi.quotawidget.R

@Composable
fun TipBanner(
    visible: Boolean,
    @DrawableRes iconRes: Int,
    message: String,
    containerColor: Color,
    contentColor: Color,
    onDismiss: (() -> Unit)? = null,
    onCardClick: (() -> Unit)? = null,
    linkText: String? = null,
    onLinkClick: (() -> Unit)? = null,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null,
) {
    AnimatedVisibility(
        visible = visible,
        enter = EnterTransition.None,
        exit = shrinkVertically(animationSpec = tween(220)) + fadeOut(tween(160)),
    ) {
        val cardModifier = Modifier.fillMaxWidth().let { base ->
            if (onCardClick != null) base.clickable(onClick = onCardClick) else base
        }
        Card(
            modifier = cardModifier,
            colors = CardDefaults.cardColors(containerColor = containerColor),
        ) {
            val hasExtraContent =
                (linkText != null && onLinkClick != null) ||
                    (actionText != null && onActionClick != null)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(
                            top = 12.dp,
                            bottom = if (hasExtraContent) 4.dp else 12.dp,
                        ),
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            painter = painterResource(iconRes),
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier
                                .padding(start = 4.dp, end = 10.dp)
                                .size(22.dp),
                        )
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = contentColor,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp),
                        )
                    }
                    if (linkText != null && onLinkClick != null) {
                        Text(
                            text = linkText,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                textDecoration = TextDecoration.Underline,
                            ),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(start = 36.dp, top = 4.dp, bottom = 4.dp)
                                .clickable(onClick = onLinkClick),
                        )
                    }
                    if (actionText != null && onActionClick != null) {
                        TextButton(
                            onClick = onActionClick,
                            modifier = Modifier.align(Alignment.End),
                        ) {
                            Text(
                                text = actionText,
                                color = contentColor,
                            )
                        }
                    }
                }
                if (onDismiss != null) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .size(32.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_close),
                            contentDescription = stringResource(R.string.tip_dismiss),
                            tint = contentColor,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}
