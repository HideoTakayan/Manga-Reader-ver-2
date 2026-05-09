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
import com.example.manga_readerver2.core.source.Extension
import com.example.manga_readerver2.core.source.InstallStep
import com.example.manga_readerver2.ui.theme.BackgroundDark
import com.example.manga_readerver2.ui.theme.PrimaryOrange

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
        val tabs = listOf("Nguồn", "Phần mở rộng")

        // Tìm kiếm local cho tab Phần mở rộng
        var isSearching by remember { mutableStateOf(false) }
        var searchQuery by remember { mutableStateOf("") }

        // Bộ lọc nội dung: 0 - Tất cả, 1 - Manga (APK), 2 - Truyện chữ (JS)
        var contentTypeFilter by remember { mutableIntStateOf(0) }
        
        var showLangDialog by remember { mutableStateOf(false) }
        var selectedExtensionForDetail by remember { mutableStateOf<Extension.Available?>(null) }

        // Dùng OpenDocument thay vì GetContent để hỗ trợ nhiều MIME type (JS + ZIP)
        val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
            contract = object : androidx.activity.result.contract.ActivityResultContract<Unit, android.net.Uri?>() {
                override fun createIntent(context: android.content.Context, input: Unit): android.content.Intent {
                    return android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(android.content.Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                        putExtra(
                            android.content.Intent.EXTRA_MIME_TYPES,
                            arrayOf(
                                "application/javascript",  // .js
                                "text/javascript",
                                "application/zip",         // .zip
                                "application/x-zip-compressed",
                                "application/octet-stream" // fallback
                            )
                        )
                    }
                }
                override fun parseResult(resultCode: Int, intent: android.content.Intent?) =
                    if (resultCode == android.app.Activity.RESULT_OK) intent?.data else null
            }
        ) { uri ->
            uri?.let { screenModel.importExtension(it) }
        }

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
        
        // Fix: Theo dõi trạng thái cài đặt hệ thống để thông báo cho người dùng
        LaunchedEffect(installSteps) {
            installSteps.forEach { (pkg, step) ->
                if (step == InstallStep.SystemInstallStarted && !processedInstallSteps.contains(pkg)) {
                    processedInstallSteps.add(pkg)
                    snackbarHostState.showSnackbar(
                        message = "Vui lòng hoàn tất cài đặt trong hộp thoại hệ thống",
                        duration = SnackbarDuration.Short
                    )
                } else if (step == InstallStep.Installed || step == InstallStep.Error) {
                    // Nếu đã cài xong hoặc lỗi, có thể bỏ qua
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
                        title = { Text("Duyệt", color = Color.White, fontWeight = FontWeight.Bold) },
                        actions = {
                            if (selectedTab == 1) {
                                IconButton(onClick = { importLauncher.launch(Unit) }) {
                                    Icon(Icons.Default.FileOpen, contentDescription = "Nhập file", tint = Color.White)
                                }
                            }
                            IconButton(onClick = { 
                                if (selectedTab == 0) {
                                    rootNavigator.push(GlobalSearchScreen()) 
                                } else {
                                    isSearching = true
                                }
                            }) {
                                Icon(Icons.Default.Search, contentDescription = "Tìm kiếm", tint = Color.White)
                            }
                            IconButton(onClick = { showLangDialog = true }) {
                                Icon(Icons.Default.Language, contentDescription = "Ngôn ngữ", tint = Color.White)
                            }
                            IconButton(onClick = { screenModel.refreshExtensions() }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Làm mới", tint = Color.White)
                            }
                            IconButton(onClick = { rootNavigator.push(ExtensionRepoScreen()) }) {
                                Icon(Icons.Default.SettingsInputComponent, contentDescription = "Quản lý nguồn", tint = Color.White)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark)
                    )
                }
            }
        ) { paddingValues ->
            Column(modifier = Modifier.padding(paddingValues)) {
                // Tab chọn Nguồn hoặc Phần mở rộng
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

                // Bộ lọc loại nội dung (Manga/Truyện chữ)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = contentTypeFilter == 0,
                        onClick = { contentTypeFilter = 0 },
                        label = { Text("Tất cả") },
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
                        label = { Text("Truyện chữ (JS)") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PrimaryOrange.copy(alpha = 0.2f),
                            selectedLabelColor = PrimaryOrange
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
                ExtensionDetailDialog(
                    extension = selectedExtensionForDetail!!,
                    installStep = installSteps[selectedExtensionForDetail!!.pkgName] ?: InstallStep.Pending,
                    onInstall = { 
                        screenModel.installExtension(selectedExtensionForDetail!!)
                        selectedExtensionForDetail = null
                    },
                    onDismiss = { selectedExtensionForDetail = null }
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
        "all" to "Tất cả",
        "vi" to "Tiếng Việt", 
        "en" to "English", 
        "ja" to "Japanese",
        "zh" to "Chinese",
        "ko" to "Korean"
    )
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ngôn ngữ nguồn", color = Color.White) },
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
            normalized == "global" -> "all"
            normalized.contains("_") -> normalized.substringBefore("_")
            normalized.contains("-") -> normalized.substringBefore("-")
            else -> normalized
        }
    }

    val normalizedEnabledLangs = enabledLangs.map { normalizeLang(it) }.toSet()
    val sourceWithExt = extensions.flatMap { ext -> ext.sources.map { source -> source to ext } }

    fun matchType(ext: Extension.Installed): Boolean = when (filter) {
        1 -> !ext.isVBook
        2 -> ext.isVBook
        else -> true
    }

    fun matchLang(ext: Extension.Installed): Boolean {
        val extLang = normalizeLang(ext.lang)
        return extLang == "all" || normalizedEnabledLangs.contains("all") || normalizedEnabledLangs.contains(extLang)
    }

    val allSources = sourceWithExt.filter { (_, ext) ->
        matchType(ext) && matchLang(ext)
    }

    val hiddenByTypeCount = sourceWithExt.count { (_, ext) -> !matchType(ext) }
    val hiddenByLanguageCount = sourceWithExt.count { (_, ext) -> matchType(ext) && !matchLang(ext) }

    val pinnedList = allSources.filter { pinnedSources.contains(it.first.id.toString()) }
    val unpinnedGrouped = allSources
        .filter { !pinnedSources.contains(it.first.id.toString()) }
        .groupBy { it.first.lang }
        .toSortedMap()

    if (allSources.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Chưa có nguồn nào phù hợp", color = Color.Gray)
                if (hiddenByTypeCount > 0 || hiddenByLanguageCount > 0) {
                    Spacer(modifier = Modifier.height(6.dp))
                    val reasons = buildList {
                        if (hiddenByLanguageCount > 0) add("$hiddenByLanguageCount nguồn bị ẩn theo ngôn ngữ")
                        if (hiddenByTypeCount > 0) add("$hiddenByTypeCount nguồn bị ẩn theo loại nội dung")
                    }.joinToString(" • ")
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
                        if (hiddenByLanguageCount > 0) add("$hiddenByLanguageCount bị ẩn theo ngôn ngữ")
                        if (hiddenByTypeCount > 0) add("$hiddenByTypeCount bị ẩn theo loại nội dung")
                    }.joinToString(" • ")
                    Text(
                        text = "Đang ẩn: $reasons",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            if (pinnedList.isNotEmpty()) {
                item(key = "header_pinned") {
                    Text(
                        "Đã ghim", 
                        color = PrimaryOrange, 
                        fontSize = 12.sp, 
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
                items(pinnedList, key = { "pinned_${it.first.id}" }) { (source, ext) ->
                    SourceItem(navigator, source, ext, true, onTogglePin)
                }
            }

            unpinnedGrouped.forEach { (lang, sources) ->
                val langName = when (lang.lowercase()) {
                    "vi" -> "Tiếng Việt"
                    "en" -> "English"
                    "all" -> "Đa ngôn ngữ"
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

                items(sources, key = { "source_${it.first.id}" }) { (source, ext) ->
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
    ListItem(
        headlineContent = { 
            Text(source.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp) 
        },
        supportingContent = { 
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = source.lang.uppercase(),
                    color = Color.Gray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(" • ", color = Color.Gray)
                Text(
                    if (ext.isVBook) "TRUYỆN CHỮ" else "MANGA",
                    color = Color.Gray,
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
                        modifier = Modifier.fillMaxSize().background(Color.DarkGray, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Public, tint = Color.LightGray, contentDescription = null)
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
                    Text("MỚI NHẤT", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
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
    onDetail: (Extension.Available) -> Unit
) {
    // Untrusted extensions
    val filteredUntrusted = untrusted.filter { ext ->
        val matchType = when (filter) {
            1 -> true // APKs
            2 -> false // JS never untrusted for now
            else -> true
        }
        val matchQuery = query.isEmpty() || ext.name.contains(query, ignoreCase = true) || ext.pkgName.contains(query, ignoreCase = true)
        matchType && matchQuery
    }

    // Installed extensions
    val filteredInstalled = installed.filter { ext ->
        val matchType = when (filter) {
            1 -> !ext.isVBook
            2 -> ext.isVBook
            else -> true
        }
        val matchQuery = query.isEmpty() || ext.name.contains(query, ignoreCase = true) || ext.pkgName.contains(query, ignoreCase = true)
        matchType && matchQuery
    }.sortedBy { it.name }
    
    // Available extensions grouped by language
    val filteredAvailable = available.filter { avail ->
        val matchType = when (filter) {
            1 -> !avail.isVBook
            2 -> avail.isVBook
            else -> true
        }
        // lang='all' luôn hiện; nếu user chọn "all" thì tất cả pass
        val matchLang = avail.lang == "all" || enabledLangs.contains("all") || enabledLangs.contains(avail.lang)
        val matchQuery = query.isEmpty() || avail.name.contains(query, ignoreCase = true) || avail.pkgName.contains(query, ignoreCase = true)
        installed.none { it.pkgName == avail.pkgName } && matchType && matchLang && matchQuery
    }.groupBy { it.lang }.toSortedMap()

    LazyColumn(
        contentPadding = PaddingValues(bottom = 16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        if (filteredUntrusted.isNotEmpty()) {
            item(key = "header_untrusted") {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        "CHƯA TIN CẬY (${filteredUntrusted.size})",
                        color = Color.Red,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Nhấn TIN CẬY để nguồn APK xuất hiện trong tab Nguồn.",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }
            }
            items(filteredUntrusted, key = { "untrusted_${it.pkgName}" }) { ext ->
                ExtensionItem(
                    name = ext.name,
                    version = ext.versionName,
                    lang = ext.lang,
                    isNsfw = ext.isNsfw,
                    isVBook = false,
                    icon = null,
                    isInstalled = false,
                    installStep = InstallStep.Pending,
                    isUpdate = false,
                    onClick = { onTrust(ext) },
                    trustLabel = "TIN CẬY"
                )
            }
        }

        if (filteredInstalled.isNotEmpty()) {
            item(key = "header_installed") {
                Text(
                    "Đã cài đặt (${filteredInstalled.size})", 
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
                    name = ext.name,
                    version = ext.versionName,
                    lang = ext.lang,
                    isNsfw = ext.isNsfw,
                    isVBook = ext.isVBook,
                    icon = ext.icon,
                    isInstalled = true,
                    installStep = if (hasUpdate) installSteps[ext.pkgName] ?: InstallStep.Pending else null,
                    isUpdate = hasUpdate,
                    onClick = if (hasUpdate) { { onInstall(availableVersion!!) } } else null,
                    onUninstall = { onUninstall(ext.pkgName) }
                )
            }
        }

        filteredAvailable.forEach { (lang, extensions) ->
            val langName = when (lang.lowercase()) {
                "vi" -> "Tiếng Việt"
                "en" -> "English"
                "all" -> "Đa ngôn ngữ"
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
                    name = ext.name,
                    version = ext.versionName,
                    lang = ext.lang,
                    isNsfw = ext.isNsfw,
                    isVBook = ext.isVBook,
                    icon = ext.iconUrl,
                    isInstalled = false,
                    installStep = step,
                    isUpdate = false,
                    onClick = { onDetail(ext) },
                    onActionClick = { onInstall(ext) }
                )
            }
        }
    }
}

@Composable
fun ExtensionDetailDialog(
    extension: Extension.Available,
    installStep: InstallStep,
    onInstall: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            if (installStep == InstallStep.Pending) {
                Button(
                    onClick = onInstall,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryOrange)
                ) {
                    Text("Cài đặt")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Đóng", color = Color.White)
            }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(extension.name, color = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    color = if (extension.isVBook) Color(0xFF4CAF50).copy(alpha = 0.2f) else Color(0xFF2196F3).copy(alpha = 0.2f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        if (extension.isVBook) "TRUYỆN CHỮ" else "MANGA",
                        color = if (extension.isVBook) Color(0xFF4CAF50) else Color(0xFF2196F3),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Gói: ${extension.pkgName}", color = Color.Gray, fontSize = 12.sp)
                Text("Phiên bản: ${extension.versionName}", color = Color.Gray, fontSize = 12.sp)
                if (extension.author != null) {
                    Text("Tác giả: ${extension.author}", color = Color.Gray, fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text("Các nguồn đi kèm:", color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                extension.sources.forEach { source ->
                    Row(
                        modifier = Modifier.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Link, contentDescription = null, tint = PrimaryOrange, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(source.name, color = Color.LightGray, fontSize = 14.sp)
                    }
                }
                if (extension.isNsfw) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("⚠️ Phần mở rộng này chứa nội dung 18+", color = Color.Red, fontSize = 12.sp)
                }
            }
        },
        containerColor = BackgroundDark
    )
}

@Composable
fun ExtensionItem(
    name: String,
    version: String,
    lang: String?,
    isNsfw: Boolean,
    isVBook: Boolean,
    icon: Any?,
    isInstalled: Boolean,
    installStep: InstallStep?,
    isUpdate: Boolean = false,
    trustLabel: String? = null,
    onClick: (() -> Unit)?,
    onActionClick: (() -> Unit)? = null,
    onUninstall: (() -> Unit)? = null
) {
    var showMenu by remember { mutableStateOf(false) }

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
                    Text(" • ", color = Color.Gray)
                }
                Text(version, color = Color.Gray, fontSize = 12.sp)
                Text(" • ", color = Color.Gray)
                Text(
                    if (isVBook) "TRUYỆN CHỮ (JS)" else "MANGA (APK)",
                    color = if (isVBook) Color(0xFF4CAF50) else Color(0xFF2196F3),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
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
                        Text(if (isUpdate) "CẬP NHẬT" else "CÀI ĐẶT", fontWeight = FontWeight.ExtraBold)
                    }
                    InstallStep.Downloading -> CircularProgressIndicator(modifier = Modifier.size(20.dp), color = PrimaryOrange, strokeWidth = 2.dp)
                    InstallStep.Installing -> Text("ĐANG CÀI...", color = PrimaryOrange, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    InstallStep.SystemInstallStarted -> Text("XONG Ở HỆ THỐNG", color = PrimaryOrange, fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                            text = { Text("Gỡ cài đặt", color = Color.Red) },
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
                placeholder = { Text("Tìm phần mở rộng...", color = Color.Gray) },
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
