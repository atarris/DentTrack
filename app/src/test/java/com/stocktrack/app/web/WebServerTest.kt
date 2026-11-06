package com.stocktrack.app.web

import com.stocktrack.app.data.GuiScheme
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.net.InetAddress
import java.net.Socket

/** Runs the real server on a real socket, with the stock kept in memory. */
class WebServerTest {

    private class Reply(val status: Int, val headers: Map<String, String>, val body: String) {
        val json: JSONObject get() = JSONObject(body)
    }

    private val backend = FakeWebBackend()
    private var clock = 1_000_000L
    private lateinit var server: HttpServer

    @Before
    fun start() {
        server = HttpServer(0, ::isLocalNetwork, WebApi(backend) { clock }::handle)
        server.start()
    }

    @After
    fun stop() = server.stop()

    private fun raw(request: ByteArray): Reply = Socket("127.0.0.1", server.boundPort).use { socket ->
        socket.soTimeout = 5_000
        socket.getOutputStream().write(request)
        socket.getOutputStream().flush()
        val text = String(socket.getInputStream().readBytes(), Charsets.UTF_8)
        val head = text.substringBefore("\r\n\r\n").split("\r\n")
        Reply(
            head.first().split(" ")[1].toInt(),
            head.drop(1).associate { it.substringBefore(":").lowercase() to it.substringAfter(": ") },
            text.substringAfter("\r\n\r\n")
        )
    }

    private fun get(path: String, cookie: String? = null): Reply = raw(
        ("GET $path HTTP/1.1\r\nHost: tablet:8080\r\n" + (cookie?.let { "Cookie: $it\r\n" } ?: "") + "\r\n")
            .toByteArray()
    )

    private fun post(
        path: String,
        body: String,
        cookie: String? = null,
        type: String = "application/json",
        origin: String? = null
    ): Reply {
        val bytes = body.toByteArray()
        val head = "POST $path HTTP/1.1\r\nHost: tablet:8080\r\nContent-Type: $type\r\n" +
            "Content-Length: ${bytes.size}\r\n" +
            (cookie?.let { "Cookie: $it\r\n" } ?: "") +
            (origin?.let { "Origin: $it\r\n" } ?: "") + "\r\n"
        return raw(head.toByteArray() + bytes)
    }

    private fun signIn(password: String = "secret"): String {
        val reply = post("/api/login", JSONObject().put("password", password).toString())
        assertEquals(200, reply.status)
        return reply.headers.getValue("set-cookie").substringBefore(";")
    }

    // ---------------------------------------------------------------- sign-in

    @Test
    fun theDataNeedsASignIn() {
        assertEquals(401, get("/api/data").status)
        assertEquals(401, post("/api/stock", "{}").status)
        assertEquals(401, get("/report/storage").status)
        assertEquals(401, get("/api/data", "denttrack_session=made-up").status)
    }

    @Test
    fun theRightPasswordSignsIn() {
        val cookie = signIn()
        assertEquals(200, get("/api/data", cookie).status)
        assertTrue(get("/api/status", cookie).json.getBoolean("signedIn"))
    }

    @Test
    fun theCookieCannotBeReadByScripts() {
        val header = post("/api/login", """{"password":"secret"}""").headers.getValue("set-cookie")
        assertTrue(header.contains("HttpOnly"))
        assertTrue(header.contains("SameSite=Strict"))
    }

    @Test
    fun aWrongPasswordIsRefused() {
        val reply = post("/api/login", """{"password":"nope"}""")
        assertEquals(401, reply.status)
        assertEquals("Wrong password", reply.json.getString("message"))
        assertNull(reply.headers["set-cookie"])
    }

    @Test
    fun fiveWrongPasswordsLockTheSignInForFiveMinutes() {
        repeat(5) { assertEquals(401, post("/api/login", """{"password":"nope"}""").status) }
        // Locked: even the right password is refused now.
        assertEquals(429, post("/api/login", """{"password":"secret"}""").status)
        clock += 5 * 60_000L + 1
        assertEquals(200, post("/api/login", """{"password":"secret"}""").status)
    }

    @Test
    fun signingOutEndsTheSession() {
        val cookie = signIn()
        assertEquals(200, post("/api/logout", "{}", cookie).status)
        assertEquals(401, get("/api/data", cookie).status)
    }

    @Test
    fun aSessionEndsAfterHalfAnHourIdle() {
        val cookie = signIn()
        clock += 29 * 60_000L
        assertEquals(200, get("/api/data", cookie).status)
        clock += 31 * 60_000L
        assertEquals(401, get("/api/data", cookie).status)
    }

    // ---------------------------------------------------------- password off

    @Test
    fun withThePasswordOffNobodyCanSignIn() {
        backend.password = null
        val status = get("/api/status").json
        assertFalse(status.getBoolean("passwordOn"))
        assertFalse(status.getBoolean("signedIn"))

        val empty = post("/api/login", """{"password":""}""")
        assertEquals(403, empty.status)
        assertEquals("password_off", empty.json.getString("error"))
        assertEquals(403, post("/api/login", """{"password":"secret"}""").status)
        assertEquals(403, get("/api/data").status)
    }

    @Test
    fun turningThePasswordOffEndsExistingSessions() {
        val cookie = signIn()
        backend.password = null
        assertEquals("password_off", get("/api/data", cookie).json.getString("error"))
        backend.password = "secret"
        assertEquals(401, get("/api/data", cookie).status)
    }

    @Test
    fun changingThePasswordEndsExistingSessions() {
        val cookie = signIn()
        backend.password = "new one"
        assertEquals(401, get("/api/data", cookie).status)
        assertEquals(401, post("/api/login", """{"password":"secret"}""").status)
        signIn("new one")
    }

    // ------------------------------------------------------------ other sites

    @Test
    fun otherWebsitesCannotMakeChanges() {
        val cookie = signIn()
        val item = """{"name":"Gloves"}"""
        assertEquals(403, post("/api/items", item, cookie, origin = "http://evil.example").status)
        assertEquals(415, post("/api/items", item, cookie, type = "text/plain").status)
        assertEquals(415, post("/api/items", item, cookie, type = "application/x-www-form-urlencoded").status)
        assertTrue(backend.items.isEmpty())
        assertEquals(200, post("/api/items", item, cookie, origin = "http://tablet:8080").status)
        assertEquals(1, backend.items.size)
    }

    @Test
    fun onlyTheLocalNetworkIsAnswered() {
        fun local(address: String) = isLocalNetwork(InetAddress.getByName(address))
        assertTrue(local("192.168.1.20"))
        assertTrue(local("10.0.0.5"))
        assertTrue(local("172.16.3.4"))
        assertTrue(local("127.0.0.1"))
        assertFalse(local("8.8.8.8"))
        assertFalse(local("100.64.0.1"))
    }

    @Test
    fun aClientThatIsNotAllowedGetsNoAnswer() {
        val closed = HttpServer(0, { false }, WebApi(backend)::handle)
        closed.start()
        try {
            Socket("127.0.0.1", closed.boundPort).use { socket ->
                socket.getOutputStream().write("GET / HTTP/1.1\r\nHost: x\r\n\r\n".toByteArray())
                assertEquals(-1, socket.getInputStream().read())
            }
        } finally {
            closed.stop()
        }
    }

    // ------------------------------------------------------------------ stock

    @Test
    fun itemsCanBeAddedEditedAndRemoved() {
        val cookie = signIn()
        val added = post(
            "/api/items",
            """{"name":" Pirštinės M ","category":"Vienkartiniai","unit":"pcs","quantity":"12","minQuantity":"3","unitCost":"2,50","note":"x"}""",
            cookie
        )
        assertEquals(200, added.status)
        val id = added.json.getLong("id")
        val saved = backend.item(id)!!
        assertEquals("Pirštinės M", saved.name)
        assertEquals("pcs", saved.unit)
        assertEquals(12, saved.quantity)
        assertEquals(2.5, saved.unitCost, 0.0001)
        assertTrue(backend.categories.contains("Vienkartiniai"))

        // An edit never changes the quantity, even if the request asks for it.
        assertEquals(200, post("/api/items", """{"id":$id,"name":"Renamed","quantity":"999"}""", cookie).status)
        assertEquals("Renamed", backend.item(id)!!.name)
        assertEquals(12, backend.item(id)!!.quantity)

        assertEquals(200, post("/api/items/delete", """{"id":$id}""", cookie).status)
        assertNull(backend.item(id))
        assertEquals(409, post("/api/items/delete", """{"id":$id}""", cookie).status)
    }

    @Test
    fun badItemValuesAreRefused() {
        val cookie = signIn()
        assertEquals(400, post("/api/items", """{"name":"   "}""", cookie).status)
        assertEquals(400, post("/api/items", """{"name":"A","quantity":"-1"}""", cookie).status)
        assertEquals(400, post("/api/items", """{"name":"A","quantity":"lots"}""", cookie).status)
        assertEquals(400, post("/api/items", """{"name":"A","unitCost":"-3"}""", cookie).status)
        assertEquals(400, post("/api/items", """{"name":"A","unitCost":"NaN"}""", cookie).status)
        assertEquals(400, post("/api/items", "not json", cookie).status)
        assertEquals(409, post("/api/items", """{"id":777,"name":"A"}""", cookie).status)
        assertTrue(backend.items.isEmpty())
    }

    @Test
    fun stockCanBeCheckedInAndOutButNotBelowZero() {
        val cookie = signIn()
        val id = post("/api/items", """{"name":"Gloves","quantity":"5"}""", cookie).json.getLong("id")

        assertEquals(200, post("/api/stock", """{"id":$id,"type":"in","quantity":"3"}""", cookie).status)
        assertEquals(8, backend.item(id)!!.quantity)
        assertEquals(200, post("/api/stock", """{"id":$id,"type":"out","quantity":2,"note":"n"}""", cookie).status)
        assertEquals(6, backend.item(id)!!.quantity)

        assertEquals("not_enough", post("/api/stock", """{"id":$id,"type":"out","quantity":"7"}""", cookie).json.getString("error"))
        assertEquals(400, post("/api/stock", """{"id":$id,"type":"in","quantity":"0"}""", cookie).status)
        assertEquals(400, post("/api/stock", """{"id":$id,"type":"sideways","quantity":"1"}""", cookie).status)
        assertEquals(409, post("/api/stock", """{"id":999,"type":"in","quantity":"1"}""", cookie).status)
        assertEquals(6, backend.item(id)!!.quantity)
    }

    @Test
    fun categoriesCanBeAddedAndDeleted() {
        val cookie = signIn()
        assertEquals(200, post("/api/categories", """{"name":"  Implantai   A "}""", cookie).status)
        assertTrue(backend.categories.contains("Implantai A"))
        assertEquals(409, post("/api/categories", """{"name":"implantai a"}""", cookie).status)
        assertEquals(400, post("/api/categories", """{"name":"  "}""", cookie).status)

        val id = post("/api/items", """{"name":"Screw","category":"Implantai A"}""", cookie).json.getLong("id")
        val data = get("/api/data", cookie).json.getJSONArray("categories").getJSONObject(0)
        assertEquals(1, data.getInt("count"))

        assertEquals(200, post("/api/categories/delete", """{"name":"Implantai A"}""", cookie).status)
        assertFalse(backend.categories.contains("Implantai A"))
        assertEquals("", backend.item(id)!!.category)
    }

    @Test
    fun usersFollowTheSameRulesAsTheApp() {
        val cookie = signIn()
        assertEquals(409, post("/api/users", """{"name":"admin"}""", cookie).status)
        assertEquals(409, post("/api/users", """{"name":"jonas"}""", cookie).status)
        assertEquals(200, post("/api/users", """{"name":"Eglė"}""", cookie).status)

        // The Admin account is neither listed nor removable.
        val listed = get("/api/data", cookie).json.getJSONArray("users")
        assertEquals(3, listed.length())
        assertEquals(403, post("/api/users/delete", """{"id":1}""", cookie).status)

        assertEquals(200, post("/api/users/delete", """{"id":2}""", cookie).status)
        assertEquals(200, post("/api/users/delete", """{"id":3}""", cookie).status)
        val last = backend.users.first { it.name == "Eglė" }
        assertEquals(409, post("/api/users/delete", """{"id":${last.id}}""", cookie).status)
    }

    @Test
    fun reportsDownloadAsFiles() {
        val cookie = signIn()
        val storage = get("/report/storage", cookie)
        assertEquals(200, storage.status)
        assertTrue(storage.headers.getValue("content-disposition").contains("DentTrack_Backup_test.xlsx"))
        assertEquals("monthly 2025", get("/report/monthly?year=2025", cookie).body)
        assertEquals(400, get("/report/monthly?year=1", cookie).status)
    }

    // --------------------------------------------------------------- settings

    private fun setting(cookie: String, key: String, value: String) =
        post("/api/settings", """{"key":"$key","value":$value}""", cookie)

    @Test
    fun settingsNeedASignIn() {
        assertEquals(401, post("/api/settings", """{"key":"hideAdmin","value":false}""").status)
    }

    @Test
    fun theAppSchemeCanBeChangedAndThePageIsToldWhichIsUsed() {
        val cookie = signIn()
        assertEquals(200, setting(cookie, "guiScheme", "\"LAGOON\"").status)
        val settings = get("/api/data", cookie).json.getJSONObject("settings")
        assertEquals("LAGOON", settings.getString("activeScheme"))
        assertEquals(GuiScheme.entries.size, settings.getJSONArray("schemes").length())
        assertEquals(400, setting(cookie, "guiScheme", "\"NOPE\"").status)

        assertEquals(200, setting(cookie, "randomScheme", "true").status)
        assertTrue(get("/api/data", cookie).json.getJSONObject("settings").getBoolean("randomScheme"))
        assertEquals(200, setting(cookie, "guiScheme", "\"PINK\"").status)
        val picked = get("/api/data", cookie).json.getJSONObject("settings")
        assertFalse(picked.getBoolean("randomScheme"))
        assertEquals("PINK", picked.getString("activeScheme"))
    }

    @Test
    fun everySettingCanBeChanged() {
        val cookie = signIn()
        assertEquals(200, setting(cookie, "hideAdmin", "false").status)
        assertFalse(backend.config.hideAdmin)
        assertEquals(200, setting(cookie, "inactivityMinutes", "15").status)
        assertEquals(15, backend.config.inactivityMinutes)
        assertEquals(200, setting(cookie, "lowStockThreshold", "\"7\"").status)
        assertEquals(7, backend.config.lowStockThreshold)
        assertEquals(200, setting(cookie, "language", "\"lt\"").status)
        assertEquals("lt", backend.language)
        assertEquals(400, setting(cookie, "language", "\"de\"").status)
    }

    @Test
    fun badSettingValuesAreRefused() {
        val cookie = signIn()
        assertEquals(400, setting(cookie, "lowStockThreshold", "\"-1\"").status)
        assertEquals(400, setting(cookie, "inactivityMinutes", "\"soon\"").status)
        assertEquals(400, setting(cookie, "hideAdmin", "\"maybe\"").status)
        assertEquals(400, setting(cookie, "unknown", "true").status)
    }

    @Test
    fun theAdminPasswordCannotBeChangedFromThePage() {
        val cookie = signIn()
        assertEquals(400, setting(cookie, "adminPassword", "\"new\"").status)
        assertEquals(400, setting(cookie, "password", "\"new\"").status)
        assertEquals(404, post("/api/password", """{"password":"new"}""", cookie).status)
        assertEquals("secret", backend.password)
    }

    @Test
    fun theUnsafeActionsAreNotOnThePage() {
        val cookie = signIn()
        assertEquals(404, post("/api/settings/reset", "{}", cookie).status)
        assertEquals(404, post("/api/stock/clear", """{"confirm":"DELETE","alsoLog":true}""", cookie).status)
        listOf("webServer" to "false", "hiddenThemes" to "false", "monthlyDrive" to "false", "backupLocal" to "false", "backupDrive" to "false", "backupSchedule" to "\"WEEKLY\"").forEach { (key, value) ->
            assertEquals(key, 400, setting(cookie, key, value).status)
        }
        val settings = get("/api/data", cookie).json.getJSONObject("settings")
        assertFalse(settings.has("hiddenThemes"))
        assertFalse(settings.has("monthlyDrive"))
        assertFalse(settings.has("backupSchedule"))
        val page = get("/app.js").body
        listOf("/api/settings/reset", "/api/stock/clear", "webServer", "hiddenThemes", "monthlyDrive", "backup").forEach {
            assertFalse(it, page.contains(it))
        }
    }

    // --------------------------------------------------------------- language

    @Test
    fun thePageFollowsTheAppLanguage() {
        backend.setLanguage("lt")
        val status = get("/api/status").json
        assertEquals("lt", status.getString("language"))
        val texts = status.getJSONObject("texts")
        assertEquals("Pridėti", texts.getString("check_in"))
        assertEquals("Paimti", texts.getString("check_out"))
        assertEquals("Atsijungti", texts.getString("web_sign_out"))

        assertEquals("Neteisingas slaptažodis", post("/api/login", """{"password":"x"}""").json.getString("message"))

        val cookie = signIn()
        val id = post("/api/items", """{"name":"Gloves","unit":"vnt","quantity":"1"}""", cookie).json.getLong("id")
        assertEquals("pcs", backend.item(id)!!.unit) // saved the same way in every language
        val item = get("/api/data", cookie).json.getJSONArray("items").getJSONObject(0)
        assertEquals("vnt", item.getString("unit"))
    }

    @Test
    fun categoryCountsUseTheRightLithuanianWord() {
        backend.setLanguage("lt")
        val expected = mapOf(
            0 to "0 prekių", 1 to "1 prekė", 2 to "2 prekės", 9 to "9 prekės", 10 to "10 prekių",
            11 to "11 prekių", 19 to "19 prekių", 21 to "21 prekė", 22 to "22 prekės", 100 to "100 prekių"
        )
        expected.forEach { (count, text) -> assertEquals(text, backend.itemCountText(count)) }

        val cookie = signIn()
        post("/api/categories", """{"name":"Implantai"}""", cookie)
        repeat(3) { post("/api/items", """{"name":"Screw $it","category":"Implantai"}""", cookie) }
        val category = get("/api/data", cookie).json.getJSONArray("categories").getJSONObject(0)
        assertEquals("3 prekės", category.getString("countText"))
    }

    @Test
    fun everyTextThePageAsksForExists() {
        val script = String(backend.file("app.js")!!, Charsets.UTF_8)
        val asked = Regex("""\bt\('([a-z_0-9]+)'""").findAll(script).map { it.groupValues[1] }.toSet()
        assertTrue("no texts found", asked.size > 40)
        val missing = asked - WebTexts.ALL.keys
        assertTrue("not in WebTexts: $missing", missing.isEmpty())
    }

    // ------------------------------------------------------------------ files

    @Test
    fun thePageAndItsFilesAreServedWithSafeHeaders() {
        for (path in listOf("/", "/app.js", "/style.css", "/logo.png")) {
            val reply = get(path)
            assertEquals(path, 200, reply.status)
            assertNotNull(reply.headers["content-security-policy"])
            assertEquals("nosniff", reply.headers["x-content-type-options"])
        }
        assertTrue(get("/").headers.getValue("content-type").startsWith("text/html"))
    }

    @Test
    fun onlyThePagesOwnFilesAreServed() {
        assertEquals(404, get("/../strings.xml").status)
        assertEquals(404, get("/assets/web/app.js").status)
        assertEquals(404, get("/shared_prefs/stocktrack.xml").status)
    }

    @Test
    fun brokenRequestsDoNotHurt() {
        assertEquals(400, raw("nonsense\r\n\r\n".toByteArray()).status)
        assertEquals(400, raw("GET noslash HTTP/1.1\r\n\r\n".toByteArray()).status)
        assertEquals(405, raw("DELETE /api/items HTTP/1.1\r\nHost: x\r\n\r\n".toByteArray()).status)
        assertEquals(413, raw("POST /api/login HTTP/1.1\r\nContent-Length: 999999\r\n\r\n".toByteArray()).status)
        assertEquals(411, raw("POST /api/login HTTP/1.1\r\nTransfer-Encoding: chunked\r\n\r\n".toByteArray()).status)
        assertEquals(431, raw(("GET / HTTP/1.1\r\nX: " + "a".repeat(20_000) + "\r\n\r\n").toByteArray()).status)
        assertEquals(200, get("/api/status").status)
    }

    @Test
    fun englishAndLithuanianHaveTheSameWebTexts() {
        val main = listOf(File("src/main"), File("app/src/main")).first { it.exists() }
        fun webKeys(folder: String) = Regex("""name="((?:web_|section_web)[a-z_]*)"""")
            .findAll(File(main, "res/$folder/strings.xml").readText()).map { it.groupValues[1] }.toSet()
        assertEquals(webKeys("values"), webKeys("values-lt"))
    }
}
