package com.example.manga_readerver2.core.source

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.example.manga_readerver2.core.utils.FileManager
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.*
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

class LocalSource(
    private val context: Context = Injekt.get(),
    private val fileManager: FileManager = Injekt.get(),
    private val sourcePreferences: SourcePreferences = Injekt.get()
) : CatalogueSource {

    override val id: Long = 0L
    override val name: String = "Local source"
    override val lang: String = "other"
    override val supportsLatest: Boolean = true

    private fun getBaseDirectory(): DocumentFile? {
        val uriString = sourcePreferences.localSourceUri.get()
        if (uriString.isBlank()) return null
        return try {
            DocumentFile.fromTreeUri(context, Uri.parse(uriString))
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun getPopularManga(page: Int): MangasPage {
        val baseDir = getBaseDirectory() ?: return MangasPage(emptyList(), false)
        val files = baseDir.listFiles().filter { it.isDirectory || it.name?.endsWith(".cbz", ignoreCase = true) == true || it.name?.endsWith(".zip", ignoreCase = true) == true }
        
        val mangas = files.map { file ->
            SManga.create().apply {
                url = file.name ?: ""
                title = file.name?.substringBeforeLast(".") ?: ""
                
                if (file.isDirectory) {
                    val coverJpg = file.findFile("cover.jpg")
                    val coverPng = file.findFile("cover.png")
                    if (coverJpg != null) thumbnail_url = coverJpg.uri.toString()
                    else if (coverPng != null) thumbnail_url = coverPng.uri.toString()
                }
            }
        }
        return MangasPage(mangas, false)
    }

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage {
        val baseDir = getBaseDirectory() ?: return MangasPage(emptyList(), false)
        val files = baseDir.listFiles().filter { 
            val name = it.name ?: ""
            (it.isDirectory || name.endsWith(".cbz", true) || name.endsWith(".zip", true)) && name.contains(query, ignoreCase = true)
        }
        
        val mangas = files.map { file ->
            SManga.create().apply {
                url = file.name ?: ""
                title = file.name?.substringBeforeLast(".") ?: ""
                
                if (file.isDirectory) {
                    val coverJpg = file.findFile("cover.jpg")
                    val coverPng = file.findFile("cover.png")
                    if (coverJpg != null) thumbnail_url = coverJpg.uri.toString()
                    else if (coverPng != null) thumbnail_url = coverPng.uri.toString()
                }
            }
        }
        return MangasPage(mangas, false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val baseDir = getBaseDirectory() ?: return MangasPage(emptyList(), false)
        val files = baseDir.listFiles().filter { it.isDirectory || it.name?.endsWith(".cbz", true) == true || it.name?.endsWith(".zip", true) == true }
            .sortedByDescending { it.lastModified() }
            
        val mangas = files.map { file ->
            SManga.create().apply {
                url = file.name ?: ""
                title = file.name?.substringBeforeLast(".") ?: ""
            }
        }
        return MangasPage(mangas, false)
    }

    override suspend fun getMangaDetails(manga: SManga): SManga {
        return manga.apply {
            description = "Truyện đọc từ bộ nhớ máy."
            status = SManga.COMPLETED
            author = "Local"
            artist = "Local"
        }
    }

    override suspend fun getChapterList(manga: SManga): List<SChapter> {
        val baseDir = getBaseDirectory() ?: return emptyList()
        val mangaFile = baseDir.findFile(manga.url) ?: return emptyList()

        if (mangaFile.isDirectory) {
            val chapters = mangaFile.listFiles().filter {
                it.isDirectory || it.name?.endsWith(".cbz", ignoreCase = true) == true || it.name?.endsWith(".zip", ignoreCase = true) == true
            }.map { file ->
                SChapter.create().apply {
                    url = "${manga.url}/${file.name}"
                    name = file.name?.substringBeforeLast(".") ?: ""
                    date_upload = file.lastModified()
                    chapter_number = -1f
                }
            }.sortedByDescending { it.name }
            return chapters
        } else {
            // It's a single zip/cbz file manga
            return listOf(
                SChapter.create().apply {
                    url = mangaFile.name ?: ""
                    name = "Chương 1"
                    date_upload = mangaFile.lastModified()
                    chapter_number = 1f
                }
            )
        }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val baseDir = getBaseDirectory() ?: return emptyList()
        val parts = chapter.url.split("/")
        
        val file = if (parts.size == 1) {
            baseDir.findFile(parts[0])
        } else {
            baseDir.findFile(parts[0])?.findFile(parts[1])
        } ?: return emptyList()

        return if (file.isDirectory) {
            val images = file.listFiles().filter { 
                val name = it.name?.lowercase() ?: ""
                name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".webp")
            }.sortedBy { it.name }

            images.mapIndexed { index, img ->
                Page(index, "", img.uri.toString())
            }
        } else if (file.name?.endsWith(".cbz", ignoreCase = true) == true || file.name?.endsWith(".zip", ignoreCase = true) == true) {
            val pages = mutableListOf<Page>()
            try {
                val parentName = file.parentFile?.name ?: "unknown"
                val tempDir = File(fileManager.getCacheDir(), "local_zip_${parentName}_${file.name?.substringBeforeLast(".")}")
                if (!tempDir.exists()) tempDir.mkdirs()

                context.contentResolver.openInputStream(file.uri)?.use { input ->
                    ZipInputStream(input).use { zis ->
                        var entry = zis.nextEntry
                        var index = 0
                        val imageEntries = mutableListOf<File>()
                        
                        while (entry != null) {
                            if (!entry.isDirectory) {
                                val name = entry.name.lowercase()
                                if (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".webp")) {
                                    val extractedFile = File(tempDir, entry.name.substringAfterLast("/"))
                                    if (!extractedFile.exists()) {
                                        FileOutputStream(extractedFile).use { output ->
                                            zis.copyTo(output)
                                        }
                                    }
                                    imageEntries.add(extractedFile)
                                }
                            }
                            entry = zis.nextEntry
                        }
                        
                        imageEntries.sortedBy { it.name }.forEachIndexed { i, imgFile ->
                            pages.add(Page(i, "", imgFile.toURI().toString()))
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            pages
        } else {
            emptyList()
        }
    }
}
