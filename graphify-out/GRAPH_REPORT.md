# Graph Report - .  (2026-05-11)

## Corpus Check
- 129 files · ~74,033 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 986 nodes · 1259 edges · 92 communities (50 shown, 42 thin omitted)
- Extraction: 88% EXTRACTED · 12% INFERRED · 0% AMBIGUOUS · INFERRED: 156 edges (avg confidence: 0.82)
- Token cost: 9,800 input · 3,200 output

## Community Hubs (Navigation)
- [[_COMMUNITY_Browse & Catalogue Screens|Browse & Catalogue Screens]]
- [[_COMMUNITY_Reader Screen Model|Reader Screen Model]]
- [[_COMMUNITY_Main Tab Screens|Main Tab Screens]]
- [[_COMMUNITY_App Configuration & DI|App Configuration & DI]]
- [[_COMMUNITY_Extension & Backup Core|Extension & Backup Core]]
- [[_COMMUNITY_Library & History Management|Library & History Management]]
- [[_COMMUNITY_Settings & Download UI|Settings & Download UI]]
- [[_COMMUNITY_Manga Data Repository|Manga Data Repository]]
- [[_COMMUNITY_Backup Data Models|Backup Data Models]]
- [[_COMMUNITY_Manga Repository Interface|Manga Repository Interface]]
- [[_COMMUNITY_JS Web Engine|JS Web Engine]]
- [[_COMMUNITY_Source API Contracts|Source API Contracts]]
- [[_COMMUNITY_Library Screen Model|Library Screen Model]]
- [[_COMMUNITY_Settings UI Components|Settings UI Components]]
- [[_COMMUNITY_Source Filter Types|Source Filter Types]]
- [[_COMMUNITY_Extension Repo Layer|Extension Repo Layer]]
- [[_COMMUNITY_Browse Screen Model|Browse Screen Model]]
- [[_COMMUNITY_Download Management|Download Management]]
- [[_COMMUNITY_Catalogue Source Paging|Catalogue Source Paging]]
- [[_COMMUNITY_JS DOM Wrapper|JS DOM Wrapper]]
- [[_COMMUNITY_App Icon & Branding|App Icon & Branding]]
- [[_COMMUNITY_Preference Store Layer|Preference Store Layer]]
- [[_COMMUNITY_Download Background Service|Download Background Service]]
- [[_COMMUNITY_Reader UI Components|Reader UI Components]]
- [[_COMMUNITY_Extension API Client|Extension API Client]]
- [[_COMMUNITY_Manga Detail Screen Model|Manga Detail Screen Model]]
- [[_COMMUNITY_App Entry & Theme|App Entry & Theme]]
- [[_COMMUNITY_DEX Extension Loader|DEX Extension Loader]]
- [[_COMMUNITY_Text-to-Speech Manager|Text-to-Speech Manager]]
- [[_COMMUNITY_Extension Manager|Extension Manager]]
- [[_COMMUNITY_EPUB File Reader|EPUB File Reader]]
- [[_COMMUNITY_Browse Extensions Model|Browse Extensions Model]]
- [[_COMMUNITY_HTTP Source Client|HTTP Source Client]]
- [[_COMMUNITY_Chapter Number Parser|Chapter Number Parser]]
- [[_COMMUNITY_Backup Operations|Backup Operations]]
- [[_COMMUNITY_Domain Model Layer|Domain Model Layer]]
- [[_COMMUNITY_Multi-Module Build Config|Multi-Module Build Config]]
- [[_COMMUNITY_Statistics Screen|Statistics Screen]]
- [[_COMMUNITY_Archive Image Reader|Archive Image Reader]]
- [[_COMMUNITY_EPUB Export Writer|EPUB Export Writer]]
- [[_COMMUNITY_Download Cache|Download Cache]]
- [[_COMMUNITY_Permission Manager|Permission Manager]]
- [[_COMMUNITY_Extension State Types|Extension State Types]]
- [[_COMMUNITY_JS Plugin Configuration|JS Plugin Configuration]]
- [[_COMMUNITY_Main Navigation Tabs|Main Navigation Tabs]]
- [[_COMMUNITY_ComicInfo XML Generator|ComicInfo XML Generator]]
- [[_COMMUNITY_OkHttp Extensions|OkHttp Extensions]]
- [[_COMMUNITY_Extension Load Result|Extension Load Result]]
- [[_COMMUNITY_Cryptographic Hashing|Cryptographic Hashing]]
- [[_COMMUNITY_Source Chapter Model|Source Chapter Model]]
- [[_COMMUNITY_Source Manga Model|Source Manga Model]]
- [[_COMMUNITY_Global Search Pipeline|Global Search Pipeline]]
- [[_COMMUNITY_Disk Utilities|Disk Utilities]]
- [[_COMMUNITY_Reusable Settings Items|Reusable Settings Items]]
- [[_COMMUNITY_Module Build Configs|Module Build Configs]]
- [[_COMMUNITY_Android Instrumented Test|Android Instrumented Test]]
- [[_COMMUNITY_Zip Archive Utility|Zip Archive Utility]]
- [[_COMMUNITY_Unit Test Placeholder|Unit Test Placeholder]]
- [[_COMMUNITY_Download Progress Listener|Download Progress Listener]]
- [[_COMMUNITY_Tri-State Toggle|Tri-State Toggle]]
- [[_COMMUNITY_Library View Mode|Library View Mode]]
- [[_COMMUNITY_Library Item Model|Library Item Model]]
- [[_COMMUNITY_Category Model|Category Model]]
- [[_COMMUNITY_History Model|History Model]]
- [[_COMMUNITY_Library Manga Model|Library Manga Model]]
- [[_COMMUNITY_Manga Update Model|Manga Update Model]]
- [[_COMMUNITY_Update Strategy Enum|Update Strategy Enum]]
- [[_COMMUNITY_Extension Repo Interface|Extension Repo Interface]]
- [[_COMMUNITY_Compose Shared Elements|Compose Shared Elements]]
- [[_COMMUNITY_Instrumented Test Class|Instrumented Test Class]]
- [[_COMMUNITY_TTS Manager Singleton|TTS Manager Singleton]]
- [[_COMMUNITY_Chapter Recognition|Chapter Recognition]]
- [[_COMMUNITY_Disk Utility|Disk Utility]]
- [[_COMMUNITY_Permission Manager|Permission Manager]]
- [[_COMMUNITY_Selection Top Bar|Selection Top Bar]]
- [[_COMMUNITY_Slider Settings Item|Slider Settings Item]]
- [[_COMMUNITY_Unit Test Class|Unit Test Class]]
- [[_COMMUNITY_Backup History Model|Backup History Model]]
- [[_COMMUNITY_Reactive Query One|Reactive Query One]]
- [[_COMMUNITY_Reactive Query Optional|Reactive Query Optional]]
- [[_COMMUNITY_JS Element Interface|JS Element Interface]]

## God Nodes (most connected - your core abstractions)
1. `ReaderScreenModel` - 38 edges
2. `MangaRepositoryImpl` - 31 edges
3. `MangaRepository` - 28 edges
4. `LibraryScreenModel` - 24 edges
5. `AppModule (DI)` - 19 edges
6. `DownloadService` - 12 edges
7. `TtsManager` - 12 edges
8. `FileManager` - 12 edges
9. `DownloadManager` - 11 edges
10. `BrowseScreenModel` - 11 edges

## Surprising Connections (you probably didn't know these)
- `CheckboxItem()` --calls--> `CheckBox`  [INFERRED]
  app/src/main/java/com/example/manga_readerver2/ui/components/MihonSettingsItems.kt → source-api/src/main/java/eu/kanade/tachiyomi/source/model/Filter.kt
- `UpdatesScreenModel` --calls--> `MangaRepositoryImpl`  [INFERRED]
  app/src/main/java/com/example/manga_readerver2/features/updates/UpdatesScreenModel.kt → data/src/main/java/com/example/manga_readerver2/data/repository/MangaRepositoryImpl.kt
- `MangaRepositoryImpl` --shares_data_with--> `BackupManga Data Class`  [INFERRED]
  data/src/main/java/com/example/manga_readerver2/data/repository/MangaRepositoryImpl.kt → core/src/main/java/com/example/manga_readerver2/core/backup/model/Backup.kt
- `QueryExtensions.subscribeToList` --conceptually_related_to--> `MangaRepository Interface`  [INFERRED]
  data/src/main/java/com/example/manga_readerver2/data/utils/QueryExtensions.kt → domain/src/main/java/com/example/manga_readerver2/domain/repository/MangaRepository.kt
- `LanguageFilterDialog()` --calls--> `CheckBox`  [INFERRED]
  app/src/main/java/com/example/manga_readerver2/features/browse/BrowseScreen.kt → source-api/src/main/java/eu/kanade/tachiyomi/source/model/Filter.kt

## Hyperedges (group relationships)
- **Download Subsystem (Manager + Service + Store + Cache)** — downloadmanager_DownloadManager, downloadservice_DownloadService, downloadstore_DownloadStore, downloadcache_DownloadCache, download_Download [EXTRACTED 0.95]
- **Preference System (General + Library + Reader)** — generalpreferences_GeneralPreferences, librarypreferences_LibraryPreferences, readerpreferences_ReaderPreferences, core_utils_PreferenceStore [INFERRED 0.95]
- **Bottom Navigation Tabs** — tabs_Tabs, features_library_LibraryScreen, features_updates_UpdatesScreen, features_history_HistoryScreen, features_browse_BrowseScreen, features_settings_SettingsScreen [EXTRACTED 1.00]
- **Injekt DI Singleton Registration** — appmodule_AppModule, downloadmanager_DownloadManager, downloadstore_DownloadStore, downloadcache_DownloadCache, extensionapi_ExtensionApi, core_source_ExtensionManager, core_source_ExtensionInstaller, database_Database, core_utils_FileManager, core_utils_PreferenceStore [EXTRACTED 1.00]
- **Multi-Module Project Architecture** — module_domain, module_data, module_core, module_source_api, module_source_dex, module_source_js, app_build_gradle_kts [EXTRACTED 1.00]
- **CBZ/EPUB Download Pipeline** — downloadservice_DownloadService, comicinfo_ComicInfo, core_utils_ZipUtil, core_utils_EpubExporter, downloadcache_DownloadCache [EXTRACTED 0.95]
- **Extension Lifecycle System** — ExtensionInstaller_ExtensionInstaller, ExtensionManager_ExtensionManager, ExtensionInstaller_InstallStep, SourcePreferences_SourcePreferences [EXTRACTED 1.00]
- **Preference / Settings System** — PreferenceStore_PreferenceStore, PreferenceStore_Preference, SourcePreferences_SourcePreferences [EXTRACTED 1.00]
- **File and Archive Utilities** — ArchiveReader_ArchiveReader, ZipUtil_ZipUtil, EpubExporter_EpubExporter, EpubReader_EpubReader, FileManager_FileManager [INFERRED 0.95]
- **HTTP and Network Utilities** — OkHttpExtensions_OkHttpExtensions, UserAgentInterceptor_UserAgentInterceptor, HttpSource_HttpSource [INFERRED 0.95]
- **Browse Feature MVVM Group** — BrowseScreen_BrowseScreen, BrowseScreenModel_BrowseScreenModel, CatalogueScreen_CatalogueScreen, CatalogueScreenModel_CatalogueScreenModel, ExtensionRepoScreen_ExtensionRepoScreen, ExtensionRepoScreen_ExtensionRepoScreenModel, GlobalSearchScreen_GlobalSearchScreen [INFERRED 0.95]
- **Reader Component System** — ReaderScreen_ReaderScreen, ReaderScreen_ReaderMainContent, ReaderScreenModel_ReaderScreenModel, ReaderComponents_ReaderDrawer, ReaderComponents_PageImage, ReaderComponents_ReaderPageContent, ReaderComponents_ChapterTransitionPage, ReaderComponents_TextReaderContent, ReaderComponents_ReaderTopBar, ReaderComponents_ReaderSystemOverlay, ReaderSettingsSheet_ReaderSettingsSheet, SubsamplingImage_SubsamplingImage, TtsComponents_TtsPlayerBar, TtsComponents_TtsSettingsDialog [EXTRACTED 1.00]
- **Library Management System** — LibraryScreen_LibraryScreen, LibraryScreenModel_LibraryScreenModel, LibraryItem_LibraryItem, LibraryDisplayMode_LibraryDisplayMode, LibrarySettingsDialog_LibrarySettingsDialog, CategoryManagerScreen_CategoryManagerScreen, LibraryScreen_LibraryContent, LibraryScreen_LibraryGridItem, LibraryScreen_LibraryListItem, LibraryScreen_SelectionTopBar [EXTRACTED 1.00]
- **Main Navigation Tabs** — MainScreen_MainScreen, LibraryScreen_LibraryScreen, HistoryScreen_HistoryScreen [EXTRACTED 0.95]
- **Browse and Search Pipeline** — GlobalSearchScreenModel_GlobalSearchScreenModel, GlobalSearchScreenModel_GlobalSearchResult, GlobalSearchScreenModel_GlobalSearchState, SourcePagingSource_SourcePagingSource [EXTRACTED 1.00]
- **Manga Detail Screen Flow** — MangaDetailScreen_MangaDetailScreen, MangaDetailScreenModel_MangaDetailScreenModel, MangaDetailScreen_MangaDetailContent, MangaDetailScreen_ChapterItem, MangaDetailScreenModel_ChapterSort, MangaDetailScreenModel_ChapterFilter, MangaDetailScreen_ChapterSettingsDialog, MangaDetailScreen_CategorySelectionDialog [EXTRACTED 1.00]
- **Settings Feature MVVM Pattern** — SettingsScreen_SettingsScreen, SettingsScreenModel_SettingsScreenModel, SettingsDetailScreen_SettingsDetailScreen [INFERRED 0.95]
- **Statistics Feature MVVM Pattern** — StatisticsScreen_StatisticsScreen, StatisticsScreenModel_StatisticsScreenModel, StatisticsScreenModel_StatisticsState [INFERRED 0.95]
- **Updates Feature MVVM Pattern** — UpdatesScreen_UpdatesScreen, UpdatesScreenModel_UpdatesScreenModel, UpdatesScreen_UpdateCard [INFERRED 0.95]
- **Backup Data Model Group** — Backup_Backup, Backup_BackupManga, Backup_BackupCategory, Backup_BackupChapter, Backup_BackupHistory [EXTRACTED 1.00]
- **Extension Type Hierarchy** — Extension_Extension, Extension_ExtensionInstalled, Extension_ExtensionAvailable, Extension_ExtensionUntrusted [EXTRACTED 1.00]
- **App Theme Components** — Color_ColorTheme, Theme_MangaReaderVer2Theme, Type_Typography [EXTRACTED 1.00]
- **Reusable Settings UI Components** — MihonSettingsItems_HeadingItem, MihonSettingsItems_SortItem, MihonSettingsItems_CheckboxItem, MihonSettingsItems_TriStateItem, MihonSettingsItems_SliderItem [INFERRED 0.95]
- **Data Layer Repository Implementations** — MangaRepositoryImpl_MangaRepositoryImpl, ExtensionRepoRepositoryImpl_ExtensionRepoRepositoryImpl [EXTRACTED 1.00]
- **Domain Model Layer (Manga, Chapter, History, Update, Category, LibraryManga, StatisticsData)** — domain_Manga, domain_Chapter, domain_History, domain_Update, domain_Category, domain_LibraryManga, domain_StatisticsData, domain_SourceStat [INFERRED 0.95]
- **Source API Model Layer (SManga, SChapter, Page, Filter, UpdateStrategy)** — sourceapi_SManga, sourceapi_SMangaImpl, sourceapi_SChapter, sourceapi_SChapterImpl, sourceapi_Page, sourceapi_Filter, sourceapi_FilterList, sourceapi_UpdateStrategy [INFERRED 0.95]
- **Extension Loading System (DEX + JS)** — sourcedex_ExtensionLoader, sourcedex_ChildFirstPathClassLoader, sourcejs_JsLoader, sourcejs_JsSource, sourcejs_VBookEngine, sourcejs_JsEnvironment [INFERRED 0.90]
- **JS Engine Components (VBookEngine, JsEnvironment, AndroidAppBridge, JsDocument, JsElement)** — sourcejs_VBookEngine, sourcejs_JsEnvironment, sourcejs_AndroidAppBridge, sourcejs_JsDocument, sourcejs_JsElement, sourcejs_JsResponse [EXTRACTED 1.00]
- **Domain Repository Interfaces (MangaRepository, ExtensionRepoRepository)** — domain_MangaRepository, domain_ExtensionRepoRepository [INFERRED 0.95]
- **Plugin Config Components (PluginConfig, Metadata, ScriptConfig)** — sourcejs_PluginConfig, sourcejs_Metadata, sourcejs_ScriptConfig [EXTRACTED 1.00]
- **SQLDelight Reactive Query Extensions** — QueryExtensions_subscribeToList, QueryExtensions_subscribeToOne, QueryExtensions_subscribeToOneOrNull [EXTRACTED 1.00]
- **All Launcher Icon Density Variants (mdpi through xxxhdpi)** — mipmap_mdpi_launcher, mipmap_mdpi_launcher_round, mipmap_hdpi_launcher, mipmap_hdpi_launcher_round, mipmap_xhdpi_launcher, mipmap_xhdpi_launcher_round, mipmap_xxhdpi_launcher, mipmap_xxhdpi_launcher_round, mipmap_xxxhdpi_launcher, mipmap_xxxhdpi_launcher_round [EXTRACTED 1.00]
- **Icon Design Gap: Samurai artwork exists but placeholder icons are deployed** — app_icon_samurai_artwork, ic_launcher_placeholder, ic_launcher_round_placeholder, manga_reader_app_brand [INFERRED 0.85]
- **Android Adaptive Icon Set (square and round variants across all densities)** — android_adaptive_icon_system, ic_launcher_placeholder, ic_launcher_round_placeholder [INFERRED 0.95]

## Communities (92 total, 42 thin omitted)

### Community 0 - "Browse & Catalogue Screens"
Cohesion: 0.07
Nodes (26): ExtensionDetailDialog(), ExtensionItem(), ExtensionsTab(), LanguageFilterDialog(), SearchAppBar(), SourceItem(), SourcesTab(), CatalogueScreen (+18 more)

### Community 1 - "Reader Screen Model"
Cohesion: 0.06
Nodes (8): Archive, Local, Online, Pdf, ReaderPage, ReaderScreenModel, ReadingMode, Text

### Community 2 - "Main Tab Screens"
Cohesion: 0.06
Nodes (22): BrowseScreen, EmptyState(), HistoryCard(), HistoryScreen, isSameDay(), HistoryScreenModel, CategoryManagerScreen, Badge() (+14 more)

### Community 3 - "App Configuration & DI"
Cohesion: 0.07
Nodes (10): AppModule, MangaApp, GeneralPreferences, LibraryPreferences, ReaderPreferences, ExtensionInstaller, InstallStep, SourcePreferences (+2 more)

### Community 4 - "Extension & Backup Core"
Cohesion: 0.08
Nodes (41): AppModule (DI), ComicInfo XML Generator, BackupManager, Extension Model, ExtensionInstaller, ExtensionManager, HttpSource, SourcePreferences (+33 more)

### Community 5 - "Library & History Management"
Cohesion: 0.08
Nodes (39): CategoryManagerScreen, DownloadQueueScreen, HistoryScreenModel, HistoryCard (Composable), HistoryScreen, LibraryDisplayMode (enum), LibraryItem, LibraryScreenModel (+31 more)

### Community 6 - "Settings & Download UI"
Cohesion: 0.08
Nodes (13): DownloadQueueScreen, BrowseSettingsScreen, PreferenceHeader(), PreferenceSliderItem(), PreferenceSwitchItem(), ReaderSettingsScreen, ReadingModeSelector(), SettingsCategoryItem() (+5 more)

### Community 7 - "Manga Data Repository"
Cohesion: 0.08
Nodes (3): SourceStat, StatisticsData, MangaRepositoryImpl

### Community 8 - "Backup Data Models"
Cohesion: 0.08
Nodes (33): BackupManager, Backup Data Model, BackupCategory Data Class, BackupChapter Data Class, BackupManga Data Class, App Color Theme Definitions, EmptyState Composable, ExtensionRepoRepositoryImpl (+25 more)

### Community 10 - "JS Web Engine"
Cohesion: 0.1
Nodes (6): AndroidAppBridge, VBookEngine, JsExtensionInfo, JsLoader, Page, JsSource

### Community 11 - "Source API Contracts"
Cohesion: 0.11
Nodes (26): CatalogueSource Interface, Filter Sealed Class, FilterList Class, MangasPage Data Class, Page Class, ProgressListener Interface, SChapter Interface, SChapterImpl Class (+18 more)

### Community 13 - "Settings UI Components"
Cohesion: 0.18
Nodes (20): DisplayPage(), FilterPage(), LibrarySettingsDialog(), SortPage(), BaseSettingsItem(), CheckboxItem(), HeadingItem(), MihonSettingsPaddings (+12 more)

### Community 14 - "Source Filter Types"
Cohesion: 0.08
Nodes (14): Filter, FilterList, Group, Header, Select, Selection, Separator, Sort (+6 more)

### Community 15 - "Extension Repo Layer"
Cohesion: 0.09
Nodes (3): ExtensionRepo, ExtensionRepoRepository, ExtensionRepoRepositoryImpl

### Community 16 - "Browse Screen Model"
Cohesion: 0.15
Nodes (21): ArchiveReader, BrowseScreenModel, BrowseScreen, CatalogueScreenModel, CatalogueScreen, EpubExporter, EpubReader, ExtensionInstaller (+13 more)

### Community 17 - "Download Management"
Cohesion: 0.12
Nodes (5): Download, State, DownloadManager, DownloadObject, DownloadStore

### Community 18 - "Catalogue Source Paging"
Cohesion: 0.12
Nodes (8): CatalogueScreenModel, Latest, Listing, Popular, Search, SourcePagingSource, create(), Manga

### Community 19 - "JS DOM Wrapper"
Cohesion: 0.13
Nodes (6): JsDocument, JsDocumentImpl, JsElement, JsElementImpl, JsEnvironment, JsResponse

### Community 20 - "App Icon & Branding"
Cohesion: 0.18
Nodes (17): Android Adaptive Icon System (square + round variants), Samurai Warrior Brand Artwork (app_icon.jpg), App Launcher Icon Design Concept, Android Default Placeholder Launcher Icon (Green/Robot), Android Default Placeholder Round Launcher Icon (Green/Robot), Manga Reader App Brand Identity, ic_launcher hdpi (72x72), ic_launcher_round hdpi (72x72) (+9 more)

### Community 21 - "Preference Store Layer"
Cohesion: 0.2
Nodes (5): Preference, PreferenceStore, subscribeToList(), subscribeToOne(), subscribeToOneOrNull()

### Community 23 - "Reader UI Components"
Cohesion: 0.22
Nodes (11): TtsPlayerBar(), TtsSettingsDialog(), ChapterTransitionPage(), PageImage(), ReaderDrawer(), ReaderPageContent(), ReaderSystemOverlay(), ReaderTopBar() (+3 more)

### Community 24 - "Extension API Client"
Cohesion: 0.17
Nodes (9): ExtensionApi, ExtensionJsonObject, ExtensionRepoJson, ExtensionRepoMetadata, ExtensionRepoMetaJson, ExtensionSourceJsonObject, VBookItemObject, VBookMetadataObject (+1 more)

### Community 25 - "Manga Detail Screen Model"
Cohesion: 0.14
Nodes (4): ChapterFilter, ChapterSort, MangaDetailScreenModel, Chapter

### Community 26 - "App Entry & Theme"
Cohesion: 0.15
Nodes (5): MainScreen, TabNavigationItem(), MainActivity, NotificationHelper, MangaReaderVer2Theme()

### Community 27 - "DEX Extension Loader"
Cohesion: 0.22
Nodes (3): ExtensionInfo, ExtensionLoader, ChildFirstPathClassLoader

### Community 30 - "EPUB File Reader"
Cohesion: 0.33
Nodes (3): EpubMetadata, EpubReader, TocEntry

### Community 34 - "Backup Operations"
Cohesion: 0.27
Nodes (6): BackupManager, Backup, BackupCategory, BackupChapter, BackupHistory, BackupManga

### Community 35 - "Domain Model Layer"
Cohesion: 0.31
Nodes (10): QueryExtensions.subscribeToList, Category Domain Model, Chapter Domain Model, History Domain Model, LibraryManga Domain Model, Manga Domain Model, MangaRepository Interface, SourceStat Domain Model (+2 more)

### Community 36 - "Multi-Module Build Config"
Cohesion: 0.22
Nodes (7): Root Build Gradle, Core Module, Data Module, Domain Module, Source-API Module, Source-DEX Module, Source-JS Module

### Community 37 - "Statistics Screen"
Cohesion: 0.28
Nodes (4): StatisticsScreen, SourceStat, StatisticsScreenModel, StatisticsState

### Community 42 - "Extension State Types"
Cohesion: 0.33
Nodes (5): Available, AvailableSource, Extension, Installed, Untrusted

### Community 43 - "JS Plugin Configuration"
Cohesion: 0.33
Nodes (3): Metadata, PluginConfig, ScriptConfig

### Community 44 - "Main Navigation Tabs"
Cohesion: 0.33
Nodes (6): BrowseScreen, HistoryScreen, LibraryScreen, SettingsScreen, UpdatesScreen, Navigation Tabs

### Community 47 - "Extension Load Result"
Cohesion: 0.4
Nodes (4): Error, LoadResult, Success, Untrusted

### Community 49 - "Source Chapter Model"
Cohesion: 0.4
Nodes (3): create(), SChapter, SChapterImpl

### Community 50 - "Source Manga Model"
Cohesion: 0.4
Nodes (3): create(), SManga, SMangaImpl

### Community 51 - "Global Search Pipeline"
Cohesion: 0.4
Nodes (5): BrowseSettingsScreen, GlobalSearchResult (sealed class), GlobalSearchScreenModel, GlobalSearchState, SourcePagingSource

### Community 53 - "Reusable Settings Items"
Cohesion: 0.5
Nodes (4): CheckboxItem Composable, HeadingItem Composable, SortItem Composable, TriStateItem Composable

### Community 54 - "Module Build Configs"
Cohesion: 0.5
Nodes (4): domain Module Build Config, source-api Module Build Config, source-dex Module Build Config, source-js Module Build Config

## Ambiguous Edges - Review These
- `Android Default Placeholder Launcher Icon (Green/Robot)` → `Manga Reader App Brand Identity`  [AMBIGUOUS]
  app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp · relation: conceptually_related_to

## Knowledge Gaps
- **144 isolated node(s):** `State`, `TriState`, `ExtensionRepoJson`, `ExtensionRepoMetaJson`, `ExtensionJsonObject` (+139 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **42 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **What is the exact relationship between `Android Default Placeholder Launcher Icon (Green/Robot)` and `Manga Reader App Brand Identity`?**
  _Edge tagged AMBIGUOUS (relation: conceptually_related_to) - confidence is low._
- **Why does `SourcesTab()` connect `Browse & Catalogue Screens` to `Extension API Client`?**
  _High betweenness centrality (0.085) - this node is a cross-community bridge._
- **Why does `ExtensionApi` connect `Extension API Client` to `App Configuration & DI`?**
  _High betweenness centrality (0.081) - this node is a cross-community bridge._
- **Why does `MangaRepositoryImpl` connect `Manga Data Repository` to `App Configuration & DI`?**
  _High betweenness centrality (0.049) - this node is a cross-community bridge._
- **Are the 2 inferred relationships involving `LibraryScreenModel` (e.g. with `.Content()` and `.Content()`) actually correct?**
  _`LibraryScreenModel` has 2 INFERRED edges - model-reasoned connections that need verification._
- **What connects `State`, `TriState`, `ExtensionRepoJson` to the rest of the system?**
  _144 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Browse & Catalogue Screens` be split into smaller, more focused modules?**
  _Cohesion score 0.07 - nodes in this community are weakly interconnected._