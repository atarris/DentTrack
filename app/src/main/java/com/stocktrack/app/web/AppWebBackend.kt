package com.stocktrack.app.web

import android.content.Context
import com.stocktrack.app.R
import com.stocktrack.app.data.ADMIN_NAME
import com.stocktrack.app.data.AppConfig
import com.stocktrack.app.data.AppDatabase
import com.stocktrack.app.data.AppSettings
import com.stocktrack.app.data.BackupRunner
import com.stocktrack.app.data.GuiScheme
import com.stocktrack.app.data.Item
import com.stocktrack.app.data.Language
import com.stocktrack.app.data.MonthlyReport
import com.stocktrack.app.data.ReportBuilder
import com.stocktrack.app.data.StockActions
import com.stocktrack.app.ui.schemeColorHex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/** The web page's link to the app's database and settings. Everything is logged under the Admin's name. */
class AppWebBackend(private val context: Context, private val settings: AppSettings) : WebBackend {

    private val db = AppDatabase.get(context)
    private val dao = db.dao()
    private val actions = StockActions(db)

    private fun <T> blocking(block: suspend () -> T): T = runBlocking(Dispatchers.IO) { block() }

    /** Looked up on every use, so a language change in Settings reaches the page at once. */
    private fun localized(): Context = Language.context(context)

    override val language: String get() = settings.state.value.language

    override fun text(id: Int, vararg args: Any): String = localized().getString(id, *args)

    override fun itemCountText(count: Int): String =
        localized().resources.getQuantityString(R.plurals.item_count, count, count)

    override fun texts(): Map<String, String> {
        val localized = localized()
        // Unformatted, because the page fills in the %1$d placeholders itself.
        return WebTexts.ALL.mapValues { (_, id) -> localized.resources.getString(id) }
    }

    override fun passwordStamp(): String? = settings.adminPasswordStamp()

    override fun checkPassword(password: String): Boolean = settings.checkAdminPassword(password)

    override fun file(name: String): ByteArray? = runCatching {
        val stream = if (name == "logo.png") {
            context.resources.openRawResource(R.drawable.logo_miclinic)
        } else {
            context.assets.open("web/$name")
        }
        stream.use { it.readBytes() }
    }.getOrNull()

    override fun threshold(): Int = settings.state.value.lowStockThreshold

    override fun config(): AppConfig = settings.state.value

    override fun schemeColor(scheme: GuiScheme): String = schemeColorHex(scheme)

    override fun setLanguage(language: String) = settings.setLanguage(language)

    override fun setGuiScheme(scheme: GuiScheme) = settings.setGuiScheme(scheme)

    override fun setRandomScheme(enabled: Boolean) = settings.setRandomScheme(enabled)

    override fun setHideAdmin(hide: Boolean) = settings.setHideAdmin(hide)

    override fun setInactivityMinutes(minutes: Int) = settings.setInactivityMinutes(minutes)

    override fun setLowStockThreshold(value: Int) = settings.setLowStockThreshold(value)

    override fun items() = blocking { dao.itemsOnce() }

    override fun item(id: Long) = blocking { dao.item(id) }

    override fun categories() = blocking { dao.categoriesOnce().map { it.name } }

    override fun users() = blocking { dao.usersOnce() }

    override fun recentLog(limit: Int) = blocking { dao.recentLog(limit) }

    override fun saveItem(item: Item) = blocking { actions.saveItem(item, ADMIN_NAME) }

    override fun deleteItem(id: Long) = blocking { actions.deleteItem(id, ADMIN_NAME) }

    override fun moveStock(id: Long, type: String, qty: Int, note: String) =
        blocking { actions.moveStock(id, type, qty, note, ADMIN_NAME) }

    override fun addCategory(name: String) = blocking { actions.addCategory(name) }

    override fun deleteCategory(name: String) = blocking { actions.deleteCategory(name, ADMIN_NAME) }

    override fun addUser(name: String) = blocking { actions.addUser(name) }

    override fun deleteUser(id: Long) = blocking { actions.deleteUser(id) }

    override fun storageReport(): ByteArray = blocking {
        ReportBuilder.build(
            dao.itemsOnce(), dao.logOnce(), dao.usersOnce(), ADMIN_NAME,
            settings.state.value.lowStockThreshold, dao.categoriesOnce().map { it.name }
        )
    }

    override fun storageReportName(): String = BackupRunner.fileName()

    override fun monthlyReport(year: Int): ByteArray =
        blocking { MonthlyReport.build(dao.itemsOnce(), dao.logOnce(), year) }

    override fun monthlyReportName(year: Int): String =
        MonthlyReport.fileName(year, System.currentTimeMillis())
}
