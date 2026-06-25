package com.example.manga_readerver2

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.SlideTransition
import com.example.manga_readerver2.core.notification.NotificationHelper
import com.example.manga_readerver2.core.utils.PermissionManager
import com.example.manga_readerver2.features.main.MainScreen
import com.example.manga_readerver2.ui.theme.MangaReaderVer2Theme
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import com.example.manga_readerver2.core.security.AppLockManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import android.view.KeyEvent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.manga_readerver2.core.utils.VolumeKeyDispatcher

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        NotificationHelper(this).createNotificationChannels()
        PermissionManager.requestNotificationPermission(this, 101)

        handleIntent(intent)

        val displayPreferences = Injekt.get<com.example.manga_readerver2.core.preference.DisplayPreferences>()

        setContent {
            val appTheme by androidx.compose.runtime.remember { displayPreferences.appTheme.asFlow() }.collectAsState(initial = displayPreferences.appTheme.get())
            val dynamicColor by androidx.compose.runtime.remember { displayPreferences.dynamicColor.asFlow() }.collectAsState(initial = displayPreferences.dynamicColor.get())
            val pureBlack by androidx.compose.runtime.remember { displayPreferences.pureBlack.asFlow() }.collectAsState(initial = displayPreferences.pureBlack.get())

            MangaReaderVer2Theme(appTheme = appTheme, dynamicColor = dynamicColor, pureBlack = pureBlack) {
                val isLocked by AppLockManager.isLocked.collectAsState()
                if (isLocked) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                            .clickable { AppLockManager.promptUnlock(this@MainActivity) },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "App Locked",
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Nhấn để mở khóa", color = MaterialTheme.colorScheme.onBackground)
                        }
                    }
                } else {
                    Navigator(MainScreen()) { navigator ->
                        SlideTransition(navigator)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        AppLockManager.onResume(this)
    }

    override fun onPause() {
        super.onPause()
        AppLockManager.onPause()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: android.content.Intent?) {
        if (intent != null && intent.action == android.content.Intent.ACTION_VIEW) {
            val data = intent.data
            if (data != null && data.scheme == "tachiyomi" && data.host == "anilist-auth") {
                val fragment = data.fragment
                if (fragment != null) {
                    val params = fragment.split("&")
                    for (param in params) {
                        val keyValue = param.split("=")
                        if (keyValue.size == 2 && keyValue[0] == "access_token") {
                            val token = keyValue[1]
                            val trackPreferences = Injekt.get<com.example.manga_readerver2.core.track.TrackPreferences>()
                            trackPreferences.anilistToken.set(token)
                            android.widget.Toast.makeText(this, "Đăng nhập AniList thành công!", android.widget.Toast.LENGTH_SHORT).show()
                            break
                        }
                    }
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (VolumeKeyDispatcher.isReaderActive) {
            val readerPreferences = Injekt.get<com.example.manga_readerver2.core.preference.ReaderPreferences>()
            if (readerPreferences.volumeKeysNavigation.get()) {
                when (keyCode) {
                    KeyEvent.KEYCODE_VOLUME_DOWN -> {
                        VolumeKeyDispatcher.dispatch(VolumeKeyDispatcher.VolumeEvent.DOWN)
                        return true // Consume event
                    }
                    KeyEvent.KEYCODE_VOLUME_UP -> {
                        VolumeKeyDispatcher.dispatch(VolumeKeyDispatcher.VolumeEvent.UP)
                        return true // Consume event
                    }
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }
}
