package com.nbradbury.wpfolio.data

import android.content.Context
import android.util.Log
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.nbradbury.wpfolio.data.db.PostDao
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val draining = Mutex()

    init {
        // Room already knows which posts lack an accent, so watch that rather than every write path.
        scope.launch {
            postDao.observeNeedingAccentCount().filter { it > 0 }.conflate().collect {
                // A failed pass (a database error, say) mustn't crash the app; the next write retries.
                try {
                    sampleNow()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Accent sampling failed", e)
                }
            }
        }
    }

    /** Samples every post still missing an accent, returning when they're done. */
    suspend fun sampleNow() = draining.withLock { drain() }

    private suspend fun drain() {
        while (true) {
            val batch = postDao.imagesNeedingAccent(BATCH)
            if (batch.isEmpty()) return
            // A few at a time, each group saved as it finishes: colours show up quickly, a
            // cancelled pass keeps what it sampled, and observers still re-query once per group.
            for (group in batch.chunked(PARALLEL)) {
                val accents = coroutineScope {
                    group.map { url -> async { url to (sample(url) ?: Accent(fallbackAccent(url), null)) } }.awaitAll()
                }
                postDao.setAccents(accents.toMap())
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
        private const val PARALLEL = 4
        private const val TAG = "AccentSampler"
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
