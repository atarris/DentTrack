@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.stocktrack.app.ui

import androidx.compose.ui.res.stringResource
import com.stocktrack.app.R
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stocktrack.app.StockViewModel
import com.stocktrack.app.data.ActionType
import com.stocktrack.app.data.DEFAULT_UNIT
import com.stocktrack.app.data.Item
import com.stocktrack.app.data.LogEntry
import com.stocktrack.app.data.alertLevel
import com.stocktrack.app.data.isLow
import com.stocktrack.app.data.isOut
import com.stocktrack.app.data.stockValue

private enum class StockFilter(@StringRes val label: Int) {
    All(R.string.filter_all), Low(R.string.stock_filter_low), Out(R.string.out_of_stock)
}

/** The activity lists in the app show at most this many entries (the backup file keeps 999). */
const val MAX_GUI_LOG_ROWS = 100

/** Below this width (portrait tablets, split screen) the list and the detail take turns. */
private val SINGLE_PANE_BELOW = 760.dp

@Composable
fun ItemsScreen(
    vm: StockViewModel,
    stock: List<Item>,
    log: List<LogEntry>,
    threshold: Int,
    isAdmin: Boolean,
    selectedId: Long?,
    onSelect: (Long?) -> Unit,
    /** True when the selected item should open straight in the Check out window (Dashboard > Recent activity). */
    startCheckOut: Boolean = false,
    onCheckOutStarted: () -> Unit = {}
) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(StockFilter.All) }
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Item?>(null) }
    var moveType by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var managingCategories by remember { mutableStateOf(false) }
    val categories by vm.categories.collectAsState()

    val selected = stock.firstOrNull { it.id == selectedId }
    LaunchedEffect(startCheckOut) {
        if (startCheckOut) {
            // An empty item cannot be checked out, so it just opens, showing its details.
            if (selected != null && selected.quantity > 0) moveType = ActionType.CHECK_OUT
            onCheckOutStarted()
        }
    }
    val filtered = remember(stock, query, filter, threshold) {
        val q = query.trim().lowercase()
        stock.filter { item ->
            val matchesFilter = when (filter) {
                StockFilter.All -> true
                StockFilter.Low -> item.isLow(threshold)
                StockFilter.Out -> item.isOut()
            }
            val matchesQuery = q.isEmpty() ||
                item.name.lowercase().contains(q) ||
                item.sku.lowercase().contains(q) ||
                item.category.lowercase().contains(q) ||
                item.location.lowercase().contains(q)
            matchesFilter && matchesQuery
        }
    }

    val list: @Composable (Modifier) -> Unit = { modifier ->
        ItemListPanel(
            modifier = modifier,
            allItems = stock,
            shown = filtered,
            threshold = threshold,
            isAdmin = isAdmin,
            query = query,
            onQuery = { query = it },
            filter = filter,
            onFilter = { filter = it },
            selectedId = selectedId,
            onSelect = { onSelect(it) },
            onAdd = { adding = true },
            onCategories = { managingCategories = true }
        )
    }
    val detail: @Composable (Modifier, Boolean) -> Unit = { modifier, showBack ->
        Panel(modifier) {
            if (selected == null) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        if (isAdmin) stringResource(R.string.select_item_admin)
                        else stringResource(R.string.select_item_user),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                ItemDetail(
                    item = selected,
                    history = remember(log, selected.id) { log.filter { it.itemId == selected.id }.take(MAX_GUI_LOG_ROWS) },
                    threshold = threshold,
                    isAdmin = isAdmin,
                    showBack = showBack,
                    onBack = { onSelect(null) },
                    onCheckIn = { moveType = ActionType.CHECK_IN },
                    onCheckOut = { moveType = ActionType.CHECK_OUT },
                    onEdit = { editing = selected },
                    onDelete = { confirmDelete = true }
                )
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth < SINGLE_PANE_BELOW) {
            // Portrait / narrow: one pane at a time, so nothing gets squeezed.
            if (selected == null) list(Modifier.fillMaxSize()) else detail(Modifier.fillMaxSize(), true)
        } else {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                list(Modifier.weight(0.42f).fillMaxHeight())
                detail(Modifier.weight(0.58f).fillMaxHeight(), false)
            }
        }
    }

    // --------------------------------------------------------------- dialogs
    if (adding && isAdmin) {
        ItemEditorDialog(
            initial = null,
            categories = categories,
            threshold = threshold,
            onSave = { newItem ->
                vm.saveItem(newItem) { id -> onSelect(id) }
                adding = false
            },
            onDismiss = { adding = false }
        )
    }

    editing?.let { current ->
        ItemEditorDialog(
            initial = current,
            categories = categories,
            threshold = threshold,
            onSave = { changed ->
                vm.saveItem(changed)
                editing = null
            },
            onDismiss = { editing = null }
        )
    }

    if (managingCategories && isAdmin) {
        CategoryManagerDialog(
            categories = categories,
            items = stock,
            onAdd = { vm.addCategory(it) },
            onDelete = { vm.deleteCategory(it) },
            onDismiss = { managingCategories = false }
        )
    }

    val type = moveType
    if (type != null && selected != null) {
        StockMoveDialog(
            item = selected,
            type = type,
            onConfirm = { qty, note ->
                vm.moveStock(selected, type, qty, note)
                moveType = null
            },
            onDismiss = { moveType = null }
        )
    }

    if (confirmDelete && selected != null) {
        KeepAwakeWhileShown()
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.remove_item_title, selected.name)) },
            text = {
                Text(
                    if (selected.quantity > 0) stringResource(R.string.remove_item_text_qty, selected.quantity, unitLabel(selected.unit)) else stringResource(R.string.remove_item_text)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.deleteItem(selected) { onSelect(null) }
                        confirmDelete = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Red)
                ) { Text(stringResource(R.string.remove)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

// ------------------------------------------------------------------ list panel

@Composable
private fun ItemListPanel(
    modifier: Modifier,
    allItems: List<Item>,
    shown: List<Item>,
    threshold: Int,
    isAdmin: Boolean,
    query: String,
    onQuery: (String) -> Unit,
    filter: StockFilter,
    onFilter: (StockFilter) -> Unit,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    onAdd: () -> Unit,
    onCategories: () -> Unit
) {
    val lowCount = allItems.count { it.isLow(threshold) }
    val outCount = allItems.count { it.isOut() }

    Panel(modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQuery,
                    placeholder = { Text(stringResource(R.string.search_items), maxLines = 1) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                )
                // Only the Admin may create new items.
                if (isAdmin) {
                    Button(onClick = onAdd) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(stringResource(R.string.new_item))
                    }
                }
            }
            if (isAdmin) {
                // Category Management (Admin only): add and delete categories.
                OutlinedButton(onClick = onCategories) {
                    Icon(Icons.Default.Category, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.categories), maxLines = 1, softWrap = false)
                }
            }
            // FlowRow: if the chips do not fit next to each other they wrap onto a second line
            // instead of squeezing their text. Each label stays on one line.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                StockFilter.entries.forEach { f ->
                    val count = when (f) {
                        StockFilter.All -> allItems.size
                        StockFilter.Low -> lowCount
                        StockFilter.Out -> outCount
                    }
                    FilterChip(
                        selected = filter == f,
                        onClick = { onFilter(f) },
                        label = {
                            Text(
                                stringResource(f.label) + " ($count)",
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        if (shown.isEmpty()) {
            EmptyText(
                when {
                    allItems.isEmpty() && isAdmin -> stringResource(R.string.no_items_admin)
                    allItems.isEmpty() -> stringResource(R.string.no_items_user)
                    else -> stringResource(R.string.no_items_match)
                },
                Modifier.fillMaxWidth()
            )
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(shown, key = { it.id }) { item ->
                    ItemRow(item, threshold, selected = item.id == selectedId, onClick = { onSelect(item.id) })
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun ItemRow(item: Item, threshold: Int, selected: Boolean, onClick: () -> Unit) {
    val qtyColor = when {
        item.isOut() -> Red
        item.isLow(threshold) -> Orange
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f) else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            val sub = listOf(item.sku, item.category).filter { it.isNotBlank() }.joinToString(" · ")
            Text(
                sub.ifBlank { "—" }, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        if (item.isOut()) Pill(stringResource(R.string.pill_out), Red) else if (item.isLow(threshold)) Pill(stringResource(R.string.pill_low), Orange)
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text("${item.quantity}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = qtyColor)
            Text(unitLabel(item.unit), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// --------------------------------------------------------------------- detail

@Composable
private fun ItemDetail(
    item: Item,
    history: List<LogEntry>,
    threshold: Int,
    isAdmin: Boolean,
    showBack: Boolean,
    onBack: () -> Unit,
    onCheckIn: () -> Unit,
    onCheckOut: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val level = item.alertLevel(threshold)
    val warn = item.isOut() || item.isLow(threshold)

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        if (showBack) {
            TextButton(onClick = onBack, modifier = Modifier.padding(bottom = 4.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.all_items))
            }
        }
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                val sub = listOf(item.sku, item.category).filter { it.isNotBlank() }.joinToString(" · ")
                if (sub.isNotBlank()) {
                    Text(sub, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(8.dp))
            when {
                item.isOut() -> Pill(stringResource(R.string.out_of_stock), Red)
                item.isLow(threshold) -> Pill(stringResource(R.string.stock_filter_low), Orange)
                else -> Pill(stringResource(R.string.in_stock), Green)
            }
        }
        Spacer(Modifier.height(16.dp))

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (warn) Red.copy(alpha = 0.08f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
        ) {
            Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.in_stock), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "${item.quantity}", style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold, color = if (warn) Red else MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            unitLabel(item.unit), style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(stringResource(R.string.low_alert_level), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        if (level > 0) "$level ${unitLabel(item.unit)}" else stringResource(R.string.off),
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold
                    )
                    if (item.minQuantity <= 0 && level > 0) {
                        Text(
                            stringResource(R.string.global_setting), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onCheckIn,
                modifier = Modifier.weight(1f).height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Green)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.check_in), style = MaterialTheme.typography.titleMedium, maxLines = 1)
            }
            Button(
                onClick = onCheckOut,
                enabled = item.quantity > 0,
                modifier = Modifier.weight(1f).height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Orange)
            ) {
                Icon(Icons.Default.Remove, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.check_out), style = MaterialTheme.typography.titleMedium, maxLines = 1)
            }
        }
        Spacer(Modifier.height(16.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            InfoField(stringResource(R.string.location),item.location.ifBlank { "—" }, Modifier.weight(1f))
            InfoField(stringResource(R.string.unit_cost),formatMoney(item.unitCost), Modifier.weight(1f))
            InfoField(stringResource(R.string.stock_value),formatMoney(item.stockValue()), Modifier.weight(1f))
        }
        if (item.note.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            InfoField(stringResource(R.string.note),item.note, Modifier.fillMaxWidth())
        }

        // Changing item details or removing an item is for the Admin only.
        if (isAdmin) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.edit))
                }
                OutlinedButton(onClick = onDelete, colors = ButtonDefaults.outlinedButtonColors(contentColor = Red)) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.remove_item))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            stringResource(R.string.history), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
        )
        if (history.isEmpty()) {
            EmptyText(stringResource(R.string.no_item_activity),Modifier.fillMaxWidth())
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(history, key = { it.id }) { h ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Pill(actionLabel(h.type), actionColor(h.type))
                                Text(h.userName, fontWeight = FontWeight.Medium)
                            }
                            Text(
                                formatTime(h.timestamp) + if (h.note.isNotBlank()) " · ${h.note}" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(formatChange(h.change), fontWeight = FontWeight.Bold, color = actionColor(h.type))
                            Text(
                                "→ ${h.quantityAfter}", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun InfoField(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
    }
}

// -------------------------------------------------------------------- dialogs

@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboard: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, maxLines = 1) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard, capitalization = capitalization),
        modifier = modifier
    )
}

/** Add a new item (initial == null) or edit the details of an existing one. Admin only. */
@Composable
private fun ItemEditorDialog(
    initial: Item?,
    categories: List<String>,
    threshold: Int,
    onSave: (Item) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var sku by remember { mutableStateOf(initial?.sku ?: "") }
    var category by remember { mutableStateOf(initial?.category ?: "") }
    var location by remember { mutableStateOf(initial?.location ?: "") }
    val defaultUnit = stringResource(R.string.unit_pcs)
    val initialUnit = unitLabel(initial?.unit ?: DEFAULT_UNIT)
    var unit by remember { mutableStateOf(initialUnit) }
    var qty by remember { mutableStateOf("0") }
    var min by remember { mutableStateOf((initial?.minQuantity ?: 0).toString()) }
    var cost by remember {
        mutableStateOf(
            if (initial == null || initial.unitCost == 0.0) ""
            else java.math.BigDecimal.valueOf(initial.unitCost).stripTrailingZeros().toPlainString()
        )
    }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var nameError by remember { mutableStateOf(false) }

    fun save() {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) {
            nameError = true
            return
        }
        onSave(
            Item(
                id = initial?.id ?: 0L,
                name = cleanName,
                sku = sku.trim(),
                category = category.trim(),
                location = location.trim(),
                unit = unit.trim().let { if (it.isBlank() || it.equals(defaultUnit, ignoreCase = true)) DEFAULT_UNIT else it },
                quantity = if (initial == null) (qty.toIntOrNull() ?: 0) else initial.quantity,
                minQuantity = min.toIntOrNull() ?: 0,
                unitCost = cost.replace(',', '.').toDoubleOrNull() ?: 0.0,
                note = note.trim(),
                createdAt = initial?.createdAt ?: System.currentTimeMillis()
            )
        )
    }

    // Save / Cancel are in the title bar at the top, so the keyboard cannot cover them.
    CompactDialog(
        title = if (initial == null) stringResource(R.string.add_item) else stringResource(R.string.edit_item),
        confirmLabel = stringResource(R.string.save),
        onConfirm = { save() },
        onDismiss = onDismiss
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it; nameError = false },
            label = { Text(stringResource(R.string.item_name_required)) },
            isError = nameError,
            supportingText = { if (nameError) Text(stringResource(R.string.name_required)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Field(stringResource(R.string.sku_barcode),sku, { sku = it }, Modifier.weight(1f), capitalization = KeyboardCapitalization.None)
            CategoryDropdown(category, categories, { category = it }, Modifier.weight(1f))
            Field(stringResource(R.string.location),location, { location = it }, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Field(stringResource(R.string.unit),unit, { unit = it }, Modifier.weight(1f), capitalization = KeyboardCapitalization.None)
            if (initial == null) {
                Field(
                    stringResource(R.string.initial_qty), qty, { qty = it.filter(Char::isDigit).take(7) },
                    Modifier.weight(1f), KeyboardType.Number
                )
            }
            Field(
                stringResource(R.string.alert_level), min, { min = it.filter(Char::isDigit).take(7) },
                Modifier.weight(1f), KeyboardType.Number
            )
            Field(
                stringResource(R.string.unit_cost), cost, { cost = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(12) },
                Modifier.weight(1f), KeyboardType.Decimal
            )
        }
        Field(stringResource(R.string.note),note, { note = it }, Modifier.fillMaxWidth())
        Text(
            stringResource(R.string.alert_level_hint, threshold),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Read-only dropdown of the existing categories (new ones are made in Categories). The first entry means "no category". */
@Composable
private fun CategoryDropdown(
    selected: String,
    categories: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var open by remember { mutableStateOf(false) }
    // An item can still carry a category that was removed from the list; keep it selectable.
    val options = if (selected.isNotBlank() && categories.none { it.equals(selected, ignoreCase = true) }) {
        listOf(selected) + categories
    } else categories

    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }, modifier = modifier) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.category), maxLines = 1) },
            placeholder = { Text(stringResource(R.string.none)) },
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.no_category_option), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                onClick = { onSelect(""); open = false }
            )
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = { onSelect(option); open = false }
                )
            }
        }
    }
}

/** Admin: add and delete categories. Deleting one leaves its items without a category. */
@Composable
private fun CategoryManagerDialog(
    categories: List<String>,
    items: List<Item>,
    onAdd: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newName by remember { mutableStateOf("") }
    var toDelete by remember { mutableStateOf<String?>(null) }
    val counts = remember(items) {
        items.groupingBy { it.category.trim().lowercase() }.eachCount()
    }
    fun add() {
        if (newName.isNotBlank()) {
            onAdd(newName)
            newName = ""
        }
    }

    CompactDialog(
        title = stringResource(R.string.category_management),
        confirmLabel = stringResource(R.string.done),
        onConfirm = onDismiss,
        onDismiss = onDismiss,
        dismissLabel = null
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text(stringResource(R.string.new_category)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.weight(1f)
            )
            Button(onClick = { add() }, enabled = newName.isNotBlank()) { Text(stringResource(R.string.add)) }
        }
        if (categories.isEmpty()) {
            Text(
                stringResource(R.string.no_categories_text),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        categories.forEach { name ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val n = counts[name.trim().lowercase()] ?: 0
                    Text(
                        LocalContext.current.resources.getQuantityString(R.plurals.item_count, n, n),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { toDelete = name }) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_named, name), tint = Red)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }

    toDelete?.let { name ->
        val n = counts[name.trim().lowercase()] ?: 0
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text(stringResource(R.string.delete_category_title, name)) },
            text = {
                Text(
                    if (n > 0) stringResource(R.string.delete_category_text_items, n)
                    else stringResource(R.string.delete_category_text_none)
                )
            },
            confirmButton = {
                Button(
                    onClick = { onDelete(name); toDelete = null },
                    colors = ButtonDefaults.buttonColors(containerColor = Red)
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

/** Check in (add stock) or check out (take stock) with a quantity stepper and optional note. */
@Composable
private fun StockMoveDialog(
    item: Item,
    type: String,
    onConfirm: (Int, String) -> Unit,
    onDismiss: () -> Unit
) {
    val isIn = type == ActionType.CHECK_IN
    var qtyText by remember { mutableStateOf("1") }
    var note by remember { mutableStateOf("") }
    val qty = qtyText.toIntOrNull() ?: 0
    val after = if (isIn) item.quantity + qty else item.quantity - qty
    val tooMany = after < 0
    val accent = if (isIn) Green else Orange

    CompactDialog(
        title = if (isIn) stringResource(R.string.check_in) else stringResource(R.string.check_out),
        confirmLabel = if (isIn) stringResource(R.string.check_in) else stringResource(R.string.check_out),
        confirmEnabled = qty > 0 && !tooMany,
        confirmColor = accent,
        onConfirm = { onConfirm(qty, note.trim()) },
        onDismiss = onDismiss
    ) {
        Text(item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            stringResource(R.string.current_stock, item.quantity, unitLabel(item.unit)),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)
        ) {
            FilledTonalIconButton(onClick = { qtyText = (qty - 1).coerceAtLeast(1).toString() }) {
                Icon(Icons.Default.Remove, contentDescription = stringResource(R.string.less))
            }
            OutlinedTextField(
                value = qtyText,
                onValueChange = { qtyText = it.filter(Char::isDigit).take(7) },
                label = { Text(stringResource(R.string.quantity)) },
                singleLine = true,
                isError = tooMany,
                textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(150.dp)
            )
            FilledTonalIconButton(onClick = { qtyText = (qty + 1).toString() }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.more))
            }
        }
        Text(
            if (tooMany) stringResource(R.string.not_enough_stock, item.quantity, unitLabel(item.unit))
            else stringResource(R.string.stock_after, after, unitLabel(item.unit)),
            color = if (tooMany) Red else accent,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text(if (isIn) stringResource(R.string.note_supplier) else stringResource(R.string.note_customer)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
