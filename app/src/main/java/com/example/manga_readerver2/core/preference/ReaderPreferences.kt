package com.example.manga_readerver2.core.preference

import com.example.manga_readerver2.core.utils.PreferenceStore
import com.example.manga_readerver2.features.reader.ReadingMode

class ReaderPreferences(preferenceStore: PreferenceStore) {
    enum class TapAction(val label: String) {
        PREVIOUS("Lùi / Cuộn Lên"),
        MENU("Mở Menu"),
        NEXT("Tiến / Cuộn Xuống"),
        NONE("Không làm gì")
    }

    val customTapZones = preferenceStore.getString(
        "custom_tap_zones",
        "0,1,2,0,1,2,0,1,2" // Default: Left=PREVIOUS(0), Mid=MENU(1), Right=NEXT(2)
    )

    val readingMode = preferenceStore.getInt("reader_mode", ReadingMode.VERTICAL.ordinal)
    
    // Manga Settings
    val doubleTapZoom = preferenceStore.getBoolean("double_tap_zoom", true)
    val showPageNumber = preferenceStore.getBoolean("show_page_number", true)
    val trueColor = preferenceStore.getBoolean("true_color", false)
    val readerTapNavigation = preferenceStore.getBoolean("reader_tap_navigation", true)
    val autoScrollSpeed = preferenceStore.getFloat("auto_scroll_speed", 1.0f)

    // Text Settings
    val fontSize = preferenceStore.getFloat("text_font_size_percent", 100f)
    val lineSpacing = preferenceStore.getFloat("text_line_spacing", 1.5f)
    val textAlign = preferenceStore.getInt("text_align", 0) // 0: Left, 1: Center, 2: Justify
    val theme = preferenceStore.getInt("text_theme", 0) // 0: Dark, 1: White, 2: Sepia

    // General Settings
    val keepScreenOn = preferenceStore.getBoolean("keep_screen_on", true)
    val fullscreen = preferenceStore.getBoolean("fullscreen", true)
    val colorFilterMode = preferenceStore.getInt("color_filter_mode", 0) // 0: None, 1: Grayscale, 2: Inverted, 3: Sepia
    val webtoonSidePadding = preferenceStore.getInt("webtoon_side_padding", 0)
    val cropBorders = preferenceStore.getBoolean("crop_borders", false)

    // TTS Settings
    val ttsVoice = preferenceStore.getString("tts_voice", "")
    val ttsPitch = preferenceStore.getFloat("tts_pitch", 1.0f)
    val ttsSpeechRate = preferenceStore.getFloat("tts_speech_rate", 1.0f)
    val customColors = preferenceStore.getBoolean("custom_colors", false)
    val customColorPrimary = preferenceStore.getInt("custom_color_primary", 0xFF6200EE.toInt())

    // Custom Color Filter Settings
    val customColorFilter = preferenceStore.getBoolean("custom_color_filter", false)
    val customColorFilterColor = preferenceStore.getInt("custom_color_filter_color", 0xFFB300) // Orange
    val customColorFilterAlpha = preferenceStore.getFloat("custom_color_filter_alpha", 0.2f)
    val customColorFilterBlendMode = preferenceStore.getInt("custom_color_filter_blend_mode", 0) // 0: Multiply, 1: Screen, 2: Overlay
    
    // Advanced Image Filters (Mihon-style)
    val invertColors = preferenceStore.getBoolean("invert_colors", false)
    val grayscale = preferenceStore.getBoolean("grayscale", false)
    
    // Hardware Navigation
    val volumeKeyNavigation = preferenceStore.getBoolean("volume_key_navigation", false)
    
    // Dual Page (Landscape)
    val dualPage = preferenceStore.getBoolean("dual_page", false)
    
    // Privacy Settings
    val autoDownloadAmount = preferenceStore.getInt("auto_download_amount", 0)

    companion object {
        const val DEVICE_ORIENTATION_FREE = 0
        const val DEVICE_ORIENTATION_PORTRAIT = 1
        const val DEVICE_ORIENTATION_LANDSCAPE = 2
    }
}
