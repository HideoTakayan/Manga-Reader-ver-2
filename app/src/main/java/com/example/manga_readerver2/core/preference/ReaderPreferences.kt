package com.example.manga_readerver2.core.preference

import com.example.manga_readerver2.core.utils.PreferenceStore
import com.example.manga_readerver2.features.reader.ReadingMode

class ReaderPreferences(preferenceStore: PreferenceStore) {
    val readingMode = preferenceStore.getInt("reader_mode", ReadingMode.VERTICAL.ordinal)
    
    // Manga Settings
    val doubleTapZoom = preferenceStore.getBoolean("double_tap_zoom", true)
    val showPageNumber = preferenceStore.getBoolean("show_page_number", true)
    val trueColor = preferenceStore.getBoolean("true_color", false)
    val volumeKeysNavigation = preferenceStore.getBoolean("volume_keys_navigation", false)
    val readerTapNavigation = preferenceStore.getBoolean("reader_tap_navigation", true)

    // Text Settings
    val fontSize = preferenceStore.getFloat("text_font_size", 18f)
    val lineSpacing = preferenceStore.getFloat("text_line_spacing", 1.5f)
    val textAlign = preferenceStore.getInt("text_align", 0) // 0: Left, 1: Center, 2: Justify
    val theme = preferenceStore.getInt("text_theme", 0) // 0: Dark, 1: White, 2: Sepia

    // General Settings
    val keepScreenOn = preferenceStore.getBoolean("keep_screen_on", true)
    val fullscreen = preferenceStore.getBoolean("fullscreen", true)
    val colorFilterMode = preferenceStore.getInt("color_filter_mode", 0) // 0: None, 1: Grayscale, 2: Inverted, 3: Sepia
    val webtoonSidePadding = preferenceStore.getInt("webtoon_side_padding", 0)

    // TTS Settings
    val ttsVoice = preferenceStore.getString("tts_voice", "")
    val ttsPitch = preferenceStore.getFloat("tts_pitch", 1.0f)
    val ttsSpeechRate = preferenceStore.getFloat("tts_speech_rate", 1.0f)
    val incognitoMode = preferenceStore.getBoolean("incognito_mode", false)
    val autoDownloadAmount = preferenceStore.getInt("auto_download_amount", 0)

    companion object {
        const val DEVICE_ORIENTATION_FREE = 0
        const val DEVICE_ORIENTATION_PORTRAIT = 1
        const val DEVICE_ORIENTATION_LANDSCAPE = 2
    }
}
