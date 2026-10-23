package com.sruti.music.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MusicDao {

    // --- Favorites ---
    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun getAllFavorites(): Flow<List<FavoriteEntity>>

    @Query("SELECT trackId FROM favorites")
    fun getAllFavoriteIds(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(favorite: FavoriteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorites(favorites: List<FavoriteEntity>)

    @Query("DELETE FROM favorites WHERE trackId = :trackId")
    suspend fun deleteFavorite(trackId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE trackId = :trackId)")
    suspend fun isFavorite(trackId: String): Boolean

    // --- History ---
    @Query("SELECT * FROM history ORDER BY playedAt DESC LIMIT 50")
    fun getRecentHistory(): Flow<List<HistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: HistoryEntity)

    @Query("DELETE FROM history")
    suspend fun clearHistory()

    // --- Playlists ---
    @Query("""
        SELECT id, name, 
            COALESCE(artworkUrl, (SELECT artworkUriString FROM playlist_songs WHERE playlistId = playlists.id AND artworkUriString IS NOT NULL AND artworkUriString != '' LIMIT 1)) AS artworkUrl,
            artworkUri,
            createdAt 
        FROM playlists 
        ORDER BY createdAt DESC
    """)
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Query("""
        SELECT id, name, 
            COALESCE(artworkUrl, (SELECT artworkUriString FROM playlist_songs WHERE playlistId = :playlistId AND artworkUriString IS NOT NULL AND artworkUriString != '' LIMIT 1)) AS artworkUrl,
            artworkUri,
            createdAt 
        FROM playlists 
        WHERE id = :playlistId
    """)
    suspend fun getPlaylistById(playlistId: Long): PlaylistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET artworkUri = :artworkUri WHERE id = :playlistId")
    suspend fun updatePlaylistArtworkUri(playlistId: Long, artworkUri: String?)

    @Query("UPDATE playlists SET artworkUrl = :artworkUrl WHERE id = :playlistId")
    suspend fun updatePlaylistArtworkUrl(playlistId: Long, artworkUrl: String?)

    @Query("UPDATE playlists SET name = :name WHERE id = :playlistId")
    suspend fun updatePlaylistName(playlistId: Long, name: String)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun deletePlaylistSongs(playlistId: Long)

    @Query("SELECT * FROM playlist_songs WHERE playlistId = :playlistId ORDER BY sortOrder ASC")
    fun getSongsForPlaylist(playlistId: Long): Flow<List<PlaylistSongEntity>>

    @Query("SELECT * FROM playlist_songs")
    fun getAllPlaylistSongs(): Flow<List<PlaylistSongEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistSong(song: PlaylistSongEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistSongs(songs: List<PlaylistSongEntity>)

    @Update
    suspend fun updatePlaylistSong(song: PlaylistSongEntity)

    @Query("UPDATE playlist_songs SET mediaUriString = :mediaUri, artworkUriString = :artworkUri, sourceId = :sourceId WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun updateTrackResolution(playlistId: Long, trackId: String, mediaUri: String?, artworkUri: String?, sourceId: String)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun removeSongFromPlaylist(playlistId: Long, trackId: String)

    // --- Queue Snapshot ---
    @Query("SELECT * FROM queue_snapshot ORDER BY orderIndex ASC")
    suspend fun getQueueSnapshot(): List<QueueSnapshotEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQueueSnapshot(items: List<QueueSnapshotEntity>)

    @Query("DELETE FROM queue_snapshot")
    suspend fun clearQueueSnapshot()

    @Transaction
    suspend fun replaceQueueSnapshot(items: List<QueueSnapshotEntity>) {
        clearQueueSnapshot()
        insertQueueSnapshot(items)
    }

    // --- Downloads ---
    @Query("SELECT * FROM downloads WHERE status = 'COMPLETED' ORDER BY downloadedAt DESC")
    fun getAllCompletedDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE trackId = :trackId")
    suspend fun getDownloadByTrackId(trackId: String): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE status = 'COMPLETED' AND (title LIKE '%' || :query || '%' OR localFilePath LIKE '%' || :query || '%') LIMIT 1")
    suspend fun findDownloadByTitle(query: String): DownloadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownload(download: DownloadEntity)

    @Query("DELETE FROM downloads WHERE trackId = :trackId")
    suspend fun deleteDownload(trackId: String)
}
