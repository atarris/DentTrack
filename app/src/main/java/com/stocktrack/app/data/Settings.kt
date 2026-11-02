package com.stocktrack.app.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import java.security.SecureRandom

enum class BackupSchedule(val label: String, val days: Long) {
    OFF("Off", 0),
    DAILY("Daily", 1),
    WEEKLY("Weekly", 7),
    MONTHLY("Monthly", 30)
}

/** Everything the Settings and Backup screens show. The admin password is kept out of this class. */
data class AppConfig(
    val lowStockThreshold: Int = DEFAULT_LOW_STOCK,
    /** 0 = never log out automatically. */
    val inactivityMinutes: Int = 0,
    val backupSchedule: BackupSchedule = BackupSchedule.OFF,
    val backupLocal: Boolean = true,
    val backupDrive: Boolean = false,
    /** Folder chosen with the system picker (tree URI). null = app storage folder. */
    val backupFolderUri: String? = null,
    val driveConnected: Boolean = false,
    val lastBackupAt: Long = 0L,
    val lastBackupStatus: String = "",
    /** true = the Admin card is not shown on the user selection screen (hold the title instead). */
    val hideAdmin: Boolean = true,
    /** Save the Monthly Report to Google Drive on the 1st of every month. */
    val monthlyDrive: Boolean = false,
    val lastMonthlyAt: Long = 0L,
    val lastMonthlyStatus: String = "",
    val adminPasswordSet: Boolean = false
)

/** Small key/value store (SharedPreferences) with a StateFlow so screens update live. */
class AppSettings(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(read())
    val state: StateFlow<AppConfig> = _state.asStateFlow()

    // Also picks up changes made by the background backup worker (it uses its own instance).
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        _state.value = read()
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    private fun read(): AppConfig = AppConfig(
        lowStockThreshold = prefs.getInt(K_LOW, DEFAULT_LOW_STOCK),
        inactivityMinutes = prefs.getInt(K_INACTIVITY, 0),
        backupSchedule = runCatching {
            BackupSchedule.valueOf(prefs.getString(K_SCHEDULE, null) ?: BackupSchedule.OFF.name)
        }.getOrDefault(BackupSchedule.OFF),
        backupLocal = prefs.getBoolean(K_LOCAL, true),
        backupDrive = prefs.getBoolean(K_DRIVE, false),
        backupFolderUri = prefs.getString(K_FOLDER, null),
        driveConnected = prefs.getBoolean(K_DRIVE_CONNECTED, false),
        lastBackupAt = prefs.getLong(K_LAST_AT, 0L),
        lastBackupStatus = prefs.getString(K_LAST_STATUS, "") ?: "",
        hideAdmin = prefs.getBoolean(K_HIDE_ADMIN, true),
        monthlyDrive = prefs.getBoolean(K_MONTHLY_DRIVE, false),
        lastMonthlyAt = prefs.getLong(K_MONTHLY_AT, 0L),
        lastMonthlyStatus = prefs.getString(K_MONTHLY_STATUS, "") ?: "",
        adminPasswordSet = prefs.contains(K_PW_HASH)
    )

    private fun edit(block: SharedPreferences.Editor.() -> Unit) {
        val editor = prefs.edit()
        editor.block()
        editor.apply()
        _state.value = read()
    }

    // ------------------------------------------------------------- current user

    val currentUserId: Long get() = prefs.getLong(K_USER, -1L)

    fun setCurrentUser(id: Long) = edit { putLong(K_USER, id) }

    fun clearCurrentUser() = edit { remove(K_USER) }

    // ----------------------------------------------------------------- settings

    fun setLowStockThreshold(value: Int) = edit { putInt(K_LOW, value.coerceIn(0, 100_000)) }

    fun setInactivityMinutes(value: Int) = edit { putInt(K_INACTIVITY, value.coerceIn(0, 1_440)) }

    fun setHideAdmin(value: Boolean) = edit { putBoolean(K_HIDE_ADMIN, value) }

    // ---------------------------------------------------------- monthly report

    fun setMonthlyDrive(value: Boolean) = edit { putBoolean(K_MONTHLY_DRIVE, value) }

    /** "yyyy-MM" of the month for which the scheduled report was already handled. */
    var monthlyKey: String
        get() = prefs.getString(K_MONTHLY_KEY, "") ?: ""
        set(value) = edit { putString(K_MONTHLY_KEY, value) }

    fun setLastMonthly(time: Long, status: String) = edit {
        putLong(K_MONTHLY_AT, time)
        putString(K_MONTHLY_STATUS, status)
    }

    // ------------------------------------------------------------------- backup

    fun setBackupSchedule(value: BackupSchedule) = edit { putString(K_SCHEDULE, value.name) }

    fun setBackupLocal(value: Boolean) = edit { putBoolean(K_LOCAL, value) }

    fun setBackupDrive(value: Boolean) = edit { putBoolean(K_DRIVE, value) }

    fun setBackupFolder(uri: String?) = edit {
        if (uri == null) remove(K_FOLDER) else putString(K_FOLDER, uri)
    }

    fun setDriveConnected(value: Boolean) = edit { putBoolean(K_DRIVE_CONNECTED, value) }

    fun setLastBackup(time: Long, status: String) = edit {
        putLong(K_LAST_AT, time)
        putString(K_LAST_STATUS, status)
    }

    // ----------------------------------------------------------- admin password

    fun hasAdminPassword(): Boolean = prefs.contains(K_PW_HASH)

    /** Stores a salted SHA-256 hash, never the password itself. No complexity rules. */
    fun setAdminPassword(password: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val saltText = Base64.encodeToString(salt, Base64.NO_WRAP)
        edit {
            putString(K_PW_SALT, saltText)
            putString(K_PW_HASH, hash(saltText, password))
        }
    }

    fun checkAdminPassword(password: String): Boolean {
        val salt = prefs.getString(K_PW_SALT, null) ?: return false
        val expected = prefs.getString(K_PW_HASH, null) ?: return false
        return MessageDigest.isEqual(
            hash(salt, password).toByteArray(Charsets.UTF_8),
            expected.toByteArray(Charsets.UTF_8)
        )
    }

    private fun hash(salt: String, password: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return Base64.encodeToString(
            digest.digest((salt + password).toByteArray(Charsets.UTF_8)),
            Base64.NO_WRAP
        )
    }

    companion object {
        // Same file name as the first version of the app, so the remembered user survives the update.
        private const val FILE = "stocktrack"
        private const val K_USER = "current_user_id"
        private const val K_LOW = "low_stock_threshold"
        private const val K_INACTIVITY = "inactivity_minutes"
        private const val K_SCHEDULE = "backup_schedule"
        private const val K_LOCAL = "backup_local"
        private const val K_DRIVE = "backup_drive"
        private const val K_FOLDER = "backup_folder_uri"
        private const val K_DRIVE_CONNECTED = "drive_connected"
        private const val K_LAST_AT = "last_backup_at"
        private const val K_LAST_STATUS = "last_backup_status"
        private const val K_HIDE_ADMIN = "hide_admin"
        private const val K_MONTHLY_DRIVE = "monthly_drive"
        private const val K_MONTHLY_KEY = "monthly_key"
        private const val K_MONTHLY_AT = "monthly_at"
        private const val K_MONTHLY_STATUS = "monthly_status"
        private const val K_PW_SALT = "admin_pw_salt"
        private const val K_PW_HASH = "admin_pw_hash"
    }
}
