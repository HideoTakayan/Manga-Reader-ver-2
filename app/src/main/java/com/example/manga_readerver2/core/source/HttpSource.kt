package com.example.manga_readerver2.core.source

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga

abstract class HttpSource(
    protected val client: OkHttpClient
) : CatalogueSource {
    abstract val baseUrl: String

    open fun headersBuilder() = okhttp3.Headers.Builder().apply {
        add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
    }

    val headers: okhttp3.Headers by lazy { headersBuilder().build() }

    override suspend fun getMangaDetails(manga: SManga): SManga = manga
    override suspend fun getChapterList(manga: SManga): List<SChapter> = emptyList()
    override suspend fun getPageList(chapter: SChapter): List<Page> = emptyList()

    open suspend fun getImageUrl(page: Page): String {
        return page.imageUrl ?: ""
    }

    open fun popularMangaRequest(page: Int): Request {
        return Request.Builder().url("$baseUrl/popular/$page").build()
    }

    open fun searchMangaRequest(page: Int, query: String): Request {
        return Request.Builder().url("$baseUrl/search/$page?q=$query").build()
    }

    open fun latestMangaRequest(page: Int): Request {
        return Request.Builder().url("$baseUrl/latest/$page").build()
    }
}
