package com.example.manga_readerver2.features.statistics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import com.example.manga_readerver2.ui.theme.BackgroundDark
import com.example.manga_readerver2.ui.theme.PrimaryOrange
import com.example.manga_readerver2.ui.theme.TextPrimary
import com.example.manga_readerver2.ui.theme.TextSecondary

class StatisticsScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        val screenModel = rememberScreenModel { StatisticsScreenModel() }
        val stats by screenModel.state.collectAsState()

        Scaffold(
            containerColor = BackgroundDark,
            topBar = {
                TopAppBar(
                    title = { Text("Thống kê", color = Color.White) },
                    navigationIcon = {
                        IconButton(onClick = { navigator?.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark)
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Text(
                        "Tổng quan",
                        style = MaterialTheme.typography.titleMedium,
                        color = PrimaryOrange,
                        fontWeight = FontWeight.Bold
                    )
                }

                item {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        StatCard(
                            label = "Tổng số truyện",
                            value = "${stats.totalManga}",
                            icon = Icons.Default.LibraryBooks,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        StatCard(
                            label = "Chương đã đọc",
                            value = "${stats.totalChaptersRead}",
                            icon = Icons.Default.History,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                item {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        StatCard(
                            label = "Thời gian đọc",
                            value = stats.formattedTime,
                            icon = Icons.Default.Timer,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        StatCard(
                            label = "Tốc độ tb",
                            value = "${stats.avgPagePerMinute} p/m",
                            icon = Icons.Default.AutoGraph,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                item {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        StatCard(
                            label = "Streak hiện tại",
                            value = "${stats.currentStreak} ngày",
                            icon = Icons.Default.LocalFireDepartment,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        StatCard(
                            label = "Streak dài nhất",
                            value = "${stats.longestStreak} ngày",
                            icon = Icons.Default.EmojiEvents,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Hoạt động đọc",
                        style = MaterialTheme.typography.titleMedium,
                        color = PrimaryOrange,
                        fontWeight = FontWeight.Bold
                    )
                }

                item {
                    if (stats.weeklyLabels.isNotEmpty()) {
                        WeeklyReadingChart(
                            values = stats.weeklyChapters,
                            labels = stats.weeklyLabels
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Tỉ lệ Thể loại",
                        style = MaterialTheme.typography.titleMedium,
                        color = PrimaryOrange,
                        fontWeight = FontWeight.Bold
                    )
                }

                item {
                    if (stats.genreStats.isNotEmpty()) {
                        GenrePieChart(stats.genreStats)
                    } else {
                        Text("Chưa có dữ liệu thể loại", color = TextSecondary)
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Chi tiết theo nguồn",
                        style = MaterialTheme.typography.titleMedium,
                        color = PrimaryOrange,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (stats.sourceStats.isNotEmpty()) {
                        SourcePieChart(stats.sourceStats)
                    } else {
                        Text("Chưa có dữ liệu", color = Color.Gray)
                    }
                }
            }
        }
    }

    @Composable
    private fun GenrePieChart(genreStats: List<GenreStat>) {
        val colors = listOf(
            Color(0xFFE57373), Color(0xFFF06292), Color(0xFFBA68C8), Color(0xFF9575CD),
            Color(0xFF7986CB), Color(0xFF64B5F6), Color(0xFF4FC3F7), Color(0xFF4DD0E1),
            Color(0xFF4DB6AC), Color(0xFF81C784)
        )

        Surface(
            color = Color.White.copy(alpha = 0.05f),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Pie Chart
                Canvas(
                    modifier = Modifier
                        .size(120.dp)
                        .padding(8.dp)
                ) {
                    var startAngle = -90f
                    genreStats.forEachIndexed { index, stat ->
                        val sweepAngle = stat.percentage * 360f
                        drawArc(
                            color = colors[index % colors.size],
                            startAngle = startAngle,
                            sweepAngle = sweepAngle,
                            useCenter = true
                        )
                        startAngle += sweepAngle
                    }
                }
                
                Spacer(modifier = Modifier.width(16.dp))

                // Legend
                Column {
                    genreStats.take(5).forEachIndexed { index, stat ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = colors[index % colors.size],
                                modifier = Modifier.size(10.dp)
                            ) {}
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${stat.name} (${(stat.percentage * 100).toInt()}%)",
                                color = TextPrimary,
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun SourcePieChart(sourceStats: List<SourceStat>) {
        val colors = listOf(
            Color(0xFF81C784), Color(0xFF4DB6AC), Color(0xFF4DD0E1), Color(0xFF4FC3F7),
            Color(0xFF64B5F6), Color(0xFF7986CB), Color(0xFF9575CD), Color(0xFFBA68C8),
            Color(0xFFF06292), Color(0xFFE57373)
        )

        Surface(
            color = Color.White.copy(alpha = 0.05f),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Pie Chart
                Canvas(
                    modifier = Modifier
                        .size(120.dp)
                        .padding(8.dp)
                ) {
                    var startAngle = -90f
                    sourceStats.forEachIndexed { index, stat ->
                        val sweepAngle = stat.percentage * 360f
                        drawArc(
                            color = colors[index % colors.size],
                            startAngle = startAngle,
                            sweepAngle = sweepAngle,
                            useCenter = true
                        )
                        startAngle += sweepAngle
                    }
                }
                
                Spacer(modifier = Modifier.width(16.dp))

                // Legend
                Column {
                    sourceStats.take(5).forEachIndexed { index, stat ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = colors[index % colors.size],
                                modifier = Modifier.size(10.dp)
                            ) {}
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${stat.sourceName} (${(stat.percentage * 100).toInt()}%)",
                                color = TextPrimary,
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun WeeklyReadingChart(
        values: List<Int>,
        labels: List<String>
    ) {
        val maxVal = values.maxOrNull()?.coerceAtLeast(1) ?: 1
        val barColor = PrimaryOrange
        val dimColor = Color.White.copy(alpha = 0.08f)

        Surface(
            color = Color.White.copy(alpha = 0.05f),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "7 ngày gần nhất",
                    color = PrimaryOrange,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                ) {
                    val count = values.size
                    val barWidth = size.width / (count * 2f)
                    val gap = barWidth
                    val maxHeight = size.height * 0.85f

                    values.forEachIndexed { i, v ->
                        val x = i * (barWidth + gap) + gap / 2f
                        val barH = (v.toFloat() / maxVal) * maxHeight
                        // Background bar
                        drawRoundRect(
                            color = dimColor,
                            topLeft = Offset(x, 0f),
                            size = Size(barWidth, size.height),
                            cornerRadius = CornerRadius(6f, 6f)
                        )
                        // Value bar
                        if (barH > 0f) {
                            drawRoundRect(
                                color = barColor,
                                topLeft = Offset(x, size.height - barH),
                                size = Size(barWidth, barH),
                                cornerRadius = CornerRadius(6f, 6f)
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    labels.forEach { label ->
                        Text(label, color = TextSecondary, fontSize = 10.sp)
                    }
                }
            }
        }
    }

    @Composable
    private fun StatCard(
        label: String,
        value: String,
        icon: ImageVector,
        modifier: Modifier = Modifier
    ) {
        Surface(
            modifier = modifier,
            color = Color.White.copy(alpha = 0.05f),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(icon, contentDescription = null, tint = PrimaryOrange, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text(value, color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(label, color = TextSecondary, fontSize = 12.sp)
            }
        }
    }
}
