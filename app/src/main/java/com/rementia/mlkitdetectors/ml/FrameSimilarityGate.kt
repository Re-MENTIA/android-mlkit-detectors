package com.rementia.mlkitdetectors.ml

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.imageembedder.ImageEmbedder
import com.google.mediapipe.framework.image.BitmapImageBuilder
import kotlin.math.sqrt

/**
 * 通話（朱里）接続直前のフレームに対して、
 * MobileNetV3埋め込み＋cos類似度で前回受理フレームとの差分を判定するゲート。
 * - 類似度 < THRESHOLD → 受理（別状態）
 * - 類似度 ≥ THRESHOLD → 拒否（同状態での連続接続を抑止）
 *
 * モデルが未配置でも安全に失敗し、常に受理(true)を返すフォールバックを持つ。
 */
class FrameSimilarityGate(context: Context) {

    companion object {
        private const val TAG = "FrameSimilarityGate"
        private const val MODEL_ASSET = "mobilenet_v3_embedder.tflite"
        private const val THRESHOLD = 0.919f
    }

    private val appContext = context.applicationContext
    private var embedder: ImageEmbedder? = null
    @Volatile private var prevEmb: FloatArray? = null

    init {
        try {
            val base = BaseOptions.builder()
                .setModelAssetPath(MODEL_ASSET)
                .build()
            val opts = ImageEmbedder.ImageEmbedderOptions.builder()
                .setBaseOptions(base)
                .setL2Normalize(true)
                .build()
            embedder = ImageEmbedder.createFromOptions(appContext, opts)
        } catch (t: Throwable) {
            Log.w(TAG, "Embedder init failed (model missing?): ${t.message}")
            embedder = null
        }
    }

    private fun cosine(a: FloatArray, b: FloatArray): Float {
        var dot = 0f; var na = 0f; var nb = 0f
        for (i in a.indices) { val ai=a[i]; val bi=b[i]; dot += ai*bi; na += ai*ai; nb += bi*bi }
        return dot / (sqrt(na) * sqrt(nb) + 1e-9f)
    }

    private fun embed(bitmap: Bitmap): FloatArray? {
        val emb = embedder ?: return null
        val mp = BitmapImageBuilder(bitmap).build()
        return try {
            emb.embed(mp).embeddings()[0].floatEmbedding()
        } catch (t: Throwable) {
            Log.w(TAG, "Embed failed: ${t.message}")
            null
        }
    }

    /**
     * 判定（true: 受理 / false: 拒否）
     */
    fun shouldAccept(frame: Bitmap): Boolean {
        val vec = embed(frame) ?: return true // モデル未配置など→常に受理
        val sim = prevEmb?.let { cosine(it, vec) } ?: -1f
        val accept = (prevEmb == null) || (sim < THRESHOLD)
        if (accept) prevEmb = vec
        return accept
    }

    fun reset() { prevEmb = null }
}

