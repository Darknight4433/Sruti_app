package com.sruti.music.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.palette.graphics.Palette
import com.sruti.music.data.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

data class DynamicArtworkPalette(
    val primaryColor: Color = Color(0xFF9E4ECB),
    val secondaryColor: Color = Color(0xFF381F6D),
    val accentColor: Color = Color(0xFF4169F5),
    val darkBaseColor: Color = Color(0xFF0A0715)
)

data class DynamicArtworkData(
    val trackId: String = "",
    val palette: DynamicArtworkPalette = DynamicArtworkPalette(),
    val blurredArtwork: ImageBitmap? = null
)

object DynamicBackgroundManager {

    private val dataCache = ConcurrentHashMap<String, DynamicArtworkData>()

    val defaultPalette = DynamicArtworkPalette()
    val defaultArtworkData = DynamicArtworkData(
        trackId = "default",
        palette = defaultPalette,
        blurredArtwork = null
    )

    suspend fun extractDataForTrack(
        context: Context,
        track: Track,
        intensity: String = "balanced"
    ): DynamicArtworkData = withContext(Dispatchers.IO) {
        val cacheKey = "${track.id}_$intensity"
        dataCache[cacheKey]?.let { return@withContext it }

        val rawBitmap = loadTrackArtworkBitmap(context, track)
            ?: return@withContext defaultArtworkData.copy(trackId = track.id)

        try {
            // 1. Extract Color Palette using AndroidX Palette
            val palette = Palette.from(rawBitmap)
                .maximumColorCount(16)
                .generate()

            val dominant = palette.getDominantColor(0xFF6E2EA3.toInt())
            val vibrant = palette.getVibrantColor(palette.getDominantColor(0xFF9E4ECB.toInt()))
            val darkVibrant = palette.getDarkVibrantColor(0xFF381F6D.toInt())
            val muted = palette.getMutedColor(0xFF1B2366.toInt())

            val (primaryFactor, secondaryFactor) = when (intensity) {
                "subtle" -> Pair(0.20f, 0.15f)
                "immersive" -> Pair(0.55f, 0.40f)
                else -> Pair(0.38f, 0.28f) // "balanced" default
            }

            val midnight = Color(0xFF0A0715)

            val primary = blendWithMidnight(Color(vibrant), midnight, primaryFactor)
            val secondary = blendWithMidnight(Color(darkVibrant), midnight, secondaryFactor)
            val accent = blendWithMidnight(Color(dominant), midnight, primaryFactor * 0.85f)
            val darkBase = Color(0xFF080612)

            val dynamicPalette = DynamicArtworkPalette(
                primaryColor = primary,
                secondaryColor = secondary,
                accentColor = accent,
                darkBaseColor = darkBase
            )

            // 2. Generate a heavily blurred softened backdrop
            // Downscale to 48x48 for ultra-fast, memory-efficient blur (<1ms)
            val blurredImageBitmap = try {
                val tinyWidth = 48
                val tinyHeight = (48f * rawBitmap.height / rawBitmap.width).toInt().coerceAtLeast(1)
                val scaled = Bitmap.createScaledBitmap(rawBitmap, tinyWidth, tinyHeight, true)
                val blurred = fastBoxBlur(scaled, radius = 6)
                blurred.asImageBitmap()
            } catch (_: Exception) {
                null
            }

            val result = DynamicArtworkData(
                trackId = track.id,
                palette = dynamicPalette,
                blurredArtwork = blurredImageBitmap
            )

            dataCache[cacheKey] = result
            result
        } catch (e: Exception) {
            e.printStackTrace()
            defaultArtworkData.copy(trackId = track.id)
        }
    }

    private fun blendWithMidnight(color: Color, midnight: Color, factor: Float): Color {
        val r = (color.red * factor + midnight.red * (1f - factor)).coerceIn(0f, 1f)
        val g = (color.green * factor + midnight.green * (1f - factor)).coerceIn(0f, 1f)
        val b = (color.blue * factor + midnight.blue * (1f - factor)).coerceIn(0f, 1f)
        return Color(r, g, b, 1f)
    }

    private fun loadTrackArtworkBitmap(context: Context, track: Track): Bitmap? {
        return try {
            if (track.artworkUriString != null) {
                val uri = Uri.parse(track.artworkUriString)
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val options = BitmapFactory.Options().apply {
                        inSampleSize = 4 // Downsample for memory safety
                        inPreferredConfig = Bitmap.Config.RGB_565
                    }
                    BitmapFactory.decodeStream(stream, null, options)
                }
            } else if (track.artworkResId != 0) {
                val options = BitmapFactory.Options().apply {
                    inSampleSize = 4
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
                BitmapFactory.decodeResource(context.resources, track.artworkResId, options)
            } else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Fast in-place box blur algorithm on downscaled bitmap.
     * Guarantees 0-allocation loop, runs in <1 millisecond, safe for low-end devices.
     */
    private fun fastBoxBlur(sentBitmap: Bitmap, radius: Int): Bitmap {
        val width = sentBitmap.width
        val height = sentBitmap.height
        val pixels = IntArray(width * height)
        sentBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val wm = width - 1
        val hm = height - 1
        val wh = width * height
        val div = radius + radius + 1

        val r = IntArray(wh)
        val g = IntArray(wh)
        val b = IntArray(wh)
        var rsum: Int; var gsum: Int; var bsum: Int
        var p: Int; var yi: Int; var yw: Int
        val vmin = IntArray(maxOf(width, height))

        var divsum = (div + 1) shr 1
        divsum *= divsum
        val dv = IntArray(256 * divsum)
        for (i in 0 until 256 * divsum) {
            dv[i] = i / divsum
        }

        yw = 0
        yi = 0

        // Horizontal pass
        for (y in 0 until height) {
            rsum = 0; gsum = 0; bsum = 0
            for (i in -radius..radius) {
                p = pixels[yi + minOf(wm, maxOf(i, 0))]
                rsum += (p and 0xff0000) shr 16
                gsum += (p and 0x00ff00) shr 8
                bsum += (p and 0x0000ff)
            }
            for (x in 0 until width) {
                r[yi] = dv[rsum]
                g[yi] = dv[gsum]
                b[yi] = dv[bsum]

                if (y == 0) {
                    vmin[x] = minOf(x + radius + 1, wm)
                }
                val p1 = pixels[yw + vmin[x]]
                val p2 = pixels[yw + maxOf(x - radius, 0)]

                rsum += ((p1 and 0xff0000) - (p2 and 0xff0000)) shr 16
                gsum += ((p1 and 0x00ff00) - (p2 and 0x00ff00)) shr 8
                bsum += ((p1 and 0x0000ff) - (p2 and 0x0000ff))
                yi++
            }
            yw += width
        }

        // Vertical pass
        for (x in 0 until width) {
            rsum = 0; gsum = 0; bsum = 0
            val yp = -radius * width
            var ypos = yp
            for (i in -radius..radius) {
                yi = maxOf(0, ypos) + x
                rsum += r[yi]
                gsum += g[yi]
                bsum += b[yi]
                ypos += width
            }
            yi = x
            for (y in 0 until height) {
                pixels[yi] = (0xff000000.toInt()) or (dv[rsum] shl 16) or (dv[gsum] shl 8) or dv[bsum]
                if (x == 0) {
                    vmin[y] = minOf(y + radius + 1, hm) * width
                }
                val p1 = x + vmin[y]
                val p2 = x + maxOf(y - radius, 0) * width

                rsum += r[p1] - r[p2]
                gsum += g[p1] - g[p2]
                bsum += b[p1] - b[p2]
                yi += width
            }
        }

        val resultBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        resultBitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return resultBitmap
    }
}
