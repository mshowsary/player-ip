package com.novaplay.tv.ui.player

/** ExoPlayer load-control durations, all in milliseconds. */
data class BufferSpec(
    val minBufferMs: Int,
    val maxBufferMs: Int,
    val playbackBufferMs: Int,
    val rebufferMs: Int,
)

/**
 * RAM-class playback buffering. Low-memory boxes (1–2 GB) get a smaller
 * ceiling so the media buffer never competes with the UI for heap — large
 * buffers on those devices cause GC churn and jank, not smoothness. Startup
 * thresholds stay identical everywhere so zapping feels equally fast.
 */
object PlaybackBufferPolicy {

    /** Live: small startup threshold for instant zapping; ceiling by device class. */
    fun liveBuffers(lowRamDevice: Boolean): BufferSpec = if (lowRamDevice) {
        BufferSpec(
            minBufferMs = 10_000,
            maxBufferMs = 15_000,
            playbackBufferMs = 1_200,
            rebufferMs = 2_400,
        )
    } else {
        BufferSpec(
            minBufferMs = 15_000,
            maxBufferMs = 30_000,
            playbackBufferMs = 1_200,
            rebufferMs = 2_400,
        )
    }

    /** VOD: deeper buffers absorb seeks and bitrate spikes; ceiling by device class. */
    fun vodBuffers(lowRamDevice: Boolean): BufferSpec = if (lowRamDevice) {
        BufferSpec(
            minBufferMs = 12_000,
            maxBufferMs = 20_000,
            playbackBufferMs = 2_000,
            rebufferMs = 4_000,
        )
    } else {
        BufferSpec(
            minBufferMs = 20_000,
            maxBufferMs = 50_000,
            playbackBufferMs = 2_000,
            rebufferMs = 4_000,
        )
    }
}
