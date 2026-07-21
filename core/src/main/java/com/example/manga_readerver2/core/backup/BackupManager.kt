package com.example.manga_readerver2.core.backup

import android.content.Context
import com.example.manga_readerver2.core.backup.model.*
import com.example.manga_readerver2.domain.repository.MangaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import logcat.logcat
import java.io.File
import java.io.FileOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class BackupManager(
    private val context: Context,
    private val mangaRepository: MangaRepository = Injekt.get()
) {
    private val json = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun createBackupString(): String? = withContext(Dispatchers.IO) {
        try {
            val allManga = mangaRepository.getLibrary().first()
            val categories = mangaRepository.getCategories().first()
            
            val backupMangaList = allManga.map { libraryManga ->
                val manga = libraryManga.manga
                val chapters = mangaRepository.getChaptersByMangaId(manga.id)
                val mangaCategoryIds = mangaRepository.getMangaCategoryIds(manga.id)
                val mangaCategories = categories.filter { it.id in mangaCategoryIds }.map { it.name }
                
                BackupManga(
                    url = manga.url,
                    title = manga.title,
                    source = manga.source,
                    favorite = manga.favorite,
                    author = manga.author,
                    artist = manga.artist,
                    description = manga.description,
                    genre = manga.genre ?: emptyList(),
                    status = manga.status,
                    thumbnailUrl = manga.thumbnailUrl,
                    dateAdded = manga.dateAdded,
                    categories = mangaCategories,
                    chapters = chapters.map { 
                        BackupChapter(
                            url = it.url,
                            name = it.name,
                            chapterNumber = it.chapterNumber,
                            read = it.read,
                            bookmark = it.bookmark,
                            lastPageRead = it.lastPageRead,
                            dateFetch = it.dateFetch,
                            dateUpload = it.dateUpload
                        )
                    }
                )
            }

            val backup = Backup(
                backupManga = backupMangaList,
                backupCategories = categories.map { BackupCategory(it.name, it.sortIndex) }
            )

            json.encodeToString(backup)
        } catch (e: Exception) {
            logcat { "Backup string creation failed: ${e.message}" }
            null
        }
    }

    suspend fun restoreBackupFromBytes(gzipBytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
        try {
            val backupString = java.io.ByteArrayInputStream(gzipBytes).use { inputStream ->
                GZIPInputStream(inputStream).bufferedReader().readText()
            }
            restoreBackupFromString(backupString)
        } catch (e: Exception) {
            logcat { "Restore from bytes failed: ${e.message}" }
            false
        }
    }

    private suspend fun restoreBackupFromString(backupString: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val backup = json.decodeFromString<Backup>(backupString)
            
            // 1. Restore Categories
            val existingCategories = mangaRepository.getCategories().first()
            backup.backupCategories.forEach { backupCat ->
                if (existingCategories.none { it.name == backupCat.name }) {
                    mangaRepository.insertCategory(backupCat.name, backupCat.order)
                }
            }
            
            val finalCategories = mangaRepository.getCategories().first()

            // 2. Restore Manga & Chapters
            backup.backupManga.forEach { bManga ->
                // Check if already exists
                var manga = mangaRepository.getMangaByUrlAndSource(bManga.url, bManga.source)
                
                val mangaToInsert = com.example.manga_readerver2.domain.model.Manga(
                    id = manga?.id ?: 0,
                    source = bManga.source,
                    url = bManga.url,
                    title = bManga.title,
                    artist = bManga.artist ?: "",
                    author = bManga.author ?: "",
                    description = bManga.description ?: "",
                    genre = bManga.genre,
                    status = bManga.status,
                    thumbnailUrl = bManga.thumbnailUrl ?: "",
                    favorite = bManga.favorite,
                    dateAdded = bManga.dateAdded
                )
                
                val mangaId = if (manga == null) {
                    mangaRepository.insertManga(mangaToInsert)
                } else {
                    mangaRepository.updateMangaDetails(mangaToInsert)
                    manga.id
                }

                // Restore Chapters
                val chapters = bManga.chapters.map { bc ->
                    com.example.manga_readerver2.domain.model.Chapter(
                        id = 0,
                        mangaId = mangaId,
                        url = bc.url,
                        name = bc.name,
                        read = bc.read,
                        bookmark = bc.bookmark,
                        lastPageRead = bc.lastPageRead,
                        dateFetch = bc.dateFetch,
                        dateUpload = bc.dateUpload,
                        chapterNumber = bc.chapterNumber
                    )
                }
                // Khôi phục danh sách chương
                // Quá trình khôi phục được bảo vệ bằng transaction cục bộ trong hàm insertChapters,
                // qua đó giảm thiểu chi phí ghi cơ sở dữ liệu trên mỗi chương.
                mangaRepository.insertChapters(chapters)

                // Đồng bộ hàng loạt trạng thái đọc (read status) thay vì gửi nhiều lệnh update rời rạc.
                // Lấy các chương đã được ghi vào DB, đối chiếu với backup để update
                // trạng thái đọc. insertChapters dùng INSERT OR IGNORE nên chỉ update
                // metadata mới (tên, số chương), không ghi đè read/lastPageRead.
                // Ta cần update riêng những chương có trạng thái đọc.
                val chaptersToUpdate = chapters.filter { it.read || it.lastPageRead > 0 || it.bookmark }
                if (chaptersToUpdate.isNotEmpty()) {
                    // Cập nhật dữ liệu thông qua batch operation để đảm bảo tính toàn vẹn (ACID) của SQLite
                    mangaRepository.updateChapterReadStatuses(chaptersToUpdate)
                }

                // Restore Category Mappings
                bManga.categories.forEach { catName ->
                    val cat = finalCategories.find { it.name == catName }
                    if (cat != null) {
                        mangaRepository.addMangaToCategory(mangaId, cat.id)
                    }
                }
            }
            true
        } catch (e: Exception) {
            logcat { "Backup restoration failed: ${e.message}" }
            false
        }
    }

    /**
     * Create a backup and save to a file.
     */
    suspend fun createBackup(uri: android.net.Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val backupString = createBackupString() ?: return@withContext false
            
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                GZIPOutputStream(outputStream).use { gzip ->
                    gzip.write(backupString.toByteArray())
                }
            }
            true
        } catch (e: Exception) {
            logcat { "Backup creation failed: ${e.message}" }
            false
        }
    }

    /**
     * Validate & preview backup file — gọi trước khi restoreBackup để user confirm.
     */
    suspend fun previewBackup(uri: android.net.Uri): com.example.manga_readerver2.core.backup.model.BackupPreview = withContext(Dispatchers.IO) {
        try {
            val backupString = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                // Try GZIP first, fallback to plain text
                try {
                    GZIPInputStream(inputStream).bufferedReader().readText()
                } catch (e: Exception) {
                    null
                }
            } ?: return@withContext com.example.manga_readerver2.core.backup.model.BackupPreview(
                mangaCount = 0, chapterCount = 0, categoryCount = 0,
                isValid = false, errorMessage = "Không thể đọc file backup"
            )

            val backup = try {
                json.decodeFromString<com.example.manga_readerver2.core.backup.model.Backup>(backupString)
            } catch (e: Exception) {
                return@withContext com.example.manga_readerver2.core.backup.model.BackupPreview(
                    mangaCount = 0, chapterCount = 0, categoryCount = 0,
                    isValid = false, errorMessage = "File backup bị lỗi hoặc không đúng định dạng"
                )
            }

            // Validate: must have at least some manga
            if (backup.backupManga.isEmpty()) {
                return@withContext com.example.manga_readerver2.core.backup.model.BackupPreview(
                    mangaCount = 0, chapterCount = 0, categoryCount = backup.backupCategories.size,
                    isValid = false, errorMessage = "File backup không chứa truyện nào"
                )
            }

            com.example.manga_readerver2.core.backup.model.BackupPreview(
                mangaCount = backup.backupManga.size,
                chapterCount = backup.backupManga.sumOf { it.chapters.size },
                categoryCount = backup.backupCategories.size,
                isValid = true
            )
        } catch (e: Exception) {
            com.example.manga_readerver2.core.backup.model.BackupPreview(
                mangaCount = 0, chapterCount = 0, categoryCount = 0,
                isValid = false, errorMessage = "Lỗi không xác định: ${e.message}"
            )
        }
    }

    /**
     * Restore from a backup file.
     */
    suspend fun restoreBackup(uri: android.net.Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val backupString = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                GZIPInputStream(inputStream).bufferedReader().readText()
            } ?: return@withContext false

            restoreBackupFromString(backupString)
        } catch (e: Exception) {
            logcat { "Backup restoration failed: ${e.message}" }
            false
        }
    }
}
