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
            val (name, coverArt, tracks) = fetchFromEmbed(playlistId)
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
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
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
            val pageProps = root.optJSONObject("props")?.optJSONObject("pageProps")
            val state = pageProps?.optJSONObject("state")
            val entity = state?.optJSONObject("data")?.optJSONObject("entity") ?: return null

            val playlistName = cleanHtml(entity.optString("name", "Spotify Playlist"))
            val coverArt = entity.optJSONObject("coverArt")
                ?.optJSONArray("sources")
                ?.optJSONObject(0)
                ?.optString("url")

            // Extract embedded session accessToken for paginated GraphQL Pathfinder requests
            val sessionToken = state.optJSONObject("settings")
                ?.optJSONObject("session")
                ?.optString("accessToken")

            // Attempt full pagination via Spotify's internal Pathfinder API (all tracks beyond 100)
            if (!sessionToken.isNullOrBlank()) {
                val paginatedTracks = fetchTracksViaPathfinder(playlistId, sessionToken, coverArt)
                if (!paginatedTracks.isNullOrEmpty()) {
                    Log.d(TAG, "Successfully fetched ${paginatedTracks.size} tracks via Pathfinder pagination")
                    return Triple(playlistName, coverArt, paginatedTracks)
                }
            }

            // Fallback: Extract initial tracks from embed trackList (first 100 tracks)
            val trackListArray = entity.optJSONArray("trackList") ?: return null
            val fallbackTracks = mutableListOf<SpotifyImportedTrack>()
            for (i in 0 until trackListArray.length()) {
                val item = trackListArray.optJSONObject(i) ?: continue
                val title = cleanHtml(item.optString("title", "Unknown"))
                val artist = cleanHtml(item.optString("subtitle", "Unknown"))
                val durationMs = item.optLong("duration", 0)
                val durationSec = (durationMs / 1000).toInt().coerceAtLeast(1)
                val uri = item.optString("uri")
                val previewUrl = item.optJSONObject("audioPreview")?.optString("url")

                fallbackTracks.add(
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

            if (fallbackTracks.isNotEmpty()) Triple(playlistName, coverArt, fallbackTracks) else null
        } catch (e: Exception) {
            Log.w(TAG, "Embed fetch failed: ${e.message}")
            null
        }
    }

    private fun fetchTracksViaPathfinder(
        playlistId: String,
        token: String,
        coverArt: String?
    ): List<SpotifyImportedTrack>? {
        val tracks = mutableListOf<SpotifyImportedTrack>()
        var offset = 0
        val limit = 100
        var totalTracks = Int.MAX_VALUE

        try {
            while (offset < totalTracks && tracks.size < 5000) {
                val varsJson = JSONObject().apply {
                    put("uri", "spotify:playlist:$playlistId")
                    put("offset", offset)
                    put("limit", limit)
                }.toString()

                val extsJson = JSONObject().apply {
                    put("persistedQuery", JSONObject().apply {
                        put("version", 1)
                        put("sha256Hash", "c56c706a062f82052d87fdaeeb300a258d2d54153222ef360682a0ee625284d9")
                    })
                }.toString()

                val queryUrl = "https://api-partner.spotify.com/pathfinder/v1/query?" +
                        "operationName=fetchPlaylistContents" +
                        "&variables=" + java.net.URLEncoder.encode(varsJson, "UTF-8") +
                        "&extensions=" + java.net.URLEncoder.encode(extsJson, "UTF-8")

                val req = Request.Builder()
                    .url(queryUrl)
                    .header("Authorization", "Bearer $token")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                    .header("Accept", "application/json")
                    .header("app-platform", "WebPlayer")
                    .build()

                val resp = httpClient.newCall(req).execute()
                if (!resp.isSuccessful) {
                    Log.w(TAG, "Pathfinder page at offset $offset failed with HTTP ${resp.code}")
                    break
                }

                val bodyStr = resp.body?.string() ?: break
                val rootJson = JSONObject(bodyStr)
                val content = rootJson.optJSONObject("data")
                    ?.optJSONObject("playlistV2")
                    ?.optJSONObject("content") ?: break

                val reportedTotal = content.optInt("totalCount", 0)
                if (reportedTotal > 0) {
                    totalTracks = reportedTotal
                }

                val itemsArray = content.optJSONArray("items") ?: break
                if (itemsArray.length() == 0) break

                for (i in 0 until itemsArray.length()) {
                    val itemWrapper = itemsArray.optJSONObject(i) ?: continue
                    val trackData = itemWrapper.optJSONObject("itemV2")?.optJSONObject("data") ?: continue

                    val name = cleanHtml(trackData.optString("name", "Unknown"))
                    val uri = trackData.optString("uri")

                    // Duration
                    val durationMs = trackData.optJSONObject("trackDuration")?.optLong("totalMilliseconds", 0) ?: 0L
                    val durationSec = (durationMs / 1000).toInt().coerceAtLeast(1)

                    // Artists
                    val artistsItems = trackData.optJSONObject("artists")?.optJSONArray("items")
                    val artistNames = mutableListOf<String>()
                    if (artistsItems != null) {
                        for (aIdx in 0 until artistsItems.length()) {
                            val aObj = artistsItems.optJSONObject(aIdx)
                            val aName = aObj?.optJSONObject("profile")?.optString("name")
                            if (!aName.isNullOrBlank()) artistNames.add(aName)
                        }
                    }
                    val artist = if (artistNames.isNotEmpty()) cleanHtml(artistNames.joinToString(", ")) else "Unknown"

                    // Album & Cover Art
                    val albumObj = trackData.optJSONObject("albumOfTrack")
                    val album = cleanHtml(albumObj?.optString("name", "") ?: "")
                    val sources = albumObj?.optJSONObject("coverArt")?.optJSONArray("sources")
                    val trackArtwork = sources?.optJSONObject(0)?.optString("url") ?: coverArt

                    tracks.add(
                        SpotifyImportedTrack(
                            title = name,
                            artist = artist,
                            album = album,
                            durationSeconds = durationSec,
                            artworkUrl = trackArtwork,
                            spotifyUri = uri,
                            audioPreviewUrl = null
                        )
                    )
                }

                offset += itemsArray.length()
            }

            return if (tracks.isNotEmpty()) tracks else null
        } catch (e: Exception) {
            Log.w(TAG, "Pathfinder track fetch error: ${e.message}")
            return if (tracks.isNotEmpty()) tracks else null
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
