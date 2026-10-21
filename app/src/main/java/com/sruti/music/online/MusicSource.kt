package com.sruti.music.online

import com.sruti.music.R
import com.sruti.music.data.Track

data class OnlineTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val durationSeconds: Int,
    val artworkUrl: String? = null,
    val streamUrl: String? = null,
    val downloadUrl: String? = null,
    val sourceId: String,
    val permitsDownload: Boolean = false,
    val license: String? = null
) {
    val durationText: String
        get() {
            val minutes = durationSeconds / 60
            val seconds = durationSeconds % 60
            return String.format("%d:%02d", minutes, seconds)
        }

    fun toTrack(): Track {
        return Track(
            id = id,
            title = title,
            artist = artist,
            album = album,
            durationText = durationText,
            durationSeconds = durationSeconds,
            artworkResId = R.drawable.sruti_artwork_fallback,
            artworkUriString = artworkUrl,
            mediaUriString = streamUrl,
            isLocal = false,
            source = sourceId
        )
    }
}

interface MusicSource {
    val sourceId: String
    val displayName: String
    val permitsDownload: Boolean

    suspend fun search(query: String): Result<List<OnlineTrack>>
    suspend fun resolveStream(track: OnlineTrack): Result<String>
}
