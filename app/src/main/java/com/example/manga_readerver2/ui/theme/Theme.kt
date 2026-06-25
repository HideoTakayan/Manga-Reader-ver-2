package com.example.manga_readerver2.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DefaultColorScheme = darkColorScheme(
    primary = PrimaryOrange,
    secondary = TextSecondary,
    background = BackgroundDark,
    surface = SurfaceDark,
    onPrimary = TextPrimary,
    onSecondary = TextPrimary,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceDark,
    onSurfaceVariant = TextSecondary
)

private val GreenAppleColorScheme = DefaultColorScheme.copy(
    primary = androidx.compose.ui.graphics.Color(0xFF4CAF50), // Green
)

private val LavenderColorScheme = DefaultColorScheme.copy(
    primary = androidx.compose.ui.graphics.Color(0xFFAB47BC), // Purple
)

private val StrawberryColorScheme = DefaultColorScheme.copy(
    primary = androidx.compose.ui.graphics.Color(0xFFE53935), // Red
)

private val MidnightDuskColorScheme = DefaultColorScheme.copy(
    primary = androidx.compose.ui.graphics.Color(0xFFF06292), // Pink/Red Accent
    background = androidx.compose.ui.graphics.Color(0xFF16151D), // Dark Blueish
    surface = androidx.compose.ui.graphics.Color(0xFF211F2D), // Lighter Blueish
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFF211F2D)
)

@Composable
fun MangaReaderVer2Theme(
    appTheme: String = "DEFAULT",
    dynamicColor: Boolean = true,
    pureBlack: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    var colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            dynamicDarkColorScheme(context)
        }
        else -> when (appTheme) {
            "GREEN_APPLE" -> GreenAppleColorScheme
            "LAVENDER" -> LavenderColorScheme
            "STRAWBERRY" -> StrawberryColorScheme
            "MIDNIGHT_DUSK" -> MidnightDuskColorScheme
            else -> DefaultColorScheme
        }
    }

    if (pureBlack) {
        colorScheme = colorScheme.copy(
            background = androidx.compose.ui.graphics.Color.Black,
            surface = androidx.compose.ui.graphics.Color.Black
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = androidx.compose.ui.graphics.Color.Transparent.toArgb()
            window.navigationBarColor = androidx.compose.ui.graphics.Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
            WindowCompat.setDecorFitsSystemWindows(window, false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.attributes.layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
