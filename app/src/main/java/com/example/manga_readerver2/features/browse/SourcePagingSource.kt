package com.example.manga_readerver2.features.browse

import androidx.paging.PagingSource
import androidx.paging.PagingState
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import com.example.manga_readerver2.domain.model.Manga
import com.example.manga_readerver2.domain.repository.MangaRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * PagingSource để hỗ trợ tải danh sách truyện có phân trang (Infinite Scrolling) từ CatalogueSource.
 * Tích hợp lưu trữ truyện vào Database ngay khi tải từ mạng về theo chuẩn Mihon.
 */
class SourcePagingSource(
    private val source: CatalogueSource,
    private val sourceId: Long,
    private val query: String,
    private val filters: FilterList,
    private val listing: CatalogueScreenModel.Listing,
    private val mangaRepository: MangaRepository = Injekt.get()
) : PagingSource<Int, Manga>() {

    private val seenManga = hashSetOf<String>()

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Manga> {
        val page = params.key ?: 1
        
        return try {
            val response = when (listing) {
                is CatalogueScreenModel.Listing.Popular -> source.getPopularManga(page)
                is CatalogueScreenModel.Listing.Latest -> source.getLatestUpdates(page)
                is CatalogueScreenModel.Listing.Search -> source.getSearchManga(page, query, filters)
            }
            
            val mangaList = response.mangas.map { sManga ->
                Manga(
                    id = 0L,
                    source = sourceId,
                    url = sManga.url,
                    title = sManga.title,
                    thumbnailUrl = sManga.thumbnailUrl,
                    artist = null,
                    author = null,
                    description = null,
                    genre = null,
                    status = 0L,
                    favorite = false,
                    lastUpdate = 0L,
                    initialized = false,
                    viewerFlags = 0,
                    chapterFlags = 0,
                    coverLastModified = 0L,
                    dateAdded = 0L
                )
            }.filter { seenManga.add(it.url) }
            
            // Lưu vào DB và lấy ra đối tượng Manga có ID thực tế
            val dbMangaList = mangaRepository.insertNetworkManga(mangaList)
            
            LoadResult.Page(
                data = dbMangaList,
                prevKey = if (page == 1) null else page - 1,
                nextKey = if (response.hasNextPage) page + 1 else null
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, Manga>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            state.closestPageToPosition(anchorPosition)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchorPosition)?.nextKey?.minus(1)
        }
    }
}
