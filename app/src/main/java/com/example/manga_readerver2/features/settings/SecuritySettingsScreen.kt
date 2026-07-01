package com.example.manga_readerver2.features.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.example.manga_readerver2.core.security.AppLockManager
import com.example.manga_readerver2.core.security.SecurityPreferences
import com.example.manga_readerver2.ui.theme.BackgroundDark
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class SecuritySettingsScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val securityPreferences = remember { Injekt.get<SecurityPreferences>() }

        var appLockEnabled by remember { mutableStateOf(securityPreferences.appLockEnabled.get()) }
        var appLockTimeout by remember { mutableStateOf(securityPreferences.appLockTimeout.get()) }

        Scaffold(
            containerColor = BackgroundDark,
            topBar = {
                TopAppBar(
                    title = { Text("Bảo mật & Quyền riêng tư", color = Color.White, fontWeight = FontWeight.Bold) },
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
                PreferenceHeader("Khóa ứng dụng")

                PreferenceSwitchItem(
                    title = "Bật khóa ứng dụng",
                    subtitle = "Sử dụng vân tay/khuôn mặt hoặc mã PIN để mở khóa",
                    checked = appLockEnabled,
                    onCheckedChange = { isEnabled ->
                        appLockEnabled = isEnabled
                        securityPreferences.appLockEnabled.set(isEnabled)
                        if (!isEnabled) {
                            // unlock instantly
                            AppLockManager.setLocked(false)
                        }
                    }
                )

                if (appLockEnabled) {
                    PreferenceHeader("Thời gian khóa")
                    val timeouts = listOf(
                        0 to "Khóa ngay lập tức",
                        1 to "1 phút",
                        2 to "2 phút",
                        5 to "5 phút",
                        10 to "10 phút"
                    )

                    timeouts.forEach { (minutes, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    appLockTimeout = minutes
                                    securityPreferences.appLockTimeout.set(minutes)
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = appLockTimeout == minutes,
                                onClick = {
                                    appLockTimeout = minutes
                                    securityPreferences.appLockTimeout.set(minutes)
                                }
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(label, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}


