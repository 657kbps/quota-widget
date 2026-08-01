package com.kuyermqi.quotawidget.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.kuyermqi.quotawidget.R
import com.kuyermqi.quotawidget.domain.CurrencyPreference
import com.kuyermqi.quotawidget.platform.PlatformIds
import com.kuyermqi.quotawidget.platform.PlatformRegistry
import com.kuyermqi.quotawidget.platform.QuotaPlatform
import com.kuyermqi.quotawidget.settings.DeepSeekSettings
import com.kuyermqi.quotawidget.settings.PlatformSettingsRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsRepository: PlatformSettingsRepository,
    onSettingsSaved: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var savedSettings by remember { mutableStateOf(DeepSeekSettings()) }
    var draftApiKey by remember { mutableStateOf("") }
    var draftCurrency by remember { mutableStateOf(CurrencyPreference.CNY) }
    var expandedPlatformId by remember { mutableStateOf<String?>(null) }
    var showPlatformTip by remember { mutableStateOf(false) }
    var tipLoaded by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val settings = settingsRepository.getDeepSeekSettings()
        savedSettings = settings
        draftApiKey = settings.apiKey
        draftCurrency = settings.currency
        showPlatformTip = !settingsRepository.isPlatformTipDismissed()
        tipLoaded = true
        loaded = true
    }

    val isDirty = loaded && (
        draftApiKey != savedSettings.apiKey || draftCurrency != savedSettings.currency
        )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("配额监控") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            AnimatedVisibility(
                visible = tipLoaded && showPlatformTip,
                enter = EnterTransition.None,
                exit = shrinkVertically(animationSpec = tween(220)) + fadeOut(tween(160)),
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_tip_lightbulb),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier
                                .padding(start = 4.dp, end = 10.dp)
                                .size(22.dp),
                        )
                        Text(
                            text = "在下方修改对应平台的配置",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 12.dp),
                        )
                        IconButton(
                            onClick = {
                                showPlatformTip = false
                                scope.launch {
                                    settingsRepository.setPlatformTipDismissed(true)
                                }
                            },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_close),
                                contentDescription = "关闭提示",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = if (tipLoaded && showPlatformTip) 0.dp else 8.dp)
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(PlatformRegistry.platforms, key = { it.id }) { platform ->
                    PlatformConfigItem(
                        platform = platform,
                        expanded = expandedPlatformId == platform.id,
                        onToggle = {
                            expandedPlatformId =
                                if (expandedPlatformId == platform.id) null else platform.id
                        },
                        apiKey = draftApiKey,
                        onApiKeyChange = { draftApiKey = it },
                        currency = draftCurrency,
                        onCurrencyChange = { draftCurrency = it },
                        isDirty = isDirty,
                        onSave = {
                            scope.launch {
                                val next = DeepSeekSettings(
                                    apiKey = draftApiKey.trim(),
                                    currency = draftCurrency,
                                )
                                settingsRepository.saveDeepSeekSettings(next)
                                savedSettings = next
                                draftApiKey = next.apiKey
                                onSettingsSaved()
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PlatformConfigItem(
    platform: QuotaPlatform,
    expanded: Boolean,
    onToggle: () -> Unit,
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    currency: CurrencyPreference,
    onCurrencyChange: (CurrencyPreference) -> Unit,
    isDirty: Boolean,
    onSave: () -> Unit,
) {
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = 240),
        label = "expandArrow",
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = platform.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Icon(
                    painter = painterResource(R.drawable.ic_expand_more),
                    contentDescription = if (expanded) "收起" else "展开",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.rotate(arrowRotation),
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(animationSpec = tween(260)) + fadeIn(tween(220)),
                exit = shrinkVertically(animationSpec = tween(240)) + fadeOut(tween(160)),
            ) {
                if (platform.id == PlatformIds.DEEPSEEK) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    ) {
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = onApiKeyChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("API Key") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "显示货币",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CurrencyPreference.entries.forEach { option ->
                                FilterChip(
                                    selected = currency == option,
                                    onClick = { onCurrencyChange(option) },
                                    label = { Text(option.name) },
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "若接口未返回所选货币，将自动回退到另一币种。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            Button(
                                onClick = onSave,
                                enabled = isDirty,
                            ) {
                                Text("保存")
                            }
                        }
                    }
                }
            }
        }
    }
}
