package com.stocktrack.app.data

import com.stocktrack.app.R
import kotlin.math.roundToInt

/**
 * Imports items from a CSV file:
 *  - column A = item name, column B = category (may be empty), column C = quantity,
 *  - a heading row is recognised (its quantity is not a number) and ignored,
 *  - rows with an empty or unreadable quantity are skipped,
 *  - negative quantities are not imported (unless the Admin chooses to import them as 0),
 *  - categories that do not exist yet are added to the category list.
 * Comma, semicolon and tab separated files work; UTF-8 and Windows-1257 are understood.
 */
object StockImporter {

    private const val MAX_QTY = 9_999_999

    class Row(val category: String, val name: String, val quantity: Int)

    class Preview(
        /** Rows that will be imported. */
        val rows: List<Row>,
        /** Rows with a negative quantity (quantity shown as in the file). */
        val negative: List<Row>,
        /** Rows whose quantity is empty or not a number. */
        val unreadable: Int,
        /** Same category and name repeated inside the file. */
        val duplicatesInFile: Int,
        /** Items that already exist in DentTrack (same category and name). */
        val alreadyInApp: Int,
        /** Categories in the file that DentTrack does not have yet. */
        val newCategories: List<String>
    ) {
        val perCategory: Map<String, Int> get() = rows.groupingBy { it.category }.eachCount()
    }

    fun key(category: String, name: String): String =
        category.trim().lowercase() + "|" + name.trim().lowercase()

    private fun clean(s: String) = s.replace(Regex("\\s+"), " ").trim()

    fun parse(bytes: ByteArray, existingKeys: Set<String>, existingCategories: Set<String>): Preview {
        val table = Csv.parse(Csv.decode(bytes))
        if (table.isEmpty()) throw BackupException(R.string.err_csv_empty)

        val rows = ArrayList<Row>()
        val negative = ArrayList<Row>()
        val seen = HashSet<String>()
        val newCats = LinkedHashMap<String, String>()
        val known = existingCategories.map { it.trim().lowercase() }.toHashSet()
        var unreadable = 0
        var duplicates = 0
        var existing = 0

        for ((index, cells) in table.withIndex()) {
            val name = clean(cells.getOrNull(0).orEmpty())
            if (name.isEmpty()) continue
            val category = clean(cells.getOrNull(1).orEmpty())
            val raw = cells.getOrNull(2).orEmpty().trim().replace(" ", "").replace(',', '.')
            val number = raw.toDoubleOrNull()

            // A heading row ("Item Name;Category;Quantity") is the first row with no numeric quantity.
            if (index == 0 && number == null) continue

            val k = key(category, name)
            if (existingKeys.contains(k)) { existing++; continue }
            if (!seen.add(k)) { duplicates++; continue }
            if (number == null) { unreadable++; continue }

            val qty = number.coerceIn(-MAX_QTY.toDouble(), MAX_QTY.toDouble()).roundToInt()
            val row = Row(category, name, qty)
            if (qty < 0) negative.add(row) else rows.add(row)
            if (category.isNotEmpty() && !known.contains(category.lowercase())) {
                newCats.putIfAbsent(category.lowercase(), category)
            }
        }

        if (rows.isEmpty() && negative.isEmpty() && existing == 0) {
            throw BackupException(R.string.err_csv_no_items)
        }
        return Preview(rows, negative, unreadable, duplicates, existing, newCats.values.toList())
    }

    /** Adds the items (and any new categories) with the quantity as starting stock. Call inside a transaction. */
    suspend fun insertAll(dao: StockDao, rows: List<Row>, userName: String, now: Long): Int {
        val existing = dao.itemsOnce().map { key(it.category, it.name) }.toHashSet()
        // Spelling of a category that already exists wins over the file's spelling.
        val canonical = HashMap<String, String>()
        dao.categoriesOnce().forEach { canonical[it.name.lowercase()] = it.name }
        var added = 0
        for (source in rows) {
            val category = if (source.category.isEmpty()) "" else canonical.getOrPut(source.category.lowercase()) {
                dao.insertCategory(Category(source.category))
                source.category
            }
            val row = Row(category, source.name, source.quantity)
            if (!existing.add(key(row.category, row.name))) continue
            val qty = row.quantity.coerceAtLeast(0)
            val id = dao.insertItem(
                Item(name = row.name, category = row.category, unit = DEFAULT_UNIT, quantity = qty, createdAt = now)
            )
            dao.insertLog(
                LogEntry(
                    timestamp = now, userName = userName, itemId = id, itemName = row.name,
                    type = ActionType.ITEM_ADDED, change = qty, quantityAfter = qty,
                    note = "Imported from CSV"
                )
            )
            added++
        }
        return added
    }
}
