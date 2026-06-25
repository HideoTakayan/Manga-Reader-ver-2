package com.example.manga_readerver2.features.statistics

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.example.manga_readerver2.domain.repository.MangaRepository
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

data class StatisticsState(
    val totalManga: Int = 0,
    val totalChaptersRead: Int = 0,
    val totalTimeMinutes: Int = 0,
    val avgPagePerMinute: Int = 0,
    val sourceStats: List<SourceStat> = emptyList(),
    /** Số chapter đọc mỗi ngày trong 7 ngày gần nhất (index 0 = 6 ngày trước, index 6 = hôm nay) */
    val weeklyChapters: List<Int> = List(7) { 0 },
    /** Tên ngày trong tuần tương ứng */
    val weeklyLabels: List<String> = emptyList(),
    /** Chuỗi ngày đọc hiện tại */
    val currentStreak: Int = 0,
    /** Chuỗi ngày đọc dài nhất */
    /** Chuỗi ngày đọc dài nhất */
    val longestStreak: Int = 0,
    /** Thống kê thể loại */
    val genreStats: List<GenreStat> = emptyList()
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
    val count: Int,
    val percentage: Float
)

data class GenreStat(
    val name: String,
    val count: Int,
    val percentage: Float
)

class StatisticsScreenModel(
    private val repository: MangaRepository = Injekt.get()
) : StateScreenModel<StatisticsState>(StatisticsState()) {

    init {
        loadStatistics()
    }

    private fun loadStatistics() {
        screenModelScope.launch {
            combine(
                repository.getStats(),
                repository.getHistory(),
                repository.getLibrary()
            ) { data, history, library ->
                val sourceManager = Injekt.get<com.example.manga_readerver2.core.source.SourceManager>()
                val totalSources = data.sourceStats.sumOf { it.count }
                val stats = data.sourceStats.map { stat ->
                    val sourceName = sourceManager.get(stat.sourceId)?.name ?: "Unknown (${stat.sourceId})"
                    SourceStat(sourceName, stat.count, if (totalSources > 0) stat.count.toFloat() / totalSources else 0f)
                }.sortedByDescending { it.count }.take(10)

                // Tính toán thống kê thể loại (Genres)
                val genreCounts = mutableMapOf<String, Int>()
                var totalGenres = 0
                library.forEach { libManga ->
                    libManga.manga.genre?.forEach { genre ->
                        val normalized = genre.trim()
                        if (normalized.isNotEmpty()) {
                            genreCounts[normalized] = (genreCounts[normalized] ?: 0) + 1
                            totalGenres++
                        }
                    }
                }
                
                val genreStats = genreCounts.map { (name, count) ->
                    GenreStat(name, count, if (totalGenres > 0) count.toFloat() / totalGenres else 0f)
                }.sortedByDescending { it.count }.take(10) // Lấy top 10 thể loại

                // Tính số chapter đọc mỗi ngày trong 7 ngày gần nhất
                val now = Calendar.getInstance()
                val dayFormat = SimpleDateFormat("EEE", Locale("vi"))
                val weeklyCount = IntArray(7)
                val labels = (6 downTo 0).map { daysAgo ->
                    val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -daysAgo) }
                    dayFormat.format(cal.time).replaceFirstChar { c -> c.uppercase() }
                }
                history.forEach { item ->
                    val diffDays = TimeUnit.MILLISECONDS.toDays(now.timeInMillis - item.lastRead)
                    if (diffDays in 0..6) {
                        val index = (6 - diffDays).toInt()
                        if (index in 0..6) weeklyCount[index]++
                    }
                }

                // Tính reading streak — nhóm history theo ngày
                val dayMs = TimeUnit.DAYS.toMillis(1)
                val readDays = history
                    .map { TimeUnit.MILLISECONDS.toDays(it.lastRead) } // epoch day
                    .toSortedSet() // unique days ascending

                var currentStreak = 0
                var longestStreak = 0
                var streak = 0
                val todayDay = TimeUnit.MILLISECONDS.toDays(now.timeInMillis)

                // Tính longest streak
                var prevDay = -2L
                for (day in readDays) {
                    streak = if (day == prevDay + 1) streak + 1 else 1
                    if (streak > longestStreak) longestStreak = streak
                    prevDay = day
                }

                // Tính current streak (từ hôm nay lùi về)
                currentStreak = 0
                var checkDay = todayDay
                while (readDays.contains(checkDay)) {
                    currentStreak++
                    checkDay--
                }
                // Nếu hôm nay chưa đọc, thử tính từ hôm qua
                if (currentStreak == 0) {
                    checkDay = todayDay - 1
                    while (readDays.contains(checkDay)) {
                        currentStreak++
                        checkDay--
                    }
                }

                StatisticsState(
                    totalManga = data.totalManga,
                    totalChaptersRead = data.totalChaptersRead,
                    totalTimeMinutes = data.totalTimeMinutes,
                    avgPagePerMinute = data.avgPagePerMinute,
                    sourceStats = stats,
                    weeklyChapters = weeklyCount.toList(),
                    weeklyLabels = labels,
                    currentStreak = currentStreak,
                    longestStreak = longestStreak,
                    genreStats = genreStats
                )

            }.collect { newState ->
                mutableState.value = newState
            }
        }
    }
}

