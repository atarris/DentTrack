package com.stocktrack.app.data

import android.content.Context
import androidx.work.Constraints
import com.stocktrack.app.R
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * The Monthly Report: one Excel file for a whole year, one sheet per item category.
 *
 * Every item has three rows: Check in, Check out and Left (stock at the end of the month), with
 * twelve month columns and a Total column. Months that have not happened yet stay empty.
 * Initial stock (an item added or imported with a quantity) is the starting balance, not a check in.
 *
 * In front of those come two review sheets with charts: one month (usage by category, Top 10 items)
 * and the whole year (categories by month, year share, Top 10 items). Charts count units checked out.
 */
object MonthlyReport {

    val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    const val METRIC_IN = "Check in"
    const val METRIC_OUT = "Check out"
    const val METRIC_LEFT = "Left (end of month)"

    /** Month by month numbers of one item. A null cell means "not known / not reached yet". */
    class ItemYear(
        val checkIn: Array<Int?>,
        val checkOut: Array<Int?>,
        val left: Array<Int?>,
        /** How many separate check outs there were each month. */
        val timesOut: IntArray = IntArray(12)
    )

    private class ItemUsage(val item: Item, val year: ItemYear)

    private class CategoryUsage(val name: String, val items: List<ItemUsage>, val total: ItemYear)

    private const val H_UNITS = "Units out"
    private const val H_TIMES = "Times"
    private const val TOP_ITEMS = 10

    /** Index of the last month that has started (11 for a past year, -1 for a future year). */
    fun lastReportedMonth(year: Int, now: Long): Int {
        val c = Calendar.getInstance()
        c.timeInMillis = now
        val nowYear = c.get(Calendar.YEAR)
        return when {
            year < nowYear -> 11
            year == nowYear -> c.get(Calendar.MONTH)
            else -> -1
        }
    }

    private fun monthStart(year: Int, month: Int): Long {
        val c = Calendar.getInstance()
        c.clear()
        c.set(year, month, 1, 0, 0, 0) // month 12 rolls over to January of the next year
        return c.timeInMillis
    }

    /** [entries] are the item's log entries, oldest first. */
    fun compute(item: Item, entries: List<LogEntry>, year: Int, now: Long): ItemYear {
        val checkIn = arrayOfNulls<Int>(12)
        val checkOut = arrayOfNulls<Int>(12)
        val left = arrayOfNulls<Int>(12)
        val timesOut = IntArray(12)
        val last = lastReportedMonth(year, now)
        val nowYear = Calendar.getInstance().also { it.timeInMillis = now }.get(Calendar.YEAR)

        for (m in 0..last) {
            val start = monthStart(year, m)
            val end = monthStart(year, m + 1)
            var inSum = 0
            var outSum = 0
            for (e in entries) {
                if (e.timestamp < start || e.timestamp >= end) continue
                when (e.type) {
                    ActionType.CHECK_IN -> inSum += abs(e.change)
                    ActionType.CHECK_OUT -> {
                        outSum += abs(e.change)
                        timesOut[m]++
                    }
                }
            }
            checkIn[m] = inSum
            checkOut[m] = outSum
            left[m] = if (year == nowYear && m == last) {
                item.quantity // the current month always ends with what is in stock now
            } else {
                entries.lastOrNull { it.timestamp < end }?.quantityAfter
            }
        }
        return ItemYear(checkIn, checkOut, left, timesOut)
    }

    private fun total(values: Array<Int?>): Int? {
        val present = values.filterNotNull()
        return if (present.isEmpty()) null else present.sum()
    }

    private fun sumColumns(list: List<Array<Int?>>): Array<Int?> = Array(12) { m ->
        val present = list.mapNotNull { it[m] }
        if (present.isEmpty()) null else present.sum()
    }

    /** The three rows (check in / check out / left) of one item or one total. */
    private fun block(label: String, y: ItemYear, last: Int): List<List<Any?>> {
        fun row(metric: String, values: Array<Int?>, rowTotal: Int?): List<Any?> =
            listOf<Any?>(label, metric) + values.toList() + listOf<Any?>(rowTotal)
        return listOf(
            row(METRIC_IN, y.checkIn, total(y.checkIn)),
            row(METRIC_OUT, y.checkOut, total(y.checkOut)),
            // "Total" of the Left row is the stock at the end of the reported period.
            row(METRIC_LEFT, y.left, if (last >= 0) y.left[last] else null)
        )
    }

    private fun totalOf(years: List<ItemYear>): ItemYear = ItemYear(
        sumColumns(years.map { it.checkIn }),
        sumColumns(years.map { it.checkOut }),
        sumColumns(years.map { it.left }),
        IntArray(12) { m -> years.sumOf { it.timesOut[m] } }
    )

    private fun monthTitle(year: Int, month: Int): String {
        val c = Calendar.getInstance()
        c.clear()
        c.set(year, month, 1)
        return SimpleDateFormat("MMMM yyyy", Locale.US).format(c.time)
    }

    /** Separates the three-row blocks: a line above each block, every second block on a light background. */
    private fun dividers(rowCount: Int): Set<Int> = (0 until rowCount step 3).toSet()

    private fun bands(rowCount: Int): Set<Int> =
        (0 until rowCount).filter { (it / 3) % 2 == 1 }.toSet()

    /** Appends a Top 10 table to [rows] and returns the range of its item rows, or null when nothing was used. */
    private fun addTopItems(
        rows: MutableList<List<Any?>>,
        titleRows: MutableSet<Int>,
        headerRows: MutableSet<Int>,
        title: String,
        used: List<Triple<Item, Int, Int>>
    ): IntRange? {
        titleRows.add(rows.size)
        rows.add(listOf(title))
        if (used.isEmpty()) {
            rows.add(listOf("No check outs in this period."))
            return null
        }
        headerRows.add(rows.size)
        rows.add(listOf("Item", H_UNITS, H_TIMES, "Category"))
        val first = rows.size
        used.take(TOP_ITEMS).forEachIndexed { i, (item, units, times) ->
            rows.add(listOf("${i + 1}. ${item.name}", units, times, item.category.trim().ifBlank { "Uncategorized" }))
        }
        return first until rows.size
    }

    private fun topChart(sheet: String, title: String, rows: List<List<Any?>>, range: IntRange, fromCol: Int, fromRow: Int, toCol: Int, toRow: Int) =
        XlsxWriter.Chart(
            kind = XlsxWriter.ChartKind.BAR_HORIZONTAL,
            title = title,
            categories = XlsxWriter.DataRef(XlsxWriter.range(sheet, 0, range.first, range.last), range.map { rows[it][0] }),
            series = listOf(
                XlsxWriter.Series(
                    XlsxWriter.DataRef(XlsxWriter.range(sheet, 1, range.first - 1), listOf(H_UNITS)),
                    XlsxWriter.DataRef(XlsxWriter.range(sheet, 1, range.first, range.last), range.map { rows[it][1] })
                )
            ),
            fromCol = fromCol, fromRow = fromRow, toCol = toCol, toRow = toRow
        )

    /** One month: units by category with a pie chart, and the Top 10 items with a bar chart. */
    private fun monthSheet(name: String, year: Int, month: Int, usage: List<CategoryUsage>): XlsxWriter.Sheet {
        val title = monthTitle(year, month)
        val used = usage.map { Triple(it.name, it.total.checkOut[month] ?: 0, it.total.timesOut[month]) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
        val allUnits = used.sumOf { it.second }

        val rows = ArrayList<List<Any?>>()
        val titleRows = HashSet<Int>()
        val headerRows = HashSet<Int>()
        val emphasis = HashSet<Int>()
        val charts = ArrayList<XlsxWriter.Chart>()

        if (used.isEmpty()) {
            rows.add(listOf("No check outs in $title."))
        } else {
            used.forEach { (category, units, times) ->
                rows.add(listOf(category, units, times, "${Math.round(units * 100.0 / allUnits)}%"))
            }
            emphasis.add(rows.size)
            rows.add(listOf("Total", allUnits, used.sumOf { it.third }, "100%"))
            charts.add(
                XlsxWriter.Chart(
                    kind = XlsxWriter.ChartKind.PIE,
                    title = "Units checked out by category, $title",
                    categories = XlsxWriter.DataRef(XlsxWriter.range(name, 0, 0, used.lastIndex), used.map { it.first }),
                    series = listOf(
                        XlsxWriter.Series(
                            XlsxWriter.DataRef(XlsxWriter.range(name, 1, -1), listOf(H_UNITS)),
                            XlsxWriter.DataRef(XlsxWriter.range(name, 1, 0, used.lastIndex), used.map { it.second })
                        )
                    ),
                    fromCol = 5, fromRow = 0, toCol = 15, toRow = 21
                )
            )
        }
        rows.add(emptyList())
        val topTitle = "Top $TOP_ITEMS most used items, $title"
        val topItems = usage.flatMap { it.items }
            .map { Triple(it.item, it.year.checkOut[month] ?: 0, it.year.timesOut[month]) }
            .filter { it.second > 0 }
            .sortedWith(compareByDescending<Triple<Item, Int, Int>> { it.second }.thenBy { it.first.name.lowercase() })
        addTopItems(rows, titleRows, headerRows, topTitle, topItems)?.let { range ->
            charts.add(topChart(name, topTitle, rows, range, 5, 22, 15, 42))
        }
        return XlsxWriter.Sheet(
            name = name,
            headers = listOf("Category", H_UNITS, H_TIMES, "Share"),
            rows = rows,
            widths = listOf(46, 11, 9, 24),
            titleRows = titleRows,
            headerRows = headerRows,
            emphasisRows = emphasis,
            dataTable = false,
            charts = charts
        )
    }

    /** The year so far: categories by month, a stacked trend chart, the year's share, and the Top 10 items. */
    private fun yearSheet(name: String, year: Int, usage: List<CategoryUsage>): XlsxWriter.Sheet {
        fun unitsOf(values: Array<Int?>) = values.filterNotNull().sum()
        val sorted = usage.sortedByDescending { unitsOf(it.total.checkOut) }
        val rows = ArrayList<List<Any?>>()
        sorted.forEach { c -> rows.add(listOf<Any?>(c.name) + c.total.checkOut.toList() + unitsOf(c.total.checkOut)) }
        val grand = totalOf(usage.map { it.total })
        val totalRow = rows.size
        rows.add(listOf<Any?>("Total") + grand.checkOut.toList() + unitsOf(grand.checkOut))
        rows.add(emptyList())

        val usedCount = sorted.count { unitsOf(it.total.checkOut) > 0 }
        val chartTop = rows.size + 1
        val charts = ArrayList<XlsxWriter.Chart>()
        if (usedCount > 0) {
            val monthRef = XlsxWriter.DataRef(XlsxWriter.rowRange(name, -1, 1, 12), MONTHS)
            charts.add(
                XlsxWriter.Chart(
                    kind = XlsxWriter.ChartKind.COLUMN_STACKED,
                    title = "Units checked out per month, $year",
                    categories = monthRef,
                    series = (0 until usedCount).map { r ->
                        XlsxWriter.Series(
                            XlsxWriter.DataRef(XlsxWriter.range(name, 0, r), listOf(sorted[r].name)),
                            XlsxWriter.DataRef(XlsxWriter.rowRange(name, r, 1, 12), sorted[r].total.checkOut.toList())
                        )
                    },
                    fromCol = 0, fromRow = chartTop, toCol = 8, toRow = chartTop + 22
                )
            )
            charts.add(
                XlsxWriter.Chart(
                    kind = XlsxWriter.ChartKind.PIE,
                    title = "Share of the year by category, $year",
                    categories = XlsxWriter.DataRef(
                        XlsxWriter.range(name, 0, 0, usedCount - 1), sorted.take(usedCount).map { it.name }
                    ),
                    series = listOf(
                        XlsxWriter.Series(
                            XlsxWriter.DataRef(XlsxWriter.range(name, 13, -1), listOf("Total")),
                            XlsxWriter.DataRef(
                                XlsxWriter.range(name, 13, 0, usedCount - 1), sorted.take(usedCount).map { unitsOf(it.total.checkOut) }
                            )
                        )
                    ),
                    fromCol = 8, fromRow = chartTop, toCol = 16, toRow = chartTop + 22
                )
            )
        }

        // Rows under the charts stay empty, then comes the Top 10.
        repeat(if (usedCount > 0) 23 else 0) { rows.add(emptyList()) }
        val titleRows = HashSet<Int>()
        val headerRows = HashSet<Int>()
        val topTitle = "Top $TOP_ITEMS most used items, $year"
        val topItems = usage.flatMap { it.items }
            .map { Triple(it.item, unitsOf(it.year.checkOut), it.year.timesOut.sum()) }
            .filter { it.second > 0 }
            .sortedWith(compareByDescending<Triple<Item, Int, Int>> { it.second }.thenBy { it.first.name.lowercase() })
        val topStart = rows.size
        addTopItems(rows, titleRows, headerRows, topTitle, topItems)?.let { range ->
            charts.add(topChart(name, topTitle, rows, range, 8, topStart + 1, 16, topStart + 18))
        }
        return XlsxWriter.Sheet(
            name = name,
            headers = listOf("Category") + MONTHS + "Total",
            rows = rows,
            widths = listOf(46) + List(12) { 9 } + listOf(10),
            titleRows = titleRows,
            headerRows = headerRows,
            emphasisRows = setOf(totalRow),
            dataTable = false,
            charts = charts
        )
    }

    /**
     * [focusMonth] is the month (0-11) of the month review sheet. Without it, that is the last month of
     * the [year] that has started; the scheduled report on the 1st passes the month that just ended.
     */
    fun build(
        items: List<Item>,
        log: List<LogEntry>,
        year: Int,
        now: Long = System.currentTimeMillis(),
        focusMonth: Int? = null
    ): ByteArray {
        val entriesByItem = log.groupBy { it.itemId }
            .mapValues { (_, list) -> list.sortedWith(compareBy<LogEntry>({ it.timestamp }, { it.id })) }
        val last = lastReportedMonth(year, now)

        val headers = listOf("Item", "Metric") + MONTHS.map { "$it $year" } + "Total"
        val widths = listOf(38, 20) + List(12) { 10 } + listOf(11)

        val categories = items.groupBy { it.category.trim().ifBlank { "Uncategorized" } }
            .toSortedMap(String.CASE_INSENSITIVE_ORDER)

        val month = (focusMonth ?: last).coerceAtMost(last)
        val monthName = if (month >= 0) "${MONTHS[month]} $year" else null
        val yearName = "Year $year"
        val usedNames = hashSetOf("overview", yearName.lowercase())
        monthName?.let { usedNames.add(it.lowercase()) }
        fun sheetName(category: String): String {
            val base = category.replace(Regex("[\\\\/?*\\[\\]:]"), " ").trim().take(31).ifBlank { "Category" }
            var name = base
            var n = 2
            while (!usedNames.add(name.lowercase())) {
                val suffix = " ($n)"
                name = base.take(31 - suffix.length) + suffix
                n++
            }
            return name
        }

        val overviewRows = ArrayList<List<Any?>>()
        val overviewEmphasis = HashSet<Int>()
        val categorySheets = ArrayList<XlsxWriter.Sheet>()
        val perCategory = ArrayList<ItemYear>()
        val usage = ArrayList<CategoryUsage>()

        for ((category, list) in categories) {
            val sorted = list.sortedBy { it.name.lowercase() }
            val years = sorted.map { compute(it, entriesByItem[it.id].orEmpty(), year, now) }
            val categoryTotal = totalOf(years)
            perCategory.add(categoryTotal)
            usage.add(CategoryUsage(category, sorted.indices.map { ItemUsage(sorted[it], years[it]) }, categoryTotal))

            val rows = ArrayList<List<Any?>>()
            rows.addAll(block("TOTAL – $category", categoryTotal, last))
            sorted.forEachIndexed { i, item -> rows.addAll(block(item.name, years[i], last)) }
            categorySheets.add(
                XlsxWriter.Sheet(
                    name = sheetName(category),
                    headers = headers,
                    rows = rows,
                    widths = widths,
                    emphasisRows = setOf(0, 1, 2),
                    bandRows = bands(rows.size),
                    dividerRows = dividers(rows.size)
                )
            )

            val start = overviewRows.size
            overviewRows.addAll(block(category, categoryTotal, last))
            overviewEmphasis.addAll(start..start + 2)
        }

        // Overview: grand total on top, then one block per category.
        val grand = block("ALL CATEGORIES", totalOf(perCategory), last)
        val overview = XlsxWriter.Sheet(
            name = "Overview",
            headers = headers,
            rows = grand + overviewRows,
            widths = widths,
            emphasisRows = setOf(0, 1, 2) + overviewEmphasis.map { it + 3 },
            dividerRows = dividers(grand.size + overviewRows.size)
        )
        val reviews = if (month >= 0) {
            listOf(monthSheet(monthName!!, year, month, usage), yearSheet(yearName, year, usage))
        } else {
            emptyList()
        }
        return XlsxWriter.build(reviews + overview + categorySheets)
    }

    fun fileName(year: Int, now: Long): String =
        "DentTrack_MonthlyReport_${year}_" + SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(now)) + ".xlsx"
}

/** Saves the Monthly Report to Google Drive on the 1st of every month. */
object MonthlyReportRunner {

    fun monthKey(now: Long): String {
        val c = Calendar.getInstance()
        c.timeInMillis = now
        return String.format(Locale.US, "%04d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1)
    }

    /** The month that just ended, which the scheduled report covers (also when it runs a few days late). */
    private fun previousMonth(now: Long): Calendar {
        val c = Calendar.getInstance()
        c.timeInMillis = now
        c.set(Calendar.DAY_OF_MONTH, 1)
        c.add(Calendar.MONTH, -1)
        return c
    }

    /** Year to report: in November this year so far, in January the year that just ended. */
    fun scheduledYear(now: Long): Int = previousMonth(now).get(Calendar.YEAR)

    /**
     * Called every day by [MonthlyReportWorker]. It does something only once per calendar month:
     * on the 1st, or on the first day after that on which the tablet was on and online.
     */
    suspend fun runIfDue(context: Context) {
        val app = context.applicationContext
        val settings = AppSettings(app)
        if (!settings.state.value.monthlyDrive) return
        val now = System.currentTimeMillis()
        val key = monthKey(now)
        if (settings.monthlyKey == key) return

        val result = uploadReport(app, scheduledYear(now), now, previousMonth(now).get(Calendar.MONTH))
        if (result.success) settings.monthlyKey = key
        settings.setLastMonthly(now, result.message)
    }

    class Result(val success: Boolean, val message: String)

    /** Builds the report for [year] and uploads it to Google Drive. Also used by "Save to Drive now". */
    suspend fun uploadReport(context: Context, year: Int, now: Long, focusMonth: Int? = null): Result {
        val app = context.applicationContext
        val text = Language.context(app)
        return try {
            if (!AppSettings(app).state.value.driveConnected) {
                return Result(false, text.getString(R.string.run_drive_not_connected))
            }
            val dao = AppDatabase.get(app).dao()
            val bytes = MonthlyReport.build(dao.itemsOnce(), dao.logOnce(), year, now, focusMonth)
            val name = MonthlyReport.fileName(year, now)
            when (val auth = DriveBackup.authorize(app)) {
                is DriveBackup.Auth.Token ->
                    DriveBackup.upload(auth.value, name, bytes, DriveBackup.MONTHLY_FOLDER_NAME)
                is DriveBackup.Auth.NeedsConsent ->
                    throw IOException(text.getString(R.string.run_permission_needed))
                is DriveBackup.Auth.Failed -> throw IOException(auth.reason)
            }
            Result(true, text.getString(R.string.run_uploaded_named, name))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result(false, text.getString(R.string.run_failed, e.message ?: e.javaClass.simpleName))
        }
    }
}

class MonthlyReportWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        MonthlyReportRunner.runIfDue(applicationContext)
        return Result.success()
    }
}

object MonthlyReportScheduler {
    private const val WORK_NAME = "denttrack_monthly_report"

    /** Checks once a day whether a new month has started. */
    fun apply(context: Context, enabled: Boolean, replace: Boolean) {
        val manager = WorkManager.getInstance(context.applicationContext)
        if (!enabled) {
            manager.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<MonthlyReportWorker>(1, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        manager.enqueueUniquePeriodicWork(
            WORK_NAME,
            if (replace) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
