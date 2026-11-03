package com.stocktrack.app

import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.google.android.gms.auth.api.identity.Identity
import com.stocktrack.app.data.ADMIN_NAME
import com.stocktrack.app.data.ActionType
import com.stocktrack.app.data.AppConfig
import com.stocktrack.app.data.AppDatabase
import com.stocktrack.app.data.AppSettings
import com.stocktrack.app.data.BackupCodec
import com.stocktrack.app.data.BackupException
import com.stocktrack.app.data.BackupRunner
import com.stocktrack.app.data.BackupSchedule
import com.stocktrack.app.data.BackupScheduler
import com.stocktrack.app.data.Category
import com.stocktrack.app.data.DriveBackup
import com.stocktrack.app.data.MonthlyReport
import com.stocktrack.app.data.MonthlyReportRunner
import com.stocktrack.app.data.MonthlyReportScheduler
import com.stocktrack.app.data.StockImporter
import com.stocktrack.app.data.Item
import com.stocktrack.app.data.LogEntry
import com.stocktrack.app.data.ReportBuilder
import com.stocktrack.app.data.User
import com.stocktrack.app.data.alertLevel
import com.stocktrack.app.data.isAdminAccount
import com.stocktrack.app.data.isLow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One-off message shown in a snackbar. If [shareUri] is set the snackbar offers a "Share" action. */
data class UiMessage(val text: String, val shareUri: Uri? = null)

private class MoveResult(val error: String?, val updated: Item?)

class StockViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.get(app)
    private val dao = db.dao()
    private val appSettings = AppSettings(app)

    /** Global settings (low stock threshold, timeout, backup options ...). */
    val config: StateFlow<AppConfig> = appSettings.state

    /** null while the first database read is still loading. Includes the hidden Admin account. */
    val users: StateFlow<List<User>?> =
        dao.users().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * The names shown on the start screen. The built-in Admin account is left out while
     * "Hide Admin user" (Settings) is on.
     */
    val pickableUsers: StateFlow<List<User>?> =
        combine(users, config) { list, cfg ->
            if (cfg.hideAdmin) list?.filterNot { it.isAdminAccount() } else list
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val items: StateFlow<List<Item>> =
        dao.items().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Category names for the dropdown and Category Management, sorted A-Z. */
    val categories: StateFlow<List<String>> =
        dao.categories().map { list -> list.map { it.name } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val log: StateFlow<List<LogEntry>> =
        dao.log().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val currentUserId = MutableStateFlow(appSettings.currentUserId)

    /** True only after the Admin password was entered in this session. Never saved. */
    private val adminAuthed = MutableStateFlow(false)

    /** The signed-in user (picked on the start screen, remembered between launches). */
    val currentUser: StateFlow<User?> =
        combine(users, currentUserId, adminAuthed) { list, id, authed ->
            val user = list?.firstOrNull { it.id == id }
            if (user != null && user.isAdminAccount() && !authed) null else user
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val isAdmin: StateFlow<Boolean> =
        currentUser.map { it?.isAdminAccount() == true }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 16)
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

    init {
        viewModelScope.launch {
            // The Admin account must exist so that its actions can be logged.
            if (dao.adminUser() == null) dao.insertUser(User(name = ADMIN_NAME))
        }
        // Make sure a previously chosen backup schedule is still registered.
        BackupScheduler.apply(app, appSettings.state.value, replace = false)
        MonthlyReportScheduler.apply(app, appSettings.state.value.monthlyDrive, replace = false)
    }

    private fun who(): String = currentUser.value?.name ?: "Unknown"
    private fun say(text: String) {
        _messages.tryEmit(UiMessage(text))
    }

    fun showMessage(text: String) = say(text)

    private fun requireAdmin(): Boolean {
        if (isAdmin.value) return true
        say("Only the Admin can do this")
        return false
    }

    // ---------------------------------------------------------------- users

    fun selectUser(id: Long) {
        appSettings.setCurrentUser(id)
        currentUserId.value = id
    }

    /** Back to the "who is using the app" screen (also ends an Admin session). */
    fun switchUser() {
        appSettings.clearCurrentUser()
        adminAuthed.value = false
        currentUserId.value = -1L
    }

    fun addUser(name: String) {
        if (!requireAdmin()) return
        val clean = name.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            val exists = users.value.orEmpty().any { it.name.equals(clean, ignoreCase = true) }
            when {
                clean.equals(ADMIN_NAME, ignoreCase = true) -> say("\"$ADMIN_NAME\" is a reserved name")
                exists -> say("A user named \"$clean\" already exists")
                else -> dao.insertUser(User(name = clean))
            }
        }
    }

    fun deleteUser(user: User) {
        if (!requireAdmin()) return
        viewModelScope.launch {
            if (user.isAdminAccount()) return@launch
            if (users.value.orEmpty().count { !it.isAdminAccount() } <= 1) {
                say("At least one user must remain")
                return@launch
            }
            dao.deleteUser(user.id)
            if (currentUserId.value == user.id) switchUser()
        }
    }

    // ------------------------------------------------------------ admin access

    /**
     * Signs in as Admin. With [createNew] the password is created first (only possible while none
     * exists yet). The Admin session is not remembered: after a restart the password is asked again.
     */
    fun adminSignIn(password: String, createNew: Boolean, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            if (createNew) {
                if (password.isEmpty() || appSettings.hasAdminPassword()) {
                    onResult(false)
                    return@launch
                }
                appSettings.setAdminPassword(password)
            } else if (!appSettings.checkAdminPassword(password)) {
                onResult(false)
                return@launch
            }
            enterAdmin()
            onResult(true)
        }
    }

    /** Opens the Admin menu directly. Only works after the Admin removed the password in Settings. */
    fun adminEnterWithoutPassword() {
        viewModelScope.launch {
            if (appSettings.state.value.adminPasswordRemoved) enterAdmin()
        }
    }

    private suspend fun enterAdmin() {
        val admin = dao.adminUser()
            ?: User(id = dao.insertUser(User(name = ADMIN_NAME)), name = ADMIN_NAME)
        appSettings.clearCurrentUser()
        adminAuthed.value = true
        currentUserId.value = admin.id
    }

    fun changeAdminPassword(current: String, new: String, onResult: (Boolean) -> Unit) {
        if (!requireAdmin()) {
            onResult(false)
            return
        }
        if (new.isEmpty() || !appSettings.checkAdminPassword(current)) {
            onResult(false)
            return
        }
        appSettings.setAdminPassword(new)
        say("Admin password changed")
        onResult(true)
    }

    /** Removes the password after checking it. From then on the Admin menu opens without one. */
    fun removeAdminPassword(current: String, onResult: (Boolean) -> Unit) {
        if (!requireAdmin()) {
            onResult(false)
            return
        }
        if (!appSettings.checkAdminPassword(current)) {
            onResult(false)
            return
        }
        appSettings.removeAdminPassword()
        say("Admin password removed")
        onResult(true)
    }

    /** Sets a password again after it was removed. */
    fun setAdminPassword(new: String) {
        if (!requireAdmin() || new.isEmpty() || appSettings.hasAdminPassword()) return
        appSettings.setAdminPassword(new)
        say("Admin password set")
    }

    // ------------------------------------------------------------- settings

    fun setLowStockThreshold(value: Int) {
        if (!requireAdmin()) return
        appSettings.setLowStockThreshold(value)
        say("Low stock threshold set to ${appSettings.state.value.lowStockThreshold}")
    }

    fun setInactivityMinutes(value: Int) {
        if (!requireAdmin()) return
        appSettings.setInactivityMinutes(value)
        val minutes = appSettings.state.value.inactivityMinutes
        say(if (minutes == 0) "Automatic log-out is off" else "Users are logged out after $minutes min of inactivity")
    }

    fun setHideAdmin(hide: Boolean) {
        if (!requireAdmin()) return
        appSettings.setHideAdmin(hide)
        say(
            if (hide) "Admin is hidden. Hold the DentTrack title for 3 seconds to sign in."
            else "Admin is now shown on the user selection screen"
        )
    }

    /** Back to the default settings, schedules included. Stock, log, users and the Admin password stay. */
    fun resetConfiguration() {
        if (!requireAdmin()) return
        val app = getApplication<Application>()
        appSettings.state.value.backupFolderUri?.let { folder ->
            runCatching {
                app.contentResolver.releasePersistableUriPermission(
                    Uri.parse(folder), Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
        }
        appSettings.resetConfig()
        BackupScheduler.apply(app, appSettings.state.value, replace = true)
        MonthlyReportScheduler.apply(app, appSettings.state.value.monthlyDrive, replace = true)
        say("App configuration reset to defaults")
    }

    // ---------------------------------------------------------- activity log

    /** Deletes every activity log entry. Stock quantities do not change. */
    fun clearLog() {
        if (!requireAdmin()) return
        viewModelScope.launch {
            dao.clearLog()
            say("Activity log cleared")
        }
    }

    // ---------------------------------------------------------------- items

    /** Adds a new item (id == 0) or updates the details of an existing one. Admin only. */
    fun saveItem(item: Item, onSaved: (Long) -> Unit = {}) {
        if (!requireAdmin()) return
        viewModelScope.launch {
            val user = who()
            val now = System.currentTimeMillis()
            val savedId = db.withTransaction {
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
            onSaved(savedId)
        }
    }

    // ----------------------------------------------------------- categories

    /** Returns the stored spelling of [name], adding the category when it is new. "" = no category. Call inside a transaction. */
    private suspend fun canonicalCategory(name: String): String {
        val clean = name.trim()
        if (clean.isEmpty()) return ""
        val found = dao.categoriesOnce().firstOrNull { it.name.equals(clean, ignoreCase = true) }
        if (found != null) return found.name
        dao.insertCategory(Category(clean))
        return clean
    }

    fun addCategory(name: String) {
        if (!requireAdmin()) return
        val clean = name.trim().replace(Regex("\\s+"), " ")
        if (clean.isEmpty()) return
        viewModelScope.launch {
            val exists = dao.categoriesOnce().any { it.name.equals(clean, ignoreCase = true) }
            if (exists) {
                say("The category \"$clean\" already exists")
            } else {
                dao.insertCategory(Category(clean))
                say("Category \"$clean\" added")
            }
        }
    }

    /** Deletes the category. Items in it are kept and get a blank category. */
    fun deleteCategory(name: String) {
        if (!requireAdmin()) return
        viewModelScope.launch {
            val user = who()
            val changed = db.withTransaction {
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
            say(if (changed > 0) "Category \"$name\" deleted, $changed items now have no category" else "Category \"$name\" deleted")
        }
    }

    fun deleteItem(item: Item, onDone: () -> Unit = {}) {
        if (!requireAdmin()) return
        viewModelScope.launch {
            val user = who()
            db.withTransaction {
                val cur = dao.item(item.id) ?: return@withTransaction
                dao.deleteItem(cur.id)
                dao.insertLog(
                    LogEntry(
                        timestamp = System.currentTimeMillis(), userName = user,
                        itemId = cur.id, itemName = cur.name, type = ActionType.ITEM_REMOVED,
                        change = -cur.quantity, quantityAfter = 0,
                        note = if (cur.quantity > 0) "Removed with ${cur.quantity} ${cur.unit} in stock" else ""
                    )
                )
            }
            say("\"${item.name}\" removed")
            onDone()
        }
    }

    // ------------------------------------------------------ check in / out

    /** [type] is [ActionType.CHECK_IN] or [ActionType.CHECK_OUT]. [qty] is always positive. */
    fun moveStock(item: Item, type: String, qty: Int, note: String) {
        if (qty <= 0) return
        viewModelScope.launch {
            val user = who()
            val result = db.withTransaction {
                val cur = dao.item(item.id)
                    ?: return@withTransaction MoveResult("This item no longer exists", null)
                val delta = if (type == ActionType.CHECK_OUT) -qty else qty
                val newQty = cur.quantity + delta
                if (newQty < 0) {
                    return@withTransaction MoveResult("Only ${cur.quantity} ${cur.unit} in stock", null)
                }
                val updated = cur.copy(quantity = newQty)
                dao.updateItem(updated)
                dao.insertLog(
                    LogEntry(
                        timestamp = System.currentTimeMillis(), userName = user,
                        itemId = cur.id, itemName = cur.name, type = type,
                        change = delta, quantityAfter = newQty, note = note
                    )
                )
                MoveResult(null, updated)
            }
            val updated = result.updated
            if (result.error != null || updated == null) {
                say(result.error ?: "Could not update stock")
                return@launch
            }
            val verb = if (type == ActionType.CHECK_IN) "Checked in" else "Checked out"
            say("$verb $qty ${updated.unit} · ${updated.name} now ${updated.quantity} ${updated.unit}")
            val threshold = appSettings.state.value.lowStockThreshold
            if (type == ActionType.CHECK_OUT && updated.isLow(threshold)) {
                say("Low stock alert: ${updated.name} is at ${updated.quantity} (alert level ${updated.alertLevel(threshold)})")
            }
        }
    }

    // --------------------------------------------------------------- export

    /** Writes the Excel storage report to a file the user chose (Storage Access Framework). */
    fun exportReport(uri: Uri) {
        viewModelScope.launch {
            try {
                val bytes = withContext(Dispatchers.Default) {
                    ReportBuilder.build(
                        items.value, log.value, users.value.orEmpty(), who(),
                        appSettings.state.value.lowStockThreshold,
                        categories.value
                    )
                }
                withContext(Dispatchers.IO) {
                    val stream = getApplication<Application>().contentResolver.openOutputStream(uri, "wt")
                        ?: throw IllegalStateException("Cannot open the selected file")
                    stream.use { it.write(bytes) }
                }
                _messages.tryEmit(UiMessage("Excel report saved", uri))
            } catch (e: Exception) {
                say("Export failed: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    // -------------------------------------------------------- monthly report

    /** Writes the Monthly Report for [year] to a file the Admin chose. */
    fun exportMonthlyReport(uri: Uri, year: Int) {
        if (!requireAdmin()) return
        viewModelScope.launch {
            try {
                val bytes = withContext(Dispatchers.Default) {
                    MonthlyReport.build(items.value, log.value, year)
                }
                withContext(Dispatchers.IO) {
                    val stream = getApplication<Application>().contentResolver.openOutputStream(uri, "wt")
                        ?: throw IllegalStateException("Cannot open the selected file")
                    stream.use { it.write(bytes) }
                }
                _messages.tryEmit(UiMessage("Monthly report $year saved", uri))
            } catch (e: Exception) {
                say("Export failed: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    /** Turns "save to Google Drive on the 1st of every month" on or off. */
    fun setMonthlyDrive(enabled: Boolean) {
        if (!requireAdmin()) return
        if (enabled && !appSettings.state.value.driveConnected) {
            say("Connect Google Drive first (Backup tab)")
            return
        }
        if (enabled) {
            // The first automatic report is for the next 1st, not for the current month.
            appSettings.monthlyKey = MonthlyReportRunner.monthKey(System.currentTimeMillis())
        }
        appSettings.setMonthlyDrive(enabled)
        MonthlyReportScheduler.apply(getApplication(), enabled, replace = true)
        say(if (enabled) "The Monthly Report will be saved to Google Drive on the 1st of each month" else "Monthly Drive upload is off")
    }

    /** Builds the report for [year] and uploads it to Google Drive right now (also a way to test the setup). */
    fun uploadMonthlyReportNow(year: Int) {
        if (!requireAdmin()) return
        viewModelScope.launch {
            say("Uploading…")
            val now = System.currentTimeMillis()
            val result = MonthlyReportRunner.uploadReport(getApplication(), year, now)
            appSettings.setLastMonthly(now, result.message)
            say(result.message)
        }
    }

    // --------------------------------------------------------------- backup

    private fun reschedule() {
        BackupScheduler.apply(getApplication(), appSettings.state.value, replace = true)
    }

    fun setBackupSchedule(schedule: BackupSchedule) {
        if (!requireAdmin()) return
        appSettings.setBackupSchedule(schedule)
        reschedule()
    }

    fun setBackupLocal(enabled: Boolean) {
        if (!requireAdmin()) return
        appSettings.setBackupLocal(enabled)
        reschedule()
    }

    fun setBackupDrive(enabled: Boolean) {
        if (!requireAdmin()) return
        appSettings.setBackupDrive(enabled)
        reschedule()
    }

    /** Remembers a folder picked with the system folder picker as the local backup location. */
    fun setBackupFolder(uri: Uri?) {
        if (!requireAdmin()) return
        if (uri != null) {
            try {
                getApplication<Application>().contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (e: Exception) {
                say("Could not keep access to that folder: ${e.message ?: ""}")
                return
            }
        }
        appSettings.setBackupFolder(uri?.toString())
    }

    /** Creates a backup right now with the destinations currently ticked. */
    fun backupNow() {
        if (!requireAdmin()) return
        viewModelScope.launch {
            say("Backing up…")
            val outcome = BackupRunner.run(getApplication(), "Manual backup by ${who()}")
            say(outcome.message)
        }
    }

    // ---- Google Drive

    /** Checks the Google Drive permission. If Google needs the user to agree, [launchConsent] is called. */
    fun connectDrive(launchConsent: (PendingIntent) -> Unit) {
        if (!requireAdmin()) return
        viewModelScope.launch {
            when (val auth = DriveBackup.authorize(getApplication())) {
                is DriveBackup.Auth.Token -> {
                    appSettings.setDriveConnected(true)
                    say("Google Drive is connected")
                }
                is DriveBackup.Auth.NeedsConsent -> launchConsent(auth.intent)
                is DriveBackup.Auth.Failed -> say("Google Drive: ${auth.reason}")
            }
        }
    }

    fun onDriveConsentResult(data: Intent?) {
        try {
            Identity.getAuthorizationClient(getApplication<Application>())
                .getAuthorizationResultFromIntent(data)
            appSettings.setDriveConnected(true)
            say("Google Drive is connected")
        } catch (e: Exception) {
            say("Google Drive was not connected")
        }
    }

    // -------------------------------------------------------------- restore

    /** Replaces all items and the activity log with the content of a DentTrack backup file. */
    fun restoreFromFile(uri: Uri) {
        if (!requireAdmin()) return
        viewModelScope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(uri)
                        ?.use { it.readBytes() }
                        ?: throw BackupException("the file could not be opened")
                }
                if (bytes.size > MAX_RESTORE_BYTES) throw BackupException("the file is too large")
                val parsed = withContext(Dispatchers.Default) { BackupCodec.parse(bytes) }
                db.withTransaction { BackupCodec.restore(dao, parsed) }
                say("Restored ${parsed.items.size} items and ${parsed.log.size} log entries")
            } catch (e: BackupException) {
                say("Restore failed: ${e.message}")
            } catch (e: Exception) {
                say("Restore failed: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    // --------------------------------------------------------- stock import

    /** Reads a CSV stock list and reports what it would import. Nothing is changed yet. */
    fun loadStockImport(uri: Uri, onLoaded: (StockImporter.Preview) -> Unit) {
        if (!requireAdmin()) return
        viewModelScope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(uri)
                        ?.use { it.readBytes() }
                        ?: throw BackupException("the file could not be opened")
                }
                if (bytes.size > MAX_RESTORE_BYTES) throw BackupException("the file is too large")
                val existing = items.value.map { StockImporter.key(it.category, it.name) }.toHashSet()
                val cats = categories.value.toHashSet()
                onLoaded(withContext(Dispatchers.Default) { StockImporter.parse(bytes, existing, cats) })
            } catch (e: BackupException) {
                say("Import failed: ${e.message}")
            } catch (e: Exception) {
                say("Import failed: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    /** Adds the previewed items. Items that already exist (same category and name) are skipped. */
    fun importStock(rows: List<StockImporter.Row>) {
        if (!requireAdmin()) return
        viewModelScope.launch {
            try {
                val user = who()
                val added = db.withTransaction {
                    StockImporter.insertAll(dao, rows, user, System.currentTimeMillis())
                }
                say("Imported $added items")
            } catch (e: Exception) {
                say("Import failed: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    companion object {
        private const val MAX_RESTORE_BYTES = 25 * 1024 * 1024
    }
}
