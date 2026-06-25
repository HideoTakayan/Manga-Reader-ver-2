package com.example.manga_readerver2.features.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.example.manga_readerver2.BuildConfig
import com.example.manga_readerver2.ui.theme.BackgroundDark
import com.example.manga_readerver2.ui.theme.PrimaryOrange

class AdvancedSettingsScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        var showClearDbDialog by remember { mutableStateOf(false) }

        Scaffold(
            containerColor = BackgroundDark,
            topBar = {
                TopAppBar(
                    title = { Text("NĂ¢ng cao", color = Color.White, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay láº¡i", tint = Color.White)
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
                SettingsSectionHeader(title = "ThĂ´ng tin á»©ng dá»¥ng")

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.Transparent
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.Info, tint = Color.Gray, contentDescription = null, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(24.dp))
                        Column {
                            Text("PhiĂªn báº£n", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            Text(
                                "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                                color = Color.Gray, fontSize = 13.sp
                            )
                        }
                    }
                }

                SettingsSectionHeader(title = "Gá»¡ lá»—i")

                SettingsPreferenceItem(
                    icon = Icons.Default.BugReport,
                    title = "ThĂ´ng tin á»©ng dá»¥ng (Há»‡ thá»‘ng)",
                    subtitle = "Má»Ÿ cĂ i Ä‘áº·t á»©ng dá»¥ng Ä‘á»ƒ kiá»ƒm tra quyá»n, bá»™ nhá»›",
                    onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    }
                )

                SettingsPreferenceItem(
                    icon = Icons.Default.Code,
                    title = "Má»Ÿ ghi nháº­t kĂ½ há»‡ thá»‘ng",
                    subtitle = "DĂ¹ng Logcat Ä‘á»ƒ xem debug logs cá»§a á»©ng dá»¥ng",
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            data = Uri.parse("https://developer.android.com/studio/command-line/logcat")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    }
                )

                SettingsSectionHeader(title = "Báº£o trĂ¬")

                SettingsPreferenceItem(
                    icon = Icons.Default.CleaningServices,
                    title = "XĂ³a cache WebView (Cloudflare)",
                    subtitle = "XĂ³a cookie vĂ  dá»¯ liá»‡u WebView, buá»™c giáº£i quyáº¿t láº¡i Cloudflare",
                    onClick = {
                        android.webkit.WebStorage.getInstance().deleteAllData()
                        android.webkit.CookieManager.getInstance().removeAllCookies(null)
                        android.webkit.CookieManager.getInstance().flush()
                    }
                )
            }

            if (showClearDbDialog) {
                AlertDialog(
                    onDismissRequest = { showClearDbDialog = false },
                    title = { Text("XĂ¡c nháº­n", color = Color.White) },
                    text = { Text("Báº¡n cĂ³ cháº¯c muá»‘n xĂ³a toĂ n bá»™ dá»¯ liá»‡u? Thao tĂ¡c nĂ y khĂ´ng thá»ƒ hoĂ n tĂ¡c.", color = Color.White) },
                    confirmButton = {
                        TextButton(onClick = { showClearDbDialog = false }) {
                            Text("Há»§y", color = Color.Gray)
                        }
                    },
                    containerColor = BackgroundDark
                )
            }
        }
    }
}


