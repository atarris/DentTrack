package com.stocktrack.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.util.Calendar
import java.util.zip.ZipInputStream

class MonthlyReportTest {

    private fun time(month: Int, day: Int): Long = Calendar.getInstance().run {
        clear()
        set(2026, month, day, 10, 0, 0)
        timeInMillis
    }

    private val categories = listOf("Implants", "Gloves & masks", "Anaesthetics", "Burs", "Filling materials", "Hygiene")

    private fun sampleReport(): ByteArray {
        val items = ArrayList<Item>()
        val log = ArrayList<LogEntry>()
        var id = 1L
        categories.forEachIndexed { c, category ->
            repeat(4) { i ->
                val itemId = id++
                items.add(Item(id = itemId, name = "$category item ${i + 1}", category = category, quantity = 90 - i))
                for (month in 0..10) {
                    val units = (c + 1) * (i + 1) + month
                    log.add(
                        LogEntry(
                            id = id * 100 + month, timestamp = time(month, 12), userName = "Test", itemId = itemId,
                            itemName = "$category item ${i + 1}", type = ActionType.CHECK_OUT,
                            change = -units, quantityAfter = 90 - i
                        )
                    )
                }
            }
        }
        return MonthlyReport.build(items, log, 2026, time(10, 20))
    }

    private fun parts(bytes: ByteArray): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                out[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
                entry = zip.nextEntry
            }
        }
        return out
    }

    @Test
    fun reviewSheetsComeFirstWithTheirCharts() {
        val bytes = sampleReport()
        File("build").mkdirs()
        File("build/monthly-report-sample.xlsx").writeBytes(bytes) // for opening by hand in Excel

        val parts = parts(bytes)
        val workbook = parts.getValue("xl/workbook.xml")
        assertTrue(workbook.indexOf("Nov 2026") < workbook.indexOf("Year 2026"))
        assertTrue(workbook.indexOf("Year 2026") < workbook.indexOf("Overview"))
        assertEquals(5, parts.keys.count { it.startsWith("xl/charts/chart") })
        assertEquals(2, parts.keys.count { it.startsWith("xl/drawings/drawing") })
    }

    @Test
    fun everyThirdItemRowStartsABlockWithALine() {
        val sheet = parts(sampleReport()).getValue("xl/worksheets/sheet4.xml") // the first category
        val rowsWithLine = Regex("<row r=\"([0-9]+)\"><c r=\"A[0-9]+\" s=\"[567]\"").findAll(sheet)
            .map { it.groupValues[1].toInt() }.toList()
        assertEquals(listOf(2, 5, 8, 11, 14), rowsWithLine)
    }
}
