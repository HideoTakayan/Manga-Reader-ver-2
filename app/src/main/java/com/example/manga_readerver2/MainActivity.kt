package com.example.manga_readerver2

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import cafe.adriel.voyager.navigator.Navigator
import com.example.manga_readerver2.core.notification.NotificationHelper
import com.example.manga_readerver2.core.utils.PermissionManager
import com.example.manga_readerver2.features.main.MainScreen
import com.example.manga_readerver2.ui.theme.MangaReaderVer2Theme

import androidx.lifecycle.lifecycleScope
import com.example.manga_readerver2.core.source.ExtensionManager
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NotificationHelper(this).createNotificationChannels()
        PermissionManager.requestNotificationPermission(this, 101)

        setContent {
            MangaReaderVer2Theme {
                Navigator(MainScreen())
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            try {
                Injekt.get<ExtensionManager>().loadLocalExtensions()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
}
