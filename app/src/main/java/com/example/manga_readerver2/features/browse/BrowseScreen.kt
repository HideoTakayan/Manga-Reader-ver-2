package com.example.manga_readerver2.features.browse

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.activity.compose.BackHandler
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import logcat.LogPriority
import logcat.logcat
import com.example.manga_readerver2.core.source.Extension
import com.example.manga_readerver2.core.source.InstallStep
import com.example.manga_readerver2.ui.theme.BackgroundDark
import com.example.manga_readerver2.ui.theme.PrimaryOrange
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

fun isJsExtension(ext: Extension): Boolean {
    return when (ext) {
        is Extension.Installed -> ext.pkgName.startsWith("js.extension.") || ext.sources.any { it::class.java.simpleName == "JsSource" }
        is Extension.Available -> ext.pkgName.startsWith("js.extension.") || ext.apkName.endsWith(".zip", ignoreCase = true)
        is Extension.Untrusted -> ext.pkgName.startsWith("js.extension.")
    }
}

class BrowseScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val rootNavigator = navigator.parent ?: navigator
        val screenModel = rememberScreenModel { BrowseScreenModel() }
        val lifecycleOwner = LocalLifecycleOwner.current
        val installedExts by screenModel.installedExtensions.collectAsState()
        val untrustedExts by screenModel.untrustedExtensions.collectAsState()
        val availableExts by screenModel.availableExtensions.collectAsState()
        val installSteps by screenModel.installSteps.collectAsState()
        val isRefreshing by screenModel.isRefreshing.collectAsState()
        val pinnedSources by screenModel.pinnedSources.collectAsState()
        val enabledLangs by screenModel.enabledLanguages.collectAsState()

        var selectedTab by remember { mutableIntStateOf(0) }
        val tabs = listOf("Nguá»“n", "Pháº§n má»Ÿ rá»™ng")

        // TĂ¬m kiáº¿m local cho tab Pháº§n má»Ÿ rá»™ng
        var isSearching by remember { mutableStateOf(false) }
        var searchQuery by remember { mutableStateOf("") }

        // Bá»™ lá»c ná»™i dung: 0 - Táº¥t cáº£, 1 - Manga (APK), 2 - Truyá»‡n chá»¯ (JS)
        var contentTypeFilter by remember { mutableIntStateOf(0) }
        
        val folderPickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree()
        ) { uri ->
            uri?.let { screenModel.updateLocalSourceUri(it.toString()) }
        }
        
        var showLangDialog by remember { mutableStateOf(false) }
        var selectedExtensionForDetail by remember { mutableStateOf<Extension?>(null) }


        val snackbarHostState = remember { SnackbarHostState() }

        DisposableEffect(lifecycleOwner, screenModel) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    screenModel.onScreenResumed()
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }

        if (isSearching) {
            BackHandler {
                isSearching = false
                searchQuery = ""
            }
        }
        
        val processedInstallSteps = remember { mutableSetOf<String>() }
        
        LaunchedEffect(installSteps) {
            installSteps.forEach { (pkg, step) ->
                if (!processedInstallSteps.contains(pkg + step.name)) {
                    processedInstallSteps.add(pkg + step.name)
                    when (step) {
                        InstallStep.SystemInstallStarted -> snackbarHostState.showSnackbar(
                            message = "Vui lĂ²ng hoĂ n táº¥t cĂ i Ä‘áº·t trong há»™p thoáº¡i há»‡ thá»‘ng",
                            duration = SnackbarDuration.Short
                        )
                        InstallStep.Installed -> snackbarHostState.showSnackbar(
                            message = "ÄĂ£ cĂ i Ä‘áº·t â€” chuyá»ƒn sang tab Nguá»“n Ä‘á»ƒ sá»­ dá»¥ng",
                            duration = SnackbarDuration.Short
                        )
                        InstallStep.Error -> snackbarHostState.showSnackbar(
                            message = "CĂ i Ä‘áº·t tháº¥t báº¡i â€” kiá»ƒm tra logcat Ä‘á»ƒ biáº¿t chi tiáº¿t",
                            duration = SnackbarDuration.Long
                        )
                        else -> {}
                    }
                }
            }
        }

        Scaffold(
            containerColor = BackgroundDark,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                if (isSearching && selectedTab == 1) {
                    SearchAppBar(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        onCloseSearch = {
                            isSearching = false
                            searchQuery = ""
                        }
                    )
                } else {
                    TopAppBar(
                        title = { Text("Duyá»‡t", color = Color.White, fontWeight = FontWeight.Bold) },
                        actions = {
                            IconButton(onClick = { 
                                if (selectedTab == 0) {
                                    rootNavigator.push(GlobalSearchScreen()) 
                                } else {
                                    isSearching = true
                                }
                            }) {
                                Icon(Icons.Default.Search, contentDescription = "TĂ¬m kiáº¿m", tint = Color.White)
                            }
                            IconButton(onClick = { showLangDialog = true }) {
                                Icon(Icons.Default.Language, contentDescription = "NgĂ´n ngá»¯", tint = Color.White)
                            }
                            IconButton(onClick = { 
                                if (selectedTab == 0) {
                                    folderPickerLauncher.launch(null)
                                } else {
                                    screenModel.refreshExtensions()
                                }
                            }) {
                                Icon(
                                    if (selectedTab == 0) Icons.Default.FolderOpen else Icons.Default.Refresh, 
                                    contentDescription = if (selectedTab == 0) "Local Source" else "LĂ m má»›i", 
                                    tint = Color.White
                                )
                            }
                            IconButton(onClick = { rootNavigator.push(ExtensionRepoScreen()) }) {
                                Icon(Icons.Default.SettingsInputComponent, contentDescription = "Quáº£n lĂ½ nguá»“n", tint = Color.White)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark)
                    )
                }
            }
        ) { paddingValues ->
            Column(modifier = Modifier.padding(paddingValues)) {
                // Tab chá»n Nguá»“n hoáº·c Pháº§n má»Ÿ rá»™ng
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = BackgroundDark,
                    contentColor = PrimaryOrange,
                    divider = {}
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = {
                                Text(
                                    text = title,
                                    fontSize = 16.sp,
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                    }
                }

                // Bá»™ lá»c loáº¡i ná»™i dung (Manga/Truyá»‡n chá»¯)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = contentTypeFilter == 0,
                        onClick = { contentTypeFilter = 0 },
                        label = { Text("Táº¥t cáº£") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PrimaryOrange.copy(alpha = 0.2f),
                            selectedLabelColor = PrimaryOrange
                        )
                    )
                    FilterChip(
                        selected = contentTypeFilter == 1,
                        onClick = { contentTypeFilter = 1 },
                        label = { Text("Manga (APK)") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PrimaryOrange.copy(alpha = 0.2f),
                            selectedLabelColor = PrimaryOrange
                        )
                    )
                    FilterChip(
                        selected = contentTypeFilter == 2,
                        onClick = { contentTypeFilter = 2 },
                        label = { Text("Truyá»‡n chá»¯ (JS)") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF1565C0).copy(alpha = 0.3f),
                            selectedLabelColor = Color(0xFF64B5F6)
                        )
                    )
                }

                Box(modifier = Modifier.weight(1f)) {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center),
                            color = PrimaryOrange
                        )
                    }

                    when (selectedTab) {
                        0 -> SourcesTab(
                            rootNavigator, 
                            installedExts, 
                            contentTypeFilter, 
                            pinnedSources,
                            enabledLangs,
                            onTogglePin = { screenModel.togglePin(it) }
                        )
                        1 -> ExtensionsTab(
                            installedExts, 
                            untrustedExts,
                            availableExts, 
                            installSteps, 
                            contentTypeFilter,
                            enabledLangs,
                            query = searchQuery,
                            onInstall = { screenModel.installExtension(it) },
                            onTrust = { screenModel.trustExtension(it) },
                            onUninstall = { screenModel.uninstallExtension(it) },
                            onDetail = { selectedExtensionForDetail = it }
                        )
                    }
                }
            }
            
            if (showLangDialog) {
                LanguageFilterDialog(
                    selectedLangs = enabledLangs,
                    onDismiss = { showLangDialog = false },
                    onLangsChanged = { screenModel.setLanguages(it) }
                )
            }

            if (selectedExtensionForDetail != null) {
                ExtensionDetailsDialog(
                    extension = selectedExtensionForDetail!!,
                    onDismiss = { selectedExtensionForDetail = null },
                    onUninstall = { screenModel.uninstallExtension(selectedExtensionForDetail!!.pkgName) }
                )
            }
        }
    }
}

@Composable
fun LanguageFilterDialog(
    selectedLangs: Set<String>,
    onDismiss: () -> Unit,
    onLangsChanged: (Set<String>) -> Unit
) {
    val allLangs = listOf(
        "all" to "Táº¥t cáº£",
        "vi" to "Tiáº¿ng Viá»‡t", 
        "en" to "English", 
        "ja" to "Japanese",
        "zh" to "Chinese",
        "ko" to "Korean"
    )
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("NgĂ´n ngá»¯ nguá»“n", color = Color.White) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                allLangs.forEach { (code, name) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val newLangs = selectedLangs.toMutableSet()
                                if (code == "all") {
                                    onLangsChanged(setOf("all"))
                                    return@clickable
                                }
                                
                                newLangs.remove("all")
                                if (newLangs.contains(code)) {
                                    if (newLangs.size > 1) newLangs.remove(code)
                                    else newLangs.add("all") // Fallback to all
                                } else {
                                    newLangs.add(code)
                                }
                                onLangsChanged(newLangs)
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = selectedLangs.contains(code),
                            onCheckedChange = null,
                            colors = CheckboxDefaults.colors(checkedColor = PrimaryOrange)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(name, color = Color.White)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Xong", color = PrimaryOrange)
            }
        },
        containerColor = BackgroundDark
    )
}

@Composable
fun SourcesTab(
    navigator: cafe.adriel.voyager.navigator.Navigator, 
    extensions: List<Extension.Installed>, 
    filter: Int,
    pinnedSources: Set<String>,
    enabledLangs: Set<String>,
    onTogglePin: (Long) -> Unit
) {
    fun normalizeLang(code: String?): String {
        if (code.isNullOrBlank()) return "all"
        val normalized = code.trim().lowercase()
        return when {
            normalized == "global" || normalized == "táº¥t cáº£" -> "all"
            normalized == "vi" || normalized.contains("viá»‡t") -> "vi"
            normalized == "en" || normalized.contains("english") || normalized.contains("anh") -> "en"
            normalized.contains("_") -> normalized.substringBefore("_")
            normalized.contains("-") -> normalized.substringBefore("-")
            else -> normalized
        }
    }

    val normalizedEnabledLangs = enabledLangs.map { normalizeLang(it) }.toSet()
    
    val localSource = remember { Injekt.get<com.example.manga_readerver2.core.source.LocalSource>() }
    val fakeLocalExt = remember(localSource) {
        Extension.Installed(
            name = "Local source",
            pkgName = "local.source",
            versionName = "1.0",
            versionCode = 1,
            libVersion = 1.0,
            lang = "other",
            isNsfw = false,
            pkgFactory = null,
            sources = listOf(localSource),
            icon = null,
            hasUpdate = false,
            isObsolete = false,
            isShared = false,
            repoUrl = null
        )
    }
    
    val sourceWithExt = listOf(localSource to fakeLocalExt) + extensions.flatMap { ext -> ext.sources.map { source -> source to ext } }

    fun matchType(ext: Extension.Installed): Boolean {
        val isJs = isJsExtension(ext)
        return when (filter) {
            1 -> !isJs  // "Manga (APK)": chá»‰ hiá»‡n APK, áº©n JS
            2 -> isJs   // "Truyá»‡n chá»¯ (JS)": chá»‰ hiá»‡n JS
            else -> true // "Táº¥t cáº£": hiá»‡n táº¥t cáº£
        }
    }

    fun matchLang(source: eu.kanade.tachiyomi.source.Source): Boolean {
        if (normalizedEnabledLangs.isEmpty()) return true
        val sourceLang = normalizeLang(source.lang)
        return sourceLang == "all" || normalizedEnabledLangs.contains("all") || normalizedEnabledLangs.contains(sourceLang)
    }

    val allSources = sourceWithExt.filter { (source, ext) ->
        matchType(ext) && matchLang(source)
    }

    val hiddenByTypeCount = sourceWithExt.count { (_, ext) -> !matchType(ext) }
    val hiddenByLanguageCount = sourceWithExt.count { (source, ext) -> matchType(ext) && !matchLang(source) }

    val pinnedList = allSources.filter { pinnedSources.contains(it.first.id.toString()) }
    val unpinnedGrouped = allSources
        .filter { !pinnedSources.contains(it.first.id.toString()) }
        .groupBy { it.first.lang }
        .toSortedMap()

    if (allSources.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("ChÆ°a cĂ³ nguá»“n nĂ o phĂ¹ há»£p", color = Color.Gray)
                if (hiddenByTypeCount > 0 || hiddenByLanguageCount > 0) {
                    Spacer(modifier = Modifier.height(6.dp))
                    val reasons = buildList {
                        if (hiddenByLanguageCount > 0) add("$hiddenByLanguageCount nguá»“n bá»‹ áº©n theo ngĂ´n ngá»¯")
                        if (hiddenByTypeCount > 0) add("$hiddenByTypeCount nguá»“n bá»‹ áº©n theo loáº¡i ná»™i dung")
                    }.joinToString(" â€¢ ")
                    Text(
                        text = reasons,
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }
            }
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(bottom = 16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            if (hiddenByTypeCount > 0 || hiddenByLanguageCount > 0) {
                item(key = "hidden_hint") {
                    val reasons = buildList {
                        if (hiddenByLanguageCount > 0) add("$hiddenByLanguageCount bá»‹ áº©n theo ngĂ´n ngá»¯")
                        if (hiddenByTypeCount > 0) add("$hiddenByTypeCount bá»‹ áº©n theo loáº¡i ná»™i dung")
                    }.joinToString(" â€¢ ")
                    Text(
                        text = "Äang áº©n: $reasons",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            if (pinnedList.isNotEmpty()) {
                item(key = "header_pinned") {
                    Text(
                        "ÄĂ£ ghim", 
                        color = PrimaryOrange, 
                        fontSize = 12.sp, 
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
                items(pinnedList, key = { "pinned_${it.first.id}_${it.second.pkgName}_${it.first.name}" }) { (source, ext) ->
                    SourceItem(navigator, source, ext, true, onTogglePin)
                }
            }

            unpinnedGrouped.forEach { (lang, sources) ->
                val langName = when (lang.lowercase()) {
                    "vi" -> "Tiáº¿ng Viá»‡t"
                    "en" -> "English"
                    "all" -> "Äa ngĂ´n ngá»¯"
                    else -> lang.uppercase()
                }

                item(key = "header_$lang") {
                    Text(
                        text = langName,
                        color = PrimaryOrange,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }

                items(sources, key = { "source_${it.first.id}_${it.second.pkgName}_${it.first.name}" }) { (source, ext) ->
                    SourceItem(navigator, source, ext, false, onTogglePin)
                }
            }
        }
    }
}

@Composable
fun SourceItem(
    navigator: cafe.adriel.voyager.navigator.Navigator,
    source: eu.kanade.tachiyomi.source.Source,
    ext: Extension.Installed,
    isPinned: Boolean,
    onTogglePin: (Long) -> Unit
) {
    val isJsSource = isJsExtension(ext)
    val isLocal = ext.pkgName == "local.source"
    ListItem(
        headlineContent = { 
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(source.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                if (isJsSource || isLocal) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = if (isLocal) Color(0xFF4CAF50).copy(alpha = 0.25f) else Color(0xFF1565C0).copy(alpha = 0.25f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = if (isLocal) "MĂY" else "JS",
                            color = if (isLocal) Color(0xFFA5D6A7) else Color(0xFF90CAF9),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        },
        supportingContent = { 
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = source.lang.uppercase(),
                    color = Color.Gray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(" â€¢ ", color = Color.Gray, fontSize = 12.sp)
                Text(
                    if (isLocal) "THÆ¯ Má»¤C" else if (isJsSource) "TRUYá»†N CHá»® (JS)" else "MANGA (APK)",
                    color = if (isLocal) Color(0xFF81C784) else if (isJsSource) Color(0xFF64B5F6) else Color.Gray,
                    fontSize = 12.sp
                )
            }
        },
        leadingContent = {
            Box(
                modifier = Modifier.size(40.dp),
                contentAlignment = Alignment.Center
            ) {
                if (ext.icon != null) {
                    AsyncImage(
                        model = ext.icon,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                if (isJsSource) Color(0xFF1565C0).copy(alpha = 0.3f) else Color.DarkGray,
                                RoundedCornerShape(8.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (isJsSource) Icons.Default.Code else Icons.Default.Public,
                            tint = if (isJsSource) Color(0xFF64B5F6) else Color.LightGray,
                            contentDescription = null
                        )
                    }
                }
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = { 
                        navigator.push(CatalogueScreen(source.id, source.name, latest = true))
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = PrimaryOrange)
                ) {
                    Text("Má»I NHáº¤T", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                }
                IconButton(onClick = { onTogglePin(source.id) }) {
                    Icon(
                        if (isPinned) Icons.Default.PushPin else Icons.Outlined.PushPin, 
                        tint = if (isPinned) PrimaryOrange else Color.Gray, 
                        modifier = Modifier.size(20.dp), 
                        contentDescription = null
                    )
                }
            }
        },
        modifier = Modifier.clickable { 
            navigator.push(CatalogueScreen(source.id, source.name, latest = false))
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
fun ExtensionsTab(
    installed: List<Extension.Installed>,
    untrusted: List<Extension.Untrusted>,
    available: List<Extension.Available>,
    installSteps: Map<String, InstallStep>,
    filter: Int,
    enabledLangs: Set<String>,
    query: String,
    onInstall: (Extension.Available) -> Unit,
    onTrust: (Extension.Untrusted) -> Unit,
    onUninstall: (String) -> Unit,
    onDetail: (Extension) -> Unit
) {
    // Untrusted extensions
    val filteredUntrusted = untrusted.filter { ext ->
        val isJs = isJsExtension(ext)
        val matchType = when (filter) {
            1 -> !isJs // APKs
            2 -> isJs // JS never untrusted for now, but technically we should filter properly
            else -> true
        }
        val matchQuery = query.isEmpty() || ext.name.contains(query, ignoreCase = true) || ext.pkgName.contains(query, ignoreCase = true)
        matchType && matchQuery
    }

    // Installed extensions
    val filteredInstalled = installed.filter { ext ->
        val isJs = isJsExtension(ext)
        val matchType = when (filter) {
            1 -> !isJs
            2 -> isJs
            else -> true
        }
        val matchQuery = query.isEmpty() || ext.name.contains(query, ignoreCase = true) || ext.pkgName.contains(query, ignoreCase = true)
        matchType && matchQuery
    }.sortedBy { it.name }
    
    // Available extensions grouped by language
    val filteredAvailable = available.filter { avail ->
        val isJs = isJsExtension(avail)
        val matchType = when (filter) {
            1 -> !isJs
            2 -> isJs
            else -> true
        }
        // lang='all' luĂ´n hiá»‡n; náº¿u user chá»n "all" thĂ¬ táº¥t cáº£ pass
        val matchLang = avail.lang == "all" || enabledLangs.contains("all") || enabledLangs.contains(avail.lang)
        val matchQuery = query.isEmpty() || avail.name.contains(query, ignoreCase = true) || avail.pkgName.contains(query, ignoreCase = true)
        installed.none { it.pkgName == avail.pkgName } && untrusted.none { it.pkgName == avail.pkgName } && matchType && matchLang && matchQuery
    }.groupBy { it.lang }.toSortedMap()

    LazyColumn(
        contentPadding = PaddingValues(bottom = 16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        if (filteredUntrusted.isNotEmpty()) {
            item(key = "header_untrusted") {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        "CHÆ¯A TIN Cáº¬Y (${filteredUntrusted.size})",
                        color = Color.Red,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Nháº¥n TIN Cáº¬Y Ä‘á»ƒ nguá»“n APK xuáº¥t hiá»‡n trong tab Nguá»“n.",
                        color = Color.Yellow,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    // NĂºt Trust All
                    TextButton(
                        onClick = { filteredUntrusted.forEach { onTrust(it) } },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFFF9800))
                    ) {
                        Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Trust táº¥t cáº£ (${filteredUntrusted.size})", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
            items(filteredUntrusted, key = { "untrusted_${it.pkgName}" }) { ext ->
                ExtensionItem(
                    extension = ext,
                    installStep = InstallStep.Pending,
                    isUpdate = false,
                    onClick = { onTrust(ext) },
                    onOpenDetails = { onDetail(ext) },
                    trustLabel = "TIN Cáº¬Y"
                )
            }
        }

        if (filteredInstalled.isNotEmpty()) {
            item(key = "header_installed") {
                Text(
                    "ÄĂ£ cĂ i Ä‘áº·t (${filteredInstalled.size})", 
                    color = PrimaryOrange, 
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
            items(filteredInstalled, key = { "installed_${it.pkgName}" }) { ext ->
                val availableVersion = available.find { it.pkgName == ext.pkgName }
                val hasUpdate = availableVersion != null && availableVersion.versionCode > ext.versionCode
                
                ExtensionItem(
                    extension = ext,
                    installStep = if (hasUpdate) installSteps[ext.pkgName] ?: InstallStep.Pending else null,
                    isUpdate = hasUpdate,
                    onClick = if (hasUpdate) { { onInstall(availableVersion!!) } } else { { onDetail(ext) } },
                    onUninstall = { onUninstall(ext.pkgName) },
                    onOpenDetails = { onDetail(ext) }
                )
            }
        }

        filteredAvailable.forEach { (lang, extensions) ->
            val langName = when (lang.lowercase()) {
                "vi" -> "Tiáº¿ng Viá»‡t"
                "en" -> "English"
                "all" -> "Äa ngĂ´n ngá»¯"
                "ja" -> "Japanese"
                "zh" -> "Chinese"
                else -> lang.uppercase()
            }
            
            item(key = "header_$lang") {
                Text(
                    text = langName,
                    color = PrimaryOrange,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }

            items(extensions, key = { "available_${it.pkgName}" }) { ext ->
                val step = installSteps[ext.pkgName] ?: InstallStep.Pending
                ExtensionItem(
                    extension = ext,
                    installStep = step,
                    isUpdate = false,
                    onClick = { onDetail(ext) },
                    onActionClick = { onInstall(ext) },
                    onOpenDetails = { onDetail(ext) }
                )
            }
        }
    }
}


@Composable
fun ExtensionItem(
    extension: Extension,
    installStep: InstallStep?,
    isUpdate: Boolean = false,
    trustLabel: String? = null,
    onClick: (() -> Unit)?,
    onActionClick: (() -> Unit)? = null,
    onUninstall: (() -> Unit)? = null,
    onOpenDetails: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }

    val name = extension.name
    val version = extension.versionName
    val lang = extension.lang
    val isNsfw = extension.isNsfw
    val icon = (extension as? Extension.Installed)?.icon ?: (extension as? Extension.Available)?.iconUrl
    val isInstalled = extension is Extension.Installed

    ListItem(
        modifier = if (onClick != null) Modifier.clickable { onClick() } else Modifier,
        headlineContent = { 
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                if (isNsfw) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = Color.Red.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            "18+", 
                            color = Color.Red, 
                            fontSize = 10.sp, 
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }
            }
        },
        supportingContent = { 
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (lang != null) {
                    Text(
                        text = lang.uppercase(),
                        color = Color.Gray,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(" â€¢ ", color = Color.Gray)
                }
                Text(version, color = Color.Gray, fontSize = 12.sp)
                Text(" â€¢ ", color = Color.Gray)
                val isJsExt = isJsExtension(extension)
                val typeLabel = if (isJsExt) "JS" else "APK"
                val typeColor = if (isJsExt) Color(0xFF64B5F6) else Color(0xFF2196F3)
                val typeBg = if (isJsExt) Color(0xFF1565C0) else Color(0xFF0D47A1)
                Surface(color = typeBg.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                    Text(
                        typeLabel,
                        color = typeColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = androidx.compose.ui.Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
            }
        },
        leadingContent = {
            Box(
                modifier = Modifier.size(40.dp),
                contentAlignment = Alignment.Center
            ) {
                if (icon != null) {
                    AsyncImage(
                        model = icon,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color.DarkGray, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Extension, tint = Color.LightGray, contentDescription = null)
                    }
                }
            }
        },
        trailingContent = {
            if (trustLabel != null && onClick != null) {
                TextButton(
                    onClick = onClick,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                ) {
                    Text(trustLabel, fontWeight = FontWeight.ExtraBold)
                }
            } else if ((!isInstalled || isUpdate) && (onClick != null || onActionClick != null)) {
                val action = onActionClick ?: onClick
                when (installStep) {
                    InstallStep.Pending -> TextButton(
                        onClick = { action?.invoke() },
                        colors = ButtonDefaults.textButtonColors(contentColor = PrimaryOrange)
                    ) {
                        Text(if (isUpdate) "Cáº¬P NHáº¬T" else "CĂ€I Äáº¶T", fontWeight = FontWeight.ExtraBold)
                    }
                    InstallStep.Downloading -> CircularProgressIndicator(modifier = Modifier.size(20.dp), color = PrimaryOrange, strokeWidth = 2.dp)
                    InstallStep.Installing -> Text("ÄANG CĂ€I...", color = PrimaryOrange, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    InstallStep.SystemInstallStarted -> Text("XONG á» Há»† THá»NG", color = PrimaryOrange, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    else -> Icon(Icons.Default.Check, tint = Color.Green, contentDescription = null)
                }
            } else if (isInstalled) {
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.Settings, tint = Color.Gray, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        containerColor = BackgroundDark
                    ) {
                        DropdownMenuItem(
                            text = { Text("Chi tiáº¿t", color = Color.White) },
                            onClick = {
                                showMenu = false
                                onOpenDetails?.invoke()
                            },
                            leadingIcon = { Icon(Icons.Default.Info, contentDescription = null, tint = Color.White) }
                        )
                        DropdownMenuItem(
                            text = { Text("ThĂ´ng tin á»©ng dá»¥ng", color = Color.White) },
                            onClick = {
                                showMenu = false
                                val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = android.net.Uri.fromParts("package", extension.pkgName, null)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            },
                            leadingIcon = { Icon(Icons.Default.Launch, contentDescription = null, tint = Color.White) }
                        )
                        DropdownMenuItem(
                            text = { Text("Gá»¡ cĂ i Ä‘áº·t", color = Color.Red) },
                            onClick = {
                                showMenu = false
                                onUninstall?.invoke()
                            },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red) }
                        )
                    }
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchAppBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onCloseSearch: () -> Unit
) {
    TopAppBar(
        title = {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("TĂ¬m pháº§n má»Ÿ rá»™ng...", color = Color.Gray) },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = PrimaryOrange,
                    focusedTextColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth()
            )
        },
        navigationIcon = {
            IconButton(onClick = onCloseSearch) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.White)
            }
        },
        actions = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = Color.White)
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark)
    )
}

@Composable
fun ExtensionDetailsDialog(
    extension: Extension,
    onDismiss: () -> Unit,
    onUninstall: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val icon = (extension as? Extension.Installed)?.icon ?: (extension as? Extension.Available)?.iconUrl
                Box(modifier = Modifier.size(48.dp)) {
                    if (icon != null) {
                        AsyncImage(
                            model = icon,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize().background(Color.DarkGray, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Extension, tint = Color.LightGray, contentDescription = null)
                        }
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(extension.name, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(extension.pkgName.substringAfterLast("."), color = Color.Gray, fontSize = 12.sp)
                }
            }
        },
        text = {
            Column {
                val isJsExtDlg = isJsExtension(extension)
                val extType = if (isJsExtDlg) "JS" else "APK"
                val sourceCount = when (extension) {
                    is Extension.Installed -> extension.sources.size
                    is Extension.Available -> extension.sources.size
                    else -> 0
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Text(extension.versionName, color = Color.White, fontWeight = FontWeight.Bold)
                        Text("PhiĂªn báº£n", color = Color.Gray, fontSize = 12.sp)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Text(extension.lang?.uppercase() ?: "ALL", color = Color.White, fontWeight = FontWeight.Bold)
                        Text("NgĂ´n ngá»¯", color = Color.Gray, fontSize = 12.sp)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Text(extType, color = if (isJsExtDlg) Color(0xFF64B5F6) else Color(0xFF2196F3), fontWeight = FontWeight.Bold)
                        Text("Loáº¡i", color = Color.Gray, fontSize = 12.sp)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Text("$sourceCount", color = Color.White, fontWeight = FontWeight.Bold)
                        Text("Nguá»“n", color = Color.Gray, fontSize = 12.sp)
                    }
                }
                if (extension.isNsfw) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(color = Color.Red.copy(alpha = 0.15f), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Text("â  Ná»™i dung ngÆ°á»i lá»›n (18+)", color = Color.Red, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                            modifier = androidx.compose.ui.Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                if (extension is Extension.Available) {
                    extension.description?.takeIf { it.isNotBlank() }?.let { description ->
                        Text("MĂ´ táº£", color = PrimaryOrange, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(description, color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }

                Text("Nguá»“n cung cáº¥p", color = PrimaryOrange, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                val sources = when(extension) {
                    is Extension.Installed -> extension.sources.map { it.name }
                    is Extension.Available -> extension.sources.map { it.name }
                    else -> emptyList()
                }
                
                if (sources.isEmpty()) {
                    Text("KhĂ´ng tĂ¬m tháº¥y nguá»“n", color = Color.Gray, fontSize = 13.sp)
                } else {
                    sources.take(5).forEach { 
                        Text("â€¢ $it", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                    }
                    if (sources.size > 5) {
                        Text("...vĂ  ${sources.size - 5} nguá»“n khĂ¡c", color = Color.Gray, fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            if (extension is Extension.Installed) {
                TextButton(onClick = {
                    onDismiss()
                    onUninstall()
                }) {
                    Text("Gá»¡ cĂ i Ä‘áº·t", color = Color.Red)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("ÄĂ³ng")
            }
        },
        containerColor = BackgroundDark
    )
}


