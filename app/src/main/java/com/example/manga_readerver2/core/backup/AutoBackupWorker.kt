package com.example.manga_readerver2.core.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import logcat.logcat
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.GZIPOutputStream
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import com.example.manga_readerver2.core.preference.GeneralPreferences

class AutoBackupWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val generalPreferences = Injekt.get<GeneralPreferences>()
            if (!generalPreferences.autoBackup.get()) {
                return Result.success()
            }

            val backupManager = BackupManager(context)
            val backupString = backupManager.createBackupString()
            
            if (backupString != null) {
                val fileManager = com.example.manga_readerver2.core.utils.FileManager(context)
                val backupDir = fileManager.getBackupPath()
                if (!backupDir.exists()) {
                    backupDir.mkdirs()
                }

                val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault())
                val fileName = "manga_reader_autobackup_${dateFormat.format(Date())}.json.gz"
                val backupFile = File(backupDir, fileName)

                FileOutputStream(backupFile).use { outputStream ->
                    GZIPOutputStream(outputStream).use { gzip ->
                        gzip.write(backupString.toByteArray())
                    }
                }

                // Cleanup old backups
                val maxAutoBackups = generalPreferences.maxAutoBackups.get()
                val files = backupDir.listFiles()?.filter { it.name.startsWith("manga_reader_autobackup_") && it.extension == "gz" }
                if (files != null && files.size > maxAutoBackups) {
                    files.sortedBy { it.lastModified() }
                         .take(files.size - maxAutoBackups)
                         .forEach { it.delete() }
                }
                
                logcat { "Auto Backup completed successfully: $fileName" }
                Result.success()
            } else {
                logcat { "Auto Backup failed: No data string" }
                Result.retry()
            }
        } catch (e: Exception) {
            logcat { "Auto Backup exception: ${e.message}" }
            Result.retry()
        }
    }
}
