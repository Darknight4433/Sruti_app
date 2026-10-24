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
            val titleScore = bestTitleSimilarity(title, candidate.title)
            val confidence = when {
                score >= 0.72f && titleScore >= 0.65f -> MatchConfidence.EXACT
                score >= 0.50f && titleScore >= 0.50f -> MatchConfidence.UNCERTAIN
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

    private fun bestTitleSimilarity(queryTitle: String, candidateTitle: String): Float {
        val normRawQuery = normalize(queryTitle)
        val normCleanQuery = normalize(cleanTitle(queryTitle))
        val normRawCandidate = normalize(candidateTitle)
        val normCleanCandidate = normalize(cleanTitle(candidateTitle))

        val s1 = tokenSimilarity(normRawQuery, normRawCandidate)
        val s2 = tokenSimilarity(normCleanQuery, normCleanCandidate)
        val s3 = tokenSimilarity(normRawQuery, normCleanCandidate)
        val s4 = tokenSimilarity(normCleanQuery, normRawCandidate)

        return maxOf(s1, s2, s3, s4)
    }

    private fun calculateSimilarity(
        queryTitle: String,
        queryArtist: String,
        queryDuration: Int,
        candidate: Track
    ): Float {
        val titleScore = bestTitleSimilarity(queryTitle, candidate.title)

        // Strict Gatekeeper: If the title doesn't match, this is definitely the WRONG song.
        // Prevent matching unrelated songs by the same artist.
        if (titleScore < 0.50f) {
            return 0.0f
        }

        val normQueryArtist = normalize(queryArtist)
        val normCandidateArtist = normalize(candidate.artist)
        val artistScore = tokenSimilarity(normQueryArtist, normCandidateArtist)

        val durationScore = if (queryDuration > 0 && candidate.durationSeconds > 0) {
            val diffSec = abs(queryDuration - candidate.durationSeconds)
            if (diffSec > 80) 0.0f else (1.0f - (diffSec / 45.0f)).coerceIn(0.0f, 1.0f)
        } else {
            0.7f // Neutral when duration is unavailable
        }

        // Apply artist penalty if both artists are known and have zero overlap
        val artistMismatchPenalty = if (normQueryArtist.length > 2 && normCandidateArtist.length > 2 && artistScore < 0.15f) {
            if (titleScore > 0.90f) 0.10f else 0.25f
        } else {
            0.0f
        }

        val totalScore = (0.65f * titleScore) + (0.25f * artistScore) + (0.10f * durationScore) - artistMismatchPenalty
        return totalScore.coerceIn(0.0f, 1.0f)
    }

    private fun tokenSimilarity(s1: String, s2: String): Float {
        if (s1 == s2) return 1.0f
        if (s1.isEmpty() || s2.isEmpty()) return 0.0f

        val minLen = minOf(s1.length, s2.length)
        val maxLen = maxOf(s1.length, s2.length)

        // Substring check: Only accept if the shorter string is at least 65% of the longer string
        if ((s1.contains(s2) || s2.contains(s1)) && (minLen.toFloat() / maxLen.toFloat() >= 0.65f)) {
            return 0.88f
        }

        val tokens1 = s1.split(" ").filter { it.isNotBlank() }.toSet()
        val tokens2 = s2.split(" ").filter { it.isNotBlank() }.toSet()

        val intersection = tokens1.intersect(tokens2).size
        val union = tokens1.union(tokens2).size
        val tokenScore = if (union > 0) intersection.toFloat() / union.toFloat() else 0.0f

        // Longest Common Subsequence (LCS) ratio for typos and slight spelling variations
        val lcsLen = longestCommonSubsequenceLength(s1, s2)
        val lcsScore = (2.0f * lcsLen) / (s1.length + s2.length)

        // Check for Asian / CJK non-spaced scripts (Kanji, Hiragana, Katakana, Hangul, Hanzi)
        val hasCjk = s1.any { isCjkOrAsian(it) } || s2.any { isCjkOrAsian(it) }
        val cjkScore = if (hasCjk) {
            val bi1 = characterBigrams(s1)
            val bi2 = characterBigrams(s2)
            val biInter = bi1.intersect(bi2).size
            val biUnion = bi1.union(bi2).size
            if (biUnion > 0) biInter.toFloat() / biUnion.toFloat() else 0.0f
        } else 0.0f

        return maxOf(tokenScore, lcsScore, cjkScore)
    }

    private fun longestCommonSubsequenceLength(s1: String, s2: String): Int {
        val m = s1.length
        val n = s2.length
        if (m == 0 || n == 0) return 0
        if (m > 80 || n > 80) {
            // Trim for performance on extremely long descriptions
            val sub1 = s1.take(80)
            val sub2 = s2.take(80)
            return longestCommonSubsequenceLength(sub1, sub2)
        }

        var prev = IntArray(n + 1)
        var curr = IntArray(n + 1)

        for (i in 1..m) {
            for (j in 1..n) {
                if (s1[i - 1] == s2[j - 1]) {
                    curr[j] = prev[j - 1] + 1
                } else {
                    curr[j] = maxOf(prev[j], curr[j - 1])
                }
            }
            val temp = prev
            prev = curr
            curr = temp
        }
        return prev[n]
    }

    private fun characterBigrams(s: String): Set<String> {
        val clean = s.filter { !it.isWhitespace() }
        if (clean.length < 2) return if (clean.isNotEmpty()) setOf(clean) else emptySet()
        val bigrams = mutableSetOf<String>()
        for (i in 0 until clean.length - 1) {
            bigrams.add(clean.substring(i, i + 2))
        }
        return bigrams
    }

    private fun isCjkOrAsian(c: Char): Boolean {
        val block = Character.UnicodeBlock.of(c)
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
                block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
                block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS ||
                block == Character.UnicodeBlock.HIRAGANA ||
                block == Character.UnicodeBlock.KATAKANA ||
                block == Character.UnicodeBlock.HANGUL_SYLLABLES ||
                block == Character.UnicodeBlock.HANGUL_JAMO ||
                block == Character.UnicodeBlock.HANGUL_COMPATIBILITY_JAMO
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

