package com.example.manga_readerver2.features.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.tab.CurrentTab
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabNavigator
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import com.example.manga_readerver2.core.navigation.*
import com.example.manga_readerver2.ui.theme.BackgroundDark
import com.example.manga_readerver2.ui.theme.PrimaryOrange

class MainScreen : Screen {
    @Composable
    override fun Content() {
        TabNavigator(LibraryTab) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = {
                    NavigationBar(
                        containerColor = Color(0xFF0E0E10),
                        contentColor = PrimaryOrange
                    ) {
                        TabNavigationItem(LibraryTab)
                        TabNavigationItem(UpdatesTab)
                        TabNavigationItem(HistoryTab)
                        TabNavigationItem(BrowseTab)
                        TabNavigationItem(SettingsTab)
                    }
                }
            ) { innerPadding ->
                Box(modifier = Modifier.padding(innerPadding)) {
                    val tabNavigator = LocalTabNavigator.current
                    AnimatedContent(
                        targetState = tabNavigator.current,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
                        },
                        label = "TabTransition"
                    ) { tab ->
                        androidx.compose.runtime.CompositionLocalProvider(
                            com.example.manga_readerver2.core.navigation.LocalNavAnimatedVisibilityScope provides this
                        ) {
                            tab.Content()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.TabNavigationItem(tab: Tab) {
    val tabNavigator = LocalTabNavigator.current

    NavigationBarItem(
        selected = tabNavigator.current == tab,
        onClick = { tabNavigator.current = tab },
        icon = { Icon(painter = tab.options.icon!!, contentDescription = tab.options.title) },
        label = { Text(tab.options.title) },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = PrimaryOrange,
            selectedTextColor = PrimaryOrange,
            indicatorColor = Color.White.copy(alpha = 0.1f),
            unselectedIconColor = Color.Gray,
            unselectedTextColor = Color.Gray
        )
    )
}
