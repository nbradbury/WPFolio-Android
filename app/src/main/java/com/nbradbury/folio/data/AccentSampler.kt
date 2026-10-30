package com.nbradbury.folio.data

import android.content.Context
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.nbradbury.folio.data.db.PostDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class Accent(val color: Int, val imageRatio: Float?)

/**
 * Samples each featured image's accent colour (used for tints, the progress bar and avatars)
 * and its aspect ratio (used by the masonry grid), and caches both on the post rows.
 */
class AccentSampler(
    private val context: Context,
    private val imageLoader: ImageLoader,
    private val postDao: PostDao,
    scope: CoroutineScope,
) {
    private val trigger = Channel<Unit>(Channel.CONFLATED)

    init {
        scope.launch {
            for (ignored in trigger) drain()
        }
    }

    fun requestSampling() {
        trigger.trySend(Unit)
    }

    private suspend fun drain() {
        while (true) {
            val batch = postDao.needingAccent(BATCH).mapNotNull { it.imageUrl }.distinct()
            if (batch.isEmpty()) return
            for (url in batch) {
                val accent = sample(url) ?: Accent(fallbackAccent(url), null)
                postDao.setAccent(url, accent.color, accent.imageRatio)
            }
        }
    }

    suspend fun sample(url: String): Accent? {
        val request = ImageRequest.Builder(context)
            .data(url)
            .size(SAMPLE_SIZE)
            .allowHardware(false)
            .build()
        val result = imageLoader.execute(request) as? SuccessResult ?: return null
        val bitmap = result.image.toBitmap()
        val ratio = if (bitmap.height > 0) bitmap.width.toFloat() / bitmap.height else null
        val color = withContext(Dispatchers.Default) {
            val palette = Palette.from(bitmap).maximumColorCount(16).generate()
            val swatch = palette.darkMutedSwatch ?: palette.mutedSwatch ?: palette.darkVibrantSwatch
                ?: palette.dominantSwatch
            swatch?.rgb?.let(::darkenForWhiteText)
        } ?: return null
        return Accent(color, ratio)
    }

    companion object {
        private const val BATCH = 12
        private const val SAMPLE_SIZE = 96
        private const val MAX_LUMINANCE = 0.12

        /** Accents taken from the design, used when an image can't be sampled. */
        private val FALLBACKS = intArrayOf(
            0xFF3D4F6B.toInt(), 0xFF7A3B1F.toInt(), 0xFF5B3A24.toInt(), 0xFF6B5A3E.toInt(),
            0xFF2F4A36.toInt(), 0xFF1F2A4A.toInt(), 0xFF2E3440.toInt(), 0xFF5A2430.toInt(),
            0xFF6B4A52.toInt(), 0xFF2C5560.toInt(), 0xFF3F5A2A.toInt(),
        )

        fun fallbackAccent(key: String): Int = FALLBACKS[Math.floorMod(key.hashCode(), FALLBACKS.size)]

        /** Captions sit in white on the accent, so keep it dark enough for contrast. */
        fun darkenForWhiteText(color: Int): Int {
            var c = color
            var steps = 0
            while (ColorUtils.calculateLuminance(c) > MAX_LUMINANCE && steps < 10) {
                c = ColorUtils.blendARGB(c, android.graphics.Color.BLACK, 0.15f)
                steps++
            }
            return c
        }
    }
}
