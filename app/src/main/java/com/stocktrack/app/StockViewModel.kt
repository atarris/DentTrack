package com.stocktrack.app

import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.google.android.gms.auth.api.identity.Identity
import com.stocktrack.app.data.ADMIN_NAME
import com.stocktrack.app.data.ActionType
import com.stocktrack.app.data.AddCategoryResult
import com.stocktrack.app.data.AddUserResult
import com.stocktrack.app.data.AppConfig
import com.stocktrack.app.data.AppDatabase
import com.stocktrack.app.data.AppSettings
import com.stocktrack.app.data.BackupCodec
import com.stocktrack.app.data.BackupException
import com.stocktrack.app.data.BackupRunner
import com.stocktrack.app.data.BackupSchedule
import com.stocktrack.app.data.BackupScheduler
import com.stocktrack.app.data.Category
import com.stocktrack.app.data.DEFAULT_UNIT
import com.stocktrack.app.data.DeleteUserResult
import com.stocktrack.app.data.DriveBackup
import com.stocktrack.app.data.MonthlyReport
import com.stocktrack.app.data.MonthlyReportRunner
import com.stocktrack.app.data.MonthlyReportScheduler
import com.stocktrack.app.data.MoveResult
import com.stocktrack.app.data.StockActions
import com.stocktrack.app.data.StockImporter
import com.stocktrack.app.data.GuiScheme
import com.stocktrack.app.data.Language
import com.stocktrack.app.data.Item
import com.stocktrack.app.data.LogEntry
import com.stocktrack.app.data.ReportBuilder
import com.stocktrack.app.data.SettingsActions
import com.stocktrack.app.data.User
import com.stocktrack.app.data.alertLevel
import com.stocktrack.app.data.cleanCategoryName
import com.stocktrack.app.data.isAdminAccount
import com.stocktrack.app.data.isLow
import com.stocktrack.app.web.WebServerService
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

class StockViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.get(app)
    private val dao = db.dao()
    private val actions = StockActions(db)
    private val appSettings = AppSettings(app)
    private val settingsActions = SettingsActions(app, appSettings)

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
        // The server normally survives the app closing; this brings it back if Android stopped it.
        if (appSettings.state.value.webServerEnabled) WebServerService.apply(app, true)
    }

    private fun who(): String = currentUser.value?.name ?: "Unknown"
    private fun say(text: String) {
        _messages.tryEmit(UiMessage(text))
    }

    fun showMessage(text: String) = say(text)

    /** A text in the app's chosen language. */
    private fun tr(@StringRes id: Int, vararg args: Any): String =
        Language.context(getApplication()).getString(id, *args)

    private fun unitText(unit: String): String =
        if (unit.equals(DEFAULT_UNIT, ignoreCase = true)) tr(R.string.unit_pcs) else unit

    private fun requireAdmin(): Boolean {
        if (isAdmin.value) return true
        say(tr(R.string.msg_only_admin))
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
            when (actions.addUser(clean)) {
                AddUserResult.RESERVED -> say(tr(R.string.msg_reserved_name, ADMIN_NAME))
                AddUserResult.EXISTS -> say(tr(R.string.msg_user_exists, clean))
                AddUserResult.ADDED -> Unit
            }
        }
    }

    fun deleteUser(user: User) {
        if (!requireAdmin()) return
        viewModelScope.launch {
            when (actions.deleteUser(user.id)) {
                DeleteUserResult.LAST_USER -> say(tr(R.string.msg_one_user_must_remain))
                DeleteUserResult.NOT_ALLOWED -> Unit
                DeleteUserResult.DELETED -> if (currentUserId.value == user.id) switchUser()
            }
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
        say(tr(R.string.msg_password_changed))
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
        say(tr(R.string.msg_password_removed))
        onResult(true)
    }

    /** Sets a password again after it was removed. */
    fun setAdminPassword(new: String) {
        if (!requireAdmin() || new.isEmpty() || appSettings.hasAdminPassword()) return
        appSettings.setAdminPassword(new)
        say(tr(R.string.msg_password_set))
    }

    // ------------------------------------------------------------- settings

    fun setLowStockThreshold(value: Int) {
        if (!requireAdmin()) return
        appSettings.setLowStockThreshold(value)
        say(tr(R.string.msg_threshold_set, appSettings.state.value.lowStockThreshold))
    }

    fun setInactivityMinutes(value: Int) {
        if (!requireAdmin()) return
        appSettings.setInactivityMinutes(value)
        val minutes = appSettings.state.value.inactivityMinutes
        say(if (minutes == 0) tr(R.string.msg_logout_off) else tr(R.string.msg_logout_after, minutes))
    }

    /** Saves the language. MainActivity restarts the screen to apply it. */
    fun setLanguage(language: String) {
        if (requireAdmin()) appSettings.setLanguage(language)
    }

    fun setHideAdmin(hide: Boolean) {
        if (!requireAdmin()) return
        appSettings.setHideAdmin(hide)
        say(
            if (hide) tr(R.string.msg_admin_hidden)
            else tr(R.string.msg_admin_shown)
        )
    }

    /** Every user may change this (Info tab), for the whole app. */
    fun setGuiScheme(scheme: GuiScheme) {
        appSettings.setGuiScheme(scheme)
    }

    fun setRandomScheme(enabled: Boolean) {
        appSettings.setRandomScheme(enabled)
    }

    fun setHiddenThemes(enabled: Boolean) {
        if (requireAdmin()) appSettings.setHiddenThemes(enabled)
    }

    fun setWebServer(enabled: Boolean) {
        if (!requireAdmin()) return
        appSettings.setWebServerEnabled(enabled)
        WebServerService.apply(getApplication(), enabled)
    }

    /** Back to the default settings, schedules included. Stock, log, users and the Admin password stay. */
    fun resetConfiguration() {
        if (!requireAdmin()) return
        settingsActions.resetConfiguration()
        WebServerService.apply(getApplication(), false)
        say(tr(R.string.msg_reset_done))
    }

    // ---------------------------------------------------------- activity log

    /** Deletes every activity log entry. Stock quantities do not change. */
    fun clearLog() {
        if (!requireAdmin()) return
        viewModelScope.launch {
            dao.clearLog()
            say(tr(R.string.msg_log_cleared))
        }
    }

    // ---------------------------------------------------------------- items

    /** Adds a new item (id == 0) or updates the details of an existing one. Admin only. */
    fun saveItem(item: Item, onSaved: (Long) -> Unit = {}) {
        if (!requireAdmin()) return
        viewModelScope.launch {
            onSaved(actions.saveItem(item, who()))
        }
    }

    // ----------------------------------------------------------- categories

    fun addCategory(name: String) {
        if (!requireAdmin()) return
        val clean = cleanCategoryName(name)
        if (clean.isEmpty()) return
        viewModelScope.launch {
            when (actions.addCategory(clean)) {
                AddCategoryResult.EXISTS -> say(tr(R.string.msg_category_exists, clean))
                AddCategoryResult.ADDED -> say(tr(R.string.msg_category_added, clean))
            }
        }
    }

    /** Deletes the category. Items in it are kept and get a blank category. */
    fun deleteCategory(name: String) {
        if (!requireAdmin()) return
        viewModelScope.launch {
            val changed = actions.deleteCategory(name, who())
            say(if (changed > 0) tr(R.string.msg_category_deleted_items, name, changed) else tr(R.string.msg_category_deleted, name))
        }
    }

    fun deleteItem(item: Item, onDone: () -> Unit = {}) {
        if (!requireAdmin()) return
        viewModelScope.launch {
            actions.deleteItem(item.id, who())
            say(tr(R.string.msg_item_removed, item.name))
            onDone()
        }
    }

    // ------------------------------------------------------ check in / out

    /** [type] is [ActionType.CHECK_IN] or [ActionType.CHECK_OUT]. [qty] is always positive. */
    fun moveStock(item: Item, type: String, qty: Int, note: String) {
        if (qty <= 0) return
        viewModelScope.launch {
            val updated = when (val result = actions.moveStock(item.id, type, qty, note, who())) {
                MoveResult.ItemGone -> {
                    say(tr(R.string.msg_item_gone))
                    return@launch
                }
                is MoveResult.NotEnough -> {
                    say(tr(R.string.msg_only_in_stock, result.item.quantity, unitText(result.item.unit)))
                    return@launch
                }
                is MoveResult.Moved -> result.item
            }
            val moved = if (type == ActionType.CHECK_IN) R.string.msg_moved_in else R.string.msg_moved_out
            val unit = unitText(updated.unit)
            say(tr(moved, qty, unit, updated.name, updated.quantity, unit))
            val threshold = appSettings.state.value.lowStockThreshold
            if (type == ActionType.CHECK_OUT && updated.isLow(threshold)) {
                say(tr(R.string.msg_low_alert, updated.name, updated.quantity, updated.alertLevel(threshold)))
            }
        }
    }

    // ------------------------------------------------------- clear database

    /** Deletes all items and categories, and the activity log when [alsoLog] is set. Users and settings stay. */
    fun clearStockDatabase(alsoLog: Boolean) {
        if (!requireAdmin()) return
        viewModelScope.launch {
            say(tr(R.string.msg_stock_cleared, actions.clearStock(alsoLog, who())))
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
                _messages.tryEmit(UiMessage(tr(R.string.msg_report_saved), uri))
            } catch (e: Exception) {
                say(tr(R.string.msg_export_failed, e.message ?: e.javaClass.simpleName))
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
                _messages.tryEmit(UiMessage(tr(R.string.msg_monthly_saved, year), uri))
            } catch (e: Exception) {
                say(tr(R.string.msg_export_failed, e.message ?: e.javaClass.simpleName))
            }
        }
    }

    /** Turns "save to Google Drive on the 1st of every month" on or off. */
    fun setMonthlyDrive(enabled: Boolean) {
        if (!requireAdmin()) return
        if (!settingsActions.setMonthlyDrive(enabled)) {
            say(tr(R.string.msg_connect_drive_first))
            return
        }
        say(tr(if (enabled) R.string.msg_monthly_on else R.string.msg_monthly_off))
    }

    /** Builds the report for [year] and uploads it to Google Drive right now (also a way to test the setup). */
    fun uploadMonthlyReportNow(year: Int) {
        if (!requireAdmin()) return
        viewModelScope.launch {
            say(tr(R.string.msg_uploading))
            val now = System.currentTimeMillis()
            val result = MonthlyReportRunner.uploadReport(getApplication(), year, now)
            appSettings.setLastMonthly(now, result.message)
            say(result.message)
        }
    }

    // --------------------------------------------------------------- backup

    fun setBackupSchedule(schedule: BackupSchedule) {
        if (requireAdmin()) settingsActions.setBackupSchedule(schedule)
    }

    fun setBackupLocal(enabled: Boolean) {
        if (requireAdmin()) settingsActions.setBackupLocal(enabled)
    }

    fun setBackupDrive(enabled: Boolean) {
        if (requireAdmin()) settingsActions.setBackupDrive(enabled)
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
                say(tr(R.string.msg_folder_access_failed, e.message ?: ""))
                return
            }
        }
        appSettings.setBackupFolder(uri?.toString())
    }

    /** Creates a backup right now with the destinations currently ticked. */
    fun backupNow() {
        if (!requireAdmin()) return
        viewModelScope.launch {
            say(tr(R.string.msg_backing_up))
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
                    say(tr(R.string.msg_drive_connected))
                }
                is DriveBackup.Auth.NeedsConsent -> launchConsent(auth.intent)
                is DriveBackup.Auth.Failed -> say(tr(R.string.msg_drive_reason, auth.reason))
            }
        }
    }

    fun onDriveConsentResult(data: Intent?) {
        try {
            Identity.getAuthorizationClient(getApplication<Application>())
                .getAuthorizationResultFromIntent(data)
            appSettings.setDriveConnected(true)
            say(tr(R.string.msg_drive_connected))
        } catch (e: Exception) {
            say(tr(R.string.drive_not_connected))
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
                        ?: throw BackupException(R.string.err_file_not_opened)
                }
                if (bytes.size > MAX_RESTORE_BYTES) throw BackupException(R.string.err_file_too_large)
                val parsed = withContext(Dispatchers.Default) { BackupCodec.parse(bytes) }
                db.withTransaction { BackupCodec.restore(dao, parsed) }
                say(tr(R.string.msg_restored, parsed.items.size, parsed.log.size))
            } catch (e: BackupException) {
                say(tr(R.string.msg_restore_failed, tr(e.res, *e.args)))
            } catch (e: Exception) {
                say(tr(R.string.msg_restore_failed, e.message ?: e.javaClass.simpleName))
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
                        ?: throw BackupException(R.string.err_file_not_opened)
                }
                if (bytes.size > MAX_RESTORE_BYTES) throw BackupException(R.string.err_file_too_large)
                val existing = items.value.map { StockImporter.key(it.category, it.name) }.toHashSet()
                val cats = categories.value.toHashSet()
                onLoaded(withContext(Dispatchers.Default) { StockImporter.parse(bytes, existing, cats) })
            } catch (e: BackupException) {
                say(tr(R.string.msg_import_failed, tr(e.res, *e.args)))
            } catch (e: Exception) {
                say(tr(R.string.msg_import_failed, e.message ?: e.javaClass.simpleName))
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
                say(tr(R.string.msg_imported, added))
            } catch (e: Exception) {
                say(tr(R.string.msg_import_failed, e.message ?: e.javaClass.simpleName))
            }
        }
    }

    companion object {
        private const val MAX_RESTORE_BYTES = 25 * 1024 * 1024
    }
}
