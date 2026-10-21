package com.sruti.music.online

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

class SaavnMusicSource : MusicSource {

    override val sourceId: String = "saavn"
    override val displayName: String = "JioSaavn Online"
    override val permitsDownload: Boolean = true

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    // DES Key for JioSaavn encrypted media URLs
    private val desKey = "38346591".toByteArray(Charsets.UTF_8)

    override suspend fun search(query: String): Result<List<OnlineTrack>> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            return@withContext Result.success(emptyList())
        }

        val encodedQuery = URLEncoder.encode(trimmed, "UTF-8")
        val searchUrl = "https://www.jiosaavn.com/api.php?__call=search.getResults&_format=json&_marker=0&api_version=4&ctx=web6dot0&n=20&p=1&q=$encodedQuery"

        try {
            val request = Request.Builder()
                .url(searchUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept", "application/json")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "Search request returned code ${response.code}")
                return@withContext searchViaAutocomplete(trimmed)
            }

            val body = response.body?.string() ?: return@withContext Result.success(emptyList())
            val root = JSONObject(body)
            val resultsArray = root.optJSONArray("results")

            if (resultsArray == null || resultsArray.length() == 0) {
                // Try autocomplete search as fallback for short/colloquial queries
                return@withContext searchViaAutocomplete(trimmed)
            }

            val tracks = mutableListOf<OnlineTrack>()
            for (i in 0 until resultsArray.length()) {
                val item = resultsArray.optJSONObject(i) ?: continue
                val id = item.optString("id")
                val title = cleanHtmlEntities(item.optString("title", item.optString("song", "Unknown Track")))
                val moreInfo = item.optJSONObject("more_info")
                val artist = cleanHtmlEntities(
                    moreInfo?.optString("primary_artists")
                        ?: moreInfo?.optString("singers")
                        ?: item.optString("more_info.primary_artists", "Unknown Artist")
                )
                val album = cleanHtmlEntities(
                    moreInfo?.optString("album") ?: item.optString("album", "")
                )
                val duration = moreInfo?.optInt("duration", 0) ?: item.optInt("duration", 0)

                val rawImage = item.optString("image")
                val artwork = if (rawImage.isNotBlank()) {
                    rawImage.replace("50x50.jpg", "500x500.jpg").replace("150x150.jpg", "500x500.jpg")
                } else null

                // Decrypt media URL
                val encMedia = moreInfo?.optString("encrypted_media_url")
                var streamUrl = if (!encMedia.isNullOrBlank()) decryptMediaUrl(encMedia) else null

                // Fallback to preview link (vlink) if full stream decryption was not possible
                if (streamUrl.isNullOrBlank()) {
                    streamUrl = moreInfo?.optString("vlink")
                }

                if (!streamUrl.isNullOrBlank()) {
                    tracks.add(
                        OnlineTrack(
                            id = "saavn_$id",
                            title = title,
                            artist = artist,
                            album = album,
                            durationSeconds = duration,
                            artworkUrl = artwork,
                            streamUrl = streamUrl,
                            downloadUrl = streamUrl,
                            sourceId = sourceId,
                            permitsDownload = true,
                            license = "JioSaavn Stream"
                        )
                    )
                }
            }

            Log.d(TAG, "Search '$trimmed' returned ${tracks.size} playable tracks from JioSaavn")
            Result.success(tracks)
        } catch (e: Exception) {
            Log.e(TAG, "Saavn search failed: ${e.message}", e)
            searchViaAutocomplete(trimmed)
        }
    }

    private fun searchViaAutocomplete(query: String): Result<List<OnlineTrack>> {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val autoUrl = "https://www.jiosaavn.com/api.php?__call=autocomplete.get&_format=json&_marker=0&cc=in&includeMetaTags=1&query=$encoded"

            val request = Request.Builder()
                .url(autoUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return Result.success(emptyList())

            val body = response.body?.string() ?: return Result.success(emptyList())
            val root = JSONObject(body)
            val songsObj = root.optJSONObject("songs") ?: return Result.success(emptyList())
            val dataArray = songsObj.optJSONArray("data") ?: return Result.success(emptyList())

            val tracks = mutableListOf<OnlineTrack>()
            for (i in 0 until dataArray.length()) {
                val item = dataArray.optJSONObject(i) ?: continue
                val id = item.optString("id")
                val title = cleanHtmlEntities(item.optString("title", "Unknown"))
                val album = cleanHtmlEntities(item.optString("album", ""))
                val moreInfo = item.optJSONObject("more_info")
                val artist = cleanHtmlEntities(moreInfo?.optString("primary_artists") ?: item.optString("description", "Unknown"))

                val rawImage = item.optString("image")
                val artwork = if (rawImage.isNotBlank()) {
                    rawImage.replace("50x50.jpg", "500x500.jpg")
                } else null

                val vlink = moreInfo?.optString("vlink")
                if (!vlink.isNullOrBlank()) {
                    tracks.add(
                        OnlineTrack(
                            id = "saavn_$id",
                            title = title,
                            artist = artist,
                            album = album,
                            durationSeconds = 180, // Default estimate when autocomplete
                            artworkUrl = artwork,
                            streamUrl = vlink,
                            downloadUrl = vlink,
                            sourceId = sourceId,
                            permitsDownload = true,
                            license = "JioSaavn Stream"
                        )
                    )
                }
            }
            Result.success(tracks)
        } catch (e: Exception) {
            Log.w(TAG, "Autocomplete fallback failed: ${e.message}")
            Result.success(emptyList())
        }
    }

    private fun decryptMediaUrl(encrypted: String): String? {
        return try {
            val cipher = Cipher.getInstance("DES/ECB/PKCS5Padding")
            val keySpec = SecretKeySpec(desKey, "DES")
            cipher.init(Cipher.DECRYPT_MODE, keySpec)
            val decoded = Base64.decode(encrypted, Base64.DEFAULT)
            val decryptedBytes = cipher.doFinal(decoded)
            val rawUrl = String(decryptedBytes, Charsets.UTF_8).trim()
            // Upgrade 96kbps to 160kbps for better audio quality
            rawUrl.replace("_96.mp4", "_160.mp4")
        } catch (e: Exception) {
            Log.w(TAG, "DES media url decryption error: ${e.message}")
            null
        }
    }

    private fun cleanHtmlEntities(str: String): String {
        return str.replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&#039;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace(Regex("<[^>]*>"), "")
            .trim()
    }

    override suspend fun resolveStream(track: OnlineTrack): Result<String> {
        return if (!track.streamUrl.isNullOrBlank()) {
            Result.success(track.streamUrl)
        } else {
            Result.failure(IllegalStateException("No stream URL available for ${track.title}"))
        }
    }

    companion object {
        private const val TAG = "SaavnMusicSource"
    }
}
