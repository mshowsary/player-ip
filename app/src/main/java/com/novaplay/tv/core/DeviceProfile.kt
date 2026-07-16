package com.novaplay.tv.core

import android.app.ActivityManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hardware class of the current device, resolved once. Drives the RAM-class
 * decisions (playback buffers; the image loader applies the same signal
 * independently at Application startup, before injection is available).
 */
@Singleton
class DeviceProfile @Inject constructor(
    @ApplicationContext context: Context,
) {
    /** True for Android's official low-RAM class — the 1–2 GB IPTV boxes. */
    val isLowRamDevice: Boolean =
        context.getSystemService(ActivityManager::class.java)?.isLowRamDevice == true
}
