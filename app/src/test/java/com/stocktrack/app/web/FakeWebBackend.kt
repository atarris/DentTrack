package com.stocktrack.app.web

import com.stocktrack.app.R
import com.stocktrack.app.data.ADMIN_NAME
import com.stocktrack.app.data.ActionType
import com.stocktrack.app.data.AddCategoryResult
import com.stocktrack.app.data.AddUserResult
import com.stocktrack.app.data.AppConfig
import com.stocktrack.app.data.DeleteUserResult
import com.stocktrack.app.data.GuiScheme
import com.stocktrack.app.data.Item
import com.stocktrack.app.data.LogEntry
import com.stocktrack.app.data.MoveResult
import com.stocktrack.app.data.User
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** The web page's backend with everything in memory, using the app's real texts and page files. */
class FakeWebBackend : WebBackend {
    var password: String? = "secret"
    override val language: String get() = config.language
    var config = AppConfig()
    val items = ArrayList<Item>()
    val categories = ArrayList<String>()
    val users = arrayListOf(User(1, ADMIN_NAME), User(2, "Jonas"), User(3, "Rūta"))
    val log = ArrayList<LogEntry>()
    private var nextId = 1L

    private val mainDir: File = listOf(File("src/main"), File("app/src/main")).first { it.exists() }

    private fun strings(language: String): Map<String, String> {
        val folder = if (language == "lt") "values-lt" else "values"
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File(mainDir, "res/$folder/strings.xml"))
        val nodes = document.getElementsByTagName("string")
        return (0 until nodes.length).map { nodes.item(it) as Element }.associate {
            it.getAttribute("name") to it.textContent.replace("\\n", "\n").replace("\\'", "'")
        }
    }

    private val resourceNames: Map<Int, String> =
        R.string::class.java.fields.associate { it.getInt(null) to it.name }

    private fun raw(id: Int): String = strings(language)[resourceNames[id]] ?: strings("en").getValue(resourceNames.getValue(id))

    override fun text(id: Int, vararg args: Any): String = raw(id).format(*args)

    /** The app's real plural texts, picked with the rules Android uses for each language. */
    override fun itemCountText(count: Int): String {
        val folder = if (language == "lt") "values-lt" else "values"
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File(mainDir, "res/$folder/strings.xml"))
        val nodes = document.getElementsByTagName("item")
        val forms = (0 until nodes.length).map { nodes.item(it) as Element }
            .associate { it.getAttribute("quantity") to it.textContent }
        val teens = count % 100 in 11..19
        val form = when {
            language != "lt" -> if (count == 1) "one" else "other"
            count % 10 == 1 && !teens -> "one"
            count % 10 in 2..9 && !teens -> "few"
            else -> "other"
        }
        return forms.getValue(form).format(count)
    }

    override fun texts(): Map<String, String> = WebTexts.ALL.mapValues { raw(it.value) }

    override fun passwordStamp(): String? = password?.let { "stamp-$it" }

    override fun checkPassword(password: String): Boolean = password == this.password

    override fun file(name: String): ByteArray? {
        val file = if (name == "logo.png") File(mainDir, "res/drawable-nodpi/logo_miclinic.png") else File(mainDir, "assets/web/$name")
        return if (file.exists()) file.readBytes() else null
    }

    override fun threshold() = config.lowStockThreshold

    override fun config() = config

    override fun schemeColor(scheme: GuiScheme) = "#123456"

    override fun setLanguage(language: String) {
        config = config.copy(language = language)
    }

    override fun setGuiScheme(scheme: GuiScheme) {
        config = config.copy(guiScheme = scheme, randomScheme = false)
    }

    override fun setRandomScheme(enabled: Boolean) {
        config = config.copy(randomScheme = enabled)
    }

    override fun setHideAdmin(hide: Boolean) {
        config = config.copy(hideAdmin = hide)
    }

    override fun setInactivityMinutes(minutes: Int) {
        config = config.copy(inactivityMinutes = minutes)
    }

    override fun setLowStockThreshold(value: Int) {
        config = config.copy(lowStockThreshold = value)
    }

    override fun items() = items.sortedBy { it.name.lowercase() }

    override fun item(id: Long) = items.firstOrNull { it.id == id }

    override fun categories() = categories.sortedBy { it.lowercase() }

    override fun users() = users.toList()

    override fun recentLog(limit: Int) = log.asReversed().take(limit)

    private fun addLog(item: Item, type: String, change: Int, note: String = "") {
        log += LogEntry(
            timestamp = System.currentTimeMillis(), userName = ADMIN_NAME, itemId = item.id, itemName = item.name,
            type = type, change = change, quantityAfter = item.quantity, note = note
        )
    }

    override fun saveItem(item: Item): Long {
        if (item.category.isNotEmpty() && categories.none { it.equals(item.category, ignoreCase = true) }) categories += item.category
        if (item.id == 0L) {
            val saved = item.copy(id = nextId++)
            items += saved
            addLog(saved, ActionType.ITEM_ADDED, saved.quantity)
            return saved.id
        }
        items[items.indexOfFirst { it.id == item.id }] = item
        addLog(item, ActionType.ITEM_EDITED, 0)
        return item.id
    }

    override fun deleteItem(id: Long): Item? {
        val item = item(id) ?: return null
        items.remove(item)
        addLog(item.copy(quantity = 0), ActionType.ITEM_REMOVED, -item.quantity)
        return item
    }

    override fun moveStock(id: Long, type: String, qty: Int, note: String): MoveResult {
        val item = item(id) ?: return MoveResult.ItemGone
        val delta = if (type == ActionType.CHECK_OUT) -qty else qty
        if (item.quantity + delta < 0) return MoveResult.NotEnough(item)
        val updated = item.copy(quantity = item.quantity + delta)
        items[items.indexOf(item)] = updated
        addLog(updated, type, delta, note)
        return MoveResult.Moved(updated)
    }

    override fun addCategory(name: String): AddCategoryResult {
        if (categories.any { it.equals(name, ignoreCase = true) }) return AddCategoryResult.EXISTS
        categories += name
        return AddCategoryResult.ADDED
    }

    override fun deleteCategory(name: String): Int {
        categories.removeAll { it.equals(name, ignoreCase = true) }
        val inside = items.filter { it.category.equals(name, ignoreCase = true) }
        inside.forEach { items[items.indexOf(it)] = it.copy(category = "") }
        return inside.size
    }

    override fun addUser(name: String): AddUserResult = when {
        name.equals(ADMIN_NAME, ignoreCase = true) -> AddUserResult.RESERVED
        users.any { it.name.equals(name, ignoreCase = true) } -> AddUserResult.EXISTS
        else -> {
            users += User(nextId++ + 100, name)
            AddUserResult.ADDED
        }
    }

    override fun deleteUser(id: Long): DeleteUserResult {
        val user = users.firstOrNull { it.id == id }
        return when {
            user == null || user.name == ADMIN_NAME -> DeleteUserResult.NOT_ALLOWED
            users.count { it.name != ADMIN_NAME } <= 1 -> DeleteUserResult.LAST_USER
            else -> {
                users.remove(user)
                DeleteUserResult.DELETED
            }
        }
    }

    override fun storageReport() = "storage".toByteArray()

    override fun storageReportName() = "DentTrack_Backup_test.xlsx"

    override fun monthlyReport(year: Int) = "monthly $year".toByteArray()

    override fun monthlyReportName(year: Int) = "DentTrack_MonthlyReport_$year.xlsx"
}
