package com.stocktrack.app.ui

import androidx.compose.ui.res.stringResource
import com.stocktrack.app.R
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stocktrack.app.data.Item
import com.stocktrack.app.data.LogEntry
import com.stocktrack.app.data.alertLevel
import com.stocktrack.app.data.isLow
import com.stocktrack.app.data.isOut
import com.stocktrack.app.data.stockValue

@Composable
fun DashboardScreen(
    stock: List<Item>,
    log: List<LogEntry>,
    threshold: Int,
    onOpenItem: (Long) -> Unit,
    onCheckOutItem: (Long) -> Unit
) {
    val alerts = stock.filter { it.isLow(threshold) }.sortedBy { it.quantity }
    val lowCount = alerts.size
    val existingIds = remember(stock) { stock.map { it.id }.toHashSet() }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StatCard(stringResource(R.string.tab_items),"${stock.size}", Icons.Default.Inventory2, MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
            StatCard(
                stringResource(R.string.units_in_stock), "%,d".format(stock.sumOf { it.quantity.toLong() }),
                Icons.Default.Layers, MaterialTheme.colorScheme.onSurface, Modifier.weight(1f)
            )
            StatCard(
                stringResource(R.string.low_stock_alerts), "$lowCount",
                Icons.Default.NotificationsActive, if (lowCount > 0) Red else Green, Modifier.weight(1f)
            )
            StatCard(
                stringResource(R.string.stock_value), formatMoney(stock.sumOf { it.stockValue() }),
                Icons.Default.Payments, MaterialTheme.colorScheme.onSurface, Modifier.weight(1f)
            )
        }

        Row(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Panel(Modifier.weight(1f).fillMaxHeight()) {
                SectionTitle(stringResource(R.string.low_stock_alerts))
                if (alerts.isEmpty()) {
                    EmptyText(
                        if (stock.isEmpty()) stringResource(R.string.no_items_add_first)
                        else stringResource(R.string.nothing_low),
                        Modifier.fillMaxWidth()
                    )
                } else {
                    LazyColumn(Modifier.weight(1f)) {
                        items(alerts, key = { it.id }) { item ->
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
                                        stringResource(R.string.alert_level_value, item.alertLevel(threshold), unitLabel(item.unit)),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Pill(
                                    if (item.isOut()) stringResource(R.string.out_of_stock) else stringResource(R.string.items_left, item.quantity, unitLabel(item.unit)),
                                    Red
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }

            Panel(Modifier.weight(1f).fillMaxHeight()) {
                SectionTitle(stringResource(R.string.recent_activity))
                if (log.isEmpty()) {
                    EmptyText(stringResource(R.string.no_activity), Modifier.fillMaxWidth())
                } else {
                    LazyColumn(Modifier.weight(1f)) {
                        items(log.take(25), key = { it.id }) { entry ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    // Entries of items that were removed have nothing to open.
                                    .clickable(enabled = entry.itemId in existingIds) { onCheckOutItem(entry.itemId) }
                                    .padding(horizontal = 20.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        entry.itemName, fontWeight = FontWeight.SemiBold,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "${actionLabel(entry.type)} · ${entry.userName} · ${formatTime(entry.timestamp)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(Modifier.height(0.dp))
                                Text(
                                    formatChange(entry.change),
                                    fontWeight = FontWeight.Bold,
                                    color = actionColor(entry.type)
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    icon: ImageVector,
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier
) {
    Panel(modifier) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(DentTrackColors.Mint),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = DentTrackColors.Primary, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.size(14.dp))
            Column {
                Text(
                    label, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1
                )
                Text(
                    value, style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold, color = accent, maxLines = 1
                )
            }
        }
    }
}
