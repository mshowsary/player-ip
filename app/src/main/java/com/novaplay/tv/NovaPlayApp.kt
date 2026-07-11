package com.novaplay.tv

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class NovaPlayApp : Application(), ImageLoaderFactory {

    // Modest memory cache: target hardware is 1-2 GB boxes. Providers often send
    // no-cache headers on logos, so we ignore cache headers and rely on our own TTL.
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.15)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(128L * 1024 * 1024)
                    .build()
            }
            .respectCacheHeaders(false)
            .crossfade(true)
            .build()
}
