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
import androidx.compose.material3.Button
import androidx.compose.material3.Switch
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
 * Admin only. Two groups: "Admin & user settings" (Admin visibility, password, inactivity timeout)
 * and "Stock management" (low stock threshold). Categories are managed under Items, import under Backup.
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

    SettingsPanel(
        title = "Admin password",
        description = if (config.adminPasswordSet) {
            "Change the password used to open the Admin menu (hold the “DentTrack” title for 3 seconds). " +
                "Any password is accepted; there are no complexity rules."
        } else {
            "No password is set."
        }
    ) {
        val fieldModifier = Modifier.widthIn(max = 360.dp).fillMaxWidth()
        OutlinedTextField(
            value = current,
            onValueChange = { current = it; error = null },
            label = { Text("Current password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = fieldModifier
        )
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
        Button(
            enabled = current.isNotEmpty() && new.isNotEmpty(),
            onClick = {
                if (new != repeat) {
                    error = "The new passwords are not the same"
                } else {
                    vm.changeAdminPassword(current, new) { ok ->
                        if (ok) {
                            current = ""
                            new = ""
                            repeat = ""
                            error = null
                        } else {
                            error = "The current password is wrong"
                        }
                    }
                }
            }
        ) { Text("Change password") }
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
