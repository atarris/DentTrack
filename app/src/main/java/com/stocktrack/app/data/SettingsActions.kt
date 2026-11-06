package com.stocktrack.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri

/** Setting changes that also reschedule background work, made by both the tablet screens and the web page. */
class SettingsActions(context: Context, private val settings: AppSettings) {

    private val app = context.applicationContext

    private fun reschedule() {
        BackupScheduler.apply(app, settings.state.value, replace = true)
    }

    fun setBackupSchedule(schedule: BackupSchedule) {
        settings.setBackupSchedule(schedule)
        reschedule()
    }

    fun setBackupLocal(enabled: Boolean) {
        settings.setBackupLocal(enabled)
        reschedule()
    }

    fun setBackupDrive(enabled: Boolean) {
        settings.setBackupDrive(enabled)
        reschedule()
    }

    /** Returns false, changing nothing, when switching on while Google Drive is not connected. */
    fun setMonthlyDrive(enabled: Boolean): Boolean {
        if (enabled && !settings.state.value.driveConnected) return false
        if (enabled) {
            // The first automatic report is for the next 1st, not for the current month.
            settings.monthlyKey = MonthlyReportRunner.monthKey(System.currentTimeMillis())
        }
        settings.setMonthlyDrive(enabled)
        MonthlyReportScheduler.apply(app, enabled, replace = true)
        return true
    }

    /** Back to the default settings, schedules included. Stock, log, users and the Admin password stay. */
    fun resetConfiguration() {
        settings.state.value.backupFolderUri?.let { folder ->
            runCatching {
                app.contentResolver.releasePersistableUriPermission(
                    Uri.parse(folder), Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
        }
        settings.resetConfig()
        BackupScheduler.apply(app, settings.state.value, replace = true)
        MonthlyReportScheduler.apply(app, settings.state.value.monthlyDrive, replace = true)
    }
}
