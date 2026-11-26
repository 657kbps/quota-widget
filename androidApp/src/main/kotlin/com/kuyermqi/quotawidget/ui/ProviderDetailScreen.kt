package com.kuyermqi.quotawidget.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kuyermqi.quotawidget.R
import com.kuyermqi.quotawidget.ui.components.LocalSaveActionState
import com.kuyermqi.quotawidget.ui.components.LocalWidgetTabScrollContext
import com.kuyermqi.quotawidget.ui.components.PageHeader
import com.kuyermqi.quotawidget.ui.components.ProviderLogo
import com.kuyermqi.quotawidget.ui.components.SaveActionFab
import com.kuyermqi.quotawidget.ui.components.SaveActionState
import com.kuyermqi.quotawidget.ui.components.WidgetTabScrollContext
import kotlin.math.roundToInt

private val DETAIL_TOP_PADDING = 8.dp
private val DETAIL_BASE_BOTTOM_PADDING = 16.dp
private val FAB_CLEARANCE_GAP = 16.dp

private data class DetailFabClearanceMetrics(
    val viewportHeightPx: Int,
    val viewportTopInRootPx: Float,
    val contentHeightPx: Int,
    val fabVisible: Boolean,
    val fabTopInRootPx: Float,
)

private fun computeExtraFabClearanceDp(
    density: Density,
    metrics: DetailFabClearanceMetrics,
): Dp {
    if (!metrics.fabVisible || metrics.viewportHeightPx <= 0 || metrics.contentHeightPx <= 0) {
        return 0.dp
    }
    return with(density) {
        val topPaddingPx = DETAIL_TOP_PADDING.roundToPx()
        val baseBottomPx = DETAIL_BASE_BOTTOM_PADDING.roundToPx()
        val fabGapPx = FAB_CLEARANCE_GAP.roundToPx()
        val contentBottomAtRestPx = topPaddingPx + metrics.contentHeightPx
        val fabTopInViewportPx = (metrics.fabTopInRootPx - metrics.viewportTopInRootPx).roundToInt()
        val overlapsFab = fabTopInViewportPx < metrics.viewportHeightPx &&
            contentBottomAtRestPx > fabTopInViewportPx
        val exceedsViewport = contentBottomAtRestPx + baseBottomPx > metrics.viewportHeightPx
        if (!overlapsFab && !exceedsViewport) {
            0.dp
        } else {
            val fabOccupiedFromBottomPx = (metrics.viewportHeightPx - fabTopInViewportPx).coerceAtLeast(0)
            val neededClearancePx = fabOccupiedFromBottomPx + fabGapPx
            (neededClearancePx - baseBottomPx).coerceAtLeast(0).toDp()
        }
    }
}

/** Single-provider page; [content] is the provider's existing configuration form. */
@Composable
internal fun ProviderDetailScreen(
    title: String,
    logo: ProviderLogo?,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val saveAction = remember { SaveActionState() }
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    var viewportCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var viewportHeightPx by remember { mutableIntStateOf(0) }
    var viewportTopInRootPx by remember { mutableFloatStateOf(0f) }
    var contentHeightPx by remember { mutableIntStateOf(0) }
    var fabTopInRootPx by remember { mutableFloatStateOf(0f) }
    var fabHeightPx by remember { mutableIntStateOf(0) }
    val tabScrollContext = remember(scrollState) {
        WidgetTabScrollContext(scrollState) { viewportCoords }
    }
    val extraBottomDp = computeExtraFabClearanceDp(
        density = density,
        metrics = DetailFabClearanceMetrics(
            viewportHeightPx = viewportHeightPx,
            viewportTopInRootPx = viewportTopInRootPx,
            contentHeightPx = contentHeightPx,
            fabVisible = saveAction.onClick != null && fabHeightPx > 0,
            fabTopInRootPx = fabTopInRootPx,
        ),
    )
    var showUnsavedDialog by remember { mutableStateOf(false) }

    val requestBack: () -> Unit = {
        when {
            saveAction.busy -> Unit
            saveAction.enabled -> showUnsavedDialog = true
            else -> onBack()
        }
    }

    BackHandler(onBack = requestBack)

    CompositionLocalProvider(
        LocalSaveActionState provides saveAction,
        LocalWidgetTabScrollContext provides tabScrollContext,
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            floatingActionButton = {
                Box(
                    modifier = Modifier.onGloballyPositioned { coords ->
                        fabHeightPx = coords.size.height
                        fabTopInRootPx = coords.positionInRoot().y
                    },
                ) {
                    SaveActionFab(saveAction)
                }
            },
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
                        .onGloballyPositioned { coords ->
                            viewportCoords = coords
                            viewportHeightPx = coords.size.height
                            viewportTopInRootPx = coords.positionInRoot().y
                        }
                        .verticalScroll(scrollState)
                        .padding(
                            start = 16.dp,
                            end = 16.dp,
                            top = DETAIL_TOP_PADDING,
                            bottom = DETAIL_BASE_BOTTOM_PADDING,
                        ),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { coords ->
                                contentHeightPx = coords.size.height
                            },
                        content = content,
                    )
                    if (extraBottomDp > 0.dp) {
                        Spacer(modifier = Modifier.height(extraBottomDp))
                    }
                }
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
