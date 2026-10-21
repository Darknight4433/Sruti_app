package com.sruti.music.data

import androidx.annotation.DrawableRes

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val durationText: String,
    val durationSeconds: Int,
    @DrawableRes val artworkResId: Int = com.sruti.music.R.drawable.sruti_artwork_fallback,
    val artworkUriString: String? = null,
    val mediaUriString: String? = null,
    val isLocal: Boolean = false,
    val isLiked: Boolean = false,
    val isRecommended: Boolean = false,
    val source: String = "local"
)

data class FeaturedCard(
    val id: String,
    val title: String,
    val songCountText: String,
    val badge: String? = null,
    @DrawableRes val artworkResId: Int = com.sruti.music.R.drawable.sruti_artwork_fallback,
    val artworkUriString: String? = null,
    val isTall: Boolean = false,
    val tracks: List<Track> = emptyList()
)
