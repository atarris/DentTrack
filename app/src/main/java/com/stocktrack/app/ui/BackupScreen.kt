@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.stocktrack.app.ui

import androidx.compose.ui.res.stringResource
import com.stocktrack.app.R
import android.app.Activity
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stocktrack.app.StockViewModel
import com.stocktrack.app.data.AppConfig
import com.stocktrack.app.data.BackupSchedule
import com.stocktrack.app.data.StockImporter

/** Admin only: automatic backup schedule, destinations, "back up now", stock import from CSV and restore. */
@Composable
fun BackupScreen(vm: StockViewModel, config: AppConfig) {
    val context = LocalContext.current
    var pendingRestore by remember { mutableStateOf<Uri?>(null) }

    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.setBackupFolder(uri)
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingRestore = uri
    }
    val driveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) vm.onDriveConsentResult(result.data)
        else vm.showMessage(context.getString(R.string.drive_not_connected))
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ------------------------------------------------------------ schedule
        BackupPanel(
            title = stringResource(R.string.auto_backup_title),
            description = stringResource(R.string.auto_backup_text)
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                BackupSchedule.entries.forEach { schedule ->
                    FilterChip(
                        selected = config.backupSchedule == schedule,
                        onClick = { vm.setBackupSchedule(schedule) },
                        label = { Text(stringResource(schedule.label), maxLines = 1, softWrap = false) }
                    )
                }
            }
        }

        // -------------------------------------------------------- destinations
        BackupPanel(
            title = stringResource(R.string.destinations_title),
            description = stringResource(R.string.destinations_text)
        ) {
            DestinationRow(
                checked = config.backupLocal,
                onChecked = { vm.setBackupLocal(it) },
                title = stringResource(R.string.local_storage),
                detail = stringResource(R.string.saved_to, folderLabel(config.backupFolderUri))
            ) {
                OutlinedButton(onClick = { folderLauncher.launch(null) }) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.choose_folder))
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            DestinationRow(
                checked = config.backupDrive,
                onChecked = { vm.setBackupDrive(it) },
                title = "Google Drive",
                detail = if (config.driveConnected) {
                    stringResource(R.string.drive_connected_text)
                } else {
                    stringResource(R.string.drive_not_connected_text)
                }
            ) {
                OutlinedButton(
                    onClick = {
                        vm.connectDrive { pending ->
                            driveLauncher.launch(IntentSenderRequest.Builder(pending.intentSender).build())
                        }
                    }
                ) { Text(if (config.driveConnected) stringResource(R.string.reconnect) else stringResource(R.string.connect)) }
            }
        }

        // ------------------------------------------------------ run + status
        BackupPanel(
            title = stringResource(R.string.backup_now),
            description = stringResource(R.string.backup_now_text)
        ) {
            Text(
                if (config.lastBackupAt > 0L) {
                    stringResource(R.string.last_backup, formatTime(config.lastBackupAt), config.lastBackupStatus)
                } else {
                    stringResource(R.string.no_backup_yet)
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                enabled = config.backupLocal || config.backupDrive,
                onClick = { vm.backupNow() }
            ) {
                Icon(Icons.Default.Backup, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.backup_now))
            }
        }

        // -------------------------------------------------------------- import
        ImportPanel(vm)

        // ------------------------------------------------------------- restore
        BackupPanel(
            title = stringResource(R.string.restore_title),
            description = stringResource(R.string.restore_text)
        ) {
            OutlinedButton(
                onClick = { restoreLauncher.launch(arrayOf("*/*")) },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Red)
            ) {
                Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.restore_title))
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    pendingRestore?.let { uri ->
        KeepAwakeWhileShown()
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text(stringResource(R.string.overwrite_title)) },
            text = {
                Text(
                    stringResource(R.string.overwrite_text)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.restoreFromFile(uri)
                        pendingRestore = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Red)
                ) { Text(stringResource(R.string.overwrite)) }
            },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@Composable
private fun BackupPanel(title: String, description: String, content: @Composable () -> Unit) {
    Panel(Modifier.widthIn(max = 820.dp).fillMaxWidth()) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

@Composable
private fun DestinationRow(
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
    title: String,
    detail: String,
    action: @Composable () -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChecked)
        Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        action()
    }
}

/** A readable name for the chosen folder, or the default app folder. */
@Composable
private fun folderLabel(uri: String?): String {
    if (uri == null) return stringResource(R.string.app_storage_folder)
    val chosen = stringResource(R.string.chosen_folder)
    val last = Uri.parse(uri).lastPathSegment ?: return chosen
    return last.substringAfter(':', last).ifBlank { chosen }
}

/**
 * Imports items from a CSV file: column A = item name, column B = category, column C = quantity.
 * Shows a preview before anything changes. New categories are added to Category management.
 */
@Composable
private fun ImportPanel(vm: StockViewModel) {
    var preview by remember { mutableStateOf<StockImporter.Preview?>(null) }
    var includeNegative by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            includeNegative = false
            vm.loadStockImport(uri) { preview = it }
        }
    }

    BackupPanel(
        title = stringResource(R.string.import_title),
        description = stringResource(R.string.import_text)
    ) {
        OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }) {
            Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.choose_csv))
        }
    }

    preview?.let { p ->
        val toImport = if (includeNegative) p.rows + p.negative else p.rows
        CompactDialog(
            title = stringResource(R.string.import_preview),
            confirmLabel = stringResource(R.string.import_count, toImport.size),
            confirmEnabled = toImport.isNotEmpty(),
            onConfirm = {
                vm.importStock(toImport)
                preview = null
            },
            onDismiss = { preview = null }
        ) {
            Text(
                stringResource(R.string.import_summary, toImport.size, toImport.map { it.category }.distinct().size),
                fontWeight = FontWeight.SemiBold
            )
            val noCategory = stringResource(R.string.no_category_paren)
            toImport.groupingBy { it.category.ifEmpty { noCategory } }.eachCount().toSortedMap().forEach { (category, count) ->
                Text("$category: $count", style = MaterialTheme.typography.bodyMedium)
            }
            if (p.newCategories.isNotEmpty()) {
                Text(
                    stringResource(R.string.new_categories, p.newCategories.joinToString(", ")),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (p.negative.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = includeNegative, onCheckedChange = { includeNegative = it })
                    Text(stringResource(R.string.import_negative, p.negative.size))
                }
            }
            val notes = buildList {
                if (p.negative.isNotEmpty() && !includeNegative) add(stringResource(R.string.skipped_negative, p.negative.size))
                if (p.alreadyInApp > 0) add(stringResource(R.string.skipped_existing, p.alreadyInApp))
                if (p.duplicatesInFile > 0) add(stringResource(R.string.skipped_repeated, p.duplicatesInFile))
                if (p.unreadable > 0) add(stringResource(R.string.skipped_unreadable, p.unreadable))
            }
            if (notes.isNotEmpty()) {
                Text(
                    notes.joinToString("\n"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
