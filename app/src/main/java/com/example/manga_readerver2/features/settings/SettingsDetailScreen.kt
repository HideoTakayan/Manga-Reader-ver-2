package com.example.manga_readerver2.features.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
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
                    title = { Text("CĂ i Ä‘áº·t", color = Color.White, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.White)
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
                    title = "Hiá»ƒn thá»‹",
                    subtitle = "Giao diá»‡n, ngĂ y thĂ¡ng",
                    onClick = { navigator.push(DisplaySettingsScreen()) }
                )
                SettingsCategoryItem(
                    icon = Icons.Default.CollectionsBookmark,
                    title = "ThÆ° viá»‡n",
                    subtitle = "Cáº­p nháº­t, danh má»¥c, nhĂ£n",
                    onClick = { navigator.push(LibrarySettingsScreen()) }
                )
                SettingsCategoryItem(
                    icon = Icons.Default.ChromeReaderMode,
                    title = "TrĂ¬nh Ä‘á»c",
                    subtitle = "Cháº¿ Ä‘á»™ Ä‘á»c, Ä‘iá»u hÆ°á»›ng, hiá»ƒn thá»‹",
                    onClick = { navigator.push(ReaderSettingsScreen()) }
                )
                SettingsCategoryItem(
                    icon = Icons.Default.Download,
                    title = "Táº£i xuá»‘ng",
                    subtitle = "Vá»‹ trĂ­ táº£i, tá»± Ä‘á»™ng xĂ³a",
                    onClick = { navigator.push(DownloadSettingsScreen()) }
                )
                SettingsCategoryItem(
                    icon = Icons.Default.Explore,
                    title = "Duyá»‡t",
                    subtitle = "Nguá»“n truyá»‡n, tiá»‡n Ă­ch má»Ÿ rá»™ng",
                    onClick = { navigator.push(BrowseSettingsScreen()) }
                )
                SettingsCategoryItem(
                    icon = Icons.Default.Security,
                    title = "Báº£o máº­t",
                    subtitle = "KhĂ³a á»©ng dá»¥ng, vĂ¢n tay",
                    onClick = { navigator.push(SecuritySettingsScreen()) }
                )
                SettingsCategoryItem(
                    icon = Icons.Default.Sync,
                    title = "Theo dĂµi",
                    subtitle = "MyAnimeList, AniList",
                    onClick = { navigator.push(TrackSettingsScreen()) }
                )
                SettingsCategoryItem(
                    icon = Icons.Default.Storage,
                    title = "Dá»¯ liá»‡u vĂ  Sao lÆ°u",
                    subtitle = "Sao lÆ°u thÆ° viá»‡n, xĂ³a bá»™ nhá»› Ä‘á»‡m",
                    onClick = { navigator.push(DataSettingsScreen()) }
                )
                SettingsCategoryItem(
                    icon = Icons.Default.Code,
                    title = "NĂ¢ng cao",
                    subtitle = "Dá»n dáº¹p database, log",
                    onClick = { navigator.push(AdvancedSettingsScreen()) }
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


