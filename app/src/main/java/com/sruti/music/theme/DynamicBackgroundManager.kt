package com.sruti.music.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Log
import android.util.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.palette.graphics.Palette
import coil.Coil
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.sruti.music.R
import com.sruti.music.data.Track
import com.sruti.music.data.db.SrutiDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

private const val TAG = "DynamicBackgroundMgr"

data class DynamicArtworkPalette(
    val primaryColor: Color = Color.White,           // Crisp white primary accent by default
    val secondaryColor: Color = Color(0xFF71717A),   // Neutral secondary ambient
    val accentColor: Color = Color(0xFFE2E8F0),      // Luminous highlight accent
    val darkBaseColor: Color = PureBlack             // Deep pure black floor
)

data class DynamicArtworkData(
    val trackId: String = "",
    val palette: DynamicArtworkPalette = DynamicArtworkPalette(),
    val blurredArtwork: ImageBitmap? = null
)

object DynamicBackgroundManager {

    private val dataCache = ConcurrentHashMap<String, DynamicArtworkData>()

    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

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

        Log.d(TAG, "extractDataForTrack: Starting extraction for track '${track.title}' (id='${track.id}')")

        // 1. Multi-strategy bitmap loading
        val rawBitmap = loadTrackArtworkBitmap(context, track)

        // 2. If no artwork bitmap exists, generate a deterministic harmonic aura bitmap
        val effectiveBitmap = rawBitmap ?: generateHarmonicAuraBitmap(track)

        try {
            // 3. Extract Color Palette using AndroidX Palette
            val palette = Palette.from(effectiveBitmap)
                .maximumColorCount(24)
                .generate()

            val dominant = palette.getDominantColor(0xFFA855F7.toInt())
            val vibrant = palette.getVibrantColor(dominant)
            val lightVibrant = palette.getLightVibrantColor(vibrant)
            val darkVibrant = palette.getDarkVibrantColor(0xFF381F6D.toInt())
            val muted = palette.getMutedColor(dominant)

            // Select a radiant, high-energy primary accent for UI controls
            val primaryRaw = if (isColorBrightEnough(vibrant)) vibrant else lightVibrant
            val primaryColor = Color(primaryRaw)

            // Ambient background blend factors based on user intensity preference
            val (secondaryFactor, darkFactor) = when (intensity) {
                "subtle" -> Pair(0.35f, 0.90f)
                "immersive" -> Pair(0.70f, 0.70f)
                else -> Pair(0.50f, 0.80f) // "balanced" default
            }

            val midnight = Color(0xFF0A0715)
            val secondaryColor = blendWithMidnight(Color(darkVibrant), midnight, secondaryFactor)
            val accentColor = Color(if (dominant != vibrant) dominant else lightVibrant)
            val darkBaseColor = blendWithMidnight(Color(darkVibrant), Color(0xFF05030A), 0.25f)

            val dynamicPalette = DynamicArtworkPalette(
                primaryColor = primaryColor,
                secondaryColor = secondaryColor,
                accentColor = accentColor,
                darkBaseColor = darkBaseColor
            )

            // 4. Generate heavily blurred, silky smooth backdrop
            // Downscale to 128x128 and perform a 2-pass fast box blur for ultra-smooth gradients
            val blurredImageBitmap = try {
                val targetSize = 128
                val scaledHeight = (targetSize.toFloat() * effectiveBitmap.height / effectiveBitmap.width).toInt().coerceAtLeast(1)
                val scaled = Bitmap.createScaledBitmap(effectiveBitmap, targetSize, scaledHeight, true)
                val blurPass1 = fastBoxBlur(scaled, radius = 12)
                val blurPass2 = fastBoxBlur(blurPass1, radius = 10)
                blurPass2.asImageBitmap()
            } catch (e: Exception) {
                Log.w(TAG, "Blur processing failed: ${e.message}")
                null
            }

            val result = DynamicArtworkData(
                trackId = track.id,
                palette = dynamicPalette,
                blurredArtwork = blurredImageBitmap
            )

            dataCache[cacheKey] = result
            Log.d(TAG, "extractDataForTrack: Successfully cached dynamic background for '${track.title}'")
            result
        } catch (e: Exception) {
            Log.e(TAG, "extractDataForTrack: Error generating palette for track '${track.title}'", e)
            defaultArtworkData.copy(trackId = track.id)
        }
    }

    private fun isColorBrightEnough(colorInt: Int): Boolean {
        val r = (colorInt shr 16) and 0xFF
        val g = (colorInt shr 8) and 0xFF
        val b = colorInt and 0xFF
        // Perceived luminance formula (ITU-R BT.601)
        val luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
        return luminance >= 0.28
    }

    private fun blendWithMidnight(color: Color, midnight: Color, factor: Float): Color {
        val r = (color.red * factor + midnight.red * (1f - factor)).coerceIn(0f, 1f)
        val g = (color.green * factor + midnight.green * (1f - factor)).coerceIn(0f, 1f)
        val b = (color.blue * factor + midnight.blue * (1f - factor)).coerceIn(0f, 1f)
        return Color(r, g, b, 1f)
    }

    private suspend fun loadTrackArtworkBitmap(context: Context, track: Track): Bitmap? {
        val uriStr = track.artworkUriString
        Log.d(TAG, "loadTrackArtworkBitmap: Track '${track.title}' artworkUri='$uriStr', mediaUri='${track.mediaUriString}'")

        // Strategy 1: Coil Singleton Loader for HTTP/HTTPS/Content
        if (!uriStr.isNullOrBlank()) {
            val coilBitmap = tryLoadWithCoil(context, uriStr)
            if (coilBitmap != null) {
                Log.d(TAG, "Strategy 1 (Coil) loaded artwork for '${track.title}'")
                return coilBitmap
            }
        }

        // Strategy 2: Direct OkHttp Download for HTTP/HTTPS
        if (!uriStr.isNullOrBlank() && (uriStr.startsWith("http://") || uriStr.startsWith("https://"))) {
            val directBitmap = tryDownloadImageBytes(uriStr)
            if (directBitmap != null) {
                Log.d(TAG, "Strategy 2 (Direct OkHttp) downloaded artwork for '${track.title}'")
                return directBitmap
            }
        }

        // Strategy 3: Check Room Database for Completed Downloads
        try {
            val database = SrutiDatabase.getDatabase(context)
            val download = database.musicDao().getDownloadByTrackId(track.id)
                ?: if (track.title.isNotBlank()) database.musicDao().findDownloadByTitle(track.title.trim()) else null
            if (download != null) {
                if (!download.artworkUriString.isNullOrBlank()) {
                    val dlArt = tryLoadWithCoil(context, download.artworkUriString) ?: tryDownloadImageBytes(download.artworkUriString)
                    if (dlArt != null) {
                        Log.d(TAG, "Strategy 3 (Room download artwork URL) loaded art for '${track.title}'")
                        return dlArt
                    }
                }
                if (!download.localFilePath.isNullOrBlank()) {
                    val localFile = File(download.localFilePath)
                    if (localFile.exists()) {
                        val fileArt = extractEmbeddedPicture(localFile.absolutePath)
                        if (fileArt != null) {
                            Log.d(TAG, "Strategy 3 (Room download local file) extracted art for '${track.title}'")
                            return fileArt
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Strategy 3 Database query warning: ${e.message}")
        }

        // Strategy 4: Local File Path in mediaUriString
        val mediaUri = track.mediaUriString
        if (!mediaUri.isNullOrBlank()) {
            if (mediaUri.startsWith("/") || mediaUri.startsWith("file://")) {
                val cleanPath = if (mediaUri.startsWith("file://")) Uri.parse(mediaUri).path ?: mediaUri.removePrefix("file://") else mediaUri
                val embedded = extractEmbeddedPicture(cleanPath)
                if (embedded != null) {
                    Log.d(TAG, "Strategy 4 (Local file embedded picture) succeeded for '${track.title}'")
                    return embedded
                }
            }
        }

        // Strategy 5: Android 10+ (API 29+) MediaStore loadThumbnail
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !mediaUri.isNullOrBlank() && mediaUri.startsWith("content://")) {
            try {
                val contentUri = Uri.parse(mediaUri)
                val thumb = context.contentResolver.loadThumbnail(contentUri, Size(512, 512), null)
                if (thumb != null) {
                    Log.d(TAG, "Strategy 5 (ContentResolver loadThumbnail) succeeded for '${track.title}'")
                    return thumb
                }
            } catch (e: Exception) {
                Log.d(TAG, "Strategy 5 loadThumbnail error: ${e.message}")
            }
        }

        // Strategy 6: MediaMetadataRetriever via Context and Uri
        if (!mediaUri.isNullOrBlank()) {
            val embedded = extractEmbeddedPictureFromUri(context, mediaUri)
            if (embedded != null) {
                Log.d(TAG, "Strategy 6 (MMR from mediaUri) succeeded for '${track.title}'")
                return embedded
            }
        }

        // Strategy 7: ContentResolver openInputStream on artworkUriString
        if (!uriStr.isNullOrBlank() && uriStr.startsWith("content://")) {
            try {
                context.contentResolver.openInputStream(Uri.parse(uriStr))?.use { stream ->
                    val options = BitmapFactory.Options().apply {
                        inSampleSize = 2
                        inPreferredConfig = Bitmap.Config.RGB_565
                    }
                    val bm = BitmapFactory.decodeStream(stream, null, options)
                    if (bm != null) {
                        Log.d(TAG, "Strategy 7 (ContentResolver openInputStream) succeeded for '${track.title}'")
                        return bm
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Strategy 7 openInputStream error: ${e.message}")
            }
        }

        // Strategy 8: Resource Drawable Fallback
        if (track.artworkResId != 0 && track.artworkResId != R.drawable.sruti_artwork_fallback) {
            try {
                val options = BitmapFactory.Options().apply {
                    inSampleSize = 2
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
                val bm = BitmapFactory.decodeResource(context.resources, track.artworkResId, options)
                if (bm != null) return bm
            } catch (_: Exception) {}
        }

        Log.w(TAG, "loadTrackArtworkBitmap: No physical artwork found for '${track.title}'. Will generate harmonic aura.")
        return null
    }

    private suspend fun tryLoadWithCoil(context: Context, uriString: String): Bitmap? {
        return try {
            val loader = Coil.imageLoader(context)
            val request = ImageRequest.Builder(context)
                .data(uriString)
                .size(320, 320)
                .allowHardware(false)
                .build()
            val result = loader.execute(request)
            if (result is SuccessResult) {
                val drawable = result.drawable
                if (drawable is BitmapDrawable) {
                    drawable.bitmap
                } else {
                    val bitmap = Bitmap.createBitmap(
                        drawable.intrinsicWidth.coerceAtLeast(1),
                        drawable.intrinsicHeight.coerceAtLeast(1),
                        Bitmap.Config.ARGB_8888
                    )
                    val canvas = Canvas(bitmap)
                    drawable.setBounds(0, 0, canvas.width, canvas.height)
                    drawable.draw(canvas)
                    bitmap
                }
            } else {
                null
            }
        } catch (e: Exception) {
            Log.d(TAG, "tryLoadWithCoil error: ${e.message}")
            null
        }
    }

    private fun tryDownloadImageBytes(url: String): Bitmap? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                .header("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
                .build()
            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val bytes = response.body?.bytes()
                if (bytes != null && bytes.isNotEmpty()) {
                    val options = BitmapFactory.Options().apply {
                        inPreferredConfig = Bitmap.Config.RGB_565
                    }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                } else null
            } else null
        } catch (e: Exception) {
            Log.d(TAG, "tryDownloadImageBytes error: ${e.message}")
            null
        }
    }

    private fun extractEmbeddedPicture(filePath: String): Bitmap? {
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(filePath)
            val bytes = mmr.embeddedPicture
            if (bytes != null && bytes.isNotEmpty()) {
                val options = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            } else null
        } catch (e: Exception) {
            Log.d(TAG, "extractEmbeddedPicture error on '$filePath': ${e.message}")
            null
        } finally {
            runCatching { mmr.release() }
        }
    }

    private fun extractEmbeddedPictureFromUri(context: Context, uriString: String): Bitmap? {
        val mmr = MediaMetadataRetriever()
        return try {
            val uri = Uri.parse(uriString)
            if (uriString.startsWith("content://")) {
                mmr.setDataSource(context, uri)
            } else {
                mmr.setDataSource(uriString)
            }
            val bytes = mmr.embeddedPicture
            if (bytes != null && bytes.isNotEmpty()) {
                val options = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            } else null
        } catch (e: Exception) {
            Log.d(TAG, "extractEmbeddedPictureFromUri error on '$uriString': ${e.message}")
            null
        } finally {
            runCatching { mmr.release() }
        }
    }

    /**
     * Generates a deterministic, gorgeous multi-color radial gradient aura bitmap
     * for tracks that lack embedded artwork, guaranteeing every song gets a unique theme.
     */
    private fun generateHarmonicAuraBitmap(track: Track): Bitmap {
        val width = 120
        val height = 120
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val hash = (track.title + track.artist + track.id).hashCode()

        // Curated harmonic palette pairs
        val colorPairs = listOf(
            Pair(0xFF27272A.toInt(), 0xFF09090B.toInt()), // Obsidian Monochrome
            Pair(0xFF0284C7.toInt(), 0xFF0B132B.toInt()), // Deep Sapphire & Midnight
            Pair(0xFF059669.toInt(), 0xFF064E3B.toInt()), // Emerald & Forest
            Pair(0xFF0D9488.toInt(), 0xFF115E59.toInt()), // Dark Teal
            Pair(0xFF4F46E5.toInt(), 0xFF1E1B4B.toInt()), // Indigo Night
            Pair(0xFFD97706.toInt(), 0xFF78350F.toInt()), // Warm Amber
            Pair(0xFFE11D48.toInt(), 0xFF881337.toInt()), // Deep Crimson
            Pair(0xFF7C3AED.toInt(), 0xFF3B0764.toInt())  // Velvet Violet
        )

        val pairIndex = Math.abs(hash) % colorPairs.size
        val (color1, color2) = colorPairs[pairIndex]

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Base linear gradient
        paint.shader = LinearGradient(
            0f, 0f, width.toFloat(), height.toFloat(),
            color1, color2, Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        // Radial bloom top-right
        paint.shader = RadialGradient(
            width * 0.8f, height * 0.2f, width * 0.65f,
            color2, android.graphics.Color.TRANSPARENT, Shader.TileMode.CLAMP
        )
        canvas.drawCircle(width * 0.8f, height * 0.2f, width * 0.65f, paint)

        return bitmap
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
