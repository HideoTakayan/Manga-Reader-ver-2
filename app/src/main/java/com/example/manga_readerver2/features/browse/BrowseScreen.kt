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
import com.example.manga_readerver2.source_js.JsSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.CatalogueSource

fun isJsExtension(ext: Extension): Boolean {
    return when (ext) {
        // Dùng `is JsSource` thay vì so sánh simpleName để tránh lỗi khi pkgName không có prefix "js.extension."
        is Extension.Installed -> ext.sources.any { it is JsSource } || ext.pkgName.startsWith("js.extension.")
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
        val hiddenSources by screenModel.hiddenSources.collectAsState()
        val enabledLangs by screenModel.enabledLanguages.collectAsState()

        var selectedTab by remember { mutableIntStateOf(0) }
        val tabs = listOf("Nguồn", "Phần mở rộng")

        // Tìm kiếm local cho tab Phần mở rộng
        var isSearching by remember { mutableStateOf(false) }
        var searchQuery by remember { mutableStateOf("") }

        // Bộ lọc nội dung: 0 - Tất cả, 1 - Manga (APK), 2 - Truyện chữ (JS)
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
                            message = "Vui lòng hoàn tất cài đặt trong hộp thoại hệ thống",
                            duration = SnackbarDuration.Short
                        )
                        InstallStep.Installed -> snackbarHostState.showSnackbar(
                            message = "Đã cài đặt — chuyển sang tab Nguồn để sử dụng",
                            duration = SnackbarDuration.Short
                        )
                        InstallStep.Error -> snackbarHostState.showSnackbar(
                            message = "Cài đặt thất bại — kiểm tra logcat để biết chi tiết",
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
                        title = { Text("Duyệt", color = Color.White, fontWeight = FontWeight.Bold) },
                        actions = {
                            if (selectedTab == 0) { // Nguồn
                                IconButton(onClick = { rootNavigator.push(GlobalSearchScreen()) }) {
                                    Icon(Icons.Default.Search, contentDescription = "Tìm kiếm", tint = Color.White)
                                }
                                IconButton(onClick = { folderPickerLauncher.launch(null) }) {
                                    Icon(Icons.Default.FolderOpen, contentDescription = "Local Source", tint = Color.White)
                                }
                                IconButton(onClick = { showLangDialog = true }) {
                                    Icon(Icons.Default.FilterList, contentDescription = "Bộ lọc", tint = Color.White)
                                }
                            } else { // Phần mở rộng
                                IconButton(onClick = { isSearching = true }) {
                                    Icon(Icons.Default.Search, contentDescription = "Tìm kiếm", tint = Color.White)
                                }
                                IconButton(onClick = { rootNavigator.push(ExtensionRepoScreen()) }) {
                                    Icon(Icons.Default.SettingsInputComponent, contentDescription = "Quản lý nguồn", tint = Color.White)
                                }
                                IconButton(onClick = { showLangDialog = true }) {
                                    Icon(Icons.Default.Translate, contentDescription = "Ngôn ngữ", tint = Color.White)
                                }
                                IconButton(onClick = { screenModel.refreshExtensions() }) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Làm mới", tint = Color.White)
                                }
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
                            hiddenSources,
                            enabledLangs,
                            onTogglePin = { screenModel.togglePin(it) },
                            onToggleHide = { screenModel.toggleHide(it) }
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
                val availableLangs = remember(installedExts, untrustedExts, availableExts) {
                    val langs = mutableSetOf<String>()
                    installedExts.forEach { ext -> langs.addAll(ext.sources.map { it.lang }) }
                    untrustedExts.forEach { ext -> ext.lang?.let { langs.add(it) } }
                    availableExts.forEach { ext -> langs.add(ext.lang) }
                    langs.filter { it.isNotBlank() && it != "all" && it != "other" }.sorted()
                }

                LanguageFilterBottomSheet(
                    availableLangs = availableLangs,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageFilterBottomSheet(
    availableLangs: List<String>,
    selectedLangs: Set<String>,
    onDismiss: () -> Unit,
    onLangsChanged: (Set<String>) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = BackgroundDark,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(modifier = Modifier.padding(bottom = 16.dp)) {
            Text(
                text = "Ngôn ngữ phần mở rộng",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // "Tất cả" option
            LanguageRow(
                name = "Tất cả",
                isChecked = selectedLangs.contains("all") || selectedLangs.isEmpty(),
                onCheckedChange = { isChecked ->
                    if (isChecked) onLangsChanged(setOf("all"))
                }
            )

            HorizontalDivider(color = Color.DarkGray, modifier = Modifier.padding(vertical = 8.dp))

            LazyColumn {
                items(availableLangs) { code ->
                    val name = try {
                        val locale = java.util.Locale(code)
                        locale.getDisplayName(java.util.Locale("vi")).replaceFirstChar { it.uppercase() }
                    } catch (e: Exception) {
                        code.uppercase()
                    }

                    LanguageRow(
                        name = name,
                        isChecked = !selectedLangs.contains("all") && selectedLangs.contains(code),
                        onCheckedChange = { isChecked ->
                            val newLangs = selectedLangs.toMutableSet()
                            newLangs.remove("all")
                            if (isChecked) {
                                newLangs.add(code)
                            } else {
                                newLangs.remove(code)
                            }
                            if (newLangs.isEmpty()) {
                                newLangs.add("all")
                            }
                            onLangsChanged(newLangs)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun LanguageRow(name: String, isChecked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!isChecked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(name, color = Color.White, fontSize = 16.sp)
        Checkbox(
            checked = isChecked,
            onCheckedChange = null,
            colors = CheckboxDefaults.colors(checkedColor = PrimaryOrange, checkmarkColor = Color.White)
        )
    }
}

@Composable
fun SourcesTab(
    navigator: cafe.adriel.voyager.navigator.Navigator, 
    extensions: List<Extension.Installed>, 
    filter: Int,
    pinnedSources: Set<String>,
    hiddenSources: Set<String>,
    enabledLangs: Set<String>,
    onTogglePin: (Long) -> Unit,
    onToggleHide: (Long) -> Unit
) {
    fun normalizeLang(code: String?): String {
        if (code.isNullOrBlank()) return "all"
        val normalized = code.trim().lowercase()
        return when {
            normalized == "global" || normalized == "tất cả" -> "all"
            normalized == "vi" || normalized.contains("việt") -> "vi"
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
            1 -> !isJs  // "Manga (APK)": chỉ hiện APK, ẩn JS
            2 -> isJs   // "Truyện chữ (JS)": chỉ hiện JS
            else -> true // "Tất cả": hiện tất cả
        }
    }

    fun matchLang(source: eu.kanade.tachiyomi.source.Source): Boolean {
        if (normalizedEnabledLangs.isEmpty()) return true
        val sourceLang = normalizeLang(source.lang)
        return sourceLang == "all" || normalizedEnabledLangs.contains("all") || normalizedEnabledLangs.contains(sourceLang)
    }

    val allSources = sourceWithExt.filter { (source, ext) ->
        matchType(ext) && matchLang(source) && !hiddenSources.contains(source.id.toString())
    }

    val hiddenByTypeCount = sourceWithExt.count { (_, ext) -> !matchType(ext) }
    val hiddenByLanguageCount = sourceWithExt.count { (source, ext) -> matchType(ext) && !matchLang(source) }
    val hiddenByUserCount = sourceWithExt.count { (source, _) -> hiddenSources.contains(source.id.toString()) }

    val pinnedList = allSources.filter { pinnedSources.contains(it.first.id.toString()) }
    val unpinnedGrouped = allSources
        .filter { !pinnedSources.contains(it.first.id.toString()) }
        .groupBy { it.first.lang }
        .toSortedMap()

    if (allSources.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Chưa có nguồn nào phù hợp", color = Color.Gray)
                if (hiddenByTypeCount > 0 || hiddenByLanguageCount > 0 || hiddenByUserCount > 0) {
                    Spacer(modifier = Modifier.height(6.dp))
                    val reasons = buildList {
                        if (hiddenByLanguageCount > 0) add("$hiddenByLanguageCount bị ẩn do ngôn ngữ")
                        if (hiddenByTypeCount > 0) add("$hiddenByTypeCount bị ẩn do loại")
                        if (hiddenByUserCount > 0) add("$hiddenByUserCount bị ẩn bởi bạn")
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
            if (hiddenByTypeCount > 0 || hiddenByLanguageCount > 0 || hiddenByUserCount > 0) {
                item(key = "hidden_hint") {
                    val reasons = buildList {
                        if (hiddenByLanguageCount > 0) add("$hiddenByLanguageCount bị ẩn do ngôn ngữ")
                        if (hiddenByTypeCount > 0) add("$hiddenByTypeCount bị ẩn do loại")
                        if (hiddenByUserCount > 0) add("$hiddenByUserCount bị ẩn bởi bạn")
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
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
                items(pinnedList, key = { "pinned_${it.first.id}_${it.second.pkgName}_${it.first.name}" }) { (source, ext) ->
                    SourceItem(navigator, source, ext, true, onTogglePin, onToggleHide)
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
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }

                items(sources, key = { "source_${it.first.id}_${it.second.pkgName}_${it.first.name}" }) { (source, ext) ->
                    SourceItem(navigator, source, ext, false, onTogglePin, onToggleHide)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SourceItem(
    navigator: cafe.adriel.voyager.navigator.Navigator,
    source: eu.kanade.tachiyomi.source.Source,
    ext: Extension.Installed,
    isPinned: Boolean,
    onTogglePin: (Long) -> Unit,
    onToggleHide: (Long) -> Unit
) {
    val isJsSource = isJsExtension(ext)
    val isLocal = ext.pkgName == "local.source"
    
    var showOptions by remember { mutableStateOf(false) }

    if (showOptions) {
        SourceOptionsDialog(
            sourceName = source.name,
            isPinned = isPinned,
            onDismiss = { showOptions = false },
            onTogglePin = { 
                onTogglePin(source.id)
                showOptions = false
            },
            onHide = {
                onToggleHide(source.id)
                showOptions = false
            }
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { navigator.push(CatalogueScreen(source.id, source.name, latest = false)) },
                onLongClick = { showOptions = true }
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon
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
        
        Spacer(modifier = Modifier.width(16.dp))
        
        // Content
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = source.name, 
                    color = Color.White, 
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (isJsSource || isLocal) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = if (isLocal) Color(0xFF4CAF50).copy(alpha = 0.25f) else Color(0xFF1565C0).copy(alpha = 0.25f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = if (isLocal) "MÁY" else "JS",
                            color = if (isLocal) Color(0xFFA5D6A7) else Color(0xFF90CAF9),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = source.lang.uppercase(),
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(" • ", color = Color.Gray, fontSize = 12.sp)
                Text(
                    text = if (isLocal) "THƯ MỤC" else if (isJsSource) "TRUYỆN CHỮ (JS)" else "MANGA (APK)",
                    color = if (isLocal) Color(0xFF81C784) else if (isJsSource) Color(0xFF64B5F6) else Color.Gray,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        
        // Actions
        Row(verticalAlignment = Alignment.CenterVertically) {
            val supportsLatest = (source as? CatalogueSource)?.supportsLatest == true
            if (supportsLatest) {
                TextButton(
                    onClick = { navigator.push(CatalogueScreen(source.id, source.name, latest = true)) },
                    colors = ButtonDefaults.textButtonColors(contentColor = PrimaryOrange)
                ) {
                    Text("MỚI NHẤT", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            IconButton(onClick = { onTogglePin(source.id) }) {
                Icon(
                    if (isPinned) Icons.Default.PushPin else Icons.Outlined.PushPin, 
                    tint = if (isPinned) PrimaryOrange else Color.Gray, 
                    contentDescription = null
                )
            }
        }
    }
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
    val availableByLang = mutableMapOf<String, MutableList<Extension.Available>>()
    
    available.filter { avail ->
        val isJs = isJsExtension(avail)
        val matchType = when (filter) {
            1 -> !isJs
            2 -> isJs
            else -> true
        }
        val matchQuery = query.isEmpty() || avail.name.contains(query, ignoreCase = true) || avail.pkgName.contains(query, ignoreCase = true)
        installed.none { it.pkgName == avail.pkgName } && untrusted.none { it.pkgName == avail.pkgName } && matchType && matchQuery
    }.forEach { avail ->
        val langs = avail.sources.map { it.lang }.distinct().filter { it.isNotBlank() }
        val effectiveLangs = if (langs.isEmpty()) listOf(avail.lang) else langs
        
        effectiveLangs.forEach { lang ->
            if (lang == "all" || enabledLangs.contains("all") || enabledLangs.contains(lang)) {
                availableByLang.getOrPut(lang) { mutableListOf() }.add(avail)
            }
        }
    }
    
    val filteredAvailable = availableByLang.toSortedMap()

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
                        color = Color.Yellow,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    // Nút Trust All
                    TextButton(
                        onClick = { filteredUntrusted.forEach { onTrust(it) } },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFFF9800))
                    ) {
                        Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Trust tất cả (${filteredUntrusted.size})", fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon
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
        
        Spacer(modifier = Modifier.width(16.dp))
        
        // Content
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = name, 
                    color = Color.White, 
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (lang != null) {
                    Text(
                        text = lang.uppercase(),
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(" • ", color = Color.Gray)
                }
                Text(text = version, color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
                Text(" • ", color = Color.Gray)
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
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
            }
        }
        
        // Actions
        Row(verticalAlignment = Alignment.CenterVertically) {
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
                            text = { Text("Chi tiết", color = Color.White) },
                            onClick = {
                                showMenu = false
                                onOpenDetails?.invoke()
                            },
                            leadingIcon = { Icon(Icons.Default.Info, contentDescription = null, tint = Color.White) }
                        )
                        DropdownMenuItem(
                            text = { Text("Thông tin ứng dụng", color = Color.White) },
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
        }
    }
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
                        Text("Phiên bản", color = Color.Gray, fontSize = 12.sp)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Text(extension.lang?.uppercase() ?: "ALL", color = Color.White, fontWeight = FontWeight.Bold)
                        Text("Ngôn ngữ", color = Color.Gray, fontSize = 12.sp)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Text(extType, color = if (isJsExtDlg) Color(0xFF64B5F6) else Color(0xFF2196F3), fontWeight = FontWeight.Bold)
                        Text("Loại", color = Color.Gray, fontSize = 12.sp)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Text("$sourceCount", color = Color.White, fontWeight = FontWeight.Bold)
                        Text("Nguồn", color = Color.Gray, fontSize = 12.sp)
                    }
                }
                if (extension.isNsfw) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(color = Color.Red.copy(alpha = 0.15f), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Text("⚠ Nội dung người lớn (18+)", color = Color.Red, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                            modifier = androidx.compose.ui.Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                if (extension is Extension.Available) {
                    extension.description?.takeIf { it.isNotBlank() }?.let { description ->
                        Text("Mô tả", color = PrimaryOrange, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(description, color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }

                Text("Nguồn cung cấp", color = PrimaryOrange, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                val sources = when(extension) {
                    is Extension.Installed -> extension.sources.map { it.name }
                    is Extension.Available -> extension.sources.map { it.name }
                    else -> emptyList()
                }
                
                if (sources.isEmpty()) {
                    Text("Không tìm thấy nguồn", color = Color.Gray, fontSize = 13.sp)
                } else {
                    sources.take(5).forEach { 
                        Text("• $it", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                    }
                    if (sources.size > 5) {
                        Text("...và ${sources.size - 5} nguồn khác", color = Color.Gray, fontSize = 12.sp)
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
                    Text("Gỡ cài đặt", color = Color.Red)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Đóng")
            }
        },
        containerColor = BackgroundDark
    )
}

@Composable
fun SourceOptionsDialog(
    sourceName: String,
    isPinned: Boolean,
    onDismiss: () -> Unit,
    onTogglePin: () -> Unit,
    onHide: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(sourceName, color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                androidx.compose.material3.TextButton(
                    onClick = onTogglePin,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start, verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (isPinned) Icons.Default.PushPin else Icons.Outlined.PushPin,
                            contentDescription = null,
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(if (isPinned) "Bỏ ghim" else "Ghim", color = Color.White, fontSize = 16.sp)
                    }
                }
                androidx.compose.material3.TextButton(
                    onClick = onHide,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start, verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.VisibilityOff, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("Ẩn nguồn này", color = Color.White, fontSize = 16.sp)
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("Đóng", color = PrimaryOrange)
            }
        },
        containerColor = BackgroundDark
    )
}
