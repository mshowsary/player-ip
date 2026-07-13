package com.novaplay.tv.ui.player

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-lifetime memory for the touch gesture demo: it plays with the first
 * live playback after every app launch and stays away for the rest of that
 * session — zapping or reopening the player never repeats it.
 */
@Singleton
class GestureHintSession @Inject constructor() {
    var shown: Boolean = false
}
