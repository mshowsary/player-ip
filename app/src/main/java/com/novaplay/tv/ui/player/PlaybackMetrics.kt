package com.novaplay.tv.ui.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-lifetime playback timing, surfaced in Sync & health so testers can
 * read zap speed off the screen instead of guessing. Numbers only — nothing
 * identifying a provider or stream is ever stored here.
 */
@Singleton
class PlaybackMetrics @Inject constructor() {

    private val _lastZapMs = MutableStateFlow<Long?>(null)
    val lastZapMs: StateFlow<Long?> = _lastZapMs.asStateFlow()

    private val _worstZapMs = MutableStateFlow<Long?>(null)
    val worstZapMs: StateFlow<Long?> = _worstZapMs.asStateFlow()

    /** Records one channel start: from zap request to the first rendered-ready state. */
    fun recordZap(durationMs: Long) {
        if (durationMs <= 0) return
        _lastZapMs.value = durationMs
        _worstZapMs.value = maxOf(_worstZapMs.value ?: 0, durationMs)
    }
}
