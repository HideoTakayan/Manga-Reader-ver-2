package com.example.manga_readerver2.features.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.example.manga_readerver2.ui.theme.BackgroundDark
import com.example.manga_readerver2.ui.theme.PrimaryOrange

class SettingsDetailScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        Scaffold(
            containerColor = BackgroundDark,
            topBar = {
                TopAppBar(
                    title = { Text("Cài đặt", color = Color.White, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = null, tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark)
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
            ) {
                SettingsCategoryItem(
                    icon = Icons.Default.Palette,
                    title = "Hiển thị",
                    subtitle = "Chủ đề, định dạng ngày tháng",
                    onClick = { /* TODO */ }
                )
                SettingsCategoryItem(
                    icon = Icons.Default.CollectionsBookmark,
                    title = "Thư viện",
                    subtitle = "Cập nhật, danh mục, nhãn",
                    onClick = { /* TODO */ }
                )
                SettingsCategoryItem(
                    icon = Icons.Default.ChromeReaderMode,
                    title = "Trình đọc",
                    subtitle = "Chế độ đọc, điều hướng, hiển thị",
                    onClick = { navigator.push(ReaderSettingsScreen()) }
                )
                SettingsCategoryItem(
                    icon = Icons.Default.Download,
                    title = "Tải xuống",
                    subtitle = "Vị trí tải, tự động xóa",
                    onClick = { /* TODO */ }
                )
                SettingsCategoryItem(
                    icon = Icons.Default.Explore,
                    title = "Duyệt",
                    subtitle = "Nguồn truyện, tiện ích mở rộng",
                    onClick = { navigator.push(BrowseSettingsScreen()) }
                )
                SettingsCategoryItem(
                    icon = Icons.Default.Security,
                    title = "Bảo mật",
                    subtitle = "Khóa ứng dụng, vân tay",
                    onClick = { /* TODO */ }
                )
                SettingsCategoryItem(
                    icon = Icons.Default.Code,
                    title = "Nâng cao",
                    subtitle = "Dọn dẹp database, log",
                    onClick = { /* TODO */ }
                )
            }
        }
    }
}

@Composable
fun SettingsCategoryItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(24.dp))
            Column {
                Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text(subtitle, color = Color.Gray, fontSize = 13.sp)
            }
        }
    }
}
