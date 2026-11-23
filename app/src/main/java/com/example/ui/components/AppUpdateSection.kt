package com.example.ui.components

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.UpdateCheckResult
import com.example.UpdateChecker
import com.example.ui.theme.AntiMagerTheme
import kotlinx.coroutines.launch

@Composable
fun AppUpdateCard(
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val extendedColors = AntiMagerTheme.extendedColors
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val currentVersion = remember(context) {
        UpdateChecker.getInstalledVersionName(context)
    }

    var isChecking by remember { mutableStateOf(false) }
    var statusFeedback by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var availableUpdate by remember { mutableStateOf<UpdateCheckResult.UpdateAvailable?>(null) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, extendedColors.cardBorder),
        modifier = modifier
            .fillMaxWidth()
            .testTag("app_update_card")
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
                            imageVector = Icons.Rounded.SystemUpdate,
                            contentDescription = null,
                            tint = extendedColors.idealAccent,
                            modifier = Modifier.padding(9.dp)
                        )
                    }
                    Column {
                        Text(
                            text = stringResource(R.string.settings_update_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.settings_update_current_version, currentVersion),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("app_current_version_text")
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = extendedColors.warmSandSurface
                ) {
                    Text(
                        text = "v$currentVersion",
                        style = MaterialTheme.typography.labelSmall,
                        color = extendedColors.subtleInk,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            statusFeedback?.let { (isPositive, message) ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isPositive) extendedColors.idealAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("app_update_status_text")
                )
            }

            OutlinedButton(
                onClick = {
                    if (isChecking) return@OutlinedButton
                    isChecking = true
                    statusFeedback = null
                    coroutineScope.launch {
                        when (val result = UpdateChecker.checkLatestRelease(context)) {
                            is UpdateCheckResult.UpToDate -> {
                                val msg = context.getString(R.string.update_status_up_to_date)
                                statusFeedback = true to msg
                                onShowMessage(msg)
                            }
                            is UpdateCheckResult.UpdateAvailable -> {
                                statusFeedback = true to "Versi baru v${result.latestVersion} tersedia!"
                                availableUpdate = result
                            }
                            is UpdateCheckResult.Error -> {
                                statusFeedback = false to result.message
                                onShowMessage(result.message)
                            }
                        }
                        isChecking = false
                    }
                },
                enabled = !isChecking,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, extendedColors.idealBorder),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = extendedColors.idealAccent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("check_update_button")
            ) {
                if (isChecking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = extendedColors.idealAccent
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.update_checking_label),
                        style = MaterialTheme.typography.labelLarge
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Update,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.action_check_update),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }

    availableUpdate?.let { update ->
        UpdateAvailableDialog(
            update = update,
            onDismiss = { availableUpdate = null },
            onDownloadClick = {
                if (UpdateChecker.isTrustedGitHubUrl(update.apkUrl)) {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(update.apkUrl))
                    try {
                        context.startActivity(intent)
                        availableUpdate = null
                    } catch (_: ActivityNotFoundException) {
                        onShowMessage("Tidak ada browser yang tersedia untuk membuka tautan pembaruan.")
                    }
                } else {
                    onShowMessage("Tautan pembaruan tidak valid.")
                }
            }
        )
    }
}

@Composable
private fun UpdateAvailableDialog(
    update: UpdateCheckResult.UpdateAvailable,
    onDismiss: () -> Unit,
    onDownloadClick: () -> Unit
) {
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
                    imageVector = Icons.Rounded.NewReleases,
                    contentDescription = null,
                    tint = extendedColors.idealAccent,
                    modifier = Modifier.padding(11.dp)
                )
            }
        },
        title = {
            Text(
                text = stringResource(R.string.update_dialog_title, update.latestVersion),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 260.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.update_dialog_notes_header),
                    style = MaterialTheme.typography.labelLarge,
                    color = extendedColors.idealAccent
                )
                Text(
                    text = update.notes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("update_dialog_notes_text")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDownloadClick,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = extendedColors.idealAccent,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("download_update_button")
            ) {
                Text(
                    text = stringResource(R.string.action_download_update),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("dismiss_update_dialog_button")
            ) {
                Text(stringResource(R.string.action_later))
            }
        }
    )
}
