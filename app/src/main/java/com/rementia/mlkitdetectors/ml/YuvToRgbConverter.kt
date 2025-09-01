package com.rementia.mlkitdetectors.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.PixelFormat
import android.media.Image
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicYuvToRGB
import android.renderscript.Type

/**
 * 簡易 YUV(NV21/420)→ARGB 変換。
 * ※ RenderScript は非推奨だがデモ用に最短で実装。
 *   将来的には ScriptIntrinsicYuvToRGB 代替（ScriptC / GPU / libyuv）に置き換え推奨。
 */
@Suppress("DEPRECATION")
class YuvToRgbConverter(context: Context) {
    private val rs = RenderScript.create(context.applicationContext)
    private val script = ScriptIntrinsicYuvToRGB.create(rs, Element.U8_4(rs))
    private var yuvType: Type? = null
    private var rgbaType: Type? = null
    private var inputAlloc: Allocation? = null
    private var outputAlloc: Allocation? = null

    fun convert(image: Image, output: Bitmap) {
        if (image.format != ImageFormat.YUV_420_888) throw IllegalArgumentException("Unsupported format")
        val y = image.planes[0].buffer
        val u = image.planes[1].buffer
        val v = image.planes[2].buffer

        val ySize = y.remaining()
        val uSize = u.remaining()
        val vSize = v.remaining()
        val nv21 = ByteArray(ySize + uSize + vSize)
        y.get(nv21, 0, ySize)
        // NV21: VU order
        val uBytes = ByteArray(uSize)
        val vBytes = ByteArray(vSize)
        u.get(uBytes, 0, uSize)
        v.get(vBytes, 0, vSize)
        // interleave VU
        var offset = ySize
        for (i in vBytes.indices) {
            nv21[offset++] = vBytes[i]
            if (i < uBytes.size) nv21[offset++] = uBytes[i]
        }

        if (yuvType == null || yuvType?.x != nv21.size) {
            yuvType = Type.createX(rs, Element.U8(rs), nv21.size)
            inputAlloc = Allocation.createTyped(rs, yuvType, Allocation.USAGE_SCRIPT)
        }
        if (rgbaType == null || rgbaType?.x != output.width || rgbaType?.y != output.height) {
            rgbaType = Type.Builder(rs, Element.RGBA_8888(rs)).setX(output.width).setY(output.height).create()
            outputAlloc = Allocation.createTyped(rs, rgbaType, Allocation.USAGE_SCRIPT)
        }

        inputAlloc!!.copyFrom(nv21)
        script.setInput(inputAlloc)
        script.forEach(outputAlloc)
        outputAlloc!!.copyTo(output)
    }
}

