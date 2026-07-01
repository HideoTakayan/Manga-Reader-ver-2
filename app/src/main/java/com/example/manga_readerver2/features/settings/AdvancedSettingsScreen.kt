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
                    title = { Text("Nâng cao", color = Color.White, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại", tint = Color.White)
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
                SettingsSectionHeader(title = "Thông tin ứng dụng")

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
                            Text("Phiên bản", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            Text(
                                "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                                color = Color.Gray, fontSize = 13.sp
                            )
                        }
                    }
                }

                SettingsSectionHeader(title = "Gỡ lỗi")

                SettingsPreferenceItem(
                    icon = Icons.Default.BugReport,
                    title = "Thông tin ứng dụng (Hệ thống)",
                    subtitle = "Mở cài đặt ứng dụng để kiểm tra quyền, bộ nhớ",
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
                    title = "Mở ghi nhật ký hệ thống",
                    subtitle = "Dùng Logcat để xem debug logs của ứng dụng",
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            data = Uri.parse("https://developer.android.com/studio/command-line/logcat")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    }
                )

                SettingsSectionHeader(title = "Bảo trì")

                SettingsPreferenceItem(
                    icon = Icons.Default.CleaningServices,
                    title = "Xóa cache WebView (Cloudflare)",
                    subtitle = "Xóa cookie và dữ liệu WebView, buộc giải quyết lại Cloudflare",
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
                    title = { Text("Xác nhận", color = Color.White) },
                    text = { Text("Bạn có chắc muốn xóa toàn bộ dữ liệu? Thao tác này không thể hoàn tác.", color = Color.White) },
                    confirmButton = {
                        TextButton(onClick = { showClearDbDialog = false }) {
                            Text("Hủy", color = Color.Gray)
                        }
                    },
                    containerColor = BackgroundDark
                )
            }
        }
    }
}


