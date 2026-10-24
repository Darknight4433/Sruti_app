package com.sruti.music.data

import com.sruti.music.data.db.FavoriteEntity
import com.sruti.music.data.db.HistoryEntity
import com.sruti.music.data.db.PlaylistSongEntity

data class UserMusicTaste(
    val primaryGenre: String,
    val searchQuery: String,
    val titleLabel: String,
    val subtitle: String,
    val topArtist: String? = null
)

object UserTasteDetector {

    // J-Pop signals (artists, anime, vocaloid, Japanese keywords)
    private val JPOP_KEYWORDS = setOf(
        "j-pop", "jpop", "yoasobi", "ado", "kenshi yonezu", "lisa", "radwimps", "eve",
        "aimer", "vocaloid", "anime", "miku", "fujii kaze", "one ok rock", "king gnu",
        "higedan", "official hige dandism", "yuuri", "vaundy", "tuyu", "zutomayo", "kana-boon",
        "asian kung-fu generation", "japan", "japanese", "j-rock", "naruto", "jujutsu",
        "demon slayer", "bleach", "shingeki", "gurenge", "kaikai kitan", "idol",
        "racing into the night", "sparkle", "suzume", "chainsaw man", "spy x family",
        "yaosobi", "unravel", "blue bird", "silence", "ost anime"
    )

    // Devotional / Spiritual signals (mantras, bhajans, aarties, stotrams)
    private val DEVOTIONAL_KEYWORDS = setOf(
        "devotional", "bhajan", "aarti", "mantra", "chalisa", "shiva", "krishna", "hanuman",
        "ganesh", "stotram", "suprabhatam", "anup jalota", "anuradha paudwal", "hariharan",
        "m.s. subbulakshmi", "aniruddhacharya", "ram", "govinda", "sai", "durga", "vishnu",
        "gayatri", "temple", "spiritual", "sloka", "shlok", "sahasranamam", "bhakti",
        "jagjit singh", "ganpati", "mahamrityunjaya", "lingashtakam", "madhurashtakam",
        "hare krishna", "iskcon", "radhe", "venkateswara", "ayigiri", "rudram"
    )

    // K-Pop signals
    private val KPOP_KEYWORDS = setOf(
        "k-pop", "kpop", "bts", "blackpink", "newjeans", "twice", "stray kids", "exo",
        "seventeen", "iu", "le sserafim", "aespa", "ive", "txt", "enhypen", "jungkook",
        "jimin", "agust d", "korean", "taeyeon", "super junior", "red velvet", "itzy",
        "dynamite", "butter", "hype boy", "super shy", "cupid", "seven"
    )

    // Bollywood / Hindi signals
    private val BOLLYWOOD_KEYWORDS = setOf(
        "arijit singh", "shreya ghoshal", "pritam", "badshah", "atif aslam", "bollywood",
        "hindi", "neha kakkar", "jubin nautiyal", "darshan raval", "vishal mishra",
        "sachin-jigar", "kumar sanu", "udit narayan", "alka yagnik", "sonu nigam",
        "sunidhi chauhan", "arman malik", "mohit chauhan", "kk", "arjit", "arijit"
    )

    // Carnatic / Indian Classical signals
    private val CARNATIC_KEYWORDS = setOf(
        "carnatic", "classical", "ragam", "thillana", "kriti", "tyagaraja", "purandara",
        "veena", "flute", "santoor", "sitar", "bhimsen joshi", "ravi shankar", "hariprasad",
        "instrumental", "m.s. subbulakshmi", "sudha ragunathan", "sanjay subrahmanyan",
        "mandolin srinivas", "kadri gopalnath", "lalgudi"
    )

    // South Indian (Tamil / Telugu / Malayalam / Kannada) signals
    private val SOUTH_KEYWORDS = setOf(
        "anirudh", "a.r. rahman", "rahman", "sid sriram", "yuvan", "harris jayaraj",
        "ilayaraja", "spb", "chithra", "devi sri prasad", "thaman", "santhosh narayanan",
        "sushin shyam", "tamil", "telugu", "malayalam", "kannada", "dsp"
    )

    // Punjabi signals
    private val PUNJABI_KEYWORDS = setOf(
        "punjabi", "diljit", "sidhu moose", "karan aujla", "ap dhillon", "shubh",
        "ammy virk", "guru randhawa", "b praak", "jaani", "harrdy sandhu"
    )

    // Lo-Fi / Chill signals
    private val LOFI_KEYWORDS = setOf(
        "lofi", "lo-fi", "chill", "relax", "study", "beats", "ambient", "aesthetic",
        "rainy", "sleep", "coffee", "calm"
    )

    // Rock / Metal signals
    private val ROCK_KEYWORDS = setOf(
        "rock", "metal", "queen", "linkin park", "metallica", "nirvana", "ac/dc",
        "imagine dragons", "coldplay", "green day", "arctic monkeys", "guns n roses"
    )

    // Western Pop signals
    private val POP_KEYWORDS = setOf(
        "taylor swift", "the weeknd", "billie eilish", "ed sheeran", "dua lipa",
        "ariana grande", "drake", "post malone", "olivia rodrigo", "justin bieber",
        "bruno mars", "charlie puth", "pop"
    )

    // Regex for Japanese Kanji, Hiragana, Katakana
    private val JAPANESE_CHAR_REGEX = Regex("[\\u3040-\\u309F\\u30A0-\\u30FF\\u4E00-\\u9FAF]")
    // Regex for Korean Hangul
    private val KOREAN_CHAR_REGEX = Regex("[\\uAC00-\\uD7AF]")

    fun detectTaste(
        history: List<HistoryEntity>,
        favorites: List<FavoriteEntity>,
        playlistSongs: List<PlaylistSongEntity>
    ): UserMusicTaste {
        val texts = mutableListOf<String>()
        val artistCounts = mutableMapOf<String, Int>()

        fun processTrack(title: String, artist: String, album: String) {
            val combined = "$title $artist $album".lowercase()
            texts.add(combined)
            val cleanedArtist = artist.trim()
            if (cleanedArtist.isNotBlank() &&
                !cleanedArtist.equals("unknown", ignoreCase = true) &&
                !cleanedArtist.equals("unknown artist", ignoreCase = true) &&
                !cleanedArtist.equals("various artists", ignoreCase = true)
            ) {
                artistCounts[cleanedArtist] = (artistCounts[cleanedArtist] ?: 0) + 1
            }
        }

        history.forEach { processTrack(it.title, it.artist, it.album) }
        favorites.forEach { processTrack(it.title, it.artist, it.album) }
        playlistSongs.take(50).forEach { processTrack(it.title, it.artist, it.album) }

        if (texts.isEmpty()) {
            return UserMusicTaste(
                primaryGenre = "Trending",
                searchQuery = "Top Trending Hits",
                titleLabel = "Suggested for You • Top Hits 🔥",
                subtitle = "Discover popular songs tailored for you"
            )
        }

        var jpopScore = 0
        var devotionalScore = 0
        var kpopScore = 0
        var bollywoodScore = 0
        var carnaticScore = 0
        var southScore = 0
        var punjabiScore = 0
        var lofiScore = 0
        var rockScore = 0
        var popScore = 0

        for (text in texts) {
            if (JPOP_KEYWORDS.any { text.contains(it) } || JAPANESE_CHAR_REGEX.containsMatchIn(text)) {
                jpopScore++
            }
            if (DEVOTIONAL_KEYWORDS.any { text.contains(it) }) {
                devotionalScore++
            }
            if (KPOP_KEYWORDS.any { text.contains(it) } || KOREAN_CHAR_REGEX.containsMatchIn(text)) {
                kpopScore++
            }
            if (BOLLYWOOD_KEYWORDS.any { text.contains(it) }) {
                bollywoodScore++
            }
            if (CARNATIC_KEYWORDS.any { text.contains(it) }) {
                carnaticScore++
            }
            if (SOUTH_KEYWORDS.any { text.contains(it) }) {
                southScore++
            }
            if (PUNJABI_KEYWORDS.any { text.contains(it) }) {
                punjabiScore++
            }
            if (LOFI_KEYWORDS.any { text.contains(it) }) {
                lofiScore++
            }
            if (ROCK_KEYWORDS.any { text.contains(it) }) {
                rockScore++
            }
            if (POP_KEYWORDS.any { text.contains(it) }) {
                popScore++
            }
        }

        val topArtist = artistCounts.maxByOrNull { it.value }?.key

        val scores = listOf(
            "J-Pop" to jpopScore,
            "Devotional" to devotionalScore,
            "K-Pop" to kpopScore,
            "Bollywood" to bollywoodScore,
            "Carnatic" to carnaticScore,
            "South Indian" to southScore,
            "Punjabi" to punjabiScore,
            "Lo-Fi" to lofiScore,
            "Rock" to rockScore,
            "Pop" to popScore
        ).sortedByDescending { it.second }

        val best = scores.firstOrNull()

        return when {
            best != null && best.second > 0 -> {
                when (best.first) {
                    "J-Pop" -> UserMusicTaste(
                        primaryGenre = "J-Pop",
                        searchQuery = if (!topArtist.isNullOrBlank() && JPOP_KEYWORDS.any { topArtist.lowercase().contains(it) }) "$topArtist Hits" else "J-Pop Hits",
                        titleLabel = "Suggested for You • J-Pop 🌸",
                        subtitle = "Curated anime & Japanese tracks matching your taste",
                        topArtist = topArtist
                    )
                    "Devotional" -> UserMusicTaste(
                        primaryGenre = "Devotional",
                        searchQuery = if (!topArtist.isNullOrBlank() && DEVOTIONAL_KEYWORDS.any { topArtist.lowercase().contains(it) }) "$topArtist Bhajans" else "Top Devotional Bhajans",
                        titleLabel = "Suggested for You • Devotional 🙏",
                        subtitle = "Sacred mantras, aarties, and spiritual bhajans",
                        topArtist = topArtist
                    )
                    "K-Pop" -> UserMusicTaste(
                        primaryGenre = "K-Pop",
                        searchQuery = if (!topArtist.isNullOrBlank() && KPOP_KEYWORDS.any { topArtist.lowercase().contains(it) }) "$topArtist Hits" else "K-Pop Top Hits",
                        titleLabel = "Suggested for You • K-Pop ✨",
                        subtitle = "Top K-Pop tracks tailored to your favorites",
                        topArtist = topArtist
                    )
                    "Bollywood" -> UserMusicTaste(
                        primaryGenre = "Bollywood",
                        searchQuery = if (!topArtist.isNullOrBlank()) "$topArtist Top Hits" else "Bollywood Top Hits",
                        titleLabel = "Suggested for You • Bollywood 🎬",
                        subtitle = "Chart-topping Bollywood & Hindi melodies",
                        topArtist = topArtist
                    )
                    "Carnatic" -> UserMusicTaste(
                        primaryGenre = "Carnatic",
                        searchQuery = "Carnatic Classical Hits",
                        titleLabel = "Suggested for You • Classical 🪕",
                        subtitle = "Soulful classical and Carnatic masterpieces",
                        topArtist = topArtist
                    )
                    "South Indian" -> UserMusicTaste(
                        primaryGenre = "South Indian",
                        searchQuery = if (!topArtist.isNullOrBlank()) "$topArtist Hits" else "South Indian Top Hits",
                        titleLabel = "Suggested for You • South Hits 🌴",
                        subtitle = "Trending South Indian cinematic melodies",
                        topArtist = topArtist
                    )
                    "Punjabi" -> UserMusicTaste(
                        primaryGenre = "Punjabi",
                        searchQuery = if (!topArtist.isNullOrBlank()) "$topArtist Hits" else "Punjabi Top Hits",
                        titleLabel = "Suggested for You • Punjabi 🪘",
                        subtitle = "High-energy Punjabi hits and beats",
                        topArtist = topArtist
                    )
                    "Lo-Fi" -> UserMusicTaste(
                        primaryGenre = "Lo-Fi",
                        searchQuery = "Lo-Fi Chill Beats",
                        titleLabel = "Suggested for You • Lo-Fi ☕",
                        subtitle = "Calm beats for relaxing, study, and focus",
                        topArtist = topArtist
                    )
                    "Rock" -> UserMusicTaste(
                        primaryGenre = "Rock",
                        searchQuery = if (!topArtist.isNullOrBlank()) "$topArtist Hits" else "Rock Classics",
                        titleLabel = "Suggested for You • Rock ⚡",
                        subtitle = "Iconic rock anthems and electrifying riffs",
                        topArtist = topArtist
                    )
                    "Pop" -> UserMusicTaste(
                        primaryGenre = "Pop",
                        searchQuery = if (!topArtist.isNullOrBlank()) "$topArtist Hits" else "Global Pop Hits",
                        titleLabel = "Suggested for You • Pop 🌟",
                        subtitle = "Global chartbusters matching your library",
                        topArtist = topArtist
                    )
                    else -> UserMusicTaste(
                        primaryGenre = "Trending",
                        searchQuery = "Top Trending Hits",
                        titleLabel = "Suggested for You 🔥",
                        subtitle = "Trending hits picked for your library"
                    )
                }
            }
            !topArtist.isNullOrBlank() && (artistCounts[topArtist] ?: 0) >= 2 -> {
                UserMusicTaste(
                    primaryGenre = "Artist",
                    searchQuery = "$topArtist Top Songs",
                    titleLabel = "Suggested for You • $topArtist",
                    subtitle = "Recommended tracks inspired by $topArtist",
                    topArtist = topArtist
                )
            }
            else -> {
                UserMusicTaste(
                    primaryGenre = "Trending",
                    searchQuery = "Top Trending Hits",
                    titleLabel = "Suggested for You 🔥",
                    subtitle = "Handpicked popular hits for your vibe"
                )
            }
        }
    }
}
