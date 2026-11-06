package com.stocktrack.app.data

import androidx.room.withTransaction

/** What a check in or check out did. */
sealed class MoveResult {
    data object ItemGone : MoveResult()
    data class NotEnough(val item: Item) : MoveResult()
    data class Moved(val item: Item) : MoveResult()
}

enum class AddCategoryResult { ADDED, EXISTS }
enum class AddUserResult { ADDED, RESERVED, EXISTS }
enum class DeleteUserResult { DELETED, NOT_ALLOWED, LAST_USER }

/**
 * The stock changes made by the tablet screens and the web page, so that both write the same
 * activity log. [user] is the name that goes into the log. Who may call these is up to the caller.
 */
class StockActions(private val db: AppDatabase) {

    private val dao = db.dao()

    /** Adds a new item (id == 0) or updates the details of an existing one. Returns its id. */
    suspend fun saveItem(item: Item, user: String): Long {
        val now = System.currentTimeMillis()
        return db.withTransaction {
            val item = item.copy(category = canonicalCategory(item.category))
            if (item.id == 0L) {
                val newId = dao.insertItem(item)
                dao.insertLog(
                    LogEntry(
                        timestamp = now, userName = user, itemId = newId, itemName = item.name,
                        type = ActionType.ITEM_ADDED, change = item.quantity,
                        quantityAfter = item.quantity,
                        note = if (item.quantity > 0) "Initial stock" else ""
                    )
                )
                newId
            } else {
                val old = dao.item(item.id)
                if (old == null) {
                    item.id
                } else {
                    // Quantity only ever changes through check in / check out.
                    dao.updateItem(item.copy(quantity = old.quantity, createdAt = old.createdAt))
                    val note = if (old.name != item.name) "Renamed from \"${old.name}\"" else "Details updated"
                    dao.insertLog(
                        LogEntry(
                            timestamp = now, userName = user, itemId = item.id, itemName = item.name,
                            type = ActionType.ITEM_EDITED, change = 0,
                            quantityAfter = old.quantity, note = note
                        )
                    )
                    item.id
                }
            }
        }
    }

    /** Returns the removed item, or null when it no longer exists. */
    suspend fun deleteItem(id: Long, user: String): Item? = db.withTransaction {
        val cur = dao.item(id) ?: return@withTransaction null
        dao.deleteItem(cur.id)
        dao.insertLog(
            LogEntry(
                timestamp = System.currentTimeMillis(), userName = user,
                itemId = cur.id, itemName = cur.name, type = ActionType.ITEM_REMOVED,
                change = -cur.quantity, quantityAfter = 0,
                note = if (cur.quantity > 0) "Removed with ${cur.quantity} ${cur.unit} in stock" else ""
            )
        )
        cur
    }

    /** [type] is [ActionType.CHECK_IN] or [ActionType.CHECK_OUT]. [qty] is always positive. */
    suspend fun moveStock(id: Long, type: String, qty: Int, note: String, user: String): MoveResult =
        db.withTransaction {
            val cur = dao.item(id) ?: return@withTransaction MoveResult.ItemGone
            val delta = if (type == ActionType.CHECK_OUT) -qty else qty
            val newQty = cur.quantity + delta
            if (newQty < 0) return@withTransaction MoveResult.NotEnough(cur)
            val updated = cur.copy(quantity = newQty)
            dao.updateItem(updated)
            dao.insertLog(
                LogEntry(
                    timestamp = System.currentTimeMillis(), userName = user,
                    itemId = cur.id, itemName = cur.name, type = type,
                    change = delta, quantityAfter = newQty, note = note
                )
            )
            MoveResult.Moved(updated)
        }

    /** Returns the stored spelling of [name], adding the category when it is new. "" = no category. Call inside a transaction. */
    private suspend fun canonicalCategory(name: String): String {
        val clean = name.trim()
        if (clean.isEmpty()) return ""
        val found = dao.categoriesOnce().firstOrNull { it.name.equals(clean, ignoreCase = true) }
        if (found != null) return found.name
        dao.insertCategory(Category(clean))
        return clean
    }

    /** [name] must already be cleaned with [cleanCategoryName]. */
    suspend fun addCategory(name: String): AddCategoryResult {
        val exists = dao.categoriesOnce().any { it.name.equals(name, ignoreCase = true) }
        if (exists) return AddCategoryResult.EXISTS
        dao.insertCategory(Category(name))
        return AddCategoryResult.ADDED
    }

    /** Deletes the category. Items in it are kept and get a blank category. Returns how many. */
    suspend fun deleteCategory(name: String, user: String): Int = db.withTransaction {
        val count = dao.blankCategory(name)
        dao.deleteCategory(name)
        if (count > 0) {
            dao.insertLog(
                LogEntry(
                    timestamp = System.currentTimeMillis(), userName = user, itemId = 0L,
                    itemName = "Category \"$name\"", type = ActionType.ITEM_EDITED, change = 0,
                    quantityAfter = 0, note = "Category deleted, $count items now have no category"
                )
            )
        }
        count
    }

    /** Deletes all items and categories, and the activity log when [alsoLog] is set. Returns how many items. */
    suspend fun clearStock(alsoLog: Boolean, user: String): Int = db.withTransaction {
        val removed = dao.itemsOnce().size
        dao.clearItems()
        dao.clearCategories()
        if (alsoLog) {
            dao.clearLog()
        } else {
            dao.insertLog(
                LogEntry(
                    timestamp = System.currentTimeMillis(), userName = user, itemId = 0L,
                    itemName = "Stock database", type = ActionType.ITEM_REMOVED, change = 0,
                    quantityAfter = 0, note = "Cleared, $removed items removed"
                )
            )
        }
        removed
    }

    /** [name] must already be trimmed and not empty. */
    suspend fun addUser(name: String): AddUserResult {
        val exists = dao.usersOnce().any { it.name.equals(name, ignoreCase = true) }
        return when {
            name.equals(ADMIN_NAME, ignoreCase = true) -> AddUserResult.RESERVED
            exists -> AddUserResult.EXISTS
            else -> {
                dao.insertUser(User(name = name))
                AddUserResult.ADDED
            }
        }
    }

    suspend fun deleteUser(id: Long): DeleteUserResult {
        val all = dao.usersOnce()
        val user = all.firstOrNull { it.id == id }
        return when {
            user == null || user.isAdminAccount() -> DeleteUserResult.NOT_ALLOWED
            all.count { !it.isAdminAccount() } <= 1 -> DeleteUserResult.LAST_USER
            else -> {
                dao.deleteUser(id)
                DeleteUserResult.DELETED
            }
        }
    }
}

fun cleanCategoryName(name: String): String = name.trim().replace(Regex("\\s+"), " ")
