package com.stocktrack.app.web

import com.stocktrack.app.R
import com.stocktrack.app.data.ADMIN_NAME
import com.stocktrack.app.data.ActionType
import com.stocktrack.app.data.AddCategoryResult
import com.stocktrack.app.data.AddUserResult
import com.stocktrack.app.data.AppConfig
import com.stocktrack.app.data.DEFAULT_UNIT
import com.stocktrack.app.data.DeleteUserResult
import com.stocktrack.app.data.GuiScheme
import com.stocktrack.app.data.Item
import com.stocktrack.app.data.Language
import com.stocktrack.app.data.LogEntry
import com.stocktrack.app.data.MoveResult
import com.stocktrack.app.data.User
import com.stocktrack.app.data.activeScheme
import com.stocktrack.app.data.alertLevel
import com.stocktrack.app.data.cleanCategoryName
import com.stocktrack.app.data.isAdminAccount
import com.stocktrack.app.data.isLow
import com.stocktrack.app.data.isOut
import com.stocktrack.app.data.stockValue
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.net.URI
import java.security.SecureRandom
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap

/** What the web page reads and changes. In the app this is the real database and settings. */
interface WebBackend {
    val language: String
    fun text(id: Int, vararg args: Any): String
    fun itemCountText(count: Int): String
    fun texts(): Map<String, String>

    /** The current password's stamp, or null while the Admin password is turned off. */
    fun passwordStamp(): String?
    fun checkPassword(password: String): Boolean

    /** The page's own files, by file name. */
    fun file(name: String): ByteArray?

    fun threshold(): Int
    fun config(): AppConfig
    fun schemeColor(scheme: GuiScheme): String
    fun setLanguage(language: String)
    fun setGuiScheme(scheme: GuiScheme)
    fun setRandomScheme(enabled: Boolean)
    fun setHideAdmin(hide: Boolean)
    fun setInactivityMinutes(minutes: Int)
    fun setLowStockThreshold(value: Int)

    fun items(): List<Item>
    fun item(id: Long): Item?
    fun categories(): List<String>
    fun users(): List<User>
    fun recentLog(limit: Int): List<LogEntry>

    fun saveItem(item: Item): Long
    fun deleteItem(id: Long): Item?
    fun moveStock(id: Long, type: String, qty: Int, note: String): MoveResult
    fun addCategory(name: String): AddCategoryResult
    fun deleteCategory(name: String): Int
    fun addUser(name: String): AddUserResult
    fun deleteUser(id: Long): DeleteUserResult

    fun storageReport(): ByteArray
    fun storageReportName(): String
    fun monthlyReport(year: Int): ByteArray
    fun monthlyReportName(year: Int): String
}

/**
 * The web page's server side: its files, the Admin sign-in and the JSON calls the page makes.
 * Only the Admin can sign in, with the Admin password. While that password is turned off nobody can.
 */
class WebApi(
    private val backend: WebBackend,
    private val now: () -> Long = System::currentTimeMillis
) {
    private class Session(val stamp: String, @Volatile var lastUsed: Long)
    private class Failures(var count: Int, var lockedUntil: Long)

    /** A problem to show on the page. */
    private class ApiError(val status: Int, val code: String, val text: String) : Exception(text)

    private val sessions = ConcurrentHashMap<String, Session>()
    private val failures = HashMap<String, Failures>()
    private val random = SecureRandom()

    private val routes: Map<String, (JSONObject) -> JSONObject> = mapOf(
        "/api/items" to ::saveItem,
        "/api/items/delete" to ::deleteItem,
        "/api/stock" to ::moveStock,
        "/api/categories" to ::addCategory,
        "/api/categories/delete" to ::deleteCategory,
        "/api/users" to ::addUser,
        "/api/users/delete" to ::deleteUser,
        "/api/settings" to ::changeSetting
    )

    fun handle(request: HttpRequest): HttpResponse = try {
        route(request)
    } catch (e: ApiError) {
        json(e.status, JSONObject().put("error", e.code).put("message", e.text))
    } catch (e: JSONException) {
        json(400, JSONObject().put("error", "bad_request").put("message", "Bad request"))
    }

    private fun route(request: HttpRequest): HttpResponse {
        val path = request.path
        if (request.method == "GET") {
            FILES[path]?.let { (name, type) ->
                val bytes = backend.file(name) ?: throw ApiError(404, "not_found", "Not found")
                return respond(200, type, bytes)
            }
            return when (path) {
                "/api/status" -> json(200, status(request))
                "/api/data" -> {
                    requireSession(request)
                    json(200, data())
                }
                "/report/storage" -> {
                    requireSession(request)
                    download(backend.storageReport(), backend.storageReportName())
                }
                "/report/monthly" -> {
                    requireSession(request)
                    val year = request.query["year"]?.toIntOrNull() ?: Calendar.getInstance().get(Calendar.YEAR)
                    if (year !in 2000..2100) throw ApiError(400, "bad_request", "Bad request")
                    download(backend.monthlyReport(year), backend.monthlyReportName(year))
                }
                else -> throw ApiError(404, "not_found", "Not found")
            }
        }
        if (request.method != "POST") throw ApiError(405, "bad_request", "Method not allowed")

        checkSameSite(request)
        val body = JSONObject(String(request.body, Charsets.UTF_8).ifBlank { "{}" })
        return when (path) {
            "/api/login" -> login(request, body)
            "/api/logout" -> logout(request)
            else -> {
                val action = routes[path] ?: throw ApiError(404, "not_found", "Not found")
                requireSession(request)
                json(200, action(body))
            }
        }
    }

    /**
     * A page on another site cannot make these calls: a browser adds an Origin header naming that
     * site, and it cannot send the JSON content type without asking first, which is never allowed.
     */
    private fun checkSameSite(request: HttpRequest) {
        val type = request.headers["content-type"].orEmpty().substringBefore(';').trim()
        if (!type.equals("application/json", ignoreCase = true)) {
            throw ApiError(415, "bad_request", "Bad request")
        }
        val origin = request.headers["origin"] ?: return
        val originHost = runCatching { URI(origin).authority }.getOrNull()
        if (originHost == null || !originHost.equals(request.headers["host"], ignoreCase = true)) {
            throw ApiError(403, "bad_request", "Bad request")
        }
    }

    // ------------------------------------------------------------------ sign-in

    private fun status(request: HttpRequest): JSONObject = JSONObject()
        .put("language", backend.language)
        .put("texts", JSONObject(backend.texts() as Map<*, *>))
        .put("passwordOn", backend.passwordStamp() != null)
        .put("signedIn", validSession(request) != null)

    private fun login(request: HttpRequest, body: JSONObject): HttpResponse {
        val stamp = backend.passwordStamp()
            ?: throw ApiError(403, CODE_PASSWORD_OFF, backend.text(R.string.web_password_off))
        val client = request.remoteAddress
        val time = now()
        synchronized(failures) {
            val locked = failures[client]?.lockedUntil ?: 0L
            if (locked > time) {
                val minutes = ((locked - time + 59_999) / 60_000).toInt()
                throw ApiError(429, "locked", backend.text(R.string.web_locked, minutes))
            }
        }
        if (!backend.checkPassword(body.optString("password"))) {
            recordFailure(client, time)
            throw ApiError(401, "wrong_password", backend.text(R.string.web_wrong_password))
        }
        synchronized(failures) { failures.remove(client) }
        sessions.entries.removeAll { time - it.value.lastUsed > SESSION_IDLE_MS }
        if (sessions.size >= MAX_SESSIONS) {
            sessions.entries.minByOrNull { it.value.lastUsed }?.let { sessions.remove(it.key) }
        }
        val token = ByteArray(32).also { random.nextBytes(it) }
            .joinToString("") { "%02x".format(it) }
        sessions[token] = Session(stamp, time)
        return json(
            200, JSONObject().put("ok", true),
            listOf("Set-Cookie" to "$COOKIE=$token; Path=/; HttpOnly; SameSite=Strict")
        )
    }

    private fun recordFailure(client: String, time: Long) = synchronized(failures) {
        if (failures.size >= MAX_TRACKED_CLIENTS && client !in failures) failures.clear()
        val entry = failures.getOrPut(client) { Failures(0, 0L) }
        entry.count++
        if (entry.count >= MAX_FAILURES) {
            entry.count = 0
            entry.lockedUntil = time + LOCK_MS
        }
    }

    private fun logout(request: HttpRequest): HttpResponse {
        cookie(request)?.let { sessions.remove(it) }
        return json(
            200, JSONObject().put("ok", true),
            listOf("Set-Cookie" to "$COOKIE=; Path=/; HttpOnly; SameSite=Strict; Max-Age=0")
        )
    }

    private fun cookie(request: HttpRequest): String? =
        request.headers["cookie"].orEmpty().split(";")
            .map { it.trim() }
            .firstOrNull { it.startsWith("$COOKIE=") }
            ?.substringAfter('=')
            ?.ifEmpty { null }

    /** A session ends after being idle, and as soon as the Admin password is changed or removed. */
    private fun validSession(request: HttpRequest): Session? {
        val token = cookie(request) ?: return null
        val session = sessions[token] ?: return null
        val time = now()
        if (session.stamp != backend.passwordStamp() || time - session.lastUsed > SESSION_IDLE_MS) {
            sessions.remove(token)
            return null
        }
        session.lastUsed = time
        return session
    }

    private fun requireSession(request: HttpRequest) {
        if (backend.passwordStamp() == null) {
            sessions.clear()
            throw ApiError(403, CODE_PASSWORD_OFF, backend.text(R.string.web_password_off))
        }
        validSession(request) ?: throw ApiError(401, "signed_out", backend.text(R.string.web_signed_out))
    }

    // --------------------------------------------------------------- read

    private fun data(): JSONObject {
        val threshold = backend.threshold()
        val items = backend.items()
        val counts = items.groupingBy { it.category.trim().lowercase() }.eachCount()
        return JSONObject()
            .put("language", backend.language)
            .put("threshold", threshold)
            .put("items", JSONArray(items.map { itemJson(it, threshold) }))
            .put("categories", JSONArray(backend.categories().map { name ->
                val count = counts[name.trim().lowercase()] ?: 0
                JSONObject().put("name", name).put("count", count).put("countText", backend.itemCountText(count))
            }))
            .put("users", JSONArray(backend.users().filterNot { it.isAdminAccount() }.map {
                JSONObject().put("id", it.id).put("name", it.name)
            }))
            .put("log", JSONArray(backend.recentLog(LOG_LIMIT).map { logJson(it) }))
            .put("settings", settingsJson())
    }

    /** The settings that are safe to change from the page. The rest are changed only on the tablet. */
    private fun settingsJson(): JSONObject {
        val config = backend.config()
        return JSONObject()
            .put("language", config.language)
            .put("guiScheme", config.guiScheme.name)
            .put("randomScheme", config.randomScheme)
            .put("activeScheme", config.activeScheme(now()).name)
            .put("schemes", JSONArray(GuiScheme.entries.map {
                JSONObject().put("id", it.name).put("label", backend.text(it.label)).put("color", backend.schemeColor(it))
            }))
            .put("hideAdmin", config.hideAdmin)
            .put("inactivityMinutes", config.inactivityMinutes)
            .put("lowStockThreshold", config.lowStockThreshold)
    }

    private fun itemJson(item: Item, threshold: Int): JSONObject = JSONObject()
        .put("id", item.id)
        .put("name", item.name)
        .put("sku", item.sku)
        .put("category", item.category)
        .put("location", item.location)
        .put("unit", unitText(item.unit))
        .put("quantity", item.quantity)
        .put("minQuantity", item.minQuantity)
        .put("unitCost", item.unitCost)
        .put("value", item.stockValue())
        .put("note", item.note)
        .put("alertLevel", item.alertLevel(threshold))
        .put("low", item.isLow(threshold))
        .put("out", item.isOut())

    private fun logJson(entry: LogEntry): JSONObject = JSONObject()
        .put("time", entry.timestamp)
        .put("user", entry.userName)
        .put("item", entry.itemName)
        .put("action", actionText(entry.type))
        .put("type", entry.type)
        .put("change", entry.change)
        .put("after", entry.quantityAfter)
        .put("note", entry.note)

    private fun actionText(type: String): String = when (type) {
        ActionType.CHECK_IN -> backend.text(R.string.check_in)
        ActionType.CHECK_OUT -> backend.text(R.string.check_out)
        ActionType.ITEM_ADDED -> backend.text(R.string.action_item_added)
        ActionType.ITEM_EDITED -> backend.text(R.string.action_item_edited)
        ActionType.ITEM_REMOVED -> backend.text(R.string.action_item_removed)
        else -> type
    }

    private fun unitText(unit: String): String =
        if (unit.equals(DEFAULT_UNIT, ignoreCase = true)) backend.text(R.string.unit_pcs) else unit

    // -------------------------------------------------------------- change

    private fun message(text: String): JSONObject = JSONObject().put("message", text)

    private fun invalid(text: String) = ApiError(400, "invalid", text)

    private fun JSONObject.text(name: String, max: Int): String = optString(name).trim().take(max)

    private fun JSONObject.whole(name: String, default: Int = 0): Int {
        val raw = opt(name)?.toString()?.trim().orEmpty()
        if (raw.isEmpty() || raw == "null") return default
        val value = raw.toIntOrNull()
        if (value == null || value !in 0..MAX_QUANTITY) throw invalid(backend.text(R.string.web_invalid_number))
        return value
    }

    private fun saveItem(body: JSONObject): JSONObject {
        val id = body.optLong("id", 0L)
        val name = body.text("name", MAX_NAME)
        if (name.isEmpty()) throw invalid(backend.text(R.string.name_required))
        val rawCost = body.optString("unitCost").trim().replace(',', '.')
        val cost = if (rawCost.isEmpty()) 0.0 else rawCost.toDoubleOrNull()
        if (cost == null || cost < 0.0 || !cost.isFinite()) throw invalid(backend.text(R.string.web_invalid_number))
        val unit = body.text("unit", MAX_NAME)
        val old = if (id != 0L) {
            backend.item(id) ?: throw ApiError(409, "gone", backend.text(R.string.msg_item_gone))
        } else {
            null
        }
        val item = Item(
            id = id,
            name = name,
            sku = body.text("sku", MAX_NAME),
            category = body.text("category", MAX_NAME),
            location = body.text("location", MAX_NAME),
            unit = if (unit.isEmpty() || unit.equals(backend.text(R.string.unit_pcs), ignoreCase = true)) DEFAULT_UNIT else unit,
            quantity = old?.quantity ?: body.whole("quantity"),
            minQuantity = body.whole("minQuantity"),
            unitCost = cost,
            note = body.text("note", MAX_NOTE),
            createdAt = old?.createdAt ?: now()
        )
        return JSONObject().put("id", backend.saveItem(item)).put("message", backend.text(R.string.web_saved))
    }

    private fun deleteItem(body: JSONObject): JSONObject {
        val removed = backend.deleteItem(body.getLong("id"))
            ?: throw ApiError(409, "gone", backend.text(R.string.msg_item_gone))
        return message(backend.text(R.string.msg_item_removed, removed.name))
    }

    private fun moveStock(body: JSONObject): JSONObject {
        val type = when (body.getString("type")) {
            "in" -> ActionType.CHECK_IN
            "out" -> ActionType.CHECK_OUT
            else -> throw ApiError(400, "bad_request", "Bad request")
        }
        val qty = body.whole("quantity")
        if (qty <= 0) throw invalid(backend.text(R.string.web_invalid_number))
        val moved = when (val result = backend.moveStock(body.getLong("id"), type, qty, body.text("note", MAX_NOTE))) {
            MoveResult.ItemGone -> throw ApiError(409, "gone", backend.text(R.string.msg_item_gone))
            is MoveResult.NotEnough -> throw ApiError(
                409, "not_enough",
                backend.text(R.string.msg_only_in_stock, result.item.quantity, unitText(result.item.unit))
            )
            is MoveResult.Moved -> result.item
        }
        val unit = unitText(moved.unit)
        val text = if (type == ActionType.CHECK_IN) R.string.msg_moved_in else R.string.msg_moved_out
        return message(backend.text(text, qty, unit, moved.name, moved.quantity, unit))
    }

    private fun addCategory(body: JSONObject): JSONObject {
        val name = cleanCategoryName(body.optString("name")).take(MAX_NAME)
        if (name.isEmpty()) throw invalid(backend.text(R.string.name_required))
        return when (backend.addCategory(name)) {
            AddCategoryResult.EXISTS -> throw ApiError(409, "exists", backend.text(R.string.msg_category_exists, name))
            AddCategoryResult.ADDED -> message(backend.text(R.string.msg_category_added, name))
        }
    }

    private fun deleteCategory(body: JSONObject): JSONObject {
        val name = body.getString("name")
        val changed = backend.deleteCategory(name)
        return message(
            if (changed > 0) backend.text(R.string.msg_category_deleted_items, name, changed)
            else backend.text(R.string.msg_category_deleted, name)
        )
    }

    private fun addUser(body: JSONObject): JSONObject {
        val name = body.text("name", MAX_NAME)
        if (name.isEmpty()) throw invalid(backend.text(R.string.name_required))
        return when (backend.addUser(name)) {
            AddUserResult.RESERVED -> throw ApiError(409, "exists", backend.text(R.string.msg_reserved_name, ADMIN_NAME))
            AddUserResult.EXISTS -> throw ApiError(409, "exists", backend.text(R.string.msg_user_exists, name))
            AddUserResult.ADDED -> message(backend.text(R.string.web_saved))
        }
    }

    private fun deleteUser(body: JSONObject): JSONObject =
        when (backend.deleteUser(body.getLong("id"))) {
            DeleteUserResult.LAST_USER -> throw ApiError(409, "last_user", backend.text(R.string.msg_one_user_must_remain))
            DeleteUserResult.NOT_ALLOWED -> throw ApiError(403, "bad_request", "Bad request")
            DeleteUserResult.DELETED -> message(backend.text(R.string.web_saved))
        }

    // ------------------------------------------------------------- settings

    private fun changeSetting(body: JSONObject): JSONObject {
        val saved = backend.text(R.string.web_saved)
        val text = when (body.getString("key")) {
            "language" -> {
                val language = body.getString("value")
                if (language != Language.ENGLISH && language != Language.LITHUANIAN) throw badRequest()
                backend.setLanguage(language)
                backend.text(R.string.web_saved)
            }
            "guiScheme" -> {
                val scheme = GuiScheme.entries.firstOrNull { it.name == body.getString("value") } ?: throw badRequest()
                backend.setGuiScheme(scheme)
                saved
            }
            "randomScheme" -> {
                backend.setRandomScheme(body.getBoolean("value"))
                saved
            }
            "hideAdmin" -> {
                val hide = body.getBoolean("value")
                backend.setHideAdmin(hide)
                backend.text(if (hide) R.string.msg_admin_hidden else R.string.msg_admin_shown)
            }
            "inactivityMinutes" -> {
                backend.setInactivityMinutes(body.whole("value"))
                val minutes = backend.config().inactivityMinutes
                if (minutes == 0) backend.text(R.string.msg_logout_off) else backend.text(R.string.msg_logout_after, minutes)
            }
            "lowStockThreshold" -> {
                backend.setLowStockThreshold(body.whole("value"))
                backend.text(R.string.msg_threshold_set, backend.config().lowStockThreshold)
            }
            else -> throw badRequest()
        }
        return message(text)
    }

    private fun badRequest() = ApiError(400, "bad_request", "Bad request")

    // ------------------------------------------------------------- responses

    private fun json(status: Int, value: JSONObject, extra: List<Pair<String, String>> = emptyList()) =
        respond(status, "application/json; charset=utf-8", value.toString().toByteArray(Charsets.UTF_8), extra)

    private fun download(bytes: ByteArray, fileName: String) = respond(
        200, XLSX, bytes, listOf("Content-Disposition" to "attachment; filename=\"$fileName\"")
    )

    private fun respond(status: Int, type: String, body: ByteArray, extra: List<Pair<String, String>> = emptyList()) =
        HttpResponse(status, type, body, SECURITY_HEADERS + extra)

    private companion object {
        const val COOKIE = "denttrack_session"
        const val CODE_PASSWORD_OFF = "password_off"
        const val SESSION_IDLE_MS = 30 * 60_000L
        const val MAX_SESSIONS = 20
        const val MAX_FAILURES = 5
        const val LOCK_MS = 5 * 60_000L
        const val MAX_TRACKED_CLIENTS = 256
        const val LOG_LIMIT = 100
        const val MAX_QUANTITY = 1_000_000
        const val MAX_NAME = 200
        const val MAX_NOTE = 1000
        const val XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

        val FILES = mapOf(
            "/" to ("index.html" to "text/html; charset=utf-8"),
            "/app.js" to ("app.js" to "text/javascript; charset=utf-8"),
            "/style.css" to ("style.css" to "text/css; charset=utf-8"),
            "/logo.png" to ("logo.png" to "image/png")
        )

        val SECURITY_HEADERS = listOf(
            "Content-Security-Policy" to "default-src 'none'; script-src 'self'; style-src 'self'; img-src 'self'; " +
                "connect-src 'self'; form-action 'self'; base-uri 'none'; frame-ancestors 'none'",
            "X-Content-Type-Options" to "nosniff",
            "X-Frame-Options" to "DENY",
            "Referrer-Policy" to "no-referrer",
            "Cache-Control" to "no-store"
        )
    }
}

/** The app's texts the page uses, under the names app.js asks for. */
object WebTexts {
    val ALL: Map<String, Int> = mapOf(
        "add" to R.string.add,
        "admin_hidden" to R.string.admin_hidden,
        "admin_shown" to R.string.admin_shown,
        "gui_scheme_title" to R.string.gui_scheme_title,
        "hide_admin_text" to R.string.hide_admin_text,
        "hide_admin_title" to R.string.hide_admin_title,
        "language_text" to R.string.language_text,
        "language_title" to R.string.language_title,
        "low_threshold_text" to R.string.low_threshold_text,
        "low_threshold_title" to R.string.low_threshold_title,
        "minutes_label" to R.string.minutes_label,
        "minutes_short" to R.string.minutes_short,
        "off" to R.string.off,
        "scheme_random" to R.string.scheme_random,
        "section_admin_users" to R.string.section_admin_users,
        "section_stock" to R.string.section_stock,
        "tab_settings" to R.string.tab_settings,
        "timeout_text" to R.string.timeout_text,
        "timeout_title" to R.string.timeout_title,
        "units_label" to R.string.units_label,
        "web_app_scheme_now" to R.string.web_app_scheme_now,
        "web_app_scheme_random" to R.string.web_app_scheme_random,
        "web_app_scheme_text" to R.string.web_app_scheme_text,
        "web_settings_note" to R.string.web_settings_note,
        "add_item" to R.string.add_item,
        "add_user" to R.string.add_user,
        "admin_sign_in" to R.string.admin_sign_in,
        "alert_level" to R.string.alert_level,
        "alert_level_hint" to R.string.alert_level_hint,
        "all_items" to R.string.all_items,
        "cancel" to R.string.cancel,
        "categories" to R.string.categories,
        "category" to R.string.category,
        "category_management" to R.string.category_management,
        "check_in" to R.string.check_in,
        "check_out" to R.string.check_out,
        "col_action" to R.string.col_action,
        "col_change" to R.string.col_change,
        "col_date_time" to R.string.col_date_time,
        "col_item" to R.string.col_item,
        "col_note" to R.string.col_note,
        "col_qty_after" to R.string.col_qty_after,
        "col_user" to R.string.col_user,
        "col_value" to R.string.col_value,
        "current_stock" to R.string.current_stock,
        "delete" to R.string.delete,
        "delete_category_text_items" to R.string.delete_category_text_items,
        "delete_category_text_none" to R.string.delete_category_text_none,
        "delete_category_title" to R.string.delete_category_title,
        "edit" to R.string.edit,
        "edit_item" to R.string.edit_item,
        "filter_all" to R.string.filter_all,
        "filter_check_in" to R.string.filter_check_in,
        "filter_check_out" to R.string.filter_check_out,
        "filter_item_changes" to R.string.filter_item_changes,
        "initial_qty" to R.string.initial_qty,
        "item_name_required" to R.string.item_name_required,
        "location" to R.string.location,
        "low_stock_alerts" to R.string.low_stock_alerts,
        "monthly_report" to R.string.monthly_report,
        "monthly_report_text" to R.string.monthly_report_text,
        "new_item" to R.string.new_item,
        "no_activity" to R.string.no_activity,
        "no_categories_web" to R.string.web_no_categories,
        "no_category_option" to R.string.no_category_option,
        "no_entries_match" to R.string.no_entries_match,
        "no_items_match" to R.string.no_items_match,
        "no_items_short" to R.string.no_items_short,
        "not_enough_stock" to R.string.not_enough_stock,
        "note" to R.string.note,
        "note_customer" to R.string.note_customer,
        "note_supplier" to R.string.note_supplier,
        "password" to R.string.password,
        "pill_low" to R.string.pill_low,
        "pill_out" to R.string.pill_out,
        "quantity" to R.string.quantity,
        "remove" to R.string.remove,
        "remove_item" to R.string.remove_item,
        "remove_item_text" to R.string.remove_item_text,
        "remove_item_text_qty" to R.string.remove_item_text_qty,
        "remove_item_title" to R.string.remove_item_title,
        "remove_user_text" to R.string.remove_user_text,
        "remove_user_title" to R.string.remove_user_title,
        "save" to R.string.save,
        "search_items" to R.string.search_items,
        "search_log" to R.string.search_log,
        "sign_in" to R.string.sign_in,
        "sku_barcode" to R.string.sku_barcode,
        "stock_after" to R.string.stock_after,
        "stock_filter_low" to R.string.stock_filter_low,
        "stock_value" to R.string.stock_value,
        "storage_report" to R.string.storage_report,
        "storage_report_text" to R.string.storage_report_text,
        "tab_activity" to R.string.tab_activity,
        "tab_items" to R.string.tab_items,
        "tab_reports" to R.string.tab_reports,
        "tab_users" to R.string.tab_users,
        "unit" to R.string.unit,
        "unit_cost" to R.string.unit_cost,
        "unit_pcs" to R.string.unit_pcs,
        "units_in_stock" to R.string.units_in_stock,
        "users_intro" to R.string.users_intro,
        "web_check_again" to R.string.web_check_again,
        "web_connection_lost" to R.string.web_connection_lost,
        "web_download" to R.string.web_download,
        "web_invalid_number" to R.string.web_invalid_number,
        "web_password_off" to R.string.web_password_off,
        "web_password_off_title" to R.string.web_password_off_title,
        "web_page_title" to R.string.web_page_title,
        "web_sign_out" to R.string.web_sign_out,
        "web_signed_out" to R.string.web_signed_out,
        "web_year" to R.string.web_year
    )
}
