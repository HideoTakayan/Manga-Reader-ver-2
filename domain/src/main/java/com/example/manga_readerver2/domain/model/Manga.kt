package com.example.manga_readerver2.domain.model

data class Manga(
    val id: Long,
    val source: Long,
    val url: String,
    val title: String,
    val artist: String? = null,
    val author: String? = null,
    val description: String? = null,
    val genre: List<String>? = null,
    val status: Long = 0,
    val thumbnailUrl: String? = null,
    val favorite: Boolean = false,
    val lastUpdate: Long = 0,
    val initialized: Boolean = false,
    val viewerFlags: Int = 0,
    val chapterFlags: Int = 0,
    val coverLastModified: Long = 0,
    val dateAdded: Long = 0,
) {
    companion object {
        fun create() = Manga(
            id = -1L,
            source = -1L,
            url = "",
            title = ""
        )
    }
}
