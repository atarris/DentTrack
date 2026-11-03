@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.stocktrack.app.ui

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Switch
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.stocktrack.app.StockViewModel
import com.stocktrack.app.data.AppConfig

/**
 * Admin only. Three groups: "Admin & user settings" (Admin visibility, password, inactivity timeout),
 * "Stock management" (low stock threshold) and "Reset and clear". Categories are managed under Items,
 * import under Backup.
 */
@Composable
fun SettingsScreen(vm: StockViewModel, config: AppConfig) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ---- Admin & user settings: who can sign in and how
        SectionTitle("Admin & user settings")
        AdminVisibilityCard(vm, config)
        PasswordCard(vm, config)
        TimeoutCard(vm, config)

        // ---- Stock management: how stock is judged
        SectionTitle("Stock management")
        LowStockCard(vm, config)

        // ---- Reset and clear: the actions that undo things, kept apart at the bottom
        SectionTitle("Reset and clear")
        ResetConfigCard(vm)
        ClearStockCard(vm)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SettingsPanel(title: String, description: String, content: @Composable () -> Unit) {
    Panel(Modifier.widthIn(max = 820.dp).fillMaxWidth()) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

@Composable
private fun LowStockCard(vm: StockViewModel, config: AppConfig) {
    var text by remember(config.lowStockThreshold) { mutableStateOf(config.lowStockThreshold.toString()) }
    val value = text.toIntOrNull()

    SettingsPanel(
        title = "Low stock alert threshold",
        description = "An item is flagged as low stock when its quantity is at or below this number of units. " +
            "An item can have its own level instead (Items → Edit). Use 0 to switch the global alert off."
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter(Char::isDigit).take(6) },
                label = { Text("Units") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.widthIn(max = 200.dp)
            )
            Button(
                enabled = value != null && value != config.lowStockThreshold,
                onClick = { if (value != null) vm.setLowStockThreshold(value) }
            ) { Text("Save") }
        }
    }
}

@Composable
private fun TimeoutCard(vm: StockViewModel, config: AppConfig) {
    var text by remember(config.inactivityMinutes) { mutableStateOf(config.inactivityMinutes.toString()) }
    val value = text.toIntOrNull()

    SettingsPanel(
        title = "Inactivity timeout",
        description = "After this many minutes without any touch, the app goes back to the user selection " +
            "screen. This also ends an Admin session. Use 0 to turn it off."
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(0, 1, 2, 5, 10, 15, 30).forEach { minutes ->
                FilterChip(
                    selected = value == minutes,
                    onClick = { text = minutes.toString() },
                    label = { Text(if (minutes == 0) "Off" else "$minutes min", maxLines = 1, softWrap = false) }
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter(Char::isDigit).take(4) },
                label = { Text("Minutes") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.widthIn(max = 200.dp)
            )
            Button(
                enabled = value != null && value != config.inactivityMinutes,
                onClick = { if (value != null) vm.setInactivityMinutes(value) }
            ) { Text("Save") }
        }
    }
}

@Composable
private fun PasswordCard(vm: StockViewModel, config: AppConfig) {
    var current by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmRemove by remember { mutableStateOf(false) }
    val hasPassword = config.adminPasswordSet
    val clearFields = {
        current = ""
        new = ""
        repeat = ""
        error = null
    }

    SettingsPanel(
        title = "Admin password",
        description = if (hasPassword) {
            "Change or remove the password used to open the Admin menu (hold the “DentTrack” title for 3 seconds). " +
                "Any password is accepted; there are no complexity rules."
        } else {
            "No password is set: anyone who holds the “DentTrack” title for 3 seconds gets into the Admin menu. " +
                "Enter a password below to protect it."
        }
    ) {
        val fieldModifier = Modifier.widthIn(max = 360.dp).fillMaxWidth()
        if (hasPassword) {
            OutlinedTextField(
                value = current,
                onValueChange = { current = it; error = null },
                label = { Text("Current password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = fieldModifier
            )
        }
        OutlinedTextField(
            value = new,
            onValueChange = { new = it; error = null },
            label = { Text("New password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = fieldModifier
        )
        OutlinedTextField(
            value = repeat,
            onValueChange = { repeat = it; error = null },
            label = { Text("Repeat new password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = fieldModifier
        )
        error?.let { Text(it, color = Red) }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                enabled = (current.isNotEmpty() || !hasPassword) && new.isNotEmpty(),
                onClick = {
                    when {
                        new != repeat -> error = "The new passwords are not the same"
                        !hasPassword -> {
                            vm.setAdminPassword(new)
                            clearFields()
                        }
                        else -> vm.changeAdminPassword(current, new) { ok ->
                            if (ok) clearFields() else error = "The current password is wrong"
                        }
                    }
                }
            ) { Text(if (hasPassword) "Change password" else "Set password") }
            if (hasPassword) {
                OutlinedButton(
                    enabled = current.isNotEmpty(),
                    onClick = { confirmRemove = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Red)
                ) { Text("Remove password") }
            }
        }
    }

    if (confirmRemove) {
        KeepAwakeWhileShown()
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove the Admin password?") },
            text = {
                Text(
                    "Anyone who holds the “DentTrack” title for 3 seconds will get into the Admin menu without " +
                        "a password. They could change items, users and settings, or clear the log."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmRemove = false
                        vm.removeAdminPassword(current) { ok ->
                            if (ok) clearFields() else error = "The current password is wrong"
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Red)
                ) { Text("Remove password") }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ClearStockCard(vm: StockViewModel) {
    var open by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }
    var alsoLog by remember { mutableStateOf(false) }

    SettingsPanel(
        title = "Clear stock database",
        description = "Deletes every item and category, to start again or before a fresh import. Users, settings " +
            "and the Admin password are kept. Make a backup first: this cannot be undone."
    ) {
        OutlinedButton(
            onClick = {
                typed = ""
                alsoLog = false
                open = true
            },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Red)
        ) { Text("Clear stock database") }
    }

    if (open) {
        KeepAwakeWhileShown()
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("Clear the stock database?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("All items, their quantities and all categories will be deleted. The only way back is restoring a backup.")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = alsoLog, onCheckedChange = { alsoLog = it })
                        Text("Also clear the activity log")
                    }
                    OutlinedTextField(
                        value = typed,
                        onValueChange = { typed = it },
                        label = { Text("Type DELETE to confirm") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = typed.trim() == "DELETE",
                    onClick = {
                        vm.clearStockDatabase(alsoLog)
                        open = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Red)
                ) { Text("Clear database") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ResetConfigCard(vm: StockViewModel) {
    var confirm by remember { mutableStateOf(false) }

    SettingsPanel(
        title = "Reset app configuration",
        description = "Puts the settings on this screen and on the Backup tab back to their defaults. Stock, " +
            "the activity log, users, categories and the Admin password are kept."
    ) {
        OutlinedButton(
            onClick = { confirm = true },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Red)
        ) { Text("Reset app configuration") }
    }

    if (confirm) {
        KeepAwakeWhileShown()
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Reset app configuration?") },
            text = {
                Text(
                    "Low stock threshold, inactivity timeout and Hide Admin user go back to their defaults. " +
                        "Automatic backups and the monthly Drive upload are turned off, and the chosen backup " +
                        "folder and Google Drive connection are forgotten."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.resetConfiguration()
                        confirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Red)
                ) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun AdminVisibilityCard(vm: StockViewModel, config: AppConfig) {
    SettingsPanel(
        title = "Hide Admin user",
        description = "On: the Admin is not listed on the user selection screen. Open the Admin menu by " +
            "holding the “DentTrack” title for 3 seconds. Off: an “Admin” card is shown with the other " +
            "users (the password is still asked)."
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Switch(checked = config.hideAdmin, onCheckedChange = { vm.setHideAdmin(it) })
            Text(
                if (config.hideAdmin) "Admin is hidden" else "Admin is shown on the user selection screen",
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
