package com.novaplay.tv.core

import java.util.Locale

/**
 * Human-readable byte amounts for download progress ("845 KB", "12.3 MB").
 * Binary units, one decimal from MB up — coarse on purpose: this labels a
 * progress bar, it is not an exact accounting of the transfer.
 */
object DataSize {
    private const val KB = 1024L
    private const val MB = KB * 1024
    private const val GB = MB * 1024

    fun format(bytes: Long): String = when {
        bytes < 0 -> ""
        bytes < KB -> "$bytes B"
        bytes < MB -> "${bytes / KB} KB"
        bytes < GB -> String.format(Locale.ROOT, "%.1f MB", bytes.toDouble() / MB)
        else -> String.format(Locale.ROOT, "%.1f GB", bytes.toDouble() / GB)
    }
}
