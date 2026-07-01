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
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.example.manga_readerver2.core.source.SourcePreferences
import com.example.manga_readerver2.ui.theme.BackgroundDark
import com.example.manga_readerver2.ui.theme.PrimaryOrange
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class BrowseSettingsScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val sourcePreferences: SourcePreferences = Injekt.get()
        
        val installerType by sourcePreferences.extensionInstaller.asFlow()
            .collectAsState(initial = sourcePreferences.extensionInstaller.get())

        Scaffold(
            containerColor = BackgroundDark,
            topBar = {
                TopAppBar(
                    title = { Text("Duyệt", color = Color.White, fontWeight = FontWeight.Bold) },
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
                Text(
                    "Tiện ích mở rộng",
                    color = PrimaryOrange,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                )

                InstallerTypeSelector(
                    selectedType = installerType,
                    onTypeSelected = { sourcePreferences.extensionInstaller.set(it) }
                )
                
                Text(
                    "Lưu ý: Cài đặt nội bộ sẽ nạp extension trực tiếp từ ứng dụng, không cần cài đặt qua hệ thống Android. Điều này giúp quá trình cài đặt mượt mà hơn nhưng có thể không tương thích với một số dòng máy cũ.",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
            }
        }
    }

    @Composable
    fun InstallerTypeSelector(
        selectedType: Int,
        onTypeSelected: (Int) -> Unit
    ) {
        var showDialog by remember { mutableStateOf(false) }
        val types = listOf("Trình cài đặt hệ thống", "Trình cài đặt nội bộ (Mihon Style)")
        
        ListItem(
            headlineContent = { Text("Trình cài đặt phần mở rộng", color = Color.White) },
            supportingContent = { Text(types[selectedType], color = Color.Gray) },
            modifier = Modifier.clickable { showDialog = true },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )

        if (showDialog) {
            AlertDialog(
                onDismissRequest = { showDialog = false },
                title = { Text("Chọn trình cài đặt") },
                text = {
                    Column {
                        types.forEachIndexed { index, type ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { 
                                        onTypeSelected(index)
                                        showDialog = false
                                    }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedType == index,
                                    onClick = { 
                                        onTypeSelected(index)
                                        showDialog = false
                                    },
                                    colors = RadioButtonDefaults.colors(selectedColor = PrimaryOrange)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(type)
                            }
                        }
                    }
                },
                confirmButton = {}
            )
        }
    }
}


