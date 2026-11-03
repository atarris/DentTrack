@file:OptIn(ExperimentalMaterial3Api::class)

package com.stocktrack.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stocktrack.app.data.ActionType
import com.stocktrack.app.data.LogEntry

private enum class LogFilter(val label: String) {
    All("All"), In("Check in"), Out("Check out"), Items("Item changes")
}

// Column weights shared by header and rows so everything lines up.
private const val W_TIME = 1.3f
private const val W_USER = 1.0f
private const val W_ACTION = 1.1f
private const val W_ITEM = 2.0f
private const val W_CHANGE = 0.7f
private const val W_AFTER = 1.0f
private const val W_NOTE = 1.8f

@Composable
fun ActivityScreen(log: List<LogEntry>, isAdmin: Boolean, onClearLog: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(LogFilter.All) }
    var confirmClear by remember { mutableStateOf(false) }

    val filtered = remember(log, query, filter) {
        val q = query.trim().lowercase()
        log.filter { e ->
            val okFilter = when (filter) {
                LogFilter.All -> true
                LogFilter.In -> e.type == ActionType.CHECK_IN
                LogFilter.Out -> e.type == ActionType.CHECK_OUT
                LogFilter.Items -> e.type == ActionType.ITEM_ADDED ||
                    e.type == ActionType.ITEM_EDITED || e.type == ActionType.ITEM_REMOVED
            }
            val okQuery = q.isEmpty() ||
                e.itemName.lowercase().contains(q) ||
                e.userName.lowercase().contains(q) ||
                e.note.lowercase().contains(q)
            okFilter && okQuery
        }
    }
    val matches = filtered.size
    val shown = filtered.take(MAX_GUI_LOG_ROWS) // newest first; the app shows the latest 100

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search item, user or note…") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            )
            LogFilter.entries.forEach { f ->
                FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) })
            }
            if (isAdmin) {
                OutlinedButton(
                    onClick = { confirmClear = true },
                    enabled = log.isNotEmpty(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Red)
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Clear log", maxLines = 1)
                }
            }
        }

        Panel(Modifier.weight(1f).fillMaxWidth()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                HeaderCell("Date / time", W_TIME)
                HeaderCell("User", W_USER)
                HeaderCell("Action", W_ACTION)
                HeaderCell("Item", W_ITEM)
                HeaderCell("Change", W_CHANGE, TextAlign.End)
                HeaderCell("Qty after", W_AFTER, TextAlign.End)
                HeaderCell("Note", W_NOTE)
            }
            if (matches > shown.size) {
                Text(
                    "Showing the latest ${shown.size} of $matches entries.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                )
            }
            if (filtered.isEmpty()) {
                EmptyText(
                    if (log.isEmpty()) "No activity yet. Check items in or out and it will show up here."
                    else "No entries match.",
                    Modifier.fillMaxWidth()
                )
            } else {
                LazyColumn(Modifier.weight(1f)) {
                    items(shown, key = { it.id }) { e ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BodyCell(formatTime(e.timestamp), W_TIME)
                            BodyCell(e.userName, W_USER, bold = true)
                            Row(Modifier.weight(W_ACTION)) { Pill(actionLabel(e.type), actionColor(e.type)) }
                            BodyCell(e.itemName, W_ITEM, bold = true)
                            BodyCell(
                                formatChange(e.change), W_CHANGE, TextAlign.End, bold = true,
                                color = actionColor(e.type)
                            )
                            BodyCell("${e.quantityAfter}", W_AFTER, TextAlign.End)
                            BodyCell(e.note.ifBlank { "—" }, W_NOTE)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }

    if (confirmClear) {
        KeepAwakeWhileShown()
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear the log?") },
            text = {
                Text(
                    "Are you sure you want to clear the log? All ${log.size} entries are deleted and cannot be " +
                        "brought back, except from a backup. Stock quantities do not change, but Monthly " +
                        "Reports lose the check ins and check outs that were in the log."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onClearLog()
                        confirmClear = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Red)
                ) { Text("Clear log") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun RowScope.HeaderCell(text: String, weight: Float, align: TextAlign = TextAlign.Start) {
    Text(
        text,
        modifier = Modifier.weight(weight).padding(end = 8.dp),
        textAlign = align,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1
    )
}

@Composable
private fun RowScope.BodyCell(
    text: String,
    weight: Float,
    align: TextAlign = TextAlign.Start,
    bold: Boolean = false,
    color: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified
) {
    Text(
        text,
        modifier = Modifier.weight(weight).padding(end = 8.dp),
        textAlign = align,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
        color = color,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}
