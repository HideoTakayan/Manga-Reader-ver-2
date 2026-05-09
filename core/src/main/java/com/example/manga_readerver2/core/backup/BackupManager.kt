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

    /**
     * Create a backup and save to a file.
     */
    suspend fun createBackup(uri: android.net.Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val allManga = mangaRepository.getLibrary().first()
            val categories = mangaRepository.getCategories().first()
            
            val backupMangaList = allManga.map { libraryManga ->
                val manga = libraryManga.manga
                val chapters = mangaRepository.getChaptersByMangaId(manga.id)
                val mangaCategoryIds = mangaRepository.getMangaCategoryIds(manga.id)
                val mangaCategories = categories.filter { it.id in mangaCategoryIds }.map { it.name }
                
                // Get history for this manga's chapters
                // For simplicity in MRv2, we'll just backup the chapters' read status
                // If we had a specific history table, we'd join it.
                
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

            val backupString = json.encodeToString(backup)
            
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
     * Restore from a backup file.
     */
    suspend fun restoreBackup(uri: android.net.Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val backupString = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                GZIPInputStream(inputStream).bufferedReader().readText()
            } ?: return@withContext false

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
                mangaRepository.insertChapters(chapters)

                // Cập nhật trạng thái đã đọc nếu bản sao lưu có thông tin mới hơn
                chapters.forEach { chapter ->
                    if (chapter.read || chapter.lastPageRead > 0) {
                        mangaRepository.updateChapterReadStatus(chapter)
                    }
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
}
