package com.stocktrack.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface StockDao {
    // Users
    @Query("SELECT * FROM users ORDER BY name COLLATE NOCASE")
    fun users(): Flow<List<User>>

    @Query("SELECT COUNT(*) FROM users")
    suspend fun userCount(): Int

    @Insert
    suspend fun insertUser(user: User): Long

    @Query("DELETE FROM users WHERE id = :id")
    suspend fun deleteUser(id: Long)

    // Items
    @Query("SELECT * FROM items ORDER BY name COLLATE NOCASE")
    fun items(): Flow<List<Item>>

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun item(id: Long): Item?

    @Insert
    suspend fun insertItem(item: Item): Long

    @Update
    suspend fun updateItem(item: Item)

    @Query("DELETE FROM items WHERE id = :id")
    suspend fun deleteItem(id: Long)

    // Activity log
    @Query("SELECT * FROM activity_log ORDER BY timestamp DESC, id DESC")
    fun log(): Flow<List<LogEntry>>

    @Insert
    suspend fun insertLog(entry: LogEntry)

    // One-shot reads and bulk operations (backup / restore)
    @Query("SELECT * FROM users WHERE name = 'Admin' COLLATE NOCASE LIMIT 1")
    suspend fun adminUser(): User?

    @Query("SELECT * FROM users ORDER BY name COLLATE NOCASE")
    suspend fun usersOnce(): List<User>

    @Query("SELECT * FROM items ORDER BY name COLLATE NOCASE")
    suspend fun itemsOnce(): List<Item>

    @Query("SELECT * FROM activity_log ORDER BY timestamp DESC, id DESC")
    suspend fun logOnce(): List<LogEntry>

    // Categories
    @Query("SELECT * FROM categories ORDER BY name COLLATE NOCASE")
    fun categories(): Flow<List<Category>>

    @Query("SELECT * FROM categories ORDER BY name COLLATE NOCASE")
    suspend fun categoriesOnce(): List<Category>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCategory(category: Category): Long

    @Query("DELETE FROM categories WHERE name = :name COLLATE NOCASE")
    suspend fun deleteCategory(name: String)

    /** Items of a deleted category keep existing, with a blank category. */
    @Query("UPDATE items SET category = '' WHERE category = :name COLLATE NOCASE")
    suspend fun blankCategory(name: String): Int

    @Query("SELECT COUNT(*) FROM items WHERE category = :name COLLATE NOCASE")
    suspend fun itemCountIn(name: String): Int

    @Query("DELETE FROM items")
    suspend fun clearItems()

    @Query("DELETE FROM activity_log")
    suspend fun clearLog()
}

@Database(
    entities = [User::class, Item::class, LogEntry::class, Category::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): StockDao

    companion object {
        /** v2 adds the categories table, filled with the categories already used by items. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `categories` (`name` TEXT NOT NULL, PRIMARY KEY(`name`))")
                db.execSQL(
                    "INSERT OR IGNORE INTO categories (name) " +
                        "SELECT DISTINCT TRIM(category) FROM items WHERE TRIM(category) != ''"
                )
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "stocktrack.db"
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}
