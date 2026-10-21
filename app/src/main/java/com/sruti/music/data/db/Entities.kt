package com.sruti.music.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val trackId: String,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val durationSeconds: Int = 0,
    val mediaUriString: String? = null,
    val artworkUriString: String? = null,
    val artworkResId: Int = com.sruti.music.R.drawable.sruti_artwork_fallback,
    val isLocal: Boolean = false,
    val addedAt: Long = System.currentTimeMillis()
)

fun FavoriteEntity.toTrack(): com.sruti.music.data.Track {
    val m = durationSeconds / 60
    val s = durationSeconds % 60
    val dur = if (durationSeconds > 0) String.format("%d:%02d", m, s) else "3:30"
    return com.sruti.music.data.Track(
        id = trackId,
        title = title.ifBlank { "Unknown Title" },
        artist = artist.ifBlank { "Unknown Artist" },
        album = album,
        durationText = dur,
        durationSeconds = durationSeconds,
        artworkResId = if (artworkResId != 0) artworkResId else com.sruti.music.R.drawable.sruti_artwork_fallback,
        artworkUriString = artworkUriString,
        mediaUriString = mediaUriString,
        isLocal = isLocal,
        isLiked = true,
        source = if (isLocal) "local" else "online"
    )
}


@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationSeconds: Int,
    val mediaUriString: String?,
    val artworkUriString: String?,
    val artworkResId: Int,
    val playedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val artworkUrl: String? = null,
    val artworkUri: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlist_songs")
data class PlaylistSongEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val trackId: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val durationSeconds: Int = 0,
    val mediaUriString: String? = null,
    val artworkUriString: String? = null,
    val artworkResId: Int = com.sruti.music.R.drawable.sruti_artwork_fallback,
    val sourceId: String = "",
    val sortOrder: Int = 0,
    val addedAt: Long = System.currentTimeMillis()
)

fun PlaylistSongEntity.toTrack(): com.sruti.music.data.Track {
    val m = durationSeconds / 60
    val s = durationSeconds % 60
    val dur = if (durationSeconds > 0) String.format("%d:%02d", m, s) else "3:30"
    return com.sruti.music.data.Track(
        id = trackId,
        title = title.ifBlank { "Unknown Title" },
        artist = artist.ifBlank { "Unknown Artist" },
        album = album,
        durationText = dur,
        durationSeconds = durationSeconds,
        artworkResId = if (artworkResId != 0) artworkResId else com.sruti.music.R.drawable.sruti_artwork_fallback,
        artworkUriString = artworkUriString,
        mediaUriString = mediaUriString,
        isLocal = sourceId == "local",
        source = sourceId
    )
}

@Entity(tableName = "queue_snapshot")
data class QueueSnapshotEntity(
    @PrimaryKey val orderIndex: Int,
    val trackId: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationText: String,
    val durationSeconds: Int,
    val artworkResId: Int,
    val artworkUriString: String?,
    val mediaUriString: String?,
    val isLocal: Boolean,
    val isRecommended: Boolean
)

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val trackId: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val durationSeconds: Int = 0,
    val artworkUriString: String? = null,
    val localFilePath: String,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val status: String = "COMPLETED",
    val sourceId: String = "",
    val downloadedAt: Long = System.currentTimeMillis()
)
