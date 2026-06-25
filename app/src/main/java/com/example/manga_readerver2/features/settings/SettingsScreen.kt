package com.example.manga_readerver2.features.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import com.example.manga_readerver2.ui.theme.BackgroundDark
import com.example.manga_readerver2.ui.theme.PrimaryOrange

import com.example.manga_readerver2.features.downloads.DownloadQueueScreen
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.LocalNavigator
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import java.util.*
import java.text.SimpleDateFormat

class SettingsScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = cafe.adriel.voyager.navigator.LocalNavigator.currentOrThrow
        val rootNavigator = navigator.parent ?: navigator
        val screenModel = rememberScreenModel { SettingsScreenModel() }
        val cacheSize by screenModel.cacheSize.collectAsState()
        val message by screenModel.message.collectAsState()
        val downloadedOnly by screenModel.downloadedOnly.collectAsState(false)
        val incognitoMode by screenModel.incognitoMode.collectAsState(false)
        val queueCount by screenModel.downloadQueueCount.collectAsState()
        
        val snackbarHostState = remember { SnackbarHostState() }
        val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current

        // LaunchedEffect for messages
        LaunchedEffect(message) {
            message?.let {
                snackbarHostState.showSnackbar(it)
                screenModel.clearMessage()
            }
        }

        Scaffold(
            containerColor = BackgroundDark,
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
            ) {
                // Logo Header
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoStories,
                        contentDescription = null,
                        tint = PrimaryOrange,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Manga-Reader v2",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        "PhiĂªn báº£n 2.5.0-VipPro",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }

                // Global Switches
                MoreSwitch(
                    icon = Icons.Outlined.CloudOff,
                    title = "Chá»‰ táº£i xuá»‘ng",
                    subtitle = "Chá»‰ hiá»ƒn thá»‹ truyá»‡n Ä‘Ă£ táº£i xuá»‘ng trong thÆ° viá»‡n",
                    checked = downloadedOnly,
                    onCheckedChange = { screenModel.setDownloadedOnly(it) }
                )

                MoreSwitch(
                    icon = Icons.Outlined.VisibilityOff,
                    title = "Cháº¿ Ä‘á»™ áº©n danh",
                    subtitle = "NgÆ°ng lÆ°u lá»‹ch sá»­ Ä‘á»c truyá»‡n",
                    checked = incognitoMode,
                    onCheckedChange = { screenModel.setIncognitoMode(it) }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color.White.copy(alpha = 0.05f))

                // General
                MoreItem(
                    icon = Icons.Outlined.GetApp,
                    title = "HĂ ng chá» táº£i xuá»‘ng",
                    subtitle = if (queueCount > 0) "$queueCount chÆ°Æ¡ng Ä‘ang chá»" else null,
                    onClick = { rootNavigator.push(DownloadQueueScreen()) }
                )
                
                MoreItem(
                    icon = Icons.Outlined.Label,
                    title = "Danh má»¥c",
                    onClick = { rootNavigator.push(com.example.manga_readerver2.features.library.CategoryManagerScreen()) }
                )

                MoreItem(
                    icon = Icons.Outlined.QueryStats,
                    title = "Thá»‘ng kĂª",
                    onClick = { rootNavigator.push(com.example.manga_readerver2.features.statistics.StatisticsScreen()) }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color.White.copy(alpha = 0.05f))

                // Data management
                MoreItem(
                    icon = Icons.Outlined.DeleteSweep,
                    title = "XĂ³a bá»™ nhá»› Ä‘á»‡m",
                    subtitle = "Dung lÆ°á»£ng hiá»‡n táº¡i: $cacheSize",
                    onClick = { screenModel.clearCache() }
                )
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color.White.copy(alpha = 0.05f))

                // Settings & Others
                MoreItem(
                    icon = Icons.Outlined.Settings,
                    title = "CĂ i Ä‘áº·t",
                    onClick = { rootNavigator.push(SettingsDetailScreen()) }
                )

                MoreItem(
                    icon = Icons.Outlined.VolunteerActivism,
                    title = "á»¦ng há»™ chĂºng tĂ´i",
                    onClick = { uriHandler.openUri("https://github.com/Darkrai9x") }
                )
                
                MoreItem(
                    icon = Icons.Outlined.Info,
                    title = "ThĂ´ng tin",
                    onClick = { /* Hiá»ƒn thá»‹ dialog thĂ´ng tin hoáº·c trang web */ }
                )

                MoreItem(
                    icon = Icons.Outlined.HelpOutline,
                    title = "Trá»£ giĂºp",
                    onClick = { uriHandler.openUri("https://mihon.app/docs") }
                )

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun MoreSwitch(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) },
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = if (checked) PrimaryOrange else Color.Gray, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(24.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = Color.White, fontSize = 16.sp)
                Text(subtitle, color = Color.Gray, fontSize = 12.sp)
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = PrimaryOrange,
                    checkedTrackColor = PrimaryOrange.copy(alpha = 0.4f),
                    uncheckedThumbColor = Color.Gray,
                    uncheckedTrackColor = Color.DarkGray
                )
            )
        }
    }
}

@Composable
fun MoreItem(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String? = null, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = title, tint = Color.Gray, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(24.dp))
            Column {
                Text(title, color = Color.White, fontSize = 16.sp)
                if (subtitle != null) {
                    Text(subtitle, color = Color.Gray, fontSize = 12.sp)
                }
            }
        }
    }
}

