package com.example.manga_readerver2.features.library

import com.example.manga_readerver2.domain.model.Manga

/**
 * Đại diện cho một mục trong thư viện với đầy đủ thông tin hiển thị.
 */
data class LibraryItem(
    val manga: Manga,
    val unreadCount: Int = 0,
    val isDownloaded: Boolean = false,
    val categoryId: Long = 0
)
