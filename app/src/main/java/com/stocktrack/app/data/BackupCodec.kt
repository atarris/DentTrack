package com.stocktrack.app.data

import androidx.annotation.StringRes
import com.stocktrack.app.R
import java.text.SimpleDateFormat
import java.util.Locale

/** Something is wrong with the file the Admin picked. The message is shown to the user. */
class BackupException(@StringRes val res: Int, vararg val args: Any) : Exception()

class ParsedBackup(
    val items: List<Item>,
    val log: List<LogEntry>,
    val users: List<String>,
    val categories: List<String> = emptyList()
)

/**
 * Reads a DentTrack report/backup (.xlsx) back into items, activity log and users, and writes
 * them into the database. Columns are found by their header names, so extra columns or a
 * different column order do no harm.
 */
object BackupCodec {

    private class Table(rows: List<List<String>>) {
        private val index: Map<String, Int> = rows.firstOrNull().orEmpty()
            .withIndex()
            .associate { (i, header) -> header.trim().lowercase() to i }
        val body: List<List<String>> = rows.drop(1)

        fun has(header: String): Boolean = index.containsKey(header.lowercase())

        fun get(row: List<String>, header: String): String {
            val i = index[header.lowercase()] ?: return ""
            return row.getOrNull(i)?.trim().orEmpty()
        }
    }

    fun parse(bytes: ByteArray): ParsedBackup {
        val sheets = try {
            XlsxReader.read(bytes)
        } catch (e: Exception) {
            throw BackupException(R.string.err_not_xlsx)
        }

        fun norm(s: String) = s.replace('\u00A0', ' ').trim().lowercase()
        fun sheet(name: String): List<List<String>>? =
            sheets.entries.firstOrNull { norm(it.key) == norm(name) }?.value

        // The "Stock" sheet by name; failing that, any sheet whose first row has Item + Quantity columns.
        val stockRows = sheet(ReportFormat.SHEET_STOCK)
            ?: sheets.values.firstOrNull { rows ->
                val head = rows.firstOrNull().orEmpty().map { norm(it) }
                head.contains(norm(ReportFormat.H_ITEM)) && head.contains(norm(ReportFormat.H_QTY))
            }
            ?: throw notAReport(sheets.keys)
        val logRows = sheet(ReportFormat.SHEET_LOG).orEmpty()

        // ---- items
        val stock = Table(stockRows)
        if (!stock.has(ReportFormat.H_ITEM) || !stock.has(ReportFormat.H_QTY)) {
            throw BackupException(R.string.err_stock_columns, ReportFormat.H_ITEM, ReportFormat.H_QTY)
        }
        val usedIds = HashSet<Long>()
        val items = stock.body.mapNotNull { r ->
            val name = stock.get(r, ReportFormat.H_ITEM)
            if (name.isEmpty()) return@mapNotNull null
            var id = stock.get(r, ReportFormat.H_ITEM_ID).toDoubleOrNull()?.toLong() ?: 0L
            if (id <= 0L || !usedIds.add(id)) id = 0L
            Item(
                id = id,
                name = name,
                sku = stock.get(r, ReportFormat.H_SKU),
                category = stock.get(r, ReportFormat.H_CATEGORY),
                location = stock.get(r, ReportFormat.H_LOCATION),
                unit = stock.get(r, ReportFormat.H_UNIT).ifBlank { DEFAULT_UNIT },
                quantity = toInt(stock.get(r, ReportFormat.H_QTY)).coerceAtLeast(0),
                minQuantity = toInt(stock.get(r, ReportFormat.H_OWN_ALERT)).coerceAtLeast(0),
                unitCost = stock.get(r, ReportFormat.H_COST).replace(',', '.').toDoubleOrNull() ?: 0.0,
                note = stock.get(r, ReportFormat.H_NOTE),
                createdAt = parseDate(stock.get(r, ReportFormat.H_CREATED)) ?: System.currentTimeMillis()
            )
        }

        // ---- activity log (file is newest first; insert oldest first so ids stay in order)
        val logTable = Table(logRows)
        val entries = logTable.body.mapNotNull { r ->
            val itemName = logTable.get(r, ReportFormat.H_ITEM)
            val whenText = logTable.get(r, ReportFormat.H_WHEN)
            if (itemName.isEmpty() && whenText.isEmpty()) return@mapNotNull null
            LogEntry(
                timestamp = parseDate(whenText) ?: 0L,
                userName = logTable.get(r, ReportFormat.H_USER).ifBlank { "Unknown" },
                itemId = logTable.get(r, ReportFormat.H_ITEM_ID).toDoubleOrNull()?.toLong() ?: 0L,
                itemName = itemName,
                type = ReportBuilder.typeFromText(logTable.get(r, ReportFormat.H_ACTION)),
                change = toInt(logTable.get(r, ReportFormat.H_CHANGE)),
                quantityAfter = toInt(logTable.get(r, ReportFormat.H_AFTER)),
                note = logTable.get(r, ReportFormat.H_NOTE)
            )
        }.reversed().sortedBy { it.timestamp }

        // ---- users (optional sheet)
        val users = sheet(ReportFormat.SHEET_USERS)?.let { rows ->
            val t = Table(rows)
            t.body.map { t.get(it, ReportFormat.H_USER) }.filter { it.isNotEmpty() }
        }.orEmpty()

        val categories = sheet(ReportFormat.SHEET_CATEGORIES)?.let { rows ->
            val t = Table(rows)
            t.body.map { t.get(it, ReportFormat.H_CATEGORY) }.filter { it.isNotEmpty() }
        }.orEmpty()

        return ParsedBackup(items, entries, users, categories)
    }

    private fun notAReport(found: Set<String>): BackupException {
        val list = found.joinToString(", ") { "\"${it.trim()}\"" }.ifEmpty { "—" }
        val message = when {
            found.any { it.trim().equals("Overview", ignoreCase = true) } -> R.string.err_report_monthly
            found.any { it.trim().equals("Implantai", ignoreCase = true) } -> R.string.err_report_stocklist
            else -> R.string.err_report_other
        }
        return BackupException(message, list)
    }

    /**
     * Replaces all items and the activity log with the file's content. Users in the file are added
     * if missing; existing users are kept. Call inside a database transaction.
     */
    suspend fun restore(dao: StockDao, data: ParsedBackup) {
        dao.clearItems()
        dao.clearLog()

        // Items that bring their own id go first, so generated ids can never collide with them.
        val idByName = HashMap<String, Long>()
        for (item in data.items.sortedBy { if (it.id == 0L) 1 else 0 }) {
            val newId = dao.insertItem(item)
            idByName.putIfAbsent(item.name.lowercase(), newId)
        }

        for (entry in data.log) {
            val itemId = if (entry.itemId > 0L) entry.itemId else idByName[entry.itemName.lowercase()] ?: 0L
            dao.insertLog(entry.copy(id = 0L, itemId = itemId))
        }

        // Categories: those listed in the file, plus every category an item uses.
        val known = dao.categoriesOnce().map { it.name.lowercase() }.toHashSet()
        for (name in data.categories + data.items.map { it.category }) {
            val clean = name.trim()
            if (clean.isNotEmpty() && known.add(clean.lowercase())) dao.insertCategory(Category(clean))
        }

        val existing = dao.usersOnce().map { it.name.lowercase() }.toHashSet()
        for (name in data.users) {
            if (name.isAdminName() || !existing.add(name.lowercase())) continue
            dao.insertUser(User(name = name))
        }
    }

    private fun String.isAdminName(): Boolean = trim().equals(ADMIN_NAME, ignoreCase = true)

    private fun toInt(text: String): Int = text.trim().toDoubleOrNull()?.toInt() ?: 0

    private fun parseDate(text: String): Long? {
        if (text.isBlank()) return null
        for (pattern in listOf("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm")) {
            val format = SimpleDateFormat(pattern, Locale.US)
            format.isLenient = false
            val time = runCatching { format.parse(text.trim())?.time }.getOrNull()
            if (time != null) return time
        }
        return null
    }
}
