package com.example.manga_readerver2.core.utils

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object VolumeKeyDispatcher {
    private val _events = MutableSharedFlow<VolumeEvent>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    // Flag to determine if the ReaderScreen is currently active
    var isReaderActive: Boolean = false

    fun dispatch(event: VolumeEvent) {
        _events.tryEmit(event)
    }

    enum class VolumeEvent {
        UP, DOWN
    }
}
