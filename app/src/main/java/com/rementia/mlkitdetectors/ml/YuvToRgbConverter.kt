package com.rementia.mlkitdetectors.ml

import android.graphics.Bitmap
import android.media.Image
import android.graphics.ImageFormat
import java.nio.ByteBuffer
import kotlin.math.max

/**
 * Lightweight YUV_420_888 -> ARGB converter without RenderScript.
 * Handles arbitrary row/pixel strides.
 */
class YuvToRgbConverter {
    fun convert(image: Image, output: Bitmap) {
        require(image.format == ImageFormat.YUV_420_888)
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val width = image.width
        val height = image.height
        val argb = IntArray(width * height)

        val yBuf = yPlane.buffer
        val uBuf = uPlane.buffer
        val vBuf = vPlane.buffer

        val yRowStride = yPlane.rowStride
        val yPixStride = yPlane.pixelStride
        val uRowStride = uPlane.rowStride
        val uPixStride = uPlane.pixelStride
        val vRowStride = vPlane.rowStride
        val vPixStride = vPlane.pixelStride

        for (y in 0 until height) {
            for (x in 0 until width) {
                val yIndex = yRowStride * y + yPixStride * x
                val uvX = x / 2
                val uvY = y / 2
                val uIndex = uRowStride * uvY + uPixStride * uvX
                val vIndex = vRowStride * uvY + vPixStride * uvX

                val Y = (yBuf[yIndex].toInt() and 0xFF)
                val U = (uBuf[uIndex].toInt() and 0xFF) - 128
                val V = (vBuf[vIndex].toInt() and 0xFF) - 128

                var r = (Y + 1.370705f * V).toInt()
                var g = (Y - 0.337633f * U - 0.698001f * V).toInt()
                var b = (Y + 1.732446f * U).toInt()
                if (r < 0) r = 0 else if (r > 255) r = 255
                if (g < 0) g = 0 else if (g > 255) g = 255
                if (b < 0) b = 0 else if (b > 255) b = 255

                argb[y * width + x] = -0x1000000 or (r shl 16) or (g shl 8) or b
            }
        }

        output.setPixels(argb, 0, width, 0, 0, width, height)
    }
}

