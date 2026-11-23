package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.AiProvider
import com.example.R
import com.example.data.ReminderSettings
import com.example.health.HealthConnectAvailability
import com.example.ui.CloudSyncStatus
import com.example.ui.theme.AntiMagerTheme
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.DeepNightBackground
import com.example.ui.theme.DeepNightSage
import com.example.ui.theme.DeepNightSurface
import com.example.ui.theme.DeepNightTerracotta
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SageGreen
import com.example.ui.theme.WarmCream
import com.example.ui.theme.WarmTerracotta

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSyncBottomSheet(
    totalSessionsCount: Int,
    isCloudSyncEnabled: Boolean = true,
    userDisplayName: String?,
    userEmail: String?,
    cloudSyncStatus: CloudSyncStatus,
    onSyncCloudNow: () -> Unit,
    onSignOut: () -> Unit,
    aiProvider: AiProvider,
    aiApiKey: String,
    onSelectAiProvider: (AiProvider) -> Unit,
    onSaveAiApiKey: (AiProvider, String) -> Unit,
    reminderSettings: ReminderSettings,
    onToggleDailyReminder: (Boolean) -> Unit,
    onChangeReminderTime: (hour: Int, minute: Int) -> Unit,
    onTestNotification5Seconds: () -> Unit,
    healthConnectAvailability: HealthConnectAvailability,
    isSyncingHealthConnect: Boolean,
    healthConnectStatusMessage: String?,
    onSyncHealthConnect: () -> Unit,
    onInstallHealthConnect: () -> Unit,
    onDismiss: () -> Unit,
    onExportJson: (onReady: (String) -> Unit) -> Unit,
    onImportJson: (String, (Boolean, String) -> Unit) -> Unit,
    onClearAll: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val extendedColors = AntiMagerTheme.extendedColors
    val themeController = AntiMagerTheme.controller
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var importJsonText by remember { mutableStateOf("") }
    var importFeedback by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var showSignOutConfirmDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.cd_close)
                    )
                }
            }

            // Cloud Sync & Google Account Card (shown only when Firebase is configured)
            if (isCloudSyncEnabled) {
                CloudAccountSyncCard(
                    userDisplayName = userDisplayName,
                    userEmail = userEmail,
                    totalSessionsCount = totalSessionsCount,
                    cloudSyncStatus = cloudSyncStatus,
                    onSyncCloudNow = onSyncCloudNow,
                    onSignOutRequest = { showSignOutConfirmDialog = true }
                )
            }

            // Multi-Provider AI Coach Card (Groq Default + Gemini Backup, Local SharedPreferences only)
            GeminiAiSettingsCard(
                selectedProvider = aiProvider,
                savedApiKey = aiApiKey,
                onSelectProvider = onSelectAiProvider,
                onSaveApiKey = onSaveAiApiKey
            )

            // Daily Reminder Notification Card (Android 10+ Banner & Sound + 5s Test)
            DailyReminderCard(
                reminderSettings = reminderSettings,
                onToggleEnabled = onToggleDailyReminder,
                onChangeTime = onChangeReminderTime,
                onTestNotification5Seconds = onTestNotification5Seconds
            )

            // Theme Switcher Card (Warm Cream vs Deep Night)
            ThemeSelectorCard(
                currentMode = themeController.themeMode,
                onSelectMode = { mode ->
                    themeController.setThemeMode(mode)
                }
            )

            // Android Health Connect / Smartwatch Sync Card
            HealthConnectSyncCard(
                availability = healthConnectAvailability,
                isSyncing = isSyncingHealthConnect,
                statusMessage = healthConnectStatusMessage,
                onSyncClick = onSyncHealthConnect,
                onInstallClick = onInstallHealthConnect
            )

            // Cloud storage summary card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, extendedColors.cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = extendedColors.idealContainer,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Storage,
                            contentDescription = null,
                            tint = extendedColors.idealAccent,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isCloudSyncEnabled) {
                                "$totalSessionsCount sesi tersimpan di Cloud"
                            } else {
                                "$totalSessionsCount sesi tersimpan di perangkat (Mode Lokal)"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isCloudSyncEnabled) {
                                stringResource(R.string.settings_storage_info)
                            } else {
                                "Riwayat latihan dan pengaturan tersimpan aman secara lokal di perangkat ini. Gunakan Ekspor JSON di bawah untuk cadangan manual."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Export JSON backup card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, extendedColors.cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.settings_export_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.settings_export_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                onExportJson { json ->
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Anti-Mager Coach JSON", json))
                                    onShowMessage("Cadangan JSON berhasil disalin ke clipboard.")
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("copy_json_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Salin JSON")
                        }
                        OutlinedButton(
                            onClick = {
                                onExportJson { json ->
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, "Cadangan Anti-Mager Coach")
                                        putExtra(Intent.EXTRA_TEXT, json)
                                    }
                                    context.startActivity(
                                        Intent.createChooser(shareIntent, "Bagikan Cadangan JSON")
                                    )
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("share_json_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Share,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Bagikan")
                        }
                    }
                }
            }

            // Import JSON card (Sync / Restore)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, extendedColors.cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.settings_import_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.settings_import_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = importJsonText,
                        onValueChange = {
                            importJsonText = it
                            importFeedback = null
                        },
                        placeholder = { Text("[{\"id\":\"...\",\"sportType\":\"RUNNING\",\"source\":\"STRAVA\",...}]") },
                        minLines = 3,
                        maxLines = 5,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("import_json_input")
                    )

                    importFeedback?.let { (success, message) ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (success) extendedColors.idealAccent else extendedColors.dangerTint
                        )
                    }

                    Button(
                        onClick = {
                            onImportJson(importJsonText) { ok, message ->
                                importFeedback = ok to message
                                if (ok) {
                                    importJsonText = ""
                                }
                            }
                        },
                        enabled = importJsonText.isNotBlank(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = extendedColors.idealAccent,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("import_json_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Impor & Gabungkan JSON")
                    }
                }
            }

            // Clear all data button (only active when sessions exist)
            if (totalSessionsCount > 0) {
                OutlinedButton(
                    onClick = { showClearConfirmDialog = true },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, extendedColors.dangerTint.copy(alpha = 0.45f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = extendedColors.dangerTint
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("clear_all_sessions_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteSweep,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_clear_title))
                }
            }

            // App Version & GitHub Releases Update Checker Card
            AppUpdateCard(onShowMessage = onShowMessage)
        }
    }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(stringResource(R.string.settings_clear_confirm_title)) },
            text = { Text(stringResource(R.string.settings_clear_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirmDialog = false
                        onClearAll()
                        onDismiss()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = extendedColors.dangerTint
                    )
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showSignOutConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutConfirmDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(stringResource(R.string.sign_out_confirm_title)) },
            text = { Text(stringResource(R.string.sign_out_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSignOutConfirmDialog = false
                        onSignOut()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = extendedColors.dangerTint
                    ),
                    modifier = Modifier.testTag("confirm_sign_out_button")
                ) {
                    Text(stringResource(R.string.action_sign_out))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutConfirmDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

private data class CloudStatusVisual(
    val label: String,
    val icon: ImageVector,
    val badgeBg: Color,
    val badgeFg: Color
)

@Composable
private fun CloudAccountSyncCard(
    userDisplayName: String?,
    userEmail: String?,
    totalSessionsCount: Int,
    cloudSyncStatus: CloudSyncStatus,
    onSyncCloudNow: () -> Unit,
    onSignOutRequest: () -> Unit
) {
    val extendedColors = AntiMagerTheme.extendedColors
    val isBusy = cloudSyncStatus == CloudSyncStatus.SYNCING || cloudSyncStatus == CloudSyncStatus.LOADING

    val visual = when (cloudSyncStatus) {
        CloudSyncStatus.SYNCED -> CloudStatusVisual(
            label = stringResource(R.string.settings_cloud_status_synced),
            icon = Icons.Rounded.CloudDone,
            badgeBg = extendedColors.idealContainer,
            badgeFg = extendedColors.idealAccent
        )
        CloudSyncStatus.LOADING, CloudSyncStatus.SYNCING -> CloudStatusVisual(
            label = stringResource(R.string.settings_cloud_status_syncing),
            icon = Icons.Rounded.CloudSync,
            badgeBg = extendedColors.warmSandSurface,
            badgeFg = extendedColors.subtleInk
        )
        CloudSyncStatus.ERROR -> CloudStatusVisual(
            label = stringResource(R.string.settings_cloud_status_error),
            icon = Icons.Rounded.CloudOff,
            badgeBg = extendedColors.lightContainer,
            badgeFg = extendedColors.lightAccent
        )
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, extendedColors.idealBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("cloud_account_sync_card")
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
                        color = extendedColors.idealContainer,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = visual.icon,
                            contentDescription = null,
                            tint = extendedColors.idealAccent,
                            modifier = Modifier.padding(9.dp)
                        )
                    }
                    Column {
                        Text(
                            text = stringResource(R.string.settings_cloud_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val accountLabel = userDisplayName?.takeIf { it.isNotBlank() }
                            ?: userEmail?.takeIf { it.isNotBlank() }
                            ?: "Akun Google Terhubung"
                        Text(
                            text = accountLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!userDisplayName.isNullOrBlank() && !userEmail.isNullOrBlank()) {
                            Text(
                                text = userEmail,
                                style = MaterialTheme.typography.labelSmall,
                                color = extendedColors.subtleInk
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = visual.badgeBg
                ) {
                    Text(
                        text = visual.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = visual.badgeFg,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = "$totalSessionsCount sesi tersinkronisasi lintas perangkat di Firebase Firestore.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onSyncCloudNow,
                    enabled = !isBusy,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = extendedColors.idealAccent,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("sync_cloud_now_button")
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Sync...")
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Sync,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.action_sync_cloud_now))
                    }
                }

                OutlinedButton(
                    onClick = onSignOutRequest,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, extendedColors.cardBorder),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("sign_out_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Logout,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.action_sign_out))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DailyReminderCard(
    reminderSettings: ReminderSettings,
    onToggleEnabled: (Boolean) -> Unit,
    onChangeTime: (hour: Int, minute: Int) -> Unit,
    onTestNotification5Seconds: () -> Unit
) {
    val extendedColors = AntiMagerTheme.extendedColors
    var showTimePickerDialog by remember { mutableStateOf(false) }

    val formattedTime = "%02d:%02d".format(reminderSettings.hour, reminderSettings.minute)
    val presetTimes = listOf(6 to 30, 7 to 30, 17 to 0, 19 to 30)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (reminderSettings.enabled) extendedColors.idealBorder else extendedColors.cardBorder
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("daily_reminder_card")
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
                        color = if (reminderSettings.enabled) {
                            extendedColors.idealContainer
                        } else {
                            extendedColors.warmSandSurface
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = if (reminderSettings.enabled) {
                                Icons.Rounded.NotificationsActive
                            } else {
                                Icons.Rounded.NotificationsNone
                            },
                            contentDescription = null,
                            tint = if (reminderSettings.enabled) {
                                extendedColors.idealAccent
                            } else {
                                extendedColors.subtleInk
                            },
                            modifier = Modifier.padding(9.dp)
                        )
                    }
                    Column(modifier = Modifier.padding(end = 8.dp)) {
                        Text(
                            text = stringResource(R.string.settings_reminder_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.settings_reminder_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Switch(
                    checked = reminderSettings.enabled,
                    onCheckedChange = onToggleEnabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = extendedColors.idealAccent
                    ),
                    modifier = Modifier.testTag("daily_reminder_switch")
                )
            }

            // Current scheduled time + TimePicker button
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = extendedColors.warmSandSurface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Schedule,
                            contentDescription = null,
                            tint = extendedColors.idealAccent,
                            modifier = Modifier.size(18.dp)
                        )
                        Column {
                            Text(
                                text = "Jam Pengingat",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "$formattedTime WIB",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    TextButton(
                        onClick = { showTimePickerDialog = true },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("change_reminder_time_button")
                    ) {
                        Text(
                            text = stringResource(R.string.action_change_reminder_time),
                            color = extendedColors.idealAccent
                        )
                    }
                }
            }

            // Quick 1-tap reminder time chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                presetTimes.forEach { (h, m) ->
                    val isSelected = reminderSettings.hour == h && reminderSettings.minute == m
                    val label = "%02d:%02d".format(h, m)
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) extendedColors.idealContainer else MaterialTheme.colorScheme.background,
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) extendedColors.idealAccent else extendedColors.cardBorder
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onChangeTime(h, m) }
                            .testTag("reminder_preset_${h}_${m}")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isSelected) {
                                    extendedColors.idealAccent
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    }
                }
            }

            // 5-second test notification button (Handler + Toast + IMPORTANCE_HIGH banner)
            OutlinedButton(
                onClick = onTestNotification5Seconds,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, extendedColors.lightAccent),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = extendedColors.lightAccent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("test_notification_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.NotificationsActive,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.action_test_notification_5s),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }

    if (showTimePickerDialog) {
        val timePickerState = rememberTimePickerState(
            initialHour = reminderSettings.hour,
            initialMinute = reminderSettings.minute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePickerDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(22.dp),
            title = {
                Text(
                    text = stringResource(R.string.reminder_time_picker_title),
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    TimePicker(state = timePickerState)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onChangeTime(timePickerState.hour, timePickerState.minute)
                        showTimePickerDialog = false
                    },
                    modifier = Modifier.testTag("confirm_reminder_time_button")
                ) {
                    Text(
                        text = stringResource(R.string.action_save_time),
                        color = extendedColors.idealAccent
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePickerDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun ThemeSelectorCard(
    currentMode: AppThemeMode,
    onSelectMode: (AppThemeMode) -> Unit
) {
    val extendedColors = AntiMagerTheme.extendedColors

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, extendedColors.cardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("theme_selector_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.settings_theme_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.settings_theme_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ThemeOptionTile(
                    title = stringResource(R.string.theme_warm_cream_label),
                    subtitle = stringResource(R.string.theme_warm_cream_desc),
                    icon = Icons.Rounded.LightMode,
                    swatchBg = WarmCream,
                    swatchCard = PureWhite,
                    swatchAccentA = SageGreen,
                    swatchAccentB = WarmTerracotta,
                    selected = currentMode == AppThemeMode.WARM_CREAM,
                    onClick = { onSelectMode(AppThemeMode.WARM_CREAM) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("theme_option_warm_cream")
                )

                ThemeOptionTile(
                    title = stringResource(R.string.theme_deep_night_label),
                    subtitle = stringResource(R.string.theme_deep_night_desc),
                    icon = Icons.Rounded.DarkMode,
                    swatchBg = DeepNightBackground,
                    swatchCard = DeepNightSurface,
                    swatchAccentA = DeepNightSage,
                    swatchAccentB = DeepNightTerracotta,
                    selected = currentMode == AppThemeMode.DEEP_NIGHT,
                    onClick = { onSelectMode(AppThemeMode.DEEP_NIGHT) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("theme_option_deep_night")
                )
            }
        }
    }
}

@Composable
private fun ThemeOptionTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    swatchBg: Color,
    swatchCard: Color,
    swatchAccentA: Color,
    swatchAccentB: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val extendedColors = AntiMagerTheme.extendedColors

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (selected) extendedColors.idealContainer else extendedColors.warmSandSurface,
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) extendedColors.idealAccent else extendedColors.cardBorder
        ),
        modifier = modifier
            .heightIn(min = 112.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Mini visual palette preview swatch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(swatchBg)
                        .border(1.dp, extendedColors.cardBorder, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(swatchCard)
                            .border(0.5.dp, swatchAccentA.copy(alpha = 0.5f), CircleShape)
                    )
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(swatchAccentA)
                    )
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(swatchAccentB)
                    )
                }

                if (selected) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = "$title aktif",
                        tint = extendedColors.idealAccent,
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = extendedColors.subtleInk,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun HealthConnectSyncCard(
    availability: HealthConnectAvailability,
    isSyncing: Boolean,
    statusMessage: String?,
    onSyncClick: () -> Unit,
    onInstallClick: () -> Unit
) {
    val extendedColors = AntiMagerTheme.extendedColors
    val (statusLabel, badgeBg, badgeFg) = when (availability) {
        HealthConnectAvailability.AVAILABLE -> Triple(
            "Siap Terhubung",
            extendedColors.idealContainer,
            extendedColors.idealAccent
        )
        HealthConnectAvailability.UPDATE_REQUIRED -> Triple(
            "Perlu Update",
            extendedColors.lightContainer,
            extendedColors.lightAccent
        )
        HealthConnectAvailability.UNAVAILABLE -> Triple(
            "Tidak Tersedia",
            extendedColors.warmSandSurface,
            extendedColors.subtleInk
        )
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, extendedColors.cardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("health_connect_card")
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
                        color = extendedColors.healthContainer,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MonitorHeart,
                            contentDescription = null,
                            tint = extendedColors.healthTint,
                            modifier = Modifier.padding(9.dp)
                        )
                    }
                    Text(
                        text = stringResource(R.string.settings_hc_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = badgeBg
                ) {
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = badgeFg,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = stringResource(R.string.settings_hc_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            statusMessage?.takeIf { it.isNotBlank() }?.let { msg ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = extendedColors.warmSandSurface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }

            if (availability == HealthConnectAvailability.UPDATE_REQUIRED) {
                OutlinedButton(
                    onClick = onInstallClick,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, extendedColors.lightAccent),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = extendedColors.lightAccent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("install_health_connect_button")
                ) {
                    Text(
                        text = stringResource(R.string.action_install_health_connect),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            } else {
                Button(
                    onClick = onSyncClick,
                    enabled = !isSyncing && availability == HealthConnectAvailability.AVAILABLE,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = extendedColors.idealAccent,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("sync_health_connect_button")
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Menyinkronkan...",
                            style = MaterialTheme.typography.labelLarge
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Sync,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.action_sync_health_connect),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}
