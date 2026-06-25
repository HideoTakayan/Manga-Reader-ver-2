package com.example.manga_readerver2.core.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions
import com.example.manga_readerver2.features.browse.BrowseScreen
import com.example.manga_readerver2.features.library.LibraryScreen
import com.example.manga_readerver2.features.settings.SettingsScreen
import com.example.manga_readerver2.features.updates.UpdatesScreen
import com.example.manga_readerver2.features.history.HistoryScreen

object LibraryTab : Tab {
    override val options: TabOptions
        @Composable
        get() {
            val icon = rememberVectorPainter(Icons.Default.CollectionsBookmark)
            return remember { TabOptions(index = 0u, title = "ThÆ° viá»‡n", icon = icon) }
        }

    @Composable
    override fun Content() {
        LibraryScreen().Content()
    }
}

object UpdatesTab : Tab {
    override val options: TabOptions
        @Composable
        get() {
            val icon = rememberVectorPainter(Icons.Filled.NewReleases)
            return remember { TabOptions(index = 1u, title = "Cáº­p nháº­t", icon = icon) }
        }

    @Composable
    override fun Content() {
        UpdatesScreen().Content()
    }
}

object HistoryTab : Tab {
    override val options: TabOptions
        @Composable
        get() {
            val icon = rememberVectorPainter(Icons.Default.History)
            return remember { TabOptions(index = 2u, title = "Lá»‹ch sá»­", icon = icon) }
        }

    @Composable
    override fun Content() {
        HistoryScreen().Content()
    }
}

object BrowseTab : Tab {
    override val options: TabOptions
        @Composable
        get() {
            val icon = rememberVectorPainter(Icons.Default.Explore)
            return remember { TabOptions(index = 3u, title = "KhĂ¡m phĂ¡", icon = icon) }
        }

    @Composable
    override fun Content() {
        BrowseScreen().Content()
    }
}

object SettingsTab : Tab {
    override val options: TabOptions
        @Composable
        get() {
            val icon = rememberVectorPainter(Icons.Default.MoreHoriz)
            return remember { TabOptions(index = 4u, title = "ThĂªm", icon = icon) }
        }

    @Composable
    override fun Content() {
        SettingsScreen().Content()
    }
}



