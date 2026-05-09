package com.example.manga_readerver2.features.statistics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Chi tiết theo nguồn",
                        style = MaterialTheme.typography.titleMedium,
                        color = PrimaryOrange,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                items(stats.sourceStats.size) { index ->
                    val sourceStat = stats.sourceStats[index]
                    ListItem(
                        headlineContent = { Text(sourceStat.sourceName, color = TextPrimary) },
                        supportingContent = { Text("${sourceStat.count} truyện", color = TextSecondary) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
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
