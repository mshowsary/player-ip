package com.novaplay.tv.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Renders [content] as a QR code on a white quiet-zone card so any phone
 * camera can read it off a TV panel. Encoding happens once per content value;
 * the tiny module bitmap is scaled up without smoothing so edges stay crisp.
 */
@Composable
fun QrImage(
    content: String,
    modifier: Modifier = Modifier,
    size: Dp = 160.dp,
) {
    val bitmap = remember(content) { encodeQr(content) } ?: return
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = "QR code",
        contentScale = ContentScale.FillBounds,
        filterQuality = FilterQuality.None,
        modifier = modifier
            .size(size)
            .background(Color.White)
            .padding(8.dp),
    )
}

private fun encodeQr(content: String): Bitmap? = runCatching {
    val matrix = QRCodeWriter().encode(
        content,
        BarcodeFormat.QR_CODE,
        0,
        0,
        mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 0,
        ),
    )
    val width = matrix.width
    val height = matrix.height
    val pixels = IntArray(width * height) { index ->
        val x = index % width
        val y = index / width
        if (matrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE
    }
    Bitmap.createBitmap(pixels, width, height, Bitmap.Config.RGB_565)
}.getOrNull()
