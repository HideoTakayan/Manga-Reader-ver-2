package com.example.manga_readerver2.domain.model

data class Chapter(
    val id: Long,
    val mangaId: Long,
    val url: String,
    val name: String,
    val scanlator: String? = null,
    val read: Boolean = false,
    val bookmark: Boolean = false,
    val lastPageRead: Long = 0,
    val dateFetch: Long = 0,
    val dateUpload: Long = 0,
    val chapterNumber: Float = -1f,
    val sourceOrder: Long = -1,
)
