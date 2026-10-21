package com.sruti.music.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.sruti.music.R
import com.sruti.music.data.MusicRepository
import com.sruti.music.data.Track
import com.sruti.music.data.UserPreferencesRepository
import com.sruti.music.data.db.FavoriteEntity
import com.sruti.music.data.db.HistoryEntity
import com.sruti.music.data.db.QueueSnapshotEntity
import com.sruti.music.data.db.SrutiDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Collections

enum class RepeatState {
    OFF,
    ALL,
    ONE
}

data class PlaybackUiState(
    val currentTrack: Track = MusicRepository.nowPlayingTrack,
    val isPlaying: Boolean = false,
    val currentPositionSeconds: Int = 0,
    val totalDurationSeconds: Int = 238,
    val isShuffleEnabled: Boolean = false,
    val isSmartShuffleEnabled: Boolean = false,
    val repeatState: RepeatState = RepeatState.OFF,
    val isLiked: Boolean = false,
    val queue: List<Track> = listOf(MusicRepository.nowPlayingTrack),
    val currentQueueIndex: Int = 0
)

object PlaybackStateManager {

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    private var appContext: Context? = null
    private var database: SrutiDatabase? = null
    private var prefsRepo: UserPreferencesRepository? = null

    private var positionTickerJob: Job? = null

    // Preserved for Smart Shuffle reversibility
    private val originalQueue = mutableListOf<Track>()

    private val _state = MutableStateFlow(PlaybackUiState())
    val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    fun init(context: Context) {
        if (appContext != null) return
        val application = context.applicationContext
        appContext = application
        database = SrutiDatabase.getDatabase(application)
        prefsRepo = UserPreferencesRepository(application)

        // Load saved preferences
        scope.launch {
            prefsRepo?.shuffleEnabled?.firstOrNull()?.let { savedShuffle ->
                _state.update { it.copy(isShuffleEnabled = savedShuffle) }
            }
            prefsRepo?.repeatMode?.firstOrNull()?.let { savedRepeat ->
                val repeatModeEnum = when (savedRepeat) {
                    1 -> RepeatState.ALL
                    2 -> RepeatState.ONE
                    else -> RepeatState.OFF
                }
                _state.update { it.copy(repeatState = repeatModeEnum) }
            }
            // Restore favorites check
            val currentId = _state.value.currentTrack.id
            val isFav = database?.musicDao()?.isFavorite(currentId) == true
            _state.update { it.copy(isLiked = isFav) }

            // Restore queue snapshot if present, otherwise populate from real device audio
            val savedQueue = database?.musicDao()?.getQueueSnapshot()
            if (!savedQueue.isNullOrEmpty()) {
                val restoredTracks = savedQueue.map { entity ->
                    Track(
                        id = entity.trackId,
                        title = entity.title,
                        artist = entity.artist,
                        album = entity.album,
                        durationText = entity.durationText,
                        durationSeconds = entity.durationSeconds,
                        artworkResId = if (entity.artworkResId == com.sruti.music.R.drawable.sruti_artwork_fallback) entity.artworkResId else com.sruti.music.R.drawable.sruti_artwork_fallback,
                        artworkUriString = entity.artworkUriString,
                        mediaUriString = entity.mediaUriString,
                        isLocal = entity.isLocal,
                        isRecommended = entity.isRecommended
                    )
                }
                _state.update {
                    it.copy(
                        queue = restoredTracks,
                        currentTrack = restoredTracks.firstOrNull() ?: it.currentTrack
                    )
                }
            } else {
                val devTracks = com.sruti.music.data.LocalAudioRepository(application).queryDeviceAudio()
                if (devTracks.isNotEmpty()) {
                    _state.update {
                        it.copy(
                            queue = devTracks,
                            currentTrack = devTracks[0]
                        )
                    }
                }
            }
        }

        // Connect MediaController to SrutiPlaybackService
        val sessionToken = SessionToken(
            application,
            ComponentName(application, SrutiPlaybackService::class.java)
        )

        controllerFuture = MediaController.Builder(application, sessionToken).buildAsync()
        controllerFuture?.addListener({
            try {
                mediaController = controllerFuture?.get()
                setupControllerListener()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, MoreExecutors.directExecutor())
    }

    private fun setupControllerListener() {
        val controller = mediaController ?: return

        controller.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.update { it.copy(isPlaying = isPlaying) }
                if (isPlaying) {
                    startPositionTicker()
                } else {
                    stopPositionTicker()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    val durationMs = controller.duration
                    if (durationMs > 0) {
                        val durationSec = (durationMs / 1000).toInt()
                        _state.update { it.copy(totalDurationSeconds = durationSec) }
                    }
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val mediaId = mediaItem?.mediaId ?: return
                val foundTrack = _state.value.queue.find { it.id == mediaId }
                if (foundTrack != null) {
                    val index = _state.value.queue.indexOf(foundTrack)
                    _state.update {
                        it.copy(
                            currentTrack = foundTrack,
                            currentQueueIndex = if (index >= 0) index else it.currentQueueIndex,
                            totalDurationSeconds = foundTrack.durationSeconds
                        )
                    }
                    recordTrackPlayed(foundTrack)
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                val sec = (newPosition.positionMs / 1000).toInt()
                _state.update { it.copy(currentPositionSeconds = sec) }
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                val repeatEnum = when (repeatMode) {
                    Player.REPEAT_MODE_ONE -> RepeatState.ONE
                    Player.REPEAT_MODE_ALL -> RepeatState.ALL
                    else -> RepeatState.OFF
                }
                _state.update { it.copy(repeatState = repeatEnum) }
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                _state.update { it.copy(isShuffleEnabled = shuffleModeEnabled) }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                android.util.Log.e("PlaybackStateManager", "ExoPlayer playback error: ${error.errorCodeName} (code ${error.errorCode}): ${error.message}")
                val q = _state.value.queue
                val nextPlayable = q.drop(_state.value.currentQueueIndex + 1).firstOrNull { !it.mediaUriString.isNullOrBlank() }
                if (nextPlayable != null) {
                    playTrack(nextPlayable, q)
                } else {
                    _state.update { it.copy(isPlaying = false) }
                }
            }
        })
    }

    private fun startPositionTicker() {
        positionTickerJob?.cancel()
        positionTickerJob = scope.launch {
            while (isActive) {
                val controller = mediaController
                if (controller != null && controller.isPlaying) {
                    val posSec = (controller.currentPosition / 1000).toInt()
                    val durSec = if (controller.duration > 0) (controller.duration / 1000).toInt() else _state.value.totalDurationSeconds
                    _state.update {
                        it.copy(
                            currentPositionSeconds = posSec,
                            totalDurationSeconds = if (durSec > 0) durSec else it.totalDurationSeconds
                        )
                    }
                }
                delay(500)
            }
        }
    }

    private fun stopPositionTicker() {
        positionTickerJob?.cancel()
        val controller = mediaController
        if (controller != null) {
            val posSec = (controller.currentPosition / 1000).toInt()
            _state.update { it.copy(currentPositionSeconds = posSec) }
        }
    }

    fun playTrack(track: Track, newQueue: List<Track> = listOf(track)) {
        val controller = mediaController

        val queueList = if (newQueue.contains(track)) newQueue else listOf(track) + newQueue
        val trackIndex = queueList.indexOf(track).coerceAtLeast(0)

        originalQueue.clear()
        originalQueue.addAll(queueList)

        _state.update {
            it.copy(
                currentTrack = track,
                queue = queueList,
                currentQueueIndex = trackIndex,
                isPlaying = true,
                currentPositionSeconds = 0,
                totalDurationSeconds = track.durationSeconds
            )
        }

        scope.launch {
            checkFavoriteStatus(track.id)
            saveQueueSnapshot(queueList)

            // If track has no media URI, attempt resolution on-the-fly
            if (track.mediaUriString.isNullOrBlank()) {
                android.util.Log.d("PlaybackStateManager", "Track '${track.title}' has no stream URI, resolving on-the-fly...")
                val (matched, _) = com.sruti.music.online.MusicSourceRegistry.matchTrackOnline(track.title, track.artist, track.durationSeconds)
                if (matched != null && !matched.streamUrl.isNullOrBlank()) {
                    val updatedTrack = track.copy(mediaUriString = matched.streamUrl, artworkUriString = matched.artworkUrl ?: track.artworkUriString)
                    val updatedQueue = queueList.map { if (it.id == track.id) updatedTrack else it }
                    playTrack(updatedTrack, updatedQueue)
                    return@launch
                }
            }
        }

        if (controller != null) {
            val mediaItems = queueList.map { it.toMediaItem() }
            controller.setMediaItems(mediaItems, trackIndex, 0L)
            controller.prepare()
            controller.play()
        } else {
            // Controller not yet connected; fallback local state update
            startPositionTicker()
        }

        recordTrackPlayed(track)
    }

    fun togglePlayPause() {
        val controller = mediaController
        if (controller != null) {
            if (controller.isPlaying) {
                controller.pause()
            } else {
                if (controller.playbackState == Player.STATE_IDLE || controller.mediaItemCount == 0) {
                    val current = _state.value.currentTrack
                    playTrack(current, _state.value.queue)
                } else {
                    controller.play()
                }
            }
        } else {
            val newState = !_state.value.isPlaying
            _state.update { it.copy(isPlaying = newState) }
            if (newState) startPositionTicker() else stopPositionTicker()
        }
    }

    fun seekTo(seconds: Int) {
        val controller = mediaController
        val targetMs = (seconds * 1000L).coerceAtLeast(0L)
        if (controller != null) {
            controller.seekTo(targetMs)
        }
        _state.update { it.copy(currentPositionSeconds = seconds) }
    }

    fun playNext() {
        val controller = mediaController
        if (controller != null && controller.hasNextMediaItem()) {
            controller.seekToNextMediaItem()
            return
        }

        val queue = _state.value.queue
        if (queue.isEmpty()) return

        val currentIndex = _state.value.currentQueueIndex
        val nextIndex = if (currentIndex + 1 < queue.size) currentIndex + 1 else 0
        val nextTrack = queue[nextIndex]

        if (controller != null && controller.mediaItemCount > nextIndex) {
            controller.seekToDefaultPosition(nextIndex)
            controller.play()
        } else {
            playTrack(nextTrack, queue)
        }
    }

    fun playPrevious() {
        val controller = mediaController
        if (controller != null) {
            if (controller.currentPosition > 3000L) {
                controller.seekTo(0L)
                return
            }
            if (controller.hasPreviousMediaItem()) {
                controller.seekToPreviousMediaItem()
                return
            }
        }

        val queue = _state.value.queue
        if (queue.isEmpty()) return

        val currentIndex = _state.value.currentQueueIndex
        val prevIndex = if (currentIndex > 0) currentIndex - 1 else queue.lastIndex
        val prevTrack = queue[prevIndex]

        if (controller != null && controller.mediaItemCount > prevIndex) {
            controller.seekToDefaultPosition(prevIndex)
            controller.play()
        } else {
            playTrack(prevTrack, queue)
        }
    }
    
    fun skipToNext() = playNext()
    fun skipToPrevious() = playPrevious()

    fun addToQueue(track: Track) {
        val updatedQueue = _state.value.queue + track
        _state.update { it.copy(queue = updatedQueue) }
        mediaController?.addMediaItem(track.toMediaItem())
        scope.launch { saveQueueSnapshot(updatedQueue) }
    }

    fun playNextInQueue(track: Track) {
        val queue = _state.value.queue.toMutableList()
        val insertIndex = (_state.value.currentQueueIndex + 1).coerceAtMost(queue.size)
        queue.add(insertIndex, track)
        _state.update { it.copy(queue = queue) }
        mediaController?.addMediaItem(insertIndex, track.toMediaItem())
        scope.launch { saveQueueSnapshot(queue) }
    }

    fun removeFromQueue(index: Int) {
        val queue = _state.value.queue.toMutableList()
        if (index in queue.indices) {
            queue.removeAt(index)
            mediaController?.removeMediaItem(index)
            val newCurrentIndex = if (index < _state.value.currentQueueIndex) {
                _state.value.currentQueueIndex - 1
            } else _state.value.currentQueueIndex
            _state.update { it.copy(queue = queue, currentQueueIndex = newCurrentIndex.coerceAtLeast(0)) }
            scope.launch { saveQueueSnapshot(queue) }
        }
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val queue = _state.value.queue.toMutableList()
        if (fromIndex in queue.indices && toIndex in queue.indices) {
            Collections.swap(queue, fromIndex, toIndex)
            mediaController?.moveMediaItem(fromIndex, toIndex)
            _state.update { it.copy(queue = queue) }
            scope.launch { saveQueueSnapshot(queue) }
        }
    }

    fun addTracksToQueue(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        val updatedQueue = _state.value.queue + tracks
        _state.update { it.copy(queue = updatedQueue) }
        val mediaItems = tracks.map { it.toMediaItem() }
        mediaController?.addMediaItems(mediaItems)
        scope.launch { saveQueueSnapshot(updatedQueue) }
    }

    fun playTracksNext(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        val queue = _state.value.queue.toMutableList()
        val insertIndex = (_state.value.currentQueueIndex + 1).coerceAtMost(queue.size)
        queue.addAll(insertIndex, tracks)
        _state.update { it.copy(queue = queue) }
        val mediaItems = tracks.map { it.toMediaItem() }
        mediaController?.addMediaItems(insertIndex, mediaItems)
        scope.launch { saveQueueSnapshot(queue) }
    }

    fun clearUpcomingQueue() {
        val queue = _state.value.queue.toMutableList()
        val curIndex = _state.value.currentQueueIndex
        if (curIndex + 1 < queue.size) {
            val toRemove = queue.size - (curIndex + 1)
            for (i in 0 until toRemove) {
                mediaController?.removeMediaItem(curIndex + 1)
            }
            val newQueue = queue.subList(0, curIndex + 1).toList()
            _state.update { it.copy(queue = newQueue) }
            scope.launch { saveQueueSnapshot(newQueue) }
        }
    }

    fun toggleShuffle() {
        val newShuffle = !_state.value.isShuffleEnabled
        _state.update { it.copy(isShuffleEnabled = newShuffle) }
        mediaController?.shuffleModeEnabled = newShuffle
        scope.launch { prefsRepo?.saveShuffleEnabled(newShuffle) }
    }

    fun toggleSmartShuffle() {
        val currentSmart = _state.value.isSmartShuffleEnabled
        val newSmart = !currentSmart

        if (newSmart) {
            // Enable Smart Shuffle: preserve original order, insert marked recommendations
            val current = _state.value.currentTrack
            val currentQueue = _state.value.queue
            originalQueue.clear()
            originalQueue.addAll(currentQueue)

            // Distinct recommendation placeholder architecture
            val sampleRecommendation = Track(
                id = "rec_smart_1",
                title = "Starlight Serenade (Recommended)",
                artist = "Sruti Discovery",
                album = "Discovery Mix",
                durationText = "3:45",
                durationSeconds = 225,
                artworkResId = R.drawable.sruti_artwork_fallback,
                isRecommended = true
            )

            val smartQueue = currentQueue.toMutableList()
            val insertIndex = (_state.value.currentQueueIndex + 1).coerceAtMost(smartQueue.size)
            smartQueue.add(insertIndex, sampleRecommendation)

            _state.update {
                it.copy(
                    isSmartShuffleEnabled = true,
                    queue = smartQueue
                )
            }
        } else {
            // Reversible restore to original playlist ordering!
            if (originalQueue.isNotEmpty()) {
                val current = _state.value.currentTrack
                val originalIndex = originalQueue.indexOfFirst { it.id == current.id }.coerceAtLeast(0)
                _state.update {
                    it.copy(
                        isSmartShuffleEnabled = false,
                        queue = originalQueue.toList(),
                        currentQueueIndex = originalIndex
                    )
                }
            } else {
                // Filter out recommended tracks
                val cleaned = _state.value.queue.filter { !it.isRecommended }
                _state.update {
                    it.copy(
                        isSmartShuffleEnabled = false,
                        queue = cleaned
                    )
                }
            }
        }
    }

    fun cycleRepeatMode() {
        val current = _state.value.repeatState
        val next = when (current) {
            RepeatState.OFF -> RepeatState.ALL
            RepeatState.ALL -> RepeatState.ONE
            RepeatState.ONE -> RepeatState.OFF
        }
        _state.update { it.copy(repeatState = next) }

        val exoRepeatMode = when (next) {
            RepeatState.ALL -> Player.REPEAT_MODE_ALL
            RepeatState.ONE -> Player.REPEAT_MODE_ONE
            RepeatState.OFF -> Player.REPEAT_MODE_OFF
        }
        mediaController?.repeatMode = exoRepeatMode

        scope.launch {
            val prefCode = when (next) {
                RepeatState.ALL -> 1
                RepeatState.ONE -> 2
                RepeatState.OFF -> 0
            }
            prefsRepo?.saveRepeatMode(prefCode)
        }
    }

    fun toggleLike() {
        val track = _state.value.currentTrack
        if (track.id.isBlank()) return
        val newLiked = !_state.value.isLiked
        _state.update { it.copy(isLiked = newLiked) }

        scope.launch(Dispatchers.IO) {
            val dao = database?.musicDao() ?: return@launch
            if (newLiked) {
                dao.insertFavorite(
                    FavoriteEntity(
                        trackId = track.id,
                        title = track.title,
                        artist = track.artist,
                        album = track.album,
                        durationSeconds = track.durationSeconds,
                        mediaUriString = track.mediaUriString,
                        artworkUriString = track.artworkUriString,
                        artworkResId = track.artworkResId,
                        isLocal = track.isLocal,
                        addedAt = System.currentTimeMillis()
                    )
                )
            } else {
                dao.deleteFavorite(track.id)
            }
        }
    }

    fun toggleLikeTrack(track: Track, isCurrentlyLiked: Boolean) {
        if (track.id.isBlank()) return
        val newLiked = !isCurrentlyLiked
        if (_state.value.currentTrack.id == track.id) {
            _state.update { it.copy(isLiked = newLiked) }
        }

        scope.launch(Dispatchers.IO) {
            val dao = database?.musicDao() ?: return@launch
            if (newLiked) {
                dao.insertFavorite(
                    FavoriteEntity(
                        trackId = track.id,
                        title = track.title,
                        artist = track.artist,
                        album = track.album,
                        durationSeconds = track.durationSeconds,
                        mediaUriString = track.mediaUriString,
                        artworkUriString = track.artworkUriString,
                        artworkResId = track.artworkResId,
                        isLocal = track.isLocal,
                        addedAt = System.currentTimeMillis()
                    )
                )
            } else {
                dao.deleteFavorite(track.id)
            }
        }
    }

    private suspend fun checkFavoriteStatus(trackId: String) {
        val isFav = database?.musicDao()?.isFavorite(trackId) == true
        _state.update { it.copy(isLiked = isFav) }
    }

    private fun recordTrackPlayed(track: Track) {
        scope.launch(Dispatchers.IO) {
            database?.musicDao()?.insertHistory(
                HistoryEntity(
                    trackId = track.id,
                    title = track.title,
                    artist = track.artist,
                    album = track.album,
                    durationSeconds = track.durationSeconds,
                    mediaUriString = track.mediaUriString,
                    artworkUriString = track.artworkUriString,
                    artworkResId = track.artworkResId
                )
            )
            prefsRepo?.saveLastPlayedTrackId(track.id)
        }
    }

    private suspend fun saveQueueSnapshot(queue: List<Track>) {
        val entities = queue.mapIndexed { index, track ->
            QueueSnapshotEntity(
                orderIndex = index,
                trackId = track.id,
                title = track.title,
                artist = track.artist,
                album = track.album,
                durationText = track.durationText,
                durationSeconds = track.durationSeconds,
                artworkResId = track.artworkResId,
                artworkUriString = track.artworkUriString,
                mediaUriString = track.mediaUriString,
                isLocal = track.isLocal,
                isRecommended = track.isRecommended
            )
        }
        database?.musicDao()?.replaceQueueSnapshot(entities)
    }

    private fun Track.toMediaItem(): MediaItem {
        val uri = if (this.mediaUriString != null) {
            Uri.parse(this.mediaUriString)
        } else {
            // Bundled asset or generated tone URI fallback
            Uri.EMPTY
        }

        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(this.title)
            .setArtist(this.artist)
            .setAlbumTitle(this.album)

        if (this.artworkUriString != null) {
            metadataBuilder.setArtworkUri(Uri.parse(this.artworkUriString))
        }

        return MediaItem.Builder()
            .setMediaId(this.id)
            .setUri(uri)
            .setMediaMetadata(metadataBuilder.build())
            .build()
    }
}
