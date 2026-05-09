package com.example.manga_readerver2.features.statistics

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.example.manga_readerver2.domain.repository.MangaRepository
import com.example.manga_readerver2.domain.model.Manga
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

data class StatisticsState(
    val totalManga: Int = 0,
    val totalChaptersRead: Int = 0,
    val totalTimeMinutes: Int = 0,
    val avgPagePerMinute: Int = 0,
    val sourceStats: List<SourceStat> = emptyList()
) {
    val formattedTime: String
        get() {
            val hours = totalTimeMinutes / 60
            val mins = totalTimeMinutes % 60
            return "${hours}h ${mins}m"
        }
}

data class SourceStat(
    val sourceName: String,
    val count: Int
)

class StatisticsScreenModel(
    private val repository: MangaRepository = Injekt.get()
) : StateScreenModel<StatisticsState>(StatisticsState()) {

    init {
        loadStatistics()
    }

    private fun loadStatistics() {
        screenModelScope.launch {
            repository.getStats().collect { data ->
                val sourceManager = Injekt.get<com.example.manga_readerver2.core.source.ExtensionManager>()
                val stats = data.sourceStats.map { stat ->
                    val sourceName = sourceManager.getSource(stat.sourceId)?.name ?: "Unknown (${stat.sourceId})"
                    SourceStat(sourceName, stat.count)
                }
                
                mutableState.update { 
                    it.copy(
                        totalManga = data.totalManga,
                        totalChaptersRead = data.totalChaptersRead,
                        totalTimeMinutes = data.totalTimeMinutes,
                        avgPagePerMinute = data.avgPagePerMinute,
                        sourceStats = stats
                    )
                }
            }
        }
    }
}
