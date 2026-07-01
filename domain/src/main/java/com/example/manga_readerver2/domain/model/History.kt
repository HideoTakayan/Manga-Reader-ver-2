package com.example.manga_readerver2.domain.model

data class History(
    val mangaId: Long,
    val chapterId: Long,
    val mangaTitle: String,
    val chapterName: String,
    val thumbnailUrl: String?,
    val sourceId: Long,
    val lastRead: Long
)
