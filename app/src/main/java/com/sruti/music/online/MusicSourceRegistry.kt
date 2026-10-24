package com.sruti.music.online

import android.util.Log
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

object MusicSourceRegistry {

    private const val TAG = "MusicSourceRegistry"

    val sources: List<MusicSource> = listOf(
        SaavnMusicSource(),
        JamendoMusicSource()
    )

    /**
     * Search across all registered sources concurrently and return aggregated results.
     */
    suspend fun searchAllSources(query: String): List<OnlineTrack> = coroutineScope {
        if (query.isBlank()) return@coroutineScope emptyList()

        val deferredResults = sources.map { source ->
            async {
                try {
                    source.search(query).getOrDefault(emptyList())
                } catch (e: Exception) {
                    Log.w(TAG, "Search failed for ${source.displayName}: ${e.message}")
                    emptyList()
                }
            }
        }

        val allTracks = deferredResults.flatMap { it.await() }
        // Deduplicate tracks by title and artist
        allTracks.distinctBy { "${it.title.lowercase()}_${it.artist.lowercase()}" }
    }

    /**
     * Resolve playable audio for an imported Spotify track by searching across sources
     * and scoring candidate matches with TrackMatchingEngine.
     */
    suspend fun matchTrackOnline(
        title: String,
        artist: String,
        durationSeconds: Int = 0
    ): Pair<OnlineTrack?, MatchConfidence> {
        val cleanT = TrackMatchingEngine.cleanTitle(title)
        val cleanA = artist.replace(Regex("(?i)feat\\.?.*"), "").replace(",", "").trim()

        val queryStrategies = mutableListOf<String>()
        if (cleanT.isNotBlank() && cleanA.isNotBlank()) {
            queryStrategies.add("$cleanT $cleanA".trim())
        }
        queryStrategies.add("$title $artist".trim())
        if (cleanT.isNotBlank()) {
            queryStrategies.add(cleanT)
        }
        if (title != cleanT && title.isNotBlank()) {
            queryStrategies.add(title)
        }
        val firstWords = cleanT.split(" ").take(2).joinToString(" ")
        if (firstWords.isNotBlank() && cleanA.isNotBlank()) {
            queryStrategies.add("$firstWords $cleanA".trim())
        }

        for (query in queryStrategies.distinct()) {
            if (query.isBlank()) continue
            val candidates = searchAllSources(query)
            if (candidates.isNotEmpty()) {
                val converted = candidates.map { it.toTrack() }
                val matchResult = TrackMatchingEngine.matchTrack(
                    title = title,
                    artist = artist,
                    durationSeconds = durationSeconds,
                    candidates = converted
                )
                val bestCandidate = matchResult.bestMatch?.let { match ->
                    candidates.find { it.id == match.candidateTrack.id }
                }
                if (bestCandidate != null && matchResult.bestMatch.confidence != MatchConfidence.UNMATCHED) {
                    return Pair(bestCandidate, matchResult.bestMatch.confidence)
                }
            }
        }

        return Pair(null, MatchConfidence.UNMATCHED)
    }
}
