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
                    title = { Text("Báº£o máº­t & Quyá»n riĂªng tÆ°", color = Color.White, fontWeight = FontWeight.Bold) },
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
                PreferenceHeader("KhĂ³a á»©ng dá»¥ng")

                PreferenceSwitchItem(
                    title = "Báº­t khĂ³a á»©ng dá»¥ng",
                    subtitle = "Sá»­ dá»¥ng vĂ¢n tay/khuĂ´n máº·t hoáº·c mĂ£ PIN Ä‘á»ƒ má»Ÿ khĂ³a",
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
                    PreferenceHeader("Thá»i gian khĂ³a")
                    val timeouts = listOf(
                        0 to "KhĂ³a ngay láº­p tá»©c",
                        1 to "1 phĂºt",
                        2 to "2 phĂºt",
                        5 to "5 phĂºt",
                        10 to "10 phĂºt"
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


