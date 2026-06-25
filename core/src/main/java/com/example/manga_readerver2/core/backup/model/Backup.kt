package com.example.manga_readerver2.core.backup.model

import kotlinx.serialization.Serializable

/** Preview hiển thị cho user trước khi confirm restore */
data class BackupPreview(
    val mangaCount: Int,
    val chapterCount: Int,
    val categoryCount: Int,
    val isValid: Boolean,
    val errorMessage: String? = null
)

@Serializable
data class Backup(
    val version: Int = 1,
    val backupManga: List<BackupManga>,
    val backupCategories: List<BackupCategory> = emptyList()
)

@Serializable
data class BackupManga(
    val url: String,
    val title: String,
    val source: Long,
    val favorite: Boolean = true,
    val author: String? = null,
    val artist: String? = null,
    val description: String? = null,
    val genre: List<String> = emptyList(),
    val status: Long = 0,
    val thumbnailUrl: String? = null,
    val dateAdded: Long = 0,
    val categories: List<String> = emptyList(),
    val history: List<BackupHistory> = emptyList(),
    val chapters: List<BackupChapter> = emptyList()
)

@Serializable
data class BackupCategory(
    val name: String,
    val order: Long = 0
)

@Serializable
data class BackupHistory(
    val url: String,
    val lastRead: Long,
    val timeRead: Long = 0
)

@Serializable
data class BackupChapter(
    val url: String,
    val name: String,
    val chapterNumber: Float = -1f,
    val read: Boolean = false,
    val bookmark: Boolean = false,
    val lastPageRead: Long = 0,
    val dateFetch: Long = 0,
    val dateUpload: Long = 0
)
