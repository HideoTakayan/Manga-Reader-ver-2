package com.example.manga_readerver2.domain.model

data class StatisticsData(
    val totalManga: Int,
    val totalChaptersRead: Int,
    val totalTimeMinutes: Int,
    val avgPagePerMinute: Int,
    val sourceStats: List<SourceStat>
)

data class SourceStat(
    val sourceId: Long,
    val count: Int
)
