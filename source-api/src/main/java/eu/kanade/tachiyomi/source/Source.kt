package eu.kanade.tachiyomi.source

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga

interface Source {
    val id: Long
    val name: String
    val lang: String get() = ""

    suspend fun getMangaDetails(manga: SManga): SManga
    suspend fun getChapterList(manga: SManga): List<SChapter>
    suspend fun getPageList(chapter: SChapter): List<Page>
}

data class MangasPage(val mangas: List<SManga>, val hasNextPage: Boolean)

interface CatalogueSource : Source {
    suspend fun getPopularManga(page: Int): MangasPage
    suspend fun fetchSearchManga(page: Int, query: String, filters: FilterList): MangasPage
    suspend fun getLatestUpdates(page: Int): MangasPage
    fun getFilterList(): FilterList = FilterList()
}

interface SourceFactory {
    fun createSources(): List<Source>
}
