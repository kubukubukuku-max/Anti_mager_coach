package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import com.example.AiProvider
import com.example.R
import com.example.ui.WeeklyReviewUiState
import com.example.ui.theme.AntiMagerTheme

@Composable
fun GeminiAiSettingsCard(
    selectedProvider: AiProvider,
    savedApiKey: String,
    onSelectProvider: (AiProvider) -> Unit,
    onSaveApiKey: (AiProvider, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val extendedColors = AntiMagerTheme.extendedColors
    val isActive = savedApiKey.isNotBlank()

    var keyInput by remember(selectedProvider, savedApiKey) { mutableStateOf(savedApiKey) }
    var isKeyVisible by remember(selectedProvider) { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = if (isActive) extendedColors.idealBorder else extendedColors.cardBorder
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("gemini_ai_settings_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isActive) extendedColors.idealContainer else extendedColors.warmSandSurface,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = if (isActive) extendedColors.idealAccent else extendedColors.subtleInk,
                            modifier = Modifier.padding(9.dp)
                        )
                    }
                    Column(modifier = Modifier.padding(end = 6.dp)) {
                        Text(
                            text = stringResource(R.string.settings_gemini_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.settings_gemini_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Status Indicator Pill
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = if (isActive) extendedColors.idealContainer else extendedColors.warmSandSurface,
                modifier = Modifier.testTag("gemini_status_badge")
            ) {
                val statusText = if (isActive) {
                    "${stringResource(R.string.settings_gemini_status_active)} • ${selectedProvider.displayName}"
                } else {
                    stringResource(R.string.settings_gemini_status_offline)
                }
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isActive) extendedColors.idealAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                )
            }

            // Provider Selector (Groq Default vs Gemini Backup)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.settings_ai_provider_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AiProvider.entries.forEach { provider ->
                        val isSelected = provider == selectedProvider
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) extendedColors.idealContainer else extendedColors.warmSandSurface,
                            border = BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) extendedColors.idealAccent else extendedColors.cardBorder
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 52.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onSelectProvider(provider) }
                                .testTag("ai_provider_${provider.name.lowercase()}")
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = if (provider == AiProvider.GROQ) {
                                        "${provider.displayName} (Default)"
                                    } else {
                                        "${provider.displayName} (Cadangan)"
                                    },
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                                    ),
                                    color = if (isSelected) {
                                        extendedColors.idealAccent
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    maxLines = 1
                                )
                                Text(
                                    text = provider.modelSummary,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            // Password-masked API Key field with peek eye toggle
            val keyLabel = when (selectedProvider) {
                AiProvider.GROQ -> stringResource(R.string.settings_groq_key_label)
                AiProvider.GEMINI -> stringResource(R.string.settings_gemini_key_label)
            }
            val keyPlaceholder = when (selectedProvider) {
                AiProvider.GROQ -> stringResource(R.string.settings_groq_key_placeholder)
                AiProvider.GEMINI -> stringResource(R.string.settings_gemini_key_placeholder)
            }

            OutlinedTextField(
                value = keyInput,
                onValueChange = { keyInput = it },
                label = { Text(keyLabel) },
                placeholder = { Text(keyPlaceholder) },
                singleLine = true,
                visualTransformation = if (isKeyVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Key,
                        contentDescription = null,
                        tint = extendedColors.subtleInk,
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = {
                    IconButton(
                        onClick = { isKeyVisible = !isKeyVisible },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("toggle_gemini_key_visibility_button")
                    ) {
                        Icon(
                            imageVector = if (isKeyVisible) {
                                Icons.Rounded.VisibilityOff
                            } else {
                                Icons.Rounded.Visibility
                            },
                            contentDescription = stringResource(R.string.cd_toggle_api_key_visibility),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("gemini_api_key_input")
            )

            Text(
                text = "Key ${selectedProvider.displayName} disimpan lokal hanya di perangkatmu (tidak diunggah ke cloud). Kosongkan lalu simpan untuk kembali ke Mode Offline.",
                style = MaterialTheme.typography.labelSmall,
                color = extendedColors.subtleInk
            )

            Button(
                onClick = { onSaveApiKey(selectedProvider, keyInput) },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = extendedColors.idealAccent,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("save_gemini_key_button")
            ) {
                Text(
                    text = "${stringResource(R.string.action_save_api_key)} ${selectedProvider.displayName}",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
fun WeeklyAiReviewDialog(
    state: WeeklyReviewUiState,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit
) {
    if (state is WeeklyReviewUiState.Idle) return

    val extendedColors = AntiMagerTheme.extendedColors

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(22.dp),
        icon = {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = extendedColors.idealContainer,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = extendedColors.idealAccent,
                    modifier = Modifier.padding(11.dp)
                )
            }
        },
        title = {
            Text(
                text = stringResource(R.string.weekly_ai_review_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            when (state) {
                is WeeklyReviewUiState.Loading -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            color = extendedColors.idealAccent,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = stringResource(R.string.weekly_ai_review_loading),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                is WeeklyReviewUiState.MissingApiKey -> {
                    Text(
                        text = stringResource(R.string.weekly_ai_review_missing_key),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("weekly_ai_missing_key_text")
                    )
                }
                is WeeklyReviewUiState.Success -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = extendedColors.warmSandSurface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = state.reviewText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .padding(14.dp)
                                    .testTag("weekly_ai_review_content")
                            )
                        }
                    }
                }
                is WeeklyReviewUiState.Error -> {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = extendedColors.lightAccent,
                        modifier = Modifier.testTag("weekly_ai_error_text")
                    )
                }
                WeeklyReviewUiState.Idle -> Unit
            }
        },
        confirmButton = {
            when (state) {
                is WeeklyReviewUiState.MissingApiKey -> {
                    Button(
                        onClick = {
                            onDismiss()
                            onOpenSettings()
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = extendedColors.idealAccent,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("weekly_ai_open_settings_button")
                    ) {
                        Text(stringResource(R.string.action_open_settings))
                    }
                }
                is WeeklyReviewUiState.Error -> {
                    Button(
                        onClick = onRetry,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = extendedColors.idealAccent,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("weekly_ai_retry_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.action_retry))
                    }
                }
                is WeeklyReviewUiState.Success -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(
                            onClick = onRetry,
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text(
                                text = "Muat Ulang",
                                color = extendedColors.idealAccent
                            )
                        }
                        Button(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = extendedColors.idealAccent,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("weekly_ai_close_button")
                        ) {
                            Text(stringResource(R.string.cd_close))
                        }
                    }
                }
                else -> {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text(stringResource(R.string.cd_close))
                    }
                }
            }
        },
        dismissButton = {
            if (state is WeeklyReviewUiState.MissingApiKey || state is WeeklyReviewUiState.Error) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(stringResource(R.string.cd_close))
                }
            }
        }
    )
}
