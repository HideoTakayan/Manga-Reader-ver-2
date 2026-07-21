package com.example.manga_readerver2.core.source

import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import logcat.LogPriority
import logcat.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AndroidSourceManager(
    private val extensionManager: ExtensionManager = Injekt.get(),
    private val localSource: LocalSource = Injekt.get()
) : SourceManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _isInitialized = MutableStateFlow(false)
    override val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val _catalogueSources = MutableStateFlow<List<CatalogueSource>>(emptyList())
    override val catalogueSources: Flow<List<CatalogueSource>> = _catalogueSources.asStateFlow()

    private var sourcesMap = emptyMap<Long, Source>()

    init {
        scope.launch {
            // Kiến trúc phản ứng (Reactive): Tự động đồng bộ nguồn dữ liệu mỗi khi installedExtensionsFlow phát ra sự kiện mới
            // Các luồng sự kiện bao gồm: xác thực mở rộng (trust extension), cài đặt mới (install) hoặc làm mới danh sách (refresh)
            extensionManager.installedExtensionsFlow.collectLatest { extensions ->
                val mutableMap = mutableMapOf<Long, Source>()
                mutableMap[localSource.id] = localSource
                
                extensions.forEach { ext ->
                    ext.sources.forEach { source ->
                        mutableMap[source.id] = source
                    }
                }
                sourcesMap = mutableMap
                _catalogueSources.value = mutableMap.values.filterIsInstance<CatalogueSource>()
                _isInitialized.value = true

                logcat(LogPriority.INFO) {
                    "[AndroidSourceManager] Sources updated: ${mutableMap.size} total" +
                    " — ${_catalogueSources.value.size} catalogue source(s)"
                }
            }
        }
    }

    override fun get(sourceKey: Long): Source? {
        return sourcesMap[sourceKey]
    }

    override fun getOrStub(sourceKey: Long): Source {
        return sourcesMap[sourceKey] ?: extensionManager.getSourceData(sourceKey) ?: StubSource(sourceKey, "", "")
    }

    override fun getOnlineSources(): List<HttpSource> {
        return sourcesMap.values.filterIsInstance<HttpSource>()
    }

    override fun getCatalogueSources(): List<CatalogueSource> {
        return _catalogueSources.value
    }

    override fun getStubSources(): List<StubSource> {
        return sourcesMap.values.filterIsInstance<StubSource>()
    }
}
