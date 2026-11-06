@file:OptIn(ExperimentalMaterial3Api::class)

package com.stocktrack.app.ui

import androidx.compose.ui.res.stringResource
import com.stocktrack.app.R
import androidx.annotation.StringRes
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

private enum class LogFilter(@StringRes val label: Int) {
    All(R.string.filter_all), In(R.string.filter_check_in), Out(R.string.filter_check_out), Items(R.string.filter_item_changes)
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

    // Upright there is no room for the Note column, and the other columns share the width differently.
    val portrait = isPortrait()
    val wTime = if (portrait) 1.2f else W_TIME
    val wAction = if (portrait) 1.5f else W_ACTION
    val wChange = if (portrait) 0.8f else W_CHANGE

    val searchField: @Composable (Modifier) -> Unit = { modifier ->
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(stringResource(R.string.search_log), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = modifier
        )
    }
    val filterChips: @Composable () -> Unit = {
        LogFilter.entries.forEach { f ->
            FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(stringResource(f.label), maxLines = 1) })
        }
    }
    val clearButton: @Composable () -> Unit = {
        if (isAdmin) {
            OutlinedButton(
                onClick = { confirmClear = true },
                enabled = log.isNotEmpty(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Red)
            ) {
                Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.clear_log), maxLines = 1)
            }
        }
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (portrait) {
            // Two rows: on one, the chips and the button would squeeze the search field to almost nothing.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                searchField(Modifier.weight(1f))
                clearButton()
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                filterChips()
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                searchField(Modifier.weight(1f))
                filterChips()
                clearButton()
            }
        }

        Panel(Modifier.weight(1f).fillMaxWidth()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                HeaderCell(stringResource(R.string.col_date_time), wTime)
                HeaderCell(stringResource(R.string.col_user), W_USER)
                HeaderCell(stringResource(R.string.col_action), wAction)
                HeaderCell(stringResource(R.string.col_item), W_ITEM)
                HeaderCell(stringResource(R.string.col_change), wChange, TextAlign.End)
                HeaderCell(stringResource(R.string.col_qty_after), W_AFTER, TextAlign.End)
                if (!portrait) HeaderCell(stringResource(R.string.col_note), W_NOTE)
            }
            if (matches > shown.size) {
                Text(
                    stringResource(R.string.showing_latest, shown.size, matches),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                )
            }
            if (filtered.isEmpty()) {
                EmptyText(
                    if (log.isEmpty()) stringResource(R.string.no_activity_long)
                    else stringResource(R.string.no_entries_match),
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
                            BodyCell(formatTime(e.timestamp), wTime)
                            BodyCell(e.userName, W_USER, bold = true)
                            Row(Modifier.weight(wAction)) { Pill(actionLabel(e.type), actionColor(e.type)) }
                            BodyCell(e.itemName, W_ITEM, bold = true)
                            BodyCell(
                                formatChange(e.change), wChange, TextAlign.End, bold = true,
                                color = actionColor(e.type)
                            )
                            BodyCell("${e.quantityAfter}", W_AFTER, TextAlign.End)
                            if (!portrait) BodyCell(e.note.ifBlank { "—" }, W_NOTE)
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
            title = { Text(stringResource(R.string.clear_log_title)) },
            text = {
                Text(
                    stringResource(R.string.clear_log_text, log.size)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onClearLog()
                        confirmClear = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Red)
                ) { Text(stringResource(R.string.clear_log)) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) } }
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
