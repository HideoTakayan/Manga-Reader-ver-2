package eu.kanade.tachiyomi.source

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga

import eu.kanade.tachiyomi.util.awaitSingle
import rx.Observable

interface Source {
    val id: Long
    val name: String
    val lang: String get() = ""

    suspend fun getMangaDetails(manga: SManga): SManga {
        return fetchMangaDetails(manga).awaitSingle()
    }

    suspend fun getChapterList(manga: SManga): List<SChapter> {
        return fetchChapterList(manga).awaitSingle()
    }

    suspend fun getPageList(chapter: SChapter): List<Page> {
        return fetchPageList(chapter).awaitSingle()
    }

    @Deprecated("Use the non-RxJava API instead", ReplaceWith("getMangaDetails"))
    fun fetchMangaDetails(manga: SManga): Observable<SManga> = throw IllegalStateException("Not used")

    @Deprecated("Use the non-RxJava API instead", ReplaceWith("getChapterList"))
    fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = throw IllegalStateException("Not used")

    @Deprecated("Use the non-RxJava API instead", ReplaceWith("getPageList"))
    fun fetchPageList(chapter: SChapter): Observable<List<Page>> = throw IllegalStateException("Not used")
}

interface CatalogueSource : Source {
    abstract override val lang: String

    val supportsLatest: Boolean

    suspend fun getPopularManga(page: Int): MangasPage {
        return fetchPopularManga(page).awaitSingle()
    }

    suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage {
        return fetchSearchManga(page, query, filters).awaitSingle()
    }

    suspend fun getLatestUpdates(page: Int): MangasPage {
        return fetchLatestUpdates(page).awaitSingle()
    }

    fun getFilterList(): FilterList = FilterList()

    @Deprecated("Use the non-RxJava API instead", ReplaceWith("getPopularManga"))
    fun fetchPopularManga(page: Int): Observable<MangasPage> = throw IllegalStateException("Not used")

    @Deprecated("Use the non-RxJava API instead", ReplaceWith("fetchSearchManga"))
    fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = throw IllegalStateException("Not used")

    @Deprecated("Use the non-RxJava API instead", ReplaceWith("getLatestUpdates"))
    fun fetchLatestUpdates(page: Int): Observable<MangasPage> = throw IllegalStateException("Not used")
}

interface SourceFactory {
    fun createSources(): List<Source>
}
