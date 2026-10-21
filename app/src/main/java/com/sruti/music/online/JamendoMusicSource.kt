package com.sruti.music.online

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class JamendoMusicSource : MusicSource {

    override val sourceId: String = "jamendo"
    override val displayName: String = "Jamendo Open Discovery"
    override val permitsDownload: Boolean = true

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    // Public developer client ID for Jamendo open audio catalog
    private val clientId = "56d30c95"

    override suspend fun search(query: String): Result<List<OnlineTrack>> = withContext(Dispatchers.IO) {
        val encodedQuery = URLEncoder.encode(query.trim(), "UTF-8")
        val url = if (query.isBlank()) {
            "https://api.jamendo.com/v3.0/tracks/?client_id=$clientId&format=json&limit=15&order=popularity_total&include=musicinfo"
        } else {
            "https://api.jamendo.com/v3.0/tracks/?client_id=$clientId&format=json&limit=15&search=$encodedQuery&include=musicinfo"
        }

        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Sruti-Music-Player/1.0")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(IllegalStateException("Online discovery error: ${response.code}"))
            }

            val body = response.body?.string() ?: return@withContext Result.success(emptyList())
            val root = JSONObject(body)
            val resultsArray = root.optJSONArray("results") ?: return@withContext Result.success(emptyList())

            val tracks = mutableListOf<OnlineTrack>()
            for (i in 0 until resultsArray.length()) {
                val item = resultsArray.optJSONObject(i) ?: continue
                val id = item.optString("id")
                val title = item.optString("name", "Unknown Track")
                val artist = item.optString("artist_name", "Unknown Artist")
                val album = item.optString("album_name", "")
                val duration = item.optInt("duration", 0)
                val artwork = item.optString("image")
                val streamUrl = item.optString("audio")
                val downloadUrl = item.optString("audiodownload")
                val license = item.optString("license_ccurl")

                if (!streamUrl.isNullOrBlank()) {
                    tracks.add(
                        OnlineTrack(
                            id = "jamendo_$id",
                            title = title,
                            artist = artist,
                            album = album,
                            durationSeconds = duration,
                            artworkUrl = artwork.ifBlank { null },
                            streamUrl = streamUrl,
                            downloadUrl = downloadUrl.ifBlank { streamUrl },
                            sourceId = sourceId,
                            permitsDownload = true,
                            license = license
                        )
                    )
                }
            }

            Log.d(TAG, "Search '$query' returned ${tracks.size} legal online tracks")
            Result.success(tracks)
        } catch (e: Exception) {
            Log.e(TAG, "Jamendo search failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun resolveStream(track: OnlineTrack): Result<String> {
        return if (!track.streamUrl.isNullOrBlank()) {
            Result.success(track.streamUrl)
        } else {
            Result.failure(IllegalStateException("No stream URL available for ${track.title}"))
        }
    }

    companion object {
        private const val TAG = "JamendoSource"
    }
}
