package com.sruti.music.online

import com.sruti.music.data.Track
import kotlin.math.abs

enum class MatchConfidence {
    EXACT,      // Confident match (> 75%)
    UNCERTAIN,  // Acceptable match (40% - 75%)
    UNMATCHED   // No suitable match found (< 40%)
}

data class TrackMatch(
    val candidateTrack: Track,
    val score: Float,
    val confidence: MatchConfidence
)

data class TrackMatchResult(
    val queryTitle: String,
    val queryArtist: String,
    val bestMatch: TrackMatch?,
    val alternativeCandidates: List<TrackMatch>
)

object TrackMatchingEngine {

    /**
     * Clean title by removing parenthetical noise, anime season tags, and feature tags
     * which often cause online search APIs to return zero results.
     */
    fun cleanTitle(title: String): String {
        return title
            .replace(Regex("(?i)\\(.*?(feat|ft|originally|from|version|remix|theme|ost|ed|op|part|season).*?\\)"), "")
            .replace(Regex("\\(.*?\\)"), "")
            .replace(Regex("\\[.*?\\]"), "")
            .replace(Regex("(?i)feat\\.?\\s+.*"), "")
            .replace(Regex("(?i)ft\\.?\\s+.*"), "")
            .replace(Regex("(?i)-\\s*original.*"), "")
            .replace(Regex("(?i)-\\s*from.*"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun matchTrack(
        title: String,
        artist: String,
        durationSeconds: Int = 0,
        candidates: List<Track>
    ): TrackMatchResult {
        if (candidates.isEmpty()) {
            return TrackMatchResult(title, artist, null, emptyList())
        }

        val scored = candidates.map { candidate ->
            val score = calculateSimilarity(
                queryTitle = title,
                queryArtist = artist,
                queryDuration = durationSeconds,
                candidate = candidate
            )
            val confidence = when {
                score >= 0.72f -> MatchConfidence.EXACT
                score >= 0.40f -> MatchConfidence.UNCERTAIN
                else -> MatchConfidence.UNMATCHED
            }
            TrackMatch(candidate, score, confidence)
        }.sortedByDescending { it.score }

        val best = scored.firstOrNull()?.takeIf { it.confidence != MatchConfidence.UNMATCHED }
        val alternatives = scored.filter { it != best && it.confidence != MatchConfidence.UNMATCHED }.take(5)

        return TrackMatchResult(
            queryTitle = title,
            queryArtist = artist,
            bestMatch = best,
            alternativeCandidates = alternatives
        )
    }

    private fun calculateSimilarity(
        queryTitle: String,
        queryArtist: String,
        queryDuration: Int,
        candidate: Track
    ): Float {
        val normQueryTitle = normalize(queryTitle)
        val normCandidateTitle = normalize(candidate.title)

        val normQueryArtist = normalize(queryArtist)
        val normCandidateArtist = normalize(candidate.artist)

        val titleScore = tokenSimilarity(normQueryTitle, normCandidateTitle)
        val artistScore = tokenSimilarity(normQueryArtist, normCandidateArtist)

        val durationScore = if (queryDuration > 0 && candidate.durationSeconds > 0) {
            val diffSec = abs(queryDuration - candidate.durationSeconds)
            (1.0f - (diffSec / 35.0f)).coerceIn(0.0f, 1.0f)
        } else {
            0.7f // Neutral when duration is unavailable
        }

        return (0.55f * titleScore) + (0.30f * artistScore) + (0.15f * durationScore)
    }

    private fun tokenSimilarity(s1: String, s2: String): Float {
        if (s1 == s2) return 1.0f
        if (s1.isEmpty() || s2.isEmpty()) return 0.0f
        if (s1.contains(s2) || s2.contains(s1)) return 0.90f

        val tokens1 = s1.split(" ").filter { it.isNotBlank() }.toSet()
        val tokens2 = s2.split(" ").filter { it.isNotBlank() }.toSet()

        val intersection = tokens1.intersect(tokens2).size
        val union = tokens1.union(tokens2).size

        val tokenScore = if (union > 0) intersection.toFloat() / union.toFloat() else 0.0f

        // For non-spaced scripts (Japanese Kanji/Kana, Chinese) or single-token titles:
        // calculate character-level intersection
        val chars1 = s1.filter { !it.isWhitespace() }.toSet()
        val chars2 = s2.filter { !it.isWhitespace() }.toSet()
        val charIntersection = chars1.intersect(chars2).size
        val charUnion = chars1.union(chars2).size
        val charScore = if (charUnion > 0) charIntersection.toFloat() / charUnion.toFloat() else 0.0f

        return maxOf(tokenScore, charScore)
    }

    /**
     * Unicode-aware normalization preserving letters and numbers in all languages
     * (Japanese Kanji/Hiragana/Katakana, Korean Hangul, Indian scripts, Latin accents, Cyrillic).
     */
    fun normalize(str: String): String {
        return str.lowercase()
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}

