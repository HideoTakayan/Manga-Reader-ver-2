package com.example.manga_readerver2.core.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import logcat.logcat
import java.util.*

/**
 * Manager for Android Text-To-Speech.
 * Handles initialization, speaking, and parameter controls (speed, pitch).
 */
class TtsManager(private val context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = TextToSpeech(context, this)
    
    private val _isInitialized = MutableStateFlow(false)
    val isInitialized = _isInitialized.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _currentParagraphIndex = MutableStateFlow(-1)
    val currentParagraphIndex = _currentParagraphIndex.asStateFlow()

    private var currentParagraphs: List<String> = emptyList()
    // Cất giữ lại vị trí index hiện tại trước khi gọi hàm stop() nhằm hỗ trợ quá trình Resume diễn ra chính xác
    var pausedAtIndex: Int = -1
        private set
    
    val availableVoices = MutableStateFlow<List<Voice>>(emptyList())
    val selectedVoice = MutableStateFlow<Voice?>(null)

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("vi", "VN"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                logcat { "TTS: Vietnamese not supported natively." }
            }
            
            try {
                val voices = tts?.voices?.filter { 
                    it.locale.language == "vi" || it.locale.language == "en" 
                }?.sortedBy { it.name } ?: emptyList()
                
                availableVoices.value = voices
                selectedVoice.value = tts?.voice ?: voices.firstOrNull()
            } catch (e: Exception) {
                logcat { "Lỗi lấy danh sách Voice: ${e.message}" }
            }

            _isInitialized.value = true
            setupCallbacks()
        } else {
            logcat { "TTS: Initialization failed" }
        }
    }

    private val _onComplete = kotlinx.coroutines.flow.MutableSharedFlow<Unit>()
    val onComplete = _onComplete.asSharedFlow()

    private fun setupCallbacks() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _isPlaying.value = true
                utteranceId?.toIntOrNull()?.let { _currentParagraphIndex.value = it }
            }

            override fun onDone(utteranceId: String?) {
                val idx = utteranceId?.toIntOrNull() ?: -1
                if (idx >= currentParagraphs.size - 1) {
                    _isPlaying.value = false
                    // Phát tín hiệu hoàn tất (completion) để hệ thống tự động chuyển sang chương tiếp theo
                    kotlinx.coroutines.GlobalScope.launch {
                        _onComplete.emit(Unit)
                    }
                }
            }

            override fun onError(utteranceId: String?) {
                _isPlaying.value = false
            }
        })
    }

    fun speak(paragraphs: List<String>, startIndex: Int = 0) {
        if (tts == null) {
            // Khởi tạo lại hệ thống Text-to-Speech nếu trước đó đã bị giải phóng (released)
            reinitialize()
            return 
        }
        if (!_isInitialized.value) return
        
        currentParagraphs = paragraphs
        tts?.stop() // Xóa hàng đợi cũ
        
        paragraphs.forEachIndexed { index, text ->
            if (index >= startIndex && text.isNotBlank()) {
                val params = android.os.Bundle()
                params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, index.toString())
                tts?.speak(text, TextToSpeech.QUEUE_ADD, params, index.toString())
            }
        }
    }

    fun setVoice(voice: Voice) {
        tts?.voice = voice
        selectedVoice.value = voice
        restartFromCurrent()
    }

    fun pause() {
        // Ghi nhận lại vị trí index hiện tại do hàm stop() sẽ thiết lập lại currentParagraphIndex
        // (Do Android TTS không cung cấp API pause() nguyên bản, ta phải mô phỏng lại thông qua stop() và resume())
        pausedAtIndex = _currentParagraphIndex.value.coerceAtLeast(0)
        tts?.stop()
        _isPlaying.value = false
    }

    fun stop() {
        tts?.stop()
        _isPlaying.value = false
        _currentParagraphIndex.value = -1
        pausedAtIndex = -1
        currentParagraphs = emptyList()
    }

    fun setSpeed(speed: Float) {
        tts?.setSpeechRate(speed)
        restartFromCurrent()
    }

    fun setPitch(pitch: Float) {
        tts?.setPitch(pitch)
        restartFromCurrent()
    }

    private fun restartFromCurrent() {
        if (_isPlaying.value && _currentParagraphIndex.value != -1) {
            val idx = _currentParagraphIndex.value
            speak(currentParagraphs, idx)
        }
    }

    private fun reinitialize() {
        _isInitialized.value = false
        tts = TextToSpeech(context, this)
    }

    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        _isInitialized.value = false
    }
}
