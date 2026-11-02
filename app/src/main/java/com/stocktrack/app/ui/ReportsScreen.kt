@file:OptIn(ExperimentalMaterial3Api::class)

package com.stocktrack.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stocktrack.app.StockViewModel
import com.stocktrack.app.data.AppConfig
import com.stocktrack.app.data.Item
import com.stocktrack.app.data.XlsxWriter
import com.stocktrack.app.data.alertLevel
import com.stocktrack.app.data.isLow
import com.stocktrack.app.data.isOut
import com.stocktrack.app.data.stockValue
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private class CategoryRow(val name: String, val items: Int, val units: Long, val value: Double)

@Composable
fun ReportsScreen(
    vm: StockViewModel,
    stock: List<Item>,
    threshold: Int,
    isAdmin: Boolean,
    config: AppConfig,
    onOpenItem: (Long) -> Unit
) {
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(XlsxWriter.MIME)
    ) { uri ->
        if (uri != null) vm.exportReport(uri)
    }

    val categories = remember(stock) {
        stock.groupBy { it.category.ifBlank { "Uncategorized" } }
            .map { (name, list) ->
                CategoryRow(name, list.size, list.sumOf { it.quantity.toLong() }, list.sumOf { it.stockValue() })
            }
            .sortedBy { it.name.lowercase() }
    }
    val lowItems = remember(stock, threshold) { stock.filter { it.isLow(threshold) || it.isOut() }.sortedBy { it.quantity } }

    val exportStorage = {
        val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        exportLauncher.launch("DentTrack_Report_$day.xlsx")
    }

    // The whole screen scrolls, so the report cards never squeeze the two lists on a short screen.
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (isAdmin && maxWidth >= 900.dp) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    StorageReportCard(Modifier.weight(1f), exportStorage)
                    MonthlyReportCard(Modifier.weight(1f), vm, config)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    StorageReportCard(Modifier.fillMaxWidth(), exportStorage)
                    if (isAdmin) MonthlyReportCard(Modifier.fillMaxWidth(), vm, config)
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .height(440.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ---------------------------------------------------- category summary
            Panel(Modifier.weight(1f).fillMaxHeight()) {
                SectionTitle("Stock by category")
                if (categories.isEmpty()) {
                    EmptyText("No items yet.", Modifier.fillMaxWidth())
                } else {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
                        Text("Category", Modifier.weight(2f), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Items", Modifier.weight(1f), textAlign = TextAlign.End, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Units", Modifier.weight(1f), textAlign = TextAlign.End, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Value", Modifier.weight(1.3f), textAlign = TextAlign.End, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    LazyColumn(Modifier.weight(1f)) {
                        items(categories, key = { it.name }) { c ->
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    c.name, Modifier.weight(2f), fontWeight = FontWeight.SemiBold,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                                Text("${c.items}", Modifier.weight(1f), textAlign = TextAlign.End)
                                Text("%,d".format(c.units), Modifier.weight(1f), textAlign = TextAlign.End)
                                Text(formatMoney(c.value), Modifier.weight(1.3f), textAlign = TextAlign.End)
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }

            // ------------------------------------------------------- reorder list
            Panel(Modifier.weight(1f).fillMaxHeight()) {
                SectionTitle("Needs restocking (${lowItems.size})")
                if (lowItems.isEmpty()) {
                    EmptyText("Nothing is below its alert level.", Modifier.fillMaxWidth())
                } else {
                    LazyColumn(Modifier.weight(1f)) {
                        items(lowItems, key = { it.id }) { item ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenItem(item.id) }
                                    .padding(horizontal = 20.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        item.name, fontWeight = FontWeight.SemiBold,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "Alert level ${item.alertLevel(threshold)} ${item.unit}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Pill(
                                    if (item.isOut()) "Out of stock" else "${item.quantity} ${item.unit} left",
                                    if (item.isOut()) Red else Orange
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun StorageReportCard(modifier: Modifier, onExport: () -> Unit) {
    Panel(modifier) {
        Column(Modifier.padding(24.dp)) {
            Text("Storage report", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                "Export the current stock to an Excel file (.xlsx). It contains five sheets: " +
                    "Summary, Stock (every item with quantity, status and value), " +
                    "Low stock, the full Activity log and Users. The same file is used for backups.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onExport) {
                Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(8.dp))
                Text("Export to Excel")
            }
        }
    }
}

/** Admin only: the Monthly Report on request, and the automatic upload to Google Drive. */
@Composable
private fun MonthlyReportCard(modifier: Modifier, vm: StockViewModel, config: AppConfig) {
    val thisYear = remember { Calendar.getInstance().get(Calendar.YEAR) }
    var year by remember { mutableIntStateOf(thisYear) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(XlsxWriter.MIME)
    ) { uri ->
        if (uri != null) vm.exportMonthlyReport(uri, year)
    }

    Panel(modifier) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Monthly report", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "One Excel file for a whole year with a sheet for every category. Each item shows " +
                    "check in, check out and items left for every month, plus a Total column.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Year", fontWeight = FontWeight.SemiBold)
                FilledTonalIconButton(onClick = { year = (year - 1).coerceAtLeast(2000) }) {
                    Icon(Icons.Default.Remove, contentDescription = "Previous year")
                }
                Text("$year", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                FilledTonalIconButton(onClick = { year = (year + 1).coerceAtMost(2100) }) {
                    Icon(Icons.Default.Add, contentDescription = "Next year")
                }
            }
            Button(onClick = { exportLauncher.launch("DentTrack_MonthlyReport_$year.xlsx") }) {
                Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(8.dp))
                Text("Export monthly report")
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Save to Google Drive on the 1st of every month", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Goes to the folder “DentTrack Monthly Reports”. Needs Google Drive connected on the " +
                            "Backup tab. Reports on the year up to the month that just ended.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(checked = config.monthlyDrive, onCheckedChange = { vm.setMonthlyDrive(it) })
            }
            if (config.lastMonthlyAt > 0L) {
                Text(
                    "Last upload: ${formatTime(config.lastMonthlyAt)}\n${config.lastMonthlyStatus}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(onClick = { vm.uploadMonthlyReportNow(year) }, enabled = config.driveConnected) {
                Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Save $year to Drive now")
            }
        }
    }
}
