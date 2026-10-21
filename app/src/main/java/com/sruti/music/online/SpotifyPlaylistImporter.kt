package com.sruti.music.online

import android.content.Context
import android.util.Log
import com.sruti.music.data.db.PlaylistEntity
import com.sruti.music.data.db.PlaylistSongEntity
import com.sruti.music.data.db.SrutiDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class SpotifyImportedTrack(
    val title: String,
    val artist: String,
    val album: String = "",
    val durationSeconds: Int,
    val artworkUrl: String? = null,
    val spotifyUri: String? = null,
    val audioPreviewUrl: String? = null
)

data class SpotifyImportResult(
    val playlistName: String,
    val artworkUrl: String?,
    val tracks: List<SpotifyImportedTrack>,
    val playlistId: Long
)

class SpotifyPlaylistImporter(private val context: Context) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val database = SrutiDatabase.getDatabase(context)
    private val scope = CoroutineScope(Dispatchers.IO)

    fun extractPlaylistId(input: String): String? {
        val trimmed = input.trim()
        val urlPattern = Pattern.compile("playlist[/:]([a-zA-Z0-9]+)")
        val matcher = urlPattern.matcher(trimmed)
        return if (matcher.find()) matcher.group(1) else null
    }

    suspend fun importPlaylist(urlOrUri: String): Result<SpotifyImportResult> = withContext(Dispatchers.IO) {
        val playlistId = extractPlaylistId(urlOrUri)
            ?: return@withContext Result.failure(IllegalArgumentException("Invalid Spotify playlist URL. Please provide a link like: https://open.spotify.com/playlist/..."))

        try {
            val (name, coverArt, tracks) = fetchFromAnonymousApi(playlistId)
                ?: fetchFromEmbed(playlistId)
                ?: return@withContext Result.failure(IllegalStateException("Could not retrieve Spotify playlist. Please verify the playlist is public."))

            if (tracks.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("Playlist is empty or contains no accessible tracks."))
            }

            // Save to local Room Database, preserving original order exactly
            val newPlaylistId = database.musicDao().insertPlaylist(
                PlaylistEntity(
                    name = name,
                    artworkUrl = coverArt ?: tracks.firstOrNull()?.artworkUrl,
                    createdAt = System.currentTimeMillis()
                )
            )

            val songEntities = tracks.mapIndexed { index, track ->
                PlaylistSongEntity(
                    playlistId = newPlaylistId,
                    trackId = "spotify_${playlistId}_$index",
                    title = track.title,
                    artist = track.artist,
                    album = track.album,
                    durationSeconds = track.durationSeconds,
                    mediaUriString = track.audioPreviewUrl, // Playable preview fallback immediately
                    artworkUriString = track.artworkUrl ?: coverArt,
                    artworkResId = com.sruti.music.R.drawable.sruti_artwork_fallback,
                    sourceId = if (track.audioPreviewUrl != null) "spotify_preview" else "",
                    sortOrder = index,
                    addedAt = System.currentTimeMillis()
                )
            }

            database.musicDao().insertPlaylistSongs(songEntities)
            Log.d(TAG, "Successfully imported playlist '$name' with ${tracks.size} tracks")

            // Launch background full streaming audio resolution for each track
            scope.launch {
                resolvePlayableStreams(newPlaylistId, tracks, playlistId)
            }

            Result.success(SpotifyImportResult(name, coverArt, tracks, newPlaylistId))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import Spotify playlist: ${e.message}", e)
            Result.failure(e)
        }
    }

    private suspend fun resolvePlayableStreams(
        playlistId: Long,
        tracks: List<SpotifyImportedTrack>,
        spotifyPlaylistId: String
    ) {
        tracks.forEachIndexed { index, track ->
            val trackEntityId = "spotify_${spotifyPlaylistId}_$index"
            try {
                val (matchedTrack, confidence) = MusicSourceRegistry.matchTrackOnline(
                    title = track.title,
                    artist = track.artist,
                    durationSeconds = track.durationSeconds
                )
                if (matchedTrack != null && confidence != MatchConfidence.UNMATCHED) {
                    database.musicDao().updateTrackResolution(
                        playlistId = playlistId,
                        trackId = trackEntityId,
                        mediaUri = matchedTrack.streamUrl,
                        artworkUri = matchedTrack.artworkUrl ?: track.artworkUrl,
                        sourceId = matchedTrack.sourceId
                    )
                    Log.d(TAG, "Resolved track '$trackEntityId' (${track.title}) -> ${matchedTrack.sourceId} stream")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed resolving stream for '${track.title}': ${e.message}")
            }
        }
    }

    private fun fetchFromEmbed(playlistId: String): Triple<String, String?, List<SpotifyImportedTrack>>? {
        val embedUrl = "https://open.spotify.com/embed/playlist/$playlistId"
        val request = Request.Builder()
            .url(embedUrl)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .build()

        return try {
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return null
            val html = response.body?.string() ?: return null

            val scriptPattern = Pattern.compile("<script\\s+id=\"__NEXT_DATA__\"[^>]*>([\\s\\S]*?)</script>")
            val matcher = scriptPattern.matcher(html)
            if (!matcher.find()) return null

            val jsonStr = matcher.group(1) ?: return null
            val root = JSONObject(jsonStr)
            val entity = root.optJSONObject("props")
                ?.optJSONObject("pageProps")
                ?.optJSONObject("state")
                ?.optJSONObject("data")
                ?.optJSONObject("entity") ?: return null

            val playlistName = cleanHtml(entity.optString("name", "Spotify Playlist"))
            val coverArt = entity.optJSONObject("coverArt")
                ?.optJSONArray("sources")
                ?.optJSONObject(0)
                ?.optString("url")

            val trackListArray = entity.optJSONArray("trackList") ?: return null

            val tracks = mutableListOf<SpotifyImportedTrack>()
            for (i in 0 until trackListArray.length()) {
                val item = trackListArray.optJSONObject(i) ?: continue
                val title = cleanHtml(item.optString("title", "Unknown"))
                val artist = cleanHtml(item.optString("subtitle", "Unknown"))
                val durationMs = item.optLong("duration", 0)
                val durationSec = (durationMs / 1000).toInt().coerceAtLeast(1)
                val uri = item.optString("uri")
                val previewUrl = item.optJSONObject("audioPreview")?.optString("url")

                tracks.add(
                    SpotifyImportedTrack(
                        title = title,
                        artist = artist,
                        durationSeconds = durationSec,
                        artworkUrl = coverArt,
                        spotifyUri = uri,
                        audioPreviewUrl = previewUrl.takeIf { !it.isNullOrBlank() }
                    )
                )
            }

            if (tracks.isNotEmpty()) Triple(playlistName, coverArt, tracks) else null
        } catch (e: Exception) {
            Log.w(TAG, "Embed fetch failed: ${e.message}")
            null
        }
    }

    private fun fetchFromAnonymousApi(playlistId: String): Triple<String, String?, List<SpotifyImportedTrack>>? {
        return try {
            val tokenReq = Request.Builder()
                .url("https://open.spotify.com/get_access_token?reason=transport&productType=embed")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .build()

            val tokenResp = httpClient.newCall(tokenReq).execute()
            if (!tokenResp.isSuccessful) return null
            val tokenBody = tokenResp.body?.string() ?: return null
            val tokenJson = JSONObject(tokenBody)
            val token = tokenJson.optString("accessToken")
            if (token.isNullOrBlank()) return null

            val apiReq = Request.Builder()
                .url("https://api.spotify.com/v1/playlists/$playlistId?fields=name,images,tracks(total,items(track(name,artists(name),duration_ms,album(name,images),preview_url)))")
                .header("Authorization", "Bearer $token")
                .build()

            val apiResp = httpClient.newCall(apiReq).execute()
            if (!apiResp.isSuccessful) return null
            val apiBody = apiResp.body?.string() ?: return null
            val apiJson = JSONObject(apiBody)

            val playlistName = cleanHtml(apiJson.optString("name", "Spotify Playlist"))
            val coverArt = apiJson.optJSONArray("images")?.optJSONObject(0)?.optString("url")
            val tracksObj = apiJson.optJSONObject("tracks") ?: return null
            val totalTracks = tracksObj.optInt("total", 0)
            val itemsArray = tracksObj.optJSONArray("items") ?: return null

            val tracks = mutableListOf<SpotifyImportedTrack>()
            extractTracksFromItems(itemsArray, coverArt, tracks)

            // Paginate if playlist has more tracks (> 100) - import all songs without artificial low caps
            var offset = tracks.size
            while (offset < totalTracks && tracks.size < 5000) {
                val pageReq = Request.Builder()
                    .url("https://api.spotify.com/v1/playlists/$playlistId/tracks?offset=$offset&limit=100&fields=items(track(name,artists(name),duration_ms,album(name,images),preview_url))")
                    .header("Authorization", "Bearer $token")
                    .build()
                val pageResp = httpClient.newCall(pageReq).execute()
                if (!pageResp.isSuccessful) break
                val pageBody = pageResp.body?.string() ?: break
                val pageJson = JSONObject(pageBody)
                val pageItems = pageJson.optJSONArray("items") ?: break
                if (pageItems.length() == 0) break
                extractTracksFromItems(pageItems, coverArt, tracks)
                offset += pageItems.length()
            }

            if (tracks.isNotEmpty()) Triple(playlistName, coverArt, tracks) else null
        } catch (e: Exception) {
            Log.w(TAG, "Anonymous API fetch failed: ${e.message}")
            null
        }
    }

    private fun extractTracksFromItems(
        itemsArray: org.json.JSONArray,
        coverArt: String?,
        output: MutableList<SpotifyImportedTrack>
    ) {
        for (i in 0 until itemsArray.length()) {
            val item = itemsArray.optJSONObject(i)?.optJSONObject("track") ?: continue
            val title = cleanHtml(item.optString("name", "Unknown"))
            val artistsArray = item.optJSONArray("artists")
            val artistList = mutableListOf<String>()
            if (artistsArray != null) {
                for (j in 0 until artistsArray.length()) {
                    val a = artistsArray.optJSONObject(j)?.optString("name")
                    if (!a.isNullOrBlank()) artistList.add(a)
                }
            }
            val artist = if (artistList.isNotEmpty()) cleanHtml(artistList.joinToString(", ")) else "Unknown"
            val album = cleanHtml(item.optJSONObject("album")?.optString("name") ?: "")
            val durationMs = item.optLong("duration_ms", 0)
            val durationSec = (durationMs / 1000).toInt().coerceAtLeast(1)
            val previewUrl = item.optString("preview_url").takeIf { !it.isNullOrBlank() }

            val imagesArray = item.optJSONObject("album")?.optJSONArray("images")
            val artworkUrl = imagesArray?.optJSONObject(0)?.optString("url") ?: coverArt

            output.add(
                SpotifyImportedTrack(
                    title = title,
                    artist = artist,
                    album = album,
                    durationSeconds = durationSec,
                    artworkUrl = artworkUrl,
                    audioPreviewUrl = previewUrl
                )
            )
        }
    }

    private fun cleanHtml(s: String): String {
        return s.replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#039;", "'")
            .replace("\u00a0", " ")
            .trim()
    }

    companion object {
        private const val TAG = "SpotifyImporter"
    }
}
