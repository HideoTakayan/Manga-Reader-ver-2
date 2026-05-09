package com.example.manga_readerver2.domain.model

data class Update(
    val mangaId: Long,
    val mangaTitle: String,
    val chapterId: Long,
    val chapterName: String,
    val scanlator: String?,
    val read: Boolean,
    val bookmark: Boolean,
    val lastPageRead: Long,
    val sourceOrder: Long,
    val chapterNumber: Float,
    val dateFetch: Long,
    val dateUpload: Long,
    val lastModifiedAt: Long,
    val thumbnailUrl: String?
)
