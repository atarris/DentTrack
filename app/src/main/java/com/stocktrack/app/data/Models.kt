package com.stocktrack.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A person who can use the app. No password: users are picked from a list. */
@Entity(tableName = "users")
data class User(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String
)

/** A product category. Items refer to it by name; an item with a blank category has none. */
@Entity(tableName = "categories")
data class Category(
    @PrimaryKey val name: String
)

/** How the default unit is saved and written to reports. The screens show it as R.string.unit_pcs. */
const val DEFAULT_UNIT = "pcs"

/** A stock item. */
@Entity(tableName = "items")
data class Item(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sku: String = "",
    val category: String = "",
    val location: String = "",
    val unit: String = DEFAULT_UNIT,
    val quantity: Int = 0,
    /** Low-stock alert level. 0 = no alert level set. */
    val minQuantity: Int = 0,
    val unitCost: Double = 0.0,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * One line of the activity log. The item name is copied in, so the log stays
 * readable even after an item has been removed.
 */
@Entity(tableName = "activity_log")
data class LogEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val userName: String,
    val itemId: Long,
    val itemName: String,
    val type: String,
    /** Signed change in quantity (+ in, - out, 0 for edits). */
    val change: Int,
    val quantityAfter: Int,
    val note: String = ""
)

object ActionType {
    const val CHECK_IN = "CHECK_IN"
    const val CHECK_OUT = "CHECK_OUT"
    const val ITEM_ADDED = "ITEM_ADDED"
    const val ITEM_EDITED = "ITEM_EDITED"
    const val ITEM_REMOVED = "ITEM_REMOVED"
}

fun Item.isOut(): Boolean = quantity <= 0

/** Name of the built-in administrator account. It is hidden from the user picker. */
const val ADMIN_NAME = "Admin"

fun User.isAdminAccount(): Boolean = name.trim().equals(ADMIN_NAME, ignoreCase = true)

/** Default for the global low stock threshold (units). */
const val DEFAULT_LOW_STOCK = 10

/** An item's own alert level wins; items without one use the global threshold from Settings. */
fun Item.alertLevel(globalThreshold: Int): Int = if (minQuantity > 0) minQuantity else globalThreshold

/** Low = stock has reached or fallen below the alert level that applies to this item. */
fun Item.isLow(globalThreshold: Int): Boolean {
    val level = alertLevel(globalThreshold)
    return level > 0 && quantity <= level
}

fun Item.stockValue(): Double = quantity * unitCost
