@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.stocktrack.app.ui

import android.app.Activity
import android.net.Uri
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
    var pendingRestore by remember { mutableStateOf<Uri?>(null) }

    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.setBackupFolder(uri)
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingRestore = uri
    }
    val driveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) vm.onDriveConsentResult(result.data)
        else vm.showMessage("Google Drive was not connected")
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ------------------------------------------------------------ schedule
        BackupPanel(
            title = "Automatic backup",
            description = "Runs in the background on the chosen schedule, even when the app is closed. " +
                "Needs at least one destination below."
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                BackupSchedule.entries.forEach { schedule ->
                    FilterChip(
                        selected = config.backupSchedule == schedule,
                        onClick = { vm.setBackupSchedule(schedule) },
                        label = { Text(schedule.label, maxLines = 1, softWrap = false) }
                    )
                }
            }
        }

        // -------------------------------------------------------- destinations
        BackupPanel(
            title = "Backup destinations",
            description = "Tick one or both. When both are ticked every backup goes to both places."
        ) {
            DestinationRow(
                checked = config.backupLocal,
                onChecked = { vm.setBackupLocal(it) },
                title = "Local file storage",
                detail = "Saved to: " + folderLabel(config.backupFolderUri)
            ) {
                OutlinedButton(onClick = { folderLauncher.launch(null) }) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Choose folder")
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            DestinationRow(
                checked = config.backupDrive,
                onChecked = { vm.setBackupDrive(it) },
                title = "Google Drive",
                detail = if (config.driveConnected) {
                    "Connected. Files go to the folder “DentTrack Backups” in your Drive."
                } else {
                    "Not connected yet. Tap Connect and allow access."
                }
            ) {
                OutlinedButton(
                    onClick = {
                        vm.connectDrive { pending ->
                            driveLauncher.launch(IntentSenderRequest.Builder(pending.intentSender).build())
                        }
                    }
                ) { Text(if (config.driveConnected) "Reconnect" else "Connect") }
            }
        }

        // ------------------------------------------------------ run + status
        BackupPanel(
            title = "Back up now",
            description = "The backup is the same Excel file as the Storage report on the Reports tab " +
                "(Summary, Stock, Low stock, Activity log, Users)."
        ) {
            Text(
                if (config.lastBackupAt > 0L) {
                    "Last backup: ${formatTime(config.lastBackupAt)}\n${config.lastBackupStatus}"
                } else {
                    "No backup has run yet."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                enabled = config.backupLocal || config.backupDrive,
                onClick = { vm.backupNow() }
            ) {
                Icon(Icons.Default.Backup, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Back up now")
            }
        }

        // -------------------------------------------------------------- import
        ImportPanel(vm)

        // ------------------------------------------------------------- restore
        BackupPanel(
            title = "Restore from local file",
            description = "Pick a DentTrack backup or report file (.xlsx) stored on this tablet. All items and the " +
                "whole activity log are replaced by the content of the file. Users in the file are added; " +
                "existing users stay. Settings and the Admin password are not part of the file."
        ) {
            OutlinedButton(
                onClick = { restoreLauncher.launch(arrayOf("*/*")) },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Red)
            ) {
                Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Restore from local file")
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    pendingRestore?.let { uri ->
        KeepAwakeWhileShown()
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("Overwrite current data?") },
            text = {
                Text(
                    "All current items and the activity log will be replaced by the content of the selected file. " +
                        "This cannot be undone. Tip: use “Back up now” first if you want to keep the current data."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.restoreFromFile(uri)
                        pendingRestore = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Red)
                ) { Text("Overwrite") }
            },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("Cancel") } }
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
private fun folderLabel(uri: String?): String {
    if (uri == null) return "app storage (Android/data/com.stocktrack.app/files/backups)"
    val last = Uri.parse(uri).lastPathSegment ?: return "chosen folder"
    return last.substringAfter(':', last).ifBlank { "chosen folder" }
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
        title = "Import stock from CSV",
        description = "Reads a CSV file with the item name in column A, the category in column B and the " +
            "quantity in column C (a heading row is fine). Categories that DentTrack does not have yet are " +
            "added to Category management. Items that already exist (same category and name) are not changed."
    ) {
        OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }) {
            Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Choose CSV file…")
        }
    }

    preview?.let { p ->
        val toImport = if (includeNegative) p.rows + p.negative else p.rows
        CompactDialog(
            title = "Import preview",
            confirmLabel = "Import ${toImport.size}",
            confirmEnabled = toImport.isNotEmpty(),
            onConfirm = {
                vm.importStock(toImport)
                preview = null
            },
            onDismiss = { preview = null }
        ) {
            Text(
                "${toImport.size} items in ${toImport.map { it.category }.distinct().size} categories will be added.",
                fontWeight = FontWeight.SemiBold
            )
            toImport.groupingBy { it.category.ifEmpty { "(no category)" } }.eachCount().toSortedMap().forEach { (category, count) ->
                Text("$category: $count", style = MaterialTheme.typography.bodyMedium)
            }
            if (p.newCategories.isNotEmpty()) {
                Text(
                    "New categories: " + p.newCategories.joinToString(", "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (p.negative.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = includeNegative, onCheckedChange = { includeNegative = it })
                    Text("Also import the ${p.negative.size} items with a negative quantity, as 0")
                }
            }
            val notes = buildList {
                if (p.negative.isNotEmpty() && !includeNegative) add("${p.negative.size} with a negative quantity skipped")
                if (p.alreadyInApp > 0) add("${p.alreadyInApp} already in DentTrack skipped")
                if (p.duplicatesInFile > 0) add("${p.duplicatesInFile} repeated in the file skipped")
                if (p.unreadable > 0) add("${p.unreadable} with no quantity (or not a number) skipped")
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
