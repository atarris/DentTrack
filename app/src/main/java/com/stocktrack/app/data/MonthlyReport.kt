package com.stocktrack.app.data

import android.content.Context
import androidx.work.Constraints
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
        val left: Array<Int?>
    )

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
                    ActionType.CHECK_OUT -> outSum += abs(e.change)
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
        return ItemYear(checkIn, checkOut, left)
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
        sumColumns(years.map { it.left })
    )

    fun build(items: List<Item>, log: List<LogEntry>, year: Int, now: Long = System.currentTimeMillis()): ByteArray {
        val entriesByItem = log.groupBy { it.itemId }
            .mapValues { (_, list) -> list.sortedWith(compareBy<LogEntry>({ it.timestamp }, { it.id })) }
        val last = lastReportedMonth(year, now)

        val headers = listOf("Item", "Metric") + MONTHS.map { "$it $year" } + "Total"
        val widths = listOf(38, 20) + List(12) { 10 } + listOf(11)

        val categories = items.groupBy { it.category.trim().ifBlank { "Uncategorized" } }
            .toSortedMap(String.CASE_INSENSITIVE_ORDER)

        val usedNames = hashSetOf("overview")
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

        for ((category, list) in categories) {
            val sorted = list.sortedBy { it.name.lowercase() }
            val years = sorted.map { compute(it, entriesByItem[it.id].orEmpty(), year, now) }
            val categoryTotal = totalOf(years)
            perCategory.add(categoryTotal)

            val rows = ArrayList<List<Any?>>()
            rows.addAll(block("TOTAL – $category", categoryTotal, last))
            sorted.forEachIndexed { i, item -> rows.addAll(block(item.name, years[i], last)) }
            categorySheets.add(
                XlsxWriter.Sheet(
                    name = sheetName(category),
                    headers = headers,
                    rows = rows,
                    widths = widths,
                    emphasisRows = setOf(0, 1, 2)
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
            emphasisRows = setOf(0, 1, 2) + overviewEmphasis.map { it + 3 }
        )
        return XlsxWriter.build(listOf(overview) + categorySheets)
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

    /** Year to report on the 1st: Nov 1 reports this year so far, Jan 1 reports the year that just ended. */
    fun scheduledYear(now: Long): Int {
        val c = Calendar.getInstance()
        c.timeInMillis = now
        c.add(Calendar.DAY_OF_MONTH, -1)
        return c.get(Calendar.YEAR)
    }

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

        val result = uploadReport(app, scheduledYear(now), now)
        if (result.success) settings.monthlyKey = key
        settings.setLastMonthly(now, result.message)
    }

    class Result(val success: Boolean, val message: String)

    /** Builds the report for [year] and uploads it to Google Drive. Also used by "Save to Drive now". */
    suspend fun uploadReport(context: Context, year: Int, now: Long): Result {
        val app = context.applicationContext
        return try {
            if (!AppSettings(app).state.value.driveConnected) {
                return Result(false, "Google Drive is not connected (Backup tab)")
            }
            val dao = AppDatabase.get(app).dao()
            val bytes = MonthlyReport.build(dao.itemsOnce(), dao.logOnce(), year, now)
            val name = MonthlyReport.fileName(year, now)
            when (val auth = DriveBackup.authorize(app)) {
                is DriveBackup.Auth.Token ->
                    DriveBackup.upload(auth.value, name, bytes, DriveBackup.MONTHLY_FOLDER_NAME)
                is DriveBackup.Auth.NeedsConsent ->
                    throw IOException("permission needed. Open Backup and tap Connect")
                is DriveBackup.Auth.Failed -> throw IOException(auth.reason)
            }
            Result(true, "Uploaded $name to Google Drive")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result(false, "Failed: ${e.message ?: e.javaClass.simpleName}")
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
