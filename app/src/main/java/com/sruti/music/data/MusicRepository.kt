package com.sruti.music.data

import com.sruti.music.R

object MusicRepository {

    // Initial clean track placeholder with official Sruti artwork fallback
    val initialTrack: Track = Track(
        id = "sruti_initial",
        title = "Sruti Music",
        artist = "Select a track to play",
        durationText = "--:--",
        durationSeconds = 0,
        artworkResId = R.drawable.sruti_artwork_fallback,
        isLocal = false
    )

    val nowPlayingTrack: Track = initialTrack

    // Bundled fallback tracks used only if offline and no device tracks exist
    val tracks: List<Track> = listOf(
        Track(
            id = "demo_1",
            title = "Sruti Acoustic Echo",
            artist = "Sruti Studio",
            durationText = "3:58",
            durationSeconds = 238,
            artworkResId = R.drawable.sruti_artwork_fallback,
            source = "bundled"
        ),
        Track(
            id = "demo_2",
            title = "Cyber Rhythm",
            artist = "Sruti Sound",
            durationText = "0:15",
            durationSeconds = 15,
            artworkResId = R.drawable.sruti_artwork_fallback,
            source = "bundled"
        )
    )
}
