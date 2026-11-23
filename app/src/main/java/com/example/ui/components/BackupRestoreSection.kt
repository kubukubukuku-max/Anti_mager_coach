package com.example.ui.components

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.SaveAlt
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
import com.example.data.AppBackupCodec
import com.example.data.AppBackupSnapshot
import com.example.ui.theme.AntiMagerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

private const val MAX_BACKUP_FILE_BYTES = 5 * 1024 * 1024 // 5 MB safety limit

private val BACKUP_OPEN_MIME_TYPES = arrayOf(
    "application/json",
    "text/plain",
    "application/octet-stream"
)

@Composable
fun BackupRestoreCard(
    currentSessionsCount: Int,
    onBuildBackupJson: () -> String,
    onParseBackupJson: (String) -> Result<AppBackupSnapshot>,
    onConfirmRestore: (AppBackupSnapshot, (Boolean, String) -> Unit) -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val extendedColors = AntiMagerTheme.extendedColors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isBusy by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var pendingRestoreSnapshot by remember { mutableStateOf<AppBackupSnapshot?>(null) }

    // Storage Access Framework: Save Backup (.json) directly to phone folder or Google Drive
    val createBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            isBusy = true
            feedback = null
            try {
                val jsonContent = onBuildBackupJson()
                writeUtf8ToUri(context, uri, jsonContent)
                val msg = "Backup (.json) berhasil disimpan! (Tanpa API Key demi keamanan)"
                feedback = true to msg
                onShowMessage(msg)
            } catch (e: Exception) {
                val msg = "Gagal menyimpan file backup: ${e.message?.take(90) ?: "tidak dapat menulis file"}."
                feedback = false to msg
                onShowMessage(msg)
            } finally {
                isBusy = false
            }
        }
    }

    // Storage Access Framework: Open Backup (.json) from WhatsApp, Downloads, or Google Drive
    val openBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            isBusy = true
            feedback = null
            try {
                val rawText = readUtf8FromUri(context, uri)
                val parsed = onParseBackupJson(rawText)
                parsed.fold(
                    onSuccess = { snapshot ->
                        pendingRestoreSnapshot = snapshot
                    },
                    onFailure = { err ->
                        val msg = err.message ?: "File backup tidak valid."
                        feedback = false to msg
                        onShowMessage(msg)
                    }
                )
            } catch (e: Exception) {
                val msg = "Gagal membaca file backup: ${e.message?.take(90) ?: "file tidak dapat dibuka"}."
                feedback = false to msg
                onShowMessage(msg)
            } finally {
                isBusy = false
            }
        }
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, extendedColors.idealBorder),
        modifier = modifier
            .fillMaxWidth()
            .testTag("backup_restore_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = extendedColors.idealContainer,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Backup,
                        contentDescription = null,
                        tint = extendedColors.idealAccent,
                        modifier = Modifier.padding(9.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_backup_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.settings_backup_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Primary Export CTA: Simpan Backup (.json)
            Button(
                onClick = {
                    try {
                        createBackupLauncher.launch(AppBackupCodec.defaultBackupFileName())
                    } catch (e: Exception) {
                        val msg = "Tidak dapat membuka pemilih folder: ${e.message?.take(80) ?: "perangkat tidak mendukung"}."
                        feedback = false to msg
                        onShowMessage(msg)
                    }
                },
                enabled = !isBusy,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = extendedColors.idealAccent,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("save_backup_file_button")
            ) {
                if (isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Memproses...")
                } else {
                    Icon(
                        imageVector = Icons.Rounded.SaveAlt,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.action_save_backup_file),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            // Secondary Import CTA: Pulihkan dari Backup
            OutlinedButton(
                onClick = {
                    try {
                        openBackupLauncher.launch(BACKUP_OPEN_MIME_TYPES)
                    } catch (e: Exception) {
                        val msg = "Tidak dapat membuka pemilih file: ${e.message?.take(80) ?: "perangkat tidak mendukung"}."
                        feedback = false to msg
                        onShowMessage(msg)
                    }
                },
                enabled = !isBusy,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, extendedColors.idealAccent),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = extendedColors.idealAccent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("restore_backup_file_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Restore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.action_restore_backup_file),
                    style = MaterialTheme.typography.labelLarge
                )
            }

            feedback?.let { (ok, message) ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (ok) extendedColors.idealContainer else extendedColors.lightContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("backup_restore_feedback")
                ) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (ok) extendedColors.idealAccent else extendedColors.dangerTint,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }

    // Confirmation Dialog before overwriting existing data
    pendingRestoreSnapshot?.let { snapshot ->
        val dateNote = snapshot.exportedAtIso.takeIf { it.length >= 10 }?.substring(0, 10)
        val details = buildString {
            append("File backup valid ditemukan:\n")
            append("• ${snapshot.workoutSessions.size} sesi latihan")
            if (dateNote != null) {
                append("\n• Dibuat pada: $dateNote")
            }
            if (snapshot.hasPreferencesSection) {
                append("\n• Preferensi tema & pengingat harian ikut dipulihkan")
            }
            append("\n\nData saat ini ($currentSessionsCount sesi) akan ditimpa dengan isi backup. API Key AI yang tersimpan di perangkat ini tidak akan berubah.")
        }

        AlertDialog(
            onDismissRequest = { pendingRestoreSnapshot = null },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(22.dp),
            title = {
                Text(
                    text = stringResource(R.string.restore_confirm_title),
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(
                    text = details,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val toRestore = snapshot
                        pendingRestoreSnapshot = null
                        isBusy = true
                        onConfirmRestore(toRestore) { ok, message ->
                            isBusy = false
                            feedback = ok to message
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = extendedColors.idealAccent
                    ),
                    modifier = Modifier.testTag("confirm_restore_backup_button")
                ) {
                    Text(stringResource(R.string.restore_confirm_action))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingRestoreSnapshot = null },
                    modifier = Modifier.testTag("cancel_restore_backup_button")
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

private suspend fun writeUtf8ToUri(context: Context, uri: Uri, content: String) =
    withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(uri, "wt")?.use { stream ->
            stream.write(content.toByteArray(Charsets.UTF_8))
            stream.flush()
        } ?: throw IllegalStateException("Tidak dapat membuka tujuan penyimpanan.")
    }

private suspend fun readUtf8FromUri(context: Context, uri: Uri): String =
    withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val read = stream.read(buffer)
                if (read == -1) break
                total += read
                if (total > MAX_BACKUP_FILE_BYTES) {
                    throw IllegalArgumentException("Ukuran file terlalu besar (maksimal 5 MB).")
                }
                out.write(buffer, 0, read)
            }
            out.toString(Charsets.UTF_8.name())
        } ?: throw IllegalStateException("Tidak dapat membaca isi file yang dipilih.")
    }
