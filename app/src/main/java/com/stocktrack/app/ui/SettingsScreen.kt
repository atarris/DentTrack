@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.stocktrack.app.ui

import androidx.compose.ui.res.stringResource
import com.stocktrack.app.R
import android.app.Activity
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.stocktrack.app.StockViewModel
import com.stocktrack.app.data.AppConfig
import com.stocktrack.app.data.Language

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
        LanguageCard(vm, config)

        // ---- Admin & user settings: who can sign in and how
        SectionTitle(stringResource(R.string.section_admin_users))
        AdminVisibilityCard(vm, config)
        PasswordCard(vm, config)
        TimeoutCard(vm, config)

        // ---- Stock management: how stock is judged
        SectionTitle(stringResource(R.string.section_stock))
        LowStockCard(vm, config)

        // ---- Reset and clear: the actions that undo things, kept apart at the bottom
        SectionTitle(stringResource(R.string.section_reset))
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
        title = stringResource(R.string.low_threshold_title),
        description = stringResource(R.string.low_threshold_text)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter(Char::isDigit).take(6) },
                label = { Text(stringResource(R.string.units_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.widthIn(max = 200.dp)
            )
            Button(
                enabled = value != null && value != config.lowStockThreshold,
                onClick = { if (value != null) vm.setLowStockThreshold(value) }
            ) { Text(stringResource(R.string.save)) }
        }
    }
}

@Composable
private fun TimeoutCard(vm: StockViewModel, config: AppConfig) {
    var text by remember(config.inactivityMinutes) { mutableStateOf(config.inactivityMinutes.toString()) }
    val value = text.toIntOrNull()

    SettingsPanel(
        title = stringResource(R.string.timeout_title),
        description = stringResource(R.string.timeout_text)
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(0, 1, 2, 5, 10, 15, 30).forEach { minutes ->
                FilterChip(
                    selected = value == minutes,
                    onClick = { text = minutes.toString() },
                    label = { Text(if (minutes == 0) stringResource(R.string.off) else stringResource(R.string.minutes_short, minutes), maxLines = 1, softWrap = false) }
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter(Char::isDigit).take(4) },
                label = { Text(stringResource(R.string.minutes_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.widthIn(max = 200.dp)
            )
            Button(
                enabled = value != null && value != config.inactivityMinutes,
                onClick = { if (value != null) vm.setInactivityMinutes(value) }
            ) { Text(stringResource(R.string.save)) }
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
    val context = LocalContext.current
    val hasPassword = config.adminPasswordSet
    val clearFields = {
        current = ""
        new = ""
        repeat = ""
        error = null
    }

    SettingsPanel(
        title = stringResource(R.string.password_title),
        description = if (hasPassword) {
            stringResource(R.string.password_text_set)
        } else {
            stringResource(R.string.password_text_none)
        }
    ) {
        val fieldModifier = Modifier.widthIn(max = 360.dp).fillMaxWidth()
        if (hasPassword) {
            OutlinedTextField(
                value = current,
                onValueChange = { current = it; error = null },
                label = { Text(stringResource(R.string.current_password)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = fieldModifier
            )
        }
        OutlinedTextField(
            value = new,
            onValueChange = { new = it; error = null },
            label = { Text(stringResource(R.string.new_password)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = fieldModifier
        )
        OutlinedTextField(
            value = repeat,
            onValueChange = { repeat = it; error = null },
            label = { Text(stringResource(R.string.repeat_new_password)) },
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
                        new != repeat -> error = context.getString(R.string.new_passwords_differ)
                        !hasPassword -> {
                            vm.setAdminPassword(new)
                            clearFields()
                        }
                        else -> vm.changeAdminPassword(current, new) { ok ->
                            if (ok) clearFields() else error = context.getString(R.string.current_password_wrong)
                        }
                    }
                }
            ) { Text(if (hasPassword) stringResource(R.string.change_password) else stringResource(R.string.set_password)) }
            if (hasPassword) {
                OutlinedButton(
                    enabled = current.isNotEmpty(),
                    onClick = { confirmRemove = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Red)
                ) { Text(stringResource(R.string.remove_password)) }
            }
        }
    }

    if (confirmRemove) {
        KeepAwakeWhileShown()
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(stringResource(R.string.remove_password_title)) },
            text = {
                Text(
                    stringResource(R.string.remove_password_text)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmRemove = false
                        vm.removeAdminPassword(current) { ok ->
                            if (ok) clearFields() else error = context.getString(R.string.current_password_wrong)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Red)
                ) { Text(stringResource(R.string.remove_password)) }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@Composable
private fun ClearStockCard(vm: StockViewModel) {
    var open by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }
    var alsoLog by remember { mutableStateOf(false) }

    SettingsPanel(
        title = stringResource(R.string.clear_stock_title),
        description = stringResource(R.string.clear_stock_text)
    ) {
        OutlinedButton(
            onClick = {
                typed = ""
                alsoLog = false
                open = true
            },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Red)
        ) { Text(stringResource(R.string.clear_stock_title)) }
    }

    if (open) {
        KeepAwakeWhileShown()
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(stringResource(R.string.clear_stock_confirm_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.clear_stock_confirm_text))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = alsoLog, onCheckedChange = { alsoLog = it })
                        Text(stringResource(R.string.also_clear_log))
                    }
                    OutlinedTextField(
                        value = typed,
                        onValueChange = { typed = it },
                        label = { Text(stringResource(R.string.type_delete)) },
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
                ) { Text(stringResource(R.string.clear_database)) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@Composable
private fun ResetConfigCard(vm: StockViewModel) {
    var confirm by remember { mutableStateOf(false) }

    SettingsPanel(
        title = stringResource(R.string.reset_title),
        description = stringResource(R.string.reset_text)
    ) {
        OutlinedButton(
            onClick = { confirm = true },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Red)
        ) { Text(stringResource(R.string.reset_title)) }
    }

    if (confirm) {
        KeepAwakeWhileShown()
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.reset_confirm_title)) },
            text = {
                Text(
                    stringResource(R.string.reset_confirm_text)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.resetConfiguration()
                        confirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Red)
                ) { Text(stringResource(R.string.reset)) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@Composable
private fun AdminVisibilityCard(vm: StockViewModel, config: AppConfig) {
    SettingsPanel(
        title = stringResource(R.string.hide_admin_title),
        description = stringResource(R.string.hide_admin_text)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Switch(checked = config.hideAdmin, onCheckedChange = { vm.setHideAdmin(it) })
            Text(
                if (config.hideAdmin) stringResource(R.string.admin_hidden) else stringResource(R.string.admin_shown),
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun LanguageCard(vm: StockViewModel, config: AppConfig) {
    val context = LocalContext.current
    SettingsPanel(
        title = stringResource(R.string.language_title),
        description = stringResource(R.string.language_text)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Each language is named in its own language, so it can always be found.
            listOf(Language.ENGLISH to "English", Language.LITHUANIAN to "Lietuvių").forEach { (code, label) ->
                FilterChip(
                    selected = config.language == code,
                    onClick = {
                        if (config.language != code) {
                            vm.setLanguage(code)
                            (context as? Activity)?.recreate()
                        }
                    },
                    label = { Text(label, maxLines = 1, softWrap = false) }
                )
            }
        }
    }
}
