package com.example.manga_readerver2.features.browse

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.example.manga_readerver2.core.source.ExtensionManager
import com.example.manga_readerver2.domain.model.Manga
import com.example.manga_readerver2.domain.repository.MangaRepository
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class CatalogueScreenModel(
    private val mangaRepository: MangaRepository = Injekt.get(),
    private val extensionManager: ExtensionManager = Injekt.get()
) : ScreenModel {

    private val _listing = MutableStateFlow<Listing>(Listing.Popular)
    val listing = _listing.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _filters = MutableStateFlow<FilterList>(FilterList())
    val filters = _filters.asStateFlow()

    var source: CatalogueSource? = null
        private set

    sealed class Listing {
        object Popular : Listing()
        object Latest : Listing()
        object Search : Listing()
    }

    private var currentPager: Flow<PagingData<Manga>> = emptyFlow()

    fun initSource(sourceId: Long) {
        val currentSource = extensionManager.getSource(sourceId) as? CatalogueSource
        source = currentSource
        currentSource?.let {
            _filters.value = it.getFilterList()
        }
    }

    fun getMangaFlow(): Flow<PagingData<Manga>> {
        val currentSource = source ?: return emptyFlow()
        
        return Pager(
            config = PagingConfig(pageSize = 20, enablePlaceholders = false),
            pagingSourceFactory = {
                SourcePagingSource(
                    source = currentSource,
                    sourceId = currentSource.id,
                    query = _searchQuery.value,
                    filters = _filters.value,
                    listing = _listing.value
                )
            }
        ).flow.cachedIn(screenModelScope)
    }

    fun setListing(newListing: Listing) {
        if (_listing.value != newListing) {
            _listing.value = newListing
        }
    }

    fun search(query: String) {
        _searchQuery.value = query
        if (query.isNotEmpty()) {
            setListing(Listing.Search)
        } else {
            setListing(Listing.Popular)
        }
    }

    fun setFilters(filters: FilterList) {
        _filters.value = filters
        // Trigger refresh if in search mode
        if (_listing.value == Listing.Search || _searchQuery.value.isNotEmpty()) {
            _listing.value = Listing.Search
        }
    }

    fun resetFilters() {
        source?.let {
            _filters.value = it.getFilterList()
        }
    }
}
