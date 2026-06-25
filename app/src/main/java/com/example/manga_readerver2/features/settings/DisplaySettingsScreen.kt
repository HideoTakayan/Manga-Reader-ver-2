package com.example.manga_readerver2.features.settings

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.example.manga_readerver2.core.preference.DisplayPreferences
import com.example.manga_readerver2.ui.theme.BackgroundDark
import com.example.manga_readerver2.ui.theme.PrimaryOrange
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class DisplaySettingsScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val displayPreferences = remember { Injekt.get<DisplayPreferences>() }

        var dynamicColor by remember { mutableStateOf(displayPreferences.dynamicColor.get()) }
        var pureBlack by remember { mutableStateOf(displayPreferences.pureBlack.get()) }
        var appTheme by remember { mutableStateOf(displayPreferences.appTheme.get()) }

        val themeOptions = mapOf(
            "DEFAULT" to "Cam (Máº·c Ä‘á»‹nh)",
            "GREEN_APPLE" to "TĂ¡o xanh",
            "LAVENDER" to "Hoa oáº£i hÆ°Æ¡ng",
            "STRAWBERRY" to "DĂ¢u tĂ¢y",
            "MIDNIGHT_DUSK" to "Cháº¡ng váº¡ng"
        )
        var expanded by remember { mutableStateOf(false) }

        Scaffold(
            containerColor = BackgroundDark,
            topBar = {
                TopAppBar(
                    title = { Text("Hiá»ƒn thá»‹", color = Color.White, fontWeight = FontWeight.Bold) },
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
                SettingsSectionHeader(title = "Chá»§ Ä‘á» (Theme)")

                // Theme Dropdown
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.Transparent
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        Text("MĂ u chá»§ Ä‘áº¡o (App Theme)", color = Color.White, fontSize = 16.sp)
                        Text("Chá»n mĂ u sáº¯c giao diá»‡n yĂªu thĂ­ch", color = Color.Gray, fontSize = 13.sp)

                        Spacer(modifier = Modifier.height(8.dp))

                        ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = { expanded = !expanded }
                        ) {
                            OutlinedTextField(
                                value = themeOptions[appTheme] ?: "Cam (Máº·c Ä‘á»‹nh)",
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                modifier = Modifier.menuAnchor().fillMaxWidth(),
                                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = Color.Gray,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )

                            ExposedDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                themeOptions.forEach { (key, title) ->
                                    DropdownMenuItem(
                                        text = { Text(title) },
                                        onClick = {
                                            appTheme = key
                                            displayPreferences.appTheme.set(key)
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable(enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        dynamicColor = !dynamicColor
                        displayPreferences.dynamicColor.set(dynamicColor)
                    },
                    color = Color.Transparent
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "MĂ u Ä‘á»™ng (Material You)", 
                                color = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Color.White else Color.DarkGray, 
                                fontSize = 16.sp
                            )
                            Text(
                                "Äá»“ng bá»™ mĂ u sáº¯c vá»›i hĂ¬nh ná»n (Cáº§n Android 12+)", 
                                color = Color.Gray, 
                                fontSize = 13.sp
                            )
                        }
                        Switch(
                            checked = dynamicColor,
                            onCheckedChange = { 
                                dynamicColor = it
                                displayPreferences.dynamicColor.set(it)
                            },
                            enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = PrimaryOrange,
                                checkedTrackColor = PrimaryOrange.copy(alpha = 0.5f)
                            )
                        )
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth().clickable {
                        pureBlack = !pureBlack
                        displayPreferences.pureBlack.set(pureBlack)
                    },
                    color = Color.Transparent
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Cháº¿ Ä‘á»™ Äen tuyá»n (Pure Black)", color = Color.White, fontSize = 16.sp)
                            Text("Tiáº¿t kiá»‡m pin cho mĂ n hĂ¬nh OLED/AMOLED", color = Color.Gray, fontSize = 13.sp)
                        }
                        Switch(
                            checked = pureBlack,
                            onCheckedChange = { 
                                pureBlack = it
                                displayPreferences.pureBlack.set(it)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = PrimaryOrange,
                                checkedTrackColor = PrimaryOrange.copy(alpha = 0.5f)
                            )
                        )
                    }
                }
            }
        }
    }
}


