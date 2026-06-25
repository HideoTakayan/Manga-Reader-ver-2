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
                    title = { Text("Duyá»‡t", color = Color.White, fontWeight = FontWeight.Bold) },
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
                    "Tiá»‡n Ă­ch má»Ÿ rá»™ng",
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
                    "LÆ°u Ă½: CĂ i Ä‘áº·t ná»™i bá»™ sáº½ náº¡p extension trá»±c tiáº¿p tá»« á»©ng dá»¥ng, khĂ´ng cáº§n cĂ i Ä‘áº·t qua há»‡ thá»‘ng Android. Äiá»u nĂ y giĂºp quĂ¡ trĂ¬nh cĂ i Ä‘áº·t mÆ°á»£t mĂ  hÆ¡n nhÆ°ng cĂ³ thá»ƒ khĂ´ng tÆ°Æ¡ng thĂ­ch vá»›i má»™t sá»‘ dĂ²ng mĂ¡y cÅ©.",
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
        val types = listOf("TrĂ¬nh cĂ i Ä‘áº·t há»‡ thá»‘ng", "TrĂ¬nh cĂ i Ä‘áº·t ná»™i bá»™ (Mihon Style)")
        
        ListItem(
            headlineContent = { Text("TrĂ¬nh cĂ i Ä‘áº·t pháº§n má»Ÿ rá»™ng", color = Color.White) },
            supportingContent = { Text(types[selectedType], color = Color.Gray) },
            modifier = Modifier.clickable { showDialog = true },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )

        if (showDialog) {
            AlertDialog(
                onDismissRequest = { showDialog = false },
                title = { Text("Chá»n trĂ¬nh cĂ i Ä‘áº·t") },
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


