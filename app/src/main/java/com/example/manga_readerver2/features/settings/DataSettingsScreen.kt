package com.example.manga_readerver2.features.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.example.manga_readerver2.core.preference.GeneralPreferences
import com.example.manga_readerver2.ui.theme.BackgroundDark
import com.example.manga_readerver2.ui.theme.PrimaryOrange
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DataSettingsScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { SettingsScreenModel() }
        
        val context = LocalContext.current
        val cacheSize by screenModel.cacheSize.collectAsState()
        val message by screenModel.message.collectAsState()
        val backupPreview by screenModel.backupPreview.collectAsState()
        
        val generalPreferences = remember { Injekt.get<GeneralPreferences>() }
        val autoClear by generalPreferences.autoClearCache.asFlow().collectAsState(initial = generalPreferences.autoClearCache.get())
        val maxSize by generalPreferences.maxCacheSize.asFlow().collectAsState(initial = generalPreferences.maxCacheSize.get())

        // Hiá»ƒn thá»‹ dialog preview trÆ°á»›c khi restore
        backupPreview?.let { preview ->
            AlertDialog(
                onDismissRequest = { screenModel.dismissBackupPreview() },
                title = {
                    Text(
                        if (preview.isValid) "XĂ¡c nháº­n khĂ´i phá»¥c" else "File backup lá»—i",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    if (preview.isValid) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("ThĂ´ng tin file backup:", color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text("â€¢ ${preview.mangaCount} truyá»‡n", color = Color.Gray)
                            Text("â€¢ ${preview.chapterCount} chÆ°Æ¡ng", color = Color.Gray)
                            Text("â€¢ ${preview.categoryCount} danh má»¥c", color = Color.Gray)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Dá»¯ liá»‡u hiá»‡n táº¡i sáº½ Ä‘Æ°á»£c giá»¯ nguyĂªn, truyá»‡n má»›i tá»« backup sáº½ Ä‘Æ°á»£c thĂªm vĂ o.",
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        }
                    } else {
                        Text(
                            preview.errorMessage ?: "File backup khĂ´ng há»£p lá»‡",
                            color = Color(0xFFFF6B6B)
                        )
                    }
                },
                confirmButton = {
                    if (preview.isValid) {
                        TextButton(onClick = { screenModel.confirmRestoreBackup() }) {
                            Text("KhĂ´i phá»¥c", color = PrimaryOrange)
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { screenModel.dismissBackupPreview() }) {
                        Text("Há»§y", color = Color.Gray)
                    }
                },
                containerColor = Color(0xFF2B2B2B)
            )
        }

        LaunchedEffect(message) {
            message?.let {
                Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                screenModel.clearMessage()
            }
        }

        // Launcher for Creating Backup (.json.gz)
        val createBackupLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/gzip")
        ) { uri ->
            if (uri != null) {
                screenModel.createBackup(uri)
            }
        }

        // Launcher for Restoring Backup
        val restoreBackupLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri != null) {
                screenModel.previewRestoreBackup(uri)
            }
        }

        Scaffold(
            containerColor = BackgroundDark,
            topBar = {
                TopAppBar(
                    title = { Text("Dá»¯ liá»‡u vĂ  Sao lÆ°u", color = Color.White, fontWeight = FontWeight.Bold) },
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
                SettingsSectionHeader(title = "Sao lÆ°u")

                SettingsPreferenceItem(
                    icon = Icons.Default.Backup,
                    title = "Táº¡o báº£n sao lÆ°u",
                    subtitle = "CĂ³ thá»ƒ dĂ¹ng Ä‘á»ƒ khĂ´i phá»¥c thÆ° viá»‡n á»Ÿ cĂ¡c thiáº¿t bá»‹ khĂ¡c",
                    onClick = {
                        val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault())
                        val fileName = "manga_reader_backup_${dateFormat.format(Date())}.json.gz"
                        createBackupLauncher.launch(fileName)
                    }
                )

                SettingsPreferenceItem(
                    icon = Icons.Default.Restore,
                    title = "KhĂ´i phá»¥c báº£n sao lÆ°u",
                    subtitle = "KhĂ´i phá»¥c thÆ° viá»‡n tá»« file sao lÆ°u Ä‘Ă£ táº¡o",
                    onClick = {
                        restoreBackupLauncher.launch(arrayOf("application/gzip", "application/octet-stream"))
                    }
                )

                val autoBackup by screenModel.autoBackup.collectAsState(initial = false)
                val autoBackupFrequency by screenModel.autoBackupFrequency.collectAsState(initial = 24)
                val maxAutoBackups by screenModel.maxAutoBackups.collectAsState(initial = 5)

                SettingsSwitchItem(
                    title = "Tá»± Ä‘á»™ng sao lÆ°u",
                    subtitle = "Sao lÆ°u Ä‘á»‹nh ká»³ thÆ° viá»‡n á»Ÿ cháº¿ Ä‘á»™ ná»n",
                    checked = autoBackup,
                    onCheckedChange = { screenModel.setAutoBackup(it) }
                )

                if (autoBackup) {
                    Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                        Text("Táº§n suáº¥t sao lÆ°u", color = Color.White, fontSize = 14.sp)
                        val frequencyOptions = listOf(12, 24, 48, 168)
                        val frequencyLabels = listOf("Má»—i 12 giá»", "Má»—i ngĂ y", "Má»—i 2 ngĂ y", "Má»—i tuáº§n")
                        val selectedIndex = frequencyOptions.indexOf(autoBackupFrequency).takeIf { it >= 0 } ?: 1
                        
                        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            frequencyOptions.forEachIndexed { index, value ->
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { screenModel.setAutoBackupFrequency(value) }) {
                                    RadioButton(
                                        selected = selectedIndex == index,
                                        onClick = { screenModel.setAutoBackupFrequency(value) },
                                        colors = RadioButtonDefaults.colors(selectedColor = PrimaryOrange)
                                    )
                                    Text(frequencyLabels[index], color = Color.White, fontSize = 12.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text("Giá»›i háº¡n sá»‘ báº£n sao lÆ°u: $maxAutoBackups", color = Color.White, fontSize = 14.sp)
                        Slider(
                            value = maxAutoBackups.toFloat(),
                            onValueChange = { screenModel.setMaxAutoBackups(it.toInt()) },
                            valueRange = 1f..10f,
                            steps = 8,
                            colors = SliderDefaults.colors(
                                thumbColor = PrimaryOrange,
                                activeTrackColor = PrimaryOrange
                            )
                        )
                        
                        Text(
                            "File Ä‘Æ°á»£c lÆ°u táº¡i: ThÆ° má»¥c Data cá»§a á»©ng dá»¥ng /files/backups/",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }

                HorizontalDivider(color = Color.DarkGray, modifier = Modifier.padding(vertical = 8.dp))

                SettingsSectionHeader(title = "Bá»™ nhá»›")

                SettingsPreferenceItem(
                    icon = Icons.Default.Cached,
                    title = "XĂ³a bá»™ nhá»› Ä‘á»‡m hĂ¬nh áº£nh",
                    subtitle = "Sá»­ dá»¥ng: $cacheSize",
                    onClick = {
                        screenModel.clearCache()
                        Toast.makeText(context, "ÄĂ£ xĂ³a bá»™ nhá»› Ä‘á»‡m", Toast.LENGTH_SHORT).show()
                    }
                )

                SettingsSwitchItem(
                    title = "Tá»± Ä‘á»™ng dá»n dáº¹p bá»™ nhá»› Ä‘á»‡m",
                    subtitle = "Tá»± Ä‘á»™ng xĂ³a khi vÆ°á»£t quĂ¡ giá»›i háº¡n",
                    checked = autoClear,
                    onCheckedChange = { 
                        generalPreferences.autoClearCache.set(it)
                        com.example.manga_readerver2.core.updater.CacheClearJob.setupTask(context, it)
                    }
                )

                if (autoClear) {
                    Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                        Text("Giá»›i háº¡n bá»™ nhá»›: ${maxSize} MB", color = Color.White, fontSize = 14.sp)
                        Slider(
                            value = maxSize.toFloat(),
                            onValueChange = { generalPreferences.maxCacheSize.set(it.toInt()) },
                            valueRange = 100f..2000f,
                            steps = 19,
                            colors = SliderDefaults.colors(
                                thumbColor = PrimaryOrange,
                                activeTrackColor = PrimaryOrange
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsSwitchItem(
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
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text(subtitle, color = Color.Gray, fontSize = 13.sp)
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(checkedThumbColor = PrimaryOrange, checkedTrackColor = PrimaryOrange.copy(alpha = 0.5f))
            )
        }
    }
}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        color = PrimaryOrange,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
    )
}

@Composable
fun SettingsPreferenceItem(
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


