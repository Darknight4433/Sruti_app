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
    val audioPreviewUrl: String? = null,
    val addedAtEpochMs: Long = System.currentTimeMillis()
)

data class SpotifyPlaylistData(
    val name: String,
    val coverArt: String?,
    val tracks: List<SpotifyImportedTrack>
)

data class SpotifyImportResult(
    val playlistName: String,
    val artworkUrl: String?,
    val tracks: List<SpotifyImportedTrack>,
    val playlistId: Long
)

class SpotifyPlaylistImporter(private val context: Context) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    private val database = SrutiDatabase.getDatabase(context)
    private val scope = CoroutineScope(Dispatchers.IO)

    fun extractPlaylistId(input: String): String? {
        val trimmed = input.trim()
        val urlPattern = Pattern.compile("(?:playlist|album)[/:]([a-zA-Z0-9]+)")
        val matcher = urlPattern.matcher(trimmed)
        return if (matcher.find()) matcher.group(1) else null
    }

    suspend fun fetchPlaylistTracks(
        urlOrUri: String,
        onProgress: ((loaded: Int, total: Int) -> Unit)? = null
    ): Result<SpotifyPlaylistData> = withContext(Dispatchers.IO) {
        val playlistId = extractPlaylistId(urlOrUri)
            ?: return@withContext Result.failure(IllegalArgumentException("Invalid Spotify playlist URL. Please provide a link like: https://open.spotify.com/playlist/..."))

        try {
            val playlistData = fetchFromEmbed(playlistId, onProgress)
                ?: return@withContext Result.failure(IllegalStateException("Could not retrieve Spotify playlist. Please verify the playlist is public."))

            if (playlistData.tracks.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("Playlist is empty or contains no accessible tracks."))
            }

            Result.success(playlistData)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch Spotify playlist: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun importPlaylist(
        urlOrUri: String,
        onProgress: ((loaded: Int, total: Int) -> Unit)? = null
    ): Result<SpotifyImportResult> = withContext(Dispatchers.IO) {
        val playlistId = extractPlaylistId(urlOrUri)
            ?: return@withContext Result.failure(IllegalArgumentException("Invalid Spotify playlist URL. Please provide a link like: https://open.spotify.com/playlist/..."))

        val fetchResult = fetchPlaylistTracks(urlOrUri, onProgress)
        val playlistData = fetchResult.getOrElse { return@withContext Result.failure(it) }

        try {
            val name = playlistData.name
            val coverArt = playlistData.coverArt
            val tracks = playlistData.tracks

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
                    mediaUriString = track.audioPreviewUrl,
                    artworkUriString = track.artworkUrl ?: coverArt,
                    artworkResId = com.sruti.music.R.drawable.sruti_artwork_fallback,
                    sourceId = if (track.audioPreviewUrl != null) "spotify_preview" else "",
                    sortOrder = index,
                    addedAt = track.addedAtEpochMs
                )
            }

            database.musicDao().insertPlaylistSongs(songEntities)
            Log.d(TAG, "Successfully imported playlist '$name' with ${tracks.size} tracks into DB (id: $newPlaylistId)")

            // Launch background full streaming audio resolution for each track
            scope.launch {
                resolveStreamsForTracks(newPlaylistId, tracks, playlistId)
            }

            Result.success(SpotifyImportResult(name, coverArt, tracks, newPlaylistId))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import Spotify playlist into DB: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun resolveStreamsForTracks(
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

    private fun fetchEmbedHtml(playlistId: String): String? {
        val embedUrl = "https://open.spotify.com/embed/playlist/$playlistId"
        val request = Request.Builder()
            .url(embedUrl)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
            .build()
        return try {
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) response.body?.string() else null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch embed HTML: ${e.message}")
            null
        }
    }

    private fun extractTokenFromHtml(html: String): String? {
        val scriptPattern = Pattern.compile("<script\\s+id=\"__NEXT_DATA__\"[^>]*>([\\s\\S]*?)</script>")
        val matcher = scriptPattern.matcher(html)
        if (matcher.find()) {
            val jsonStr = matcher.group(1)
            if (!jsonStr.isNullOrBlank()) {
                val token = runCatching {
                    val root = JSONObject(jsonStr)
                    root.optJSONObject("props")
                        ?.optJSONObject("pageProps")
                        ?.optJSONObject("state")
                        ?.optJSONObject("settings")
                        ?.optJSONObject("session")
                        ?.optString("accessToken")
                }.getOrNull()
                if (!token.isNullOrBlank()) return token
            }
        }

        // Fallback regex search for accessToken in raw HTML
        val tokenMatcher = Pattern.compile("\"accessToken\"\\s*:\\s*\"([^\"]+)\"").matcher(html)
        if (tokenMatcher.find()) {
            val token = tokenMatcher.group(1)
            if (!token.isNullOrBlank()) return token
        }

        return null
    }

    private fun fetchFromEmbed(
        playlistId: String,
        onProgress: ((loaded: Int, total: Int) -> Unit)?
    ): SpotifyPlaylistData? {
        val html = fetchEmbedHtml(playlistId) ?: return null

        return try {
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

            // Extract session accessToken for Pathfinder pagination
            var sessionToken = state.optJSONObject("settings")
                ?.optJSONObject("session")
                ?.optString("accessToken")
            if (sessionToken.isNullOrBlank()) {
                sessionToken = extractTokenFromHtml(html)
            }

            // Attempt full multi-page pagination via Pathfinder API (fetches ALL songs: 100, 200, 500, 1000+)
            if (!sessionToken.isNullOrBlank()) {
                val paginatedTracks = fetchTracksViaPathfinder(playlistId, sessionToken, coverArt, onProgress)
                if (!paginatedTracks.isNullOrEmpty()) {
                    Log.d(TAG, "Successfully fetched ${paginatedTracks.size} tracks via Pathfinder pagination")
                    return SpotifyPlaylistData(playlistName, coverArt, paginatedTracks)
                }
            }

            // Fallback: Extract initial tracks from embed trackList (first 100 tracks)
            val trackListArray = entity.optJSONArray("trackList") ?: return null
            val fallbackTracks = mutableListOf<SpotifyImportedTrack>()
            val baseTime = System.currentTimeMillis()
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
                        audioPreviewUrl = previewUrl.takeIf { !it.isNullOrBlank() },
                        addedAtEpochMs = baseTime - (trackListArray.length() - i) * 1000L
                    )
                )
            }

            onProgress?.invoke(fallbackTracks.size, fallbackTracks.size)
            if (fallbackTracks.isNotEmpty()) SpotifyPlaylistData(playlistName, coverArt, fallbackTracks) else null
        } catch (e: Exception) {
            Log.w(TAG, "Embed fetch failed: ${e.message}")
            null
        }
    }

    private fun fetchTracksViaPathfinder(
        playlistId: String,
        initialToken: String,
        coverArt: String?,
        onProgress: ((loaded: Int, total: Int) -> Unit)?
    ): List<SpotifyImportedTrack>? {
        val tracks = mutableListOf<SpotifyImportedTrack>()
        var offset = 0
        val limit = 100
        var totalTracks = Int.MAX_VALUE
        var currentToken = initialToken
        val baseTime = System.currentTimeMillis()

        try {
            while (offset < totalTracks && tracks.size < 10000) {
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

                var pageSuccess = false
                var retryCount = 0

                while (!pageSuccess && retryCount < 3) {
                    val req = Request.Builder()
                        .url(queryUrl)
                        .header("Authorization", "Bearer $currentToken")
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                        .header("Accept", "application/json")
                        .header("app-platform", "WebPlayer")
                        .build()

                    val resp = try {
                        httpClient.newCall(req).execute()
                    } catch (e: Exception) {
                        Log.w(TAG, "Network call failed at offset $offset (attempt $retryCount): ${e.message}")
                        Thread.sleep(1000L * (retryCount + 1))
                        retryCount++
                        continue
                    }

                    if (resp.code == 429) {
                        // Rate limited: back off and retry
                        resp.close()
                        Log.w(TAG, "Rate limited (429) at offset $offset. Backing off...")
                        Thread.sleep(1500L * (retryCount + 1))
                        retryCount++
                        continue
                    }

                    if (resp.code == 401) {
                        // Token expired: fetch fresh token from embed
                        resp.close()
                        Log.w(TAG, "Token expired (401). Refreshing token...")
                        val freshHtml = fetchEmbedHtml(playlistId)
                        val freshToken = if (freshHtml != null) extractTokenFromHtml(freshHtml) else null
                        if (!freshToken.isNullOrBlank()) {
                            currentToken = freshToken
                            retryCount++
                            continue
                        } else {
                            break
                        }
                    }

                    if (!resp.isSuccessful) {
                        Log.w(TAG, "Pathfinder request failed at offset $offset with HTTP ${resp.code}")
                        resp.close()
                        break
                    }

                    val bodyStr = resp.body?.string()
                    resp.close()
                    if (bodyStr.isNullOrBlank()) break

                    val rootJson = JSONObject(bodyStr)
                    val content = rootJson.optJSONObject("data")
                        ?.optJSONObject("playlistV2")
                        ?.optJSONObject("content") ?: break

                    val reportedTotal = content.optInt("totalCount", 0)
                    if (reportedTotal > 0) {
                        totalTracks = reportedTotal
                    }

                    val itemsArray = content.optJSONArray("items") ?: break
                    if (itemsArray.length() == 0) {
                        pageSuccess = true
                        break
                    }

                    for (i in 0 until itemsArray.length()) {
                        val itemWrapper = itemsArray.optJSONObject(i) ?: continue
                        val trackData = itemWrapper.optJSONObject("itemV2")?.optJSONObject("data")
                            ?: itemWrapper.optJSONObject("track")
                            ?: continue

                        val name = cleanHtml(trackData.optString("name", "Unknown"))
                        if (name.isBlank() || name == "Unknown") continue

                        val uri = trackData.optString("uri")

                        // Duration
                        val durationMs = trackData.optJSONObject("trackDuration")?.optLong("totalMilliseconds", 0)
                            ?: trackData.optLong("duration_ms", 0)
                        val durationSec = (durationMs / 1000).toInt().coerceAtLeast(1)

                        // Artists
                        val artistNames = mutableListOf<String>()
                        val artistsItems = trackData.optJSONObject("artists")?.optJSONArray("items")
                            ?: trackData.optJSONArray("artists")
                        if (artistsItems != null) {
                            for (aIdx in 0 until artistsItems.length()) {
                                val aObj = artistsItems.optJSONObject(aIdx) ?: continue
                                val aName = aObj.optJSONObject("profile")?.optString("name")
                                    ?: aObj.optString("name")
                                if (!aName.isNullOrBlank()) artistNames.add(aName)
                            }
                        }
                        val artist = if (artistNames.isNotEmpty()) cleanHtml(artistNames.joinToString(", ")) else "Unknown"

                        // Album & Cover Art
                        val albumObj = trackData.optJSONObject("albumOfTrack") ?: trackData.optJSONObject("album")
                        val album = cleanHtml(albumObj?.optString("name", "") ?: "")
                        val sources = albumObj?.optJSONObject("coverArt")?.optJSONArray("sources")
                            ?: albumObj?.optJSONArray("images")
                        val trackArtwork = sources?.optJSONObject(0)?.optString("url") ?: coverArt

                        // Track Added Date
                        val addedAtObj = itemWrapper.optJSONObject("addedAt")
                        val addedAtIso = addedAtObj?.optString("isoString")
                        val addedAtMs = if (!addedAtIso.isNullOrBlank()) {
                            runCatching {
                                java.time.Instant.parse(addedAtIso).toEpochMilli()
                            }.getOrDefault(baseTime - (totalTracks - (offset + i)) * 1000L)
                        } else {
                            baseTime - (totalTracks - (offset + i)) * 1000L
                        }

                        tracks.add(
                            SpotifyImportedTrack(
                                title = name,
                                artist = artist,
                                album = album,
                                durationSeconds = durationSec,
                                artworkUrl = trackArtwork,
                                spotifyUri = uri,
                                audioPreviewUrl = null,
                                addedAtEpochMs = addedAtMs
                            )
                        )
                    }

                    offset += itemsArray.length()
                    pageSuccess = true

                    // Notify caller of live progress
                    onProgress?.invoke(tracks.size, if (totalTracks < Int.MAX_VALUE) totalTracks else tracks.size)

                    // Brief polite pause between pages to prevent hitting rate limits
                    Thread.sleep(70L)
                }

                if (!pageSuccess) {
                    Log.w(TAG, "Stopping pagination at offset $offset due to failed page fetch")
                    break
                }
            }

            return if (tracks.isNotEmpty()) tracks else null
        } catch (e: Exception) {
            Log.w(TAG, "Pathfinder track fetch error: ${e.message}")
            return if (tracks.isNotEmpty()) tracks else null
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
