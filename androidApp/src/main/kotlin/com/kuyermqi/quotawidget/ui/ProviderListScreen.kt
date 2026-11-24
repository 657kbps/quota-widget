package com.kuyermqi.quotawidget.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kuyermqi.quotawidget.R
import com.kuyermqi.quotawidget.ui.components.providerLogo

/** One provider row on the home list; [searchText] holds all names used for matching. */
internal data class ProviderEntry(
    val platformId: String,
    val name: String,
    val summary: String?,
    val configured: Boolean,
    val searchText: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProviderListScreen(
    entries: List<ProviderEntry>,
    query: String,
    onQueryChange: (String) -> Unit,
    unconfiguredExpanded: Boolean,
    onUnconfiguredExpandedChange: (Boolean) -> Unit,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onOpenProvider: (String) -> Unit,
    onOpenAppSettings: () -> Unit,
    listState: LazyListState,
    tips: (@Composable () -> Unit)? = null,
) {
    val normalizedQuery = query.trim()
    val searching = normalizedQuery.isNotEmpty()
    fun matches(entry: ProviderEntry): Boolean =
        !searching || entry.searchText.contains(normalizedQuery, ignoreCase = true)

    val configuredEntries = entries.filter { it.configured && matches(it) }
    val unconfiguredEntries = entries.filter { !it.configured && matches(it) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_title)) },
                actions = {
                    IconButton(onClick = onOpenAppSettings) {
                        Icon(
                            painter = painterResource(R.drawable.ic_settings),
                            contentDescription = stringResource(R.string.app_settings_open),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding(),
        ) {
            ProviderSearchField(
                query = query,
                onQueryChange = onQueryChange,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            )
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (tips != null && !searching) {
                        item(key = "tips") {
                            tips()
                        }
                    }
                    if (searching) {
                        val results = configuredEntries + unconfiguredEntries
                        if (results.isEmpty()) {
                            item(key = "no_results") {
                                NoSearchResults()
                            }
                        } else {
                            item(key = "search_results") {
                                ProviderRows(
                                    rows = results,
                                    onOpenProvider = onOpenProvider,
                                )
                            }
                        }
                    } else if (configuredEntries.isEmpty()) {
                        item(key = "all_providers") {
                            ProviderRows(
                                rows = unconfiguredEntries,
                                onOpenProvider = onOpenProvider,
                            )
                        }
                    } else {
                        item(key = "configured") {
                            ProviderSection(
                                title = stringResource(R.string.home_section_configured),
                                count = configuredEntries.size,
                                rows = configuredEntries,
                                expanded = true,
                                onToggle = null,
                                onOpenProvider = onOpenProvider,
                            )
                        }
                        if (unconfiguredEntries.isNotEmpty()) {
                            item(key = "unconfigured") {
                                ProviderSection(
                                    title = stringResource(R.string.home_section_unconfigured),
                                    count = unconfiguredEntries.size,
                                    rows = unconfiguredEntries,
                                    expanded = unconfiguredExpanded,
                                    onToggle = { onUnconfiguredExpandedChange(!unconfiguredExpanded) },
                                    onOpenProvider = onOpenProvider,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val trailingIcon: (@Composable () -> Unit)? = if (query.isEmpty()) {
        null
    } else {
        {
            IconButton(onClick = { onQueryChange("") }) {
                Icon(
                    painter = painterResource(R.drawable.ic_close),
                    contentDescription = stringResource(R.string.home_search_clear),
                )
            }
        }
    }

    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(stringResource(R.string.home_search_placeholder)) },
        leadingIcon = {
            Icon(
                painter = painterResource(R.drawable.ic_search),
                contentDescription = null,
            )
        },
        trailingIcon = trailingIcon,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge,
        shape = RoundedCornerShape(28.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            focusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            focusedTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unfocusedTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
    )
}

@Composable
private fun ProviderSection(
    title: String,
    count: Int,
    rows: List<ProviderEntry>,
    expanded: Boolean,
    onToggle: (() -> Unit)?,
    onOpenProvider: (String) -> Unit,
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "providerSectionChevron",
    )

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 24.dp)
                .clip(RoundedCornerShape(16.dp))
                .then(
                    if (onToggle != null) {
                        Modifier.clickable(role = Role.Button, onClick = onToggle)
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (onToggle != null) {
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    painter = painterResource(R.drawable.ic_expand_more),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.rotate(chevronRotation),
                )
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(animationSpec = tween(240)) + fadeIn(tween(200)),
            exit = shrinkVertically(animationSpec = tween(220)) + fadeOut(tween(140)),
        ) {
            ProviderRows(
                rows = rows,
                onOpenProvider = onOpenProvider,
            )
        }
    }
}

@Composable
private fun ProviderRows(
    rows: List<ProviderEntry>,
    onOpenProvider: (String) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    Column(modifier = Modifier.fillMaxWidth()) {
        rows.forEachIndexed { index, entry ->
            if (index > 0) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
            }
            ProviderRow(
                entry = entry,
                onClick = {
                    focusManager.clearFocus()
                    onOpenProvider(entry.platformId)
                },
            )
        }
    }
}

@Composable
private fun ProviderRow(
    entry: ProviderEntry,
    onClick: () -> Unit,
) {
    val supportingText = if (entry.configured) {
        entry.summary ?: stringResource(R.string.widget_updated_never)
    } else {
        stringResource(R.string.home_provider_add_hint)
    }

    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        leadingContent = { ProviderAvatar(entry) },
        supportingContent = {
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        content = {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}

@Composable
private fun ProviderAvatar(entry: ProviderEntry) {
    val logo = providerLogo(entry.platformId)
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        if (logo != null) {
            Image(
                painter = painterResource(logo.drawableRes),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                contentScale = ContentScale.Fit,
                colorFilter = if (logo.monochrome) {
                    ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    null
                },
            )
        } else {
            Text(
                text = entry.name.take(1),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NoSearchResults() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_search),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(28.dp),
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.home_search_no_results),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
