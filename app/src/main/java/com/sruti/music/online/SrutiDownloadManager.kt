package com.sruti.music.online

import android.content.Context
import android.net.Uri
import android.util.Log
import com.sruti.music.R
import com.sruti.music.data.Track
import com.sruti.music.data.db.DownloadEntity
import com.sruti.music.data.db.SrutiDatabase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

enum class DownloadStatus {
    IDLE,
    QUEUED,
    DOWNLOADING,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class DownloadRecord(
    val trackId: String,
    val title: String,
    val artist: String,
    val progress: Float,
    val status: DownloadStatus,
    val localFilePath: String? = null,
    val errorMessage: String? = null
)

class SrutiDownloadManager(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val database = SrutiDatabase.getDatabase(context)

    private val downloadDir: File by lazy {
        val dir = File(context.getExternalFilesDir(null), "downloads")
        if (!dir.exists()) dir.mkdirs()
        dir
    }

    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val _downloads = MutableStateFlow<Map<String, DownloadRecord>>(emptyMap())
    val downloads: StateFlow<Map<String, DownloadRecord>> = _downloads.asStateFlow()

    init {
        // Load initial records from Room database
        scope.launch {
            database.musicDao().getAllCompletedDownloads().collect { list ->
                val map = list.associate { entity ->
                    entity.trackId to DownloadRecord(
                        trackId = entity.trackId,
                        title = entity.title,
                        artist = entity.artist,
                        progress = 1f,
                        status = DownloadStatus.COMPLETED,
                        localFilePath = entity.localFilePath
                    )
                }
                _downloads.update { current ->
                    // Merge completed with any actively running downloads
                    map + current.filterValues { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED }
                }
            }
        }
    }

    fun isTrackDownloaded(trackId: String): Boolean {
        val record = _downloads.value[trackId]
        if (record?.status == DownloadStatus.COMPLETED && record.localFilePath != null) {
            val file = File(record.localFilePath)
            return file.exists() && file.length() > 0
        }
        return false
    }

    fun downloadTrack(track: Track): Result<Unit> {
        val streamUrl = track.mediaUriString
        if (streamUrl.isNullOrBlank() || (!streamUrl.startsWith("http://") && !streamUrl.startsWith("https://"))) {
            return Result.failure(IllegalStateException("Downloads are not available for this source (No valid stream)."))
        }

        return performDownload(
            trackId = track.id,
            title = track.title,
            artist = track.artist,
            album = track.album,
            durationSeconds = track.durationSeconds,
            artworkUrl = track.artworkUriString,
            url = streamUrl,
            sourceId = track.source
        )
    }

    fun startDownload(onlineTrack: OnlineTrack): Result<Unit> {
        if (!onlineTrack.permitsDownload) {
            return Result.failure(IllegalStateException("Downloads are not permitted for this source/track."))
        }

        val url = onlineTrack.downloadUrl ?: onlineTrack.streamUrl
        if (url.isNullOrBlank()) {
            return Result.failure(IllegalArgumentException("No valid download URL provided."))
        }

        return performDownload(
            trackId = onlineTrack.id,
            title = onlineTrack.title,
            artist = onlineTrack.artist,
            album = onlineTrack.album,
            durationSeconds = onlineTrack.durationSeconds,
            artworkUrl = onlineTrack.artworkUrl,
            url = url,
            sourceId = onlineTrack.sourceId
        )
    }

    private fun performDownload(
        trackId: String,
        title: String,
        artist: String,
        album: String,
        durationSeconds: Int,
        artworkUrl: String?,
        url: String,
        sourceId: String
    ): Result<Unit> {
        if (isTrackDownloaded(trackId)) {
            return Result.success(Unit) // Already downloaded
        }

        if (activeJobs.containsKey(trackId)) {
            return Result.success(Unit) // Already downloading
        }

        _downloads.update { current ->
            current + (trackId to DownloadRecord(
                trackId = trackId,
                title = title,
                artist = artist,
                progress = 0f,
                status = DownloadStatus.QUEUED
            ))
        }

        val job = scope.launch {
            val sanitizedName = "${title}_${artist}"
                .replace(Regex("[^a-zA-Z0-9_]"), "_")
                .take(40)
            val tempFile = File(downloadDir, "${trackId}_${sanitizedName}.tmp")
            val targetFile = File(downloadDir, "${trackId}_${sanitizedName}.mp3")

            try {
                _downloads.update { it + (trackId to it[trackId]!!.copy(status = DownloadStatus.DOWNLOADING)) }

                val request = Request.Builder().url(url).build()
                val response = httpClient.newCall(request).execute()

                if (!response.isSuccessful) {
                    throw IllegalStateException("Download failed with HTTP ${response.code}")
                }

                val body = response.body ?: throw IllegalStateException("Empty response body")
                val contentLength = body.contentLength()
                val inputStream = body.byteStream()
                val outputStream = FileOutputStream(tempFile)

                val buffer = ByteArray(8 * 1024)
                var bytesRead: Int
                var totalBytesRead = 0L

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    if (!isActive) {
                        outputStream.close()
                        inputStream.close()
                        if (tempFile.exists()) tempFile.delete()
                        _downloads.update { it + (trackId to it[trackId]!!.copy(status = DownloadStatus.CANCELLED)) }
                        return@launch
                    }

                    outputStream.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead

                    val progress = if (contentLength > 0) {
                        (totalBytesRead.toFloat() / contentLength.toFloat()).coerceIn(0f, 1f)
                    } else 0.5f

                    _downloads.update { it + (trackId to it[trackId]!!.copy(progress = progress)) }
                }

                outputStream.flush()
                outputStream.close()
                inputStream.close()

                // Move from temp to final
                if (targetFile.exists()) targetFile.delete()
                val moved = tempFile.renameTo(targetFile)
                if (!moved || !targetFile.exists() || targetFile.length() == 0L) {
                    throw IllegalStateException("Failed moving temp download to destination")
                }

                // Persist into Room
                database.musicDao().insertDownload(
                    DownloadEntity(
                        trackId = trackId,
                        title = title,
                        artist = artist,
                        album = album,
                        durationSeconds = durationSeconds,
                        artworkUriString = artworkUrl,
                        localFilePath = targetFile.absolutePath,
                        downloadedBytes = targetFile.length(),
                        totalBytes = if (contentLength > 0) contentLength else targetFile.length(),
                        status = "COMPLETED",
                        sourceId = sourceId,
                        downloadedAt = System.currentTimeMillis()
                    )
                )

                _downloads.update {
                    it + (trackId to it[trackId]!!.copy(
                        progress = 1f,
                        status = DownloadStatus.COMPLETED,
                        localFilePath = targetFile.absolutePath
                    ))
                }
                Log.d(TAG, "Completed and verified download for $title at ${targetFile.absolutePath}")
            } catch (e: Exception) {
                if (tempFile.exists()) tempFile.delete()
                _downloads.update {
                    it + (trackId to (it[trackId]?.copy(
                        status = DownloadStatus.FAILED,
                        errorMessage = e.message
                    ) ?: DownloadRecord(trackId, title, artist, 0f, DownloadStatus.FAILED, errorMessage = e.message)))
                }
                Log.e(TAG, "Download error for $title: ${e.message}")
            } finally {
                activeJobs.remove(trackId)
            }
        }

        activeJobs[trackId] = job
        return Result.success(Unit)
    }

    fun cancelDownload(trackId: String) {
        activeJobs[trackId]?.cancel()
        activeJobs.remove(trackId)
        _downloads.update {
            it[trackId]?.let { rec -> it + (trackId to rec.copy(status = DownloadStatus.CANCELLED)) } ?: it
        }
    }

    fun deleteDownloadedTrack(trackId: String): Boolean {
        val record = _downloads.value[trackId]
        val filePath = record?.localFilePath
        if (filePath != null) {
            val file = File(filePath)
            if (file.exists()) file.delete()
        }
        scope.launch {
            database.musicDao().deleteDownload(trackId)
        }
        _downloads.update { it - trackId }
        return true
    }

    fun getDownloadedTracks(): List<Track> {
        val files = downloadDir.listFiles() ?: return emptyList()
        return files.filter { it.isFile && (it.name.endsWith(".mp3") || it.name.endsWith(".wav") || it.name.endsWith(".m4a")) }
            .map { file ->
                val baseName = file.nameWithoutExtension
                val parts = baseName.split("_")
                val title = if (parts.size >= 2) parts[1].replace("_", " ") else file.nameWithoutExtension
                val artist = if (parts.size >= 3) parts[2].replace("_", " ") else "Sruti Offline"

                Track(
                    id = "download_${file.name.hashCode()}",
                    title = title,
                    artist = artist,
                    durationText = "--:--",
                    durationSeconds = 180,
                    artworkResId = R.drawable.sruti_artwork_fallback,
                    mediaUriString = Uri.fromFile(file).toString(),
                    isLocal = true,
                    source = "download"
                )
            }
    }

    companion object {
        private const val TAG = "SrutiDownloadManager"

        @Volatile
        private var instance: SrutiDownloadManager? = null

        fun getInstance(context: Context): SrutiDownloadManager {
            return instance ?: synchronized(this) {
                instance ?: SrutiDownloadManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
