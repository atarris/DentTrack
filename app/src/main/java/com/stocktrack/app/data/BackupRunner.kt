package com.stocktrack.app.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Creates a backup: the same Excel file as the Storage report, written to every destination the
 * Admin ticked (local file storage and/or Google Drive). Used by the schedule and by "Back up now".
 */
object BackupRunner {

    class Outcome(val message: String, val success: Boolean)

    /** Name of a backup file. Reports > Export to Excel saves the same file under the same name. */
    fun fileName(): String =
        "DentTrack_Backup_" + SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date()) + ".xlsx"

    suspend fun run(context: Context, generatedBy: String): Outcome {
        val app = context.applicationContext
        val settings = AppSettings(app)
        val config = settings.state.value

        if (!config.backupLocal && !config.backupDrive) {
            return finish(settings, "Choose at least one backup destination", false)
        }

        val dao = AppDatabase.get(app).dao()
        val bytes = ReportBuilder.build(
            items = dao.itemsOnce(),
            log = dao.logOnce(),
            users = dao.usersOnce(),
            generatedBy = generatedBy,
            threshold = config.lowStockThreshold,
            categories = dao.categoriesOnce().map { it.name }
        )
        val fileName = fileName()

        val parts = ArrayList<String>()
        var allOk = true

        if (config.backupLocal) {
            try {
                val where = withContext(Dispatchers.IO) { writeLocal(app, config.backupFolderUri, fileName, bytes) }
                parts.add("Local: saved to $where")
            } catch (e: Exception) {
                allOk = false
                parts.add("Local failed: ${e.message ?: e.javaClass.simpleName}")
            }
        }

        if (config.backupDrive) {
            try {
                when (val auth = DriveBackup.authorize(app)) {
                    is DriveBackup.Auth.Token -> {
                        DriveBackup.upload(auth.value, fileName, bytes)
                        parts.add("Google Drive: uploaded")
                    }
                    is DriveBackup.Auth.NeedsConsent -> {
                        allOk = false
                        parts.add("Google Drive: permission needed. Open Backup and tap Connect")
                    }
                    is DriveBackup.Auth.Failed -> {
                        allOk = false
                        parts.add("Google Drive failed: ${auth.reason}")
                    }
                }
            } catch (e: Exception) {
                allOk = false
                parts.add("Google Drive failed: ${e.message ?: e.javaClass.simpleName}")
            }
        }

        return finish(settings, parts.joinToString(" · "), allOk)
    }

    private fun finish(settings: AppSettings, message: String, success: Boolean): Outcome {
        settings.setLastBackup(System.currentTimeMillis(), message)
        return Outcome(message, success)
    }

    /** Writes into the folder the Admin picked, or into the app's own storage folder. */
    private fun writeLocal(context: Context, folderUri: String?, fileName: String, bytes: ByteArray): String {
        if (folderUri != null) {
            val resolver = context.contentResolver
            val tree = Uri.parse(folderUri)
            val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val document = DocumentsContract.createDocument(resolver, parent, XlsxWriter.MIME, fileName)
                ?: throw IOException("could not create a file in the chosen folder")
            val stream = resolver.openOutputStream(document)
                ?: throw IOException("could not write to the chosen folder")
            stream.use { it.write(bytes) }
            return "the chosen folder"
        }
        val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "backups")
        dir.mkdirs()
        File(dir, fileName).writeBytes(bytes)
        return dir.absolutePath
    }
}

/** Runs [BackupRunner] in the background on the chosen schedule, even when the app is closed. */
class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val outcome = BackupRunner.run(applicationContext, "Automatic backup")
        return if (outcome.success) Result.success() else Result.failure()
    }
}

object BackupScheduler {
    private const val WORK_NAME = "denttrack_scheduled_backup"

    /** [replace] = true when the Admin changed something; false when the app just started. */
    fun apply(context: Context, config: AppConfig, replace: Boolean) {
        val manager = WorkManager.getInstance(context.applicationContext)
        val active = config.backupSchedule != BackupSchedule.OFF && (config.backupLocal || config.backupDrive)
        if (!active) {
            manager.cancelUniqueWork(WORK_NAME)
            return
        }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (config.backupDrive) NetworkType.CONNECTED else NetworkType.NOT_REQUIRED)
            .build()
        val request = PeriodicWorkRequestBuilder<BackupWorker>(config.backupSchedule.days, TimeUnit.DAYS)
            .setConstraints(constraints)
            .build()
        manager.enqueueUniquePeriodicWork(
            WORK_NAME,
            if (replace) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
