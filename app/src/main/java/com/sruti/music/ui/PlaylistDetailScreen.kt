package com.sruti.music.ui

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.sruti.music.R
import com.sruti.music.components.GlassCircleButton
import com.sruti.music.components.PlaylistArtwork
import com.sruti.music.data.Track
import com.sruti.music.data.db.PlaylistEntity
import com.sruti.music.data.db.PlaylistSongEntity
import com.sruti.music.data.db.SrutiDatabase
import com.sruti.music.online.MatchConfidence
import com.sruti.music.online.MusicSourceRegistry
import com.sruti.music.online.SrutiDownloadManager
import com.sruti.music.playback.PlaybackStateManager
import com.sruti.music.theme.GlassWhite
import com.sruti.music.theme.LocalDynamicTheme
import com.sruti.music.theme.NeonPurple
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

enum class PlaylistSortOption(val displayName: String, val shortName: String) {
    CUSTOM("Custom order", "Custom"),
    TITLE_AZ("Title (A → Z)", "Title A-Z"),
    TITLE_ZA("Title (Z → A)", "Title Z-A"),
    ARTIST_AZ("Artist (A → Z)", "Artist A-Z"),
    ARTIST_ZA("Artist (Z → A)", "Artist Z-A"),
    ALBUM_AZ("Album (A → Z)", "Album"),
    RECENTLY_ADDED("Recently added", "Recently Added"),
    OLDEST_ADDED("Oldest added", "Oldest Added"),
    DURATION_ASC("Duration (Shortest first)", "Shortest"),
    DURATION_DESC("Duration (Longest first)", "Longest")
}

enum class PlaylistViewMode {
    LIST,
    COMPACT
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistDetailScreen(
    playlist: PlaylistEntity,
    onBackClick: () -> Unit,
    onTrackSelected: (Track, List<Track>) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val database = remember { SrutiDatabase.getDatabase(context) }
    val downloadManager = remember { SrutiDownloadManager.getInstance(context) }
    val playbackState by PlaybackStateManager.state.collectAsState()
    val dynamicTheme = LocalDynamicTheme.current
    val prefs = remember { context.getSharedPreferences("sruti_playlist_prefs", Context.MODE_PRIVATE) }

    var songs by remember { mutableStateOf<List<PlaylistSongEntity>>(emptyList()) }
    var sortOption by remember(playlist.id) {
        val savedName = prefs.getString("sort_${playlist.id}", PlaylistSortOption.CUSTOM.name)
        val initial = when (savedName) {
            "TITLE" -> PlaylistSortOption.TITLE_AZ
            "ARTIST" -> PlaylistSortOption.ARTIST_AZ
            "ALBUM" -> PlaylistSortOption.ALBUM_AZ
            "DURATION" -> PlaylistSortOption.DURATION_ASC
            else -> runCatching { PlaylistSortOption.valueOf(savedName ?: "") }.getOrDefault(PlaylistSortOption.CUSTOM)
        }
        mutableStateOf(initial)
    }
    var viewMode by remember { mutableStateOf(PlaylistViewMode.LIST) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showOptionsMenu by remember { mutableStateOf(false) }

    // Multi-select mode state
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedTrackIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var trackForAddToPlaylist by remember { mutableStateOf<Track?>(null) }
    var tracksForAddToPlaylist by remember { mutableStateOf<List<Track>?>(null) }

    var playlistCurrentName by remember(playlist.id) { mutableStateOf(playlist.name) }
    var playlistCustomArtwork by remember(playlist.id) { mutableStateOf<String?>(playlist.artworkUri ?: playlist.artworkUrl) }

    var showRenameDialog by remember { mutableStateOf(false) }
    var renameInputText by remember { mutableStateOf("") }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showImportSongsDialog by remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val uriStr = uri.toString()
            playlistCustomArtwork = uriStr
            scope.launch {
                database.musicDao().updatePlaylistArtworkUri(playlist.id, uriStr)
            }
        }
    }

    var isMatchingInProgress by remember { mutableStateOf(false) }
    var matchingProgressText by remember { mutableStateOf<String?>(null) }

    // Observe playlist songs from Room
    LaunchedEffect(playlist.id) {
        if (playlist.id == -999L) {
            database.musicDao().getAllCompletedDownloads().collect { list ->
                songs = list.mapIndexed { idx, d ->
                    PlaylistSongEntity(
                        id = idx.toLong() + 1,
                        playlistId = -999L,
                        trackId = d.trackId,
                        title = d.title,
                        artist = d.artist,
                        album = d.album,
                        durationSeconds = d.durationSeconds,
                        mediaUriString = d.localFilePath,
                        artworkUriString = d.artworkUriString,
                        sourceId = "downloaded",
                        sortOrder = idx
                    )
                }
                if (playlistCustomArtwork.isNullOrBlank()) {
                    val firstArt = list.firstOrNull { !it.artworkUriString.isNullOrBlank() }?.artworkUriString
                    if (!firstArt.isNullOrBlank()) {
                        playlistCustomArtwork = firstArt
                    }
                }
            }
        } else {
            database.musicDao().getSongsForPlaylist(playlist.id).collect { list ->
                songs = list
                if (playlistCustomArtwork.isNullOrBlank()) {
                    val firstArt = list.firstOrNull { !it.artworkUriString.isNullOrBlank() }?.artworkUriString
                    if (!firstArt.isNullOrBlank()) {
                        playlistCustomArtwork = firstArt
                        database.musicDao().updatePlaylistArtworkUrl(playlist.id, firstArt)
                    }
                }
            }
        }
    }

    // Convert entities to playback tracks
    fun toTrack(entity: PlaylistSongEntity): Track {
        val minutes = entity.durationSeconds / 60
        val seconds = entity.durationSeconds % 60
        return Track(
            id = entity.trackId,
            title = entity.title,
            artist = entity.artist,
            album = entity.album,
            durationText = if (entity.durationSeconds > 0) String.format("%d:%02d", minutes, seconds) else "--:--",
            durationSeconds = entity.durationSeconds,
            artworkResId = entity.artworkResId,
            artworkUriString = entity.artworkUriString,
            mediaUriString = entity.mediaUriString,
            isLocal = entity.sourceId == "local",
            source = entity.sourceId
        )
    }

    // Sorted song list based on user selection
    val sortedSongs = remember(songs, sortOption) {
        when (sortOption) {
            PlaylistSortOption.CUSTOM -> songs.sortedBy { it.sortOrder }
            PlaylistSortOption.TITLE_AZ -> songs.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
            PlaylistSortOption.TITLE_ZA -> songs.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.title })
            PlaylistSortOption.ARTIST_AZ -> songs.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.artist })
            PlaylistSortOption.ARTIST_ZA -> songs.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.artist })
            PlaylistSortOption.ALBUM_AZ -> songs.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.album })
            PlaylistSortOption.RECENTLY_ADDED -> songs.sortedByDescending { it.addedAt }
            PlaylistSortOption.OLDEST_ADDED -> songs.sortedBy { it.addedAt }
            PlaylistSortOption.DURATION_ASC -> songs.sortedBy { it.durationSeconds }
            PlaylistSortOption.DURATION_DESC -> songs.sortedByDescending { it.durationSeconds }
        }
    }

    val playableSongs = remember(sortedSongs) {
        sortedSongs.filter { !it.mediaUriString.isNullOrBlank() }
    }
    val unplayableCount = sortedSongs.size - playableSongs.size

    val totalDurationSec = remember(sortedSongs) { sortedSongs.sumOf { it.durationSeconds } }
    val durationSummary = remember(totalDurationSec) {
        val hours = totalDurationSec / 3600
        val mins = (totalDurationSec % 3600) / 60
        if (hours > 0) "$hours hr $mins min" else "$mins min"
    }

    // Auto-matching engine that loops until all possible tracks are matched
    fun startAutoMatchingLoop() {
        if (isMatchingInProgress) return
        isMatchingInProgress = true
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            var unmatchedList = songs.filter { it.mediaUriString.isNullOrBlank() }
            var resolvedTotal = 0
            var pass = 1

            while (unmatchedList.isNotEmpty() && pass <= 3) {
                var passResolved = 0
                unmatchedList.forEachIndexed { idx, song ->
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        matchingProgressText = "Matching ${idx + 1}/${unmatchedList.size} (Pass $pass): ${song.title}"
                    }

                    val (matchedTrack, confidence) = MusicSourceRegistry.matchTrackOnline(
                        title = song.title,
                        artist = song.artist,
                        durationSeconds = song.durationSeconds
                    )

                    if (matchedTrack != null && confidence != MatchConfidence.UNMATCHED) {
                        database.musicDao().updateTrackResolution(
                            playlistId = playlist.id,
                            trackId = song.trackId,
                            mediaUri = matchedTrack.streamUrl,
                            artworkUri = matchedTrack.artworkUrl ?: song.artworkUriString,
                            sourceId = matchedTrack.sourceId
                        )
                        passResolved++
                        resolvedTotal++
                    }
                    kotlinx.coroutines.delay(100)
                }

                // Check remaining
                unmatchedList = database.musicDao().getSongsForPlaylist(playlist.id).firstOrNull()
                    ?.filter { it.mediaUriString.isNullOrBlank() } ?: emptyList()

                pass++
                if (passResolved == 0) break
            }

            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                matchingProgressText = if (unmatchedList.isEmpty()) "All tracks matched & playable! 🎉" else "Matched $resolvedTotal tracks! (${unmatchedList.size} remaining)"
                isMatchingInProgress = false
            }
        }
    }

    // Single track on-demand match
    fun matchSingleTrack(song: PlaylistSongEntity) {
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                Toast.makeText(context, "Searching stream for '${song.title}'...", Toast.LENGTH_SHORT).show()
            }
            val (matched, conf) = MusicSourceRegistry.matchTrackOnline(song.title, song.artist, song.durationSeconds)
            if (matched != null && conf != MatchConfidence.UNMATCHED) {
                database.musicDao().updateTrackResolution(
                    playlistId = playlist.id,
                    trackId = song.trackId,
                    mediaUri = matched.streamUrl,
                    artworkUri = matched.artworkUrl ?: song.artworkUriString,
                    sourceId = matched.sourceId
                )
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    Toast.makeText(context, "Matched: ${matched.title}! Ready to play.", Toast.LENGTH_SHORT).show()
                }
            } else {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    Toast.makeText(context, "No stream found online for '${song.title}'", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(14.dp))

            // Top Navigation Bar (Clean & non-redundant)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (isSelectionMode) {
                    GlassCircleButton(
                        icon = Icons.Rounded.Close,
                        contentDescription = "Cancel Selection",
                        iconSize = 22.dp,
                        onClick = {
                            isSelectionMode = false
                            selectedTrackIds = emptySet()
                        }
                    )

                    Text(
                        text = "${selectedTrackIds.size} of ${sortedSongs.size} selected",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )

                    TextButton(
                        onClick = {
                            selectedTrackIds = if (selectedTrackIds.size == sortedSongs.size) emptySet()
                            else sortedSongs.map { it.trackId }.toSet()
                        }
                    ) {
                        Text(
                            text = if (selectedTrackIds.size == sortedSongs.size) "Deselect" else "Select All",
                            color = dynamicTheme.primary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    GlassCircleButton(
                        icon = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                        contentDescription = "Back",
                        iconSize = 28.dp,
                        onClick = onBackClick
                    )

                    Text(
                        text = "Playlist Details",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    // Single Authority Options Menu (NO duplicate sort button!)
                    Box {
                        GlassCircleButton(
                            icon = Icons.Rounded.MoreVert,
                            contentDescription = "More Options",
                            onClick = { showOptionsMenu = true }
                        )

                        DropdownMenu(
                            expanded = showOptionsMenu,
                            onDismissRequest = { showOptionsMenu = false },
                            modifier = Modifier
                                .background(Color(0xFF0F1114))
                                .border(1.dp, dynamicTheme.primary.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                        ) {
                            if (playlist.id != -999L) {
                                DropdownMenuItem(
                                    text = { Text("Add / Import Songs", color = Color.White, fontSize = 13.5.sp) },
                                    leadingIcon = { Icon(Icons.Rounded.AddCircleOutline, contentDescription = null, tint = dynamicTheme.primary, modifier = Modifier.size(18.dp)) },
                                    onClick = {
                                        showOptionsMenu = false
                                        showImportSongsDialog = true
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Add All to Queue", color = Color.White, fontSize = 13.5.sp) },
                                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = null, tint = dynamicTheme.primary, modifier = Modifier.size(18.dp)) },
                                onClick = {
                                    showOptionsMenu = false
                                    val queueTracks = playableSongs.map { toTrack(it) }
                                    PlaybackStateManager.addTracksToQueue(queueTracks)
                                    Toast.makeText(context, "Added ${queueTracks.size} tracks to queue", Toast.LENGTH_SHORT).show()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (isSelectionMode) "Exit Selection" else "Select Multiple Songs", color = Color.White, fontSize = 13.5.sp) },
                                leadingIcon = { Icon(Icons.Rounded.Checklist, contentDescription = null, tint = dynamicTheme.primary, modifier = Modifier.size(18.dp)) },
                                onClick = {
                                    showOptionsMenu = false
                                    isSelectionMode = !isSelectionMode
                                    if (!isSelectionMode) selectedTrackIds = emptySet()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (viewMode == PlaylistViewMode.COMPACT) "Switch to List View" else "Switch to Compact View", color = Color.White, fontSize = 13.5.sp) },
                                leadingIcon = { Icon(if (viewMode == PlaylistViewMode.COMPACT) Icons.Rounded.ViewHeadline else Icons.Rounded.ViewAgenda, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp)) },
                                onClick = {
                                    showOptionsMenu = false
                                    viewMode = if (viewMode == PlaylistViewMode.COMPACT) PlaylistViewMode.LIST else PlaylistViewMode.COMPACT
                                }
                            )

                            if (playlist.id != -999L) {
                                HorizontalDivider(color = Color(0x25FFFFFF), modifier = Modifier.padding(vertical = 4.dp))
                                DropdownMenuItem(
                                    text = { Text("Change Cover Artwork", color = Color.White, fontSize = 13.5.sp) },
                                    leadingIcon = { Icon(Icons.Rounded.Image, contentDescription = null, tint = dynamicTheme.primary, modifier = Modifier.size(18.dp)) },
                                    onClick = {
                                        showOptionsMenu = false
                                        imagePickerLauncher.launch("image/*")
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Rename Playlist", color = Color.White, fontSize = 13.5.sp) },
                                    leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp)) },
                                    onClick = {
                                        showOptionsMenu = false
                                        renameInputText = playlistCurrentName
                                        showRenameDialog = true
                                    }
                                )
                                HorizontalDivider(color = Color(0x25FFFFFF), modifier = Modifier.padding(vertical = 4.dp))
                                DropdownMenuItem(
                                    text = { Text("Delete Playlist", color = Color(0xFFFF5252), fontSize = 13.5.sp) },
                                    leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(18.dp)) },
                                    onClick = {
                                        showOptionsMenu = false
                                        showDeleteConfirmDialog = true
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Playlist Header Card with Authoritative Artwork
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, Color(0x30FFFFFF), RoundedCornerShape(16.dp))
                ) {
                    PlaylistArtwork(
                        artworkModel = playlistCustomArtwork,
                        playlistTitle = playlistCurrentName,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = playlistCurrentName,
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${sortedSongs.size} tracks • $durationSummary",
                        color = Color(0xFFA1A1AA),
                        fontSize = 12.5.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Availability Badges
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(dynamicTheme.primary.copy(alpha = 0.20f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "${playableSongs.size} Playable",
                                color = dynamicTheme.primary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (unplayableCount > 0) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0x30FF5252))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "$unplayableCount Unmatched",
                                    color = Color(0xFFFF8A80),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Continuous Auto-Match Banner (if unmatched tracks exist)
            if (unplayableCount > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(dynamicTheme.primary.copy(alpha = 0.12f))
                        .border(1.dp, dynamicTheme.primary.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                        .clickable { startAutoMatchingLoop() }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = dynamicTheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (isMatchingInProgress) "Auto-Matching Audio Streams..." else "$unplayableCount tracks need audio",
                                color = Color.White,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isMatchingInProgress) (matchingProgressText ?: "Searching online catalogues...") else "Tap to auto-match & enable all tracks",
                                color = Color(0xFFA1A1AA),
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                        }
                    }

                    if (isMatchingInProgress) {
                        CircularProgressIndicator(
                            color = dynamicTheme.primary,
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(dynamicTheme.primary)
                                .padding(horizontal = 12.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "Match All",
                                color = if (dynamicTheme.primary.luminance() > 0.55f) Color.Black else Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons Row: Authentic Spotify Layout (No text squishing!)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Actions: Add Songs, Download All
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Add Songs Button
                    IconButton(
                        onClick = { showImportSongsDialog = true },
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF14171A))
                            .border(1.dp, Color(0x25FFFFFF), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "Add Songs",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Download All Button
                    IconButton(
                        onClick = {
                            if (playableSongs.isNotEmpty()) {
                                var queued = 0
                                playableSongs.forEach { song ->
                                    downloadManager.downloadTrack(toTrack(song))
                                    queued++
                                }
                                Toast.makeText(context, "Queued $queued songs for download", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "No playable songs to download yet", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF14171A))
                            .border(1.dp, Color(0x25FFFFFF), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = "Download All",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Right Actions: Shuffle & Big Circular Play Button (Spotify Hallmark!)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            if (playableSongs.isNotEmpty()) {
                                val shuffled = playableSongs.shuffled().map { toTrack(it) }
                                onTrackSelected(shuffled.first(), shuffled)
                            }
                        },
                        enabled = playableSongs.isNotEmpty(),
                        modifier = Modifier.size(42.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (playableSongs.isNotEmpty()) dynamicTheme.primary else Color(0x40FFFFFF),
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(if (playableSongs.isNotEmpty()) dynamicTheme.primary else Color(0x35FFFFFF))
                            .clickable(enabled = playableSongs.isNotEmpty()) {
                                if (playableSongs.isNotEmpty()) {
                                    val first = toTrack(playableSongs.first())
                                    val queue = playableSongs.map { toTrack(it) }
                                    onTrackSelected(first, queue)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        val playIconColor = if (dynamicTheme.primary.luminance() > 0.55f) Color.Black else Color.White
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = "Play",
                            tint = if (playableSongs.isNotEmpty()) playIconColor else Color(0x60FFFFFF),
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Tracks List Section Header (Zero collisions!)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TRACKS (${sortedSongs.size})",
                    color = Color(0xFFA1A1AA),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Single Authoritative Sort Chip
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF14171A))
                            .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(12.dp))
                            .clickable { showSortMenu = true }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Sort,
                                contentDescription = "Sort",
                                tint = dynamicTheme.primary,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = sortOption.shortName,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Icon(
                                imageVector = Icons.Rounded.ArrowDropDown,
                                contentDescription = null,
                                tint = Color(0xFFA1A1AA),
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false },
                            modifier = Modifier
                                .background(Color(0xFF0F1114))
                                .border(1.dp, dynamicTheme.primary.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                        ) {
                            Text(
                                text = "Sort by",
                                color = Color(0xFFA1A1AA),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                            )

                            PlaylistSortOption.values().forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = option.displayName,
                                                color = if (sortOption == option) dynamicTheme.primary else Color.White,
                                                fontSize = 13.5.sp,
                                                fontWeight = if (sortOption == option) FontWeight.Bold else FontWeight.Normal
                                            )
                                            if (sortOption == option) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Check,
                                                    contentDescription = null,
                                                    tint = dynamicTheme.primary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        sortOption = option
                                        prefs.edit().putString("sort_${playlist.id}", option.name).apply()
                                        showSortMenu = false
                                    }
                                )
                            }
                        }
                    }

                    // Select Toggle Chip
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelectionMode) dynamicTheme.primary.copy(alpha = 0.2f) else Color(0xFF14171A))
                            .border(1.dp, if (isSelectionMode) dynamicTheme.primary else Color(0x22FFFFFF), RoundedCornerShape(12.dp))
                            .clickable {
                                isSelectionMode = !isSelectionMode
                                if (!isSelectionMode) selectedTrackIds = emptySet()
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (isSelectionMode) "Done" else "Select",
                            color = if (isSelectionMode) dynamicTheme.primary else Color.White,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Track List Items or Empty State (Generous 200dp bottom clearance!)
            if (sortedSongs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(70.dp)
                                .clip(CircleShape)
                                .background(Color(0x22A855F7)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.QueueMusic,
                                contentDescription = null,
                                tint = NeonPurple,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "This playlist is empty",
                            color = Color.White,
                            fontSize = 16.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Import songs from device, liked music, or Spotify",
                            color = Color(0xFFA1A1AA),
                            fontSize = 12.5.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { showImportSongsDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonPurple),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(40.dp)
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add / Import Songs", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    contentPadding = PaddingValues(bottom = 200.dp)
                ) {
                    itemsIndexed(sortedSongs, key = { _, s -> s.id }) { index, song ->
                        val isPlayable = !song.mediaUriString.isNullOrBlank()
                        val isCurrent = playbackState.currentTrack.id == song.trackId
                        val isSelected = selectedTrackIds.contains(song.trackId)

                        if (viewMode == PlaylistViewMode.LIST) {
                            PlaylistTrackRowItem(
                                index = index + 1,
                                song = song,
                                isPlayable = isPlayable,
                                isCurrent = isCurrent,
                                isSelectionMode = isSelectionMode,
                                isSelected = isSelected,
                                onClick = {
                                    if (isSelectionMode) {
                                        selectedTrackIds = if (isSelected) selectedTrackIds - song.trackId else selectedTrackIds + song.trackId
                                    } else if (isPlayable) {
                                        val track = toTrack(song)
                                        val queue = playableSongs.map { toTrack(it) }
                                        onTrackSelected(track, queue)
                                    } else {
                                        matchSingleTrack(song)
                                    }
                                },
                                onLongClick = {
                                    isSelectionMode = true
                                    selectedTrackIds = selectedTrackIds + song.trackId
                                },
                                onMatchClick = {
                                    matchSingleTrack(song)
                                },
                                onPlayNext = {
                                    PlaybackStateManager.playNextInQueue(toTrack(song))
                                    Toast.makeText(context, "Will play next", Toast.LENGTH_SHORT).show()
                                },
                                onAddToQueue = {
                                    PlaybackStateManager.addToQueue(toTrack(song))
                                    Toast.makeText(context, "Added to queue", Toast.LENGTH_SHORT).show()
                                },
                                onAddToPlaylist = {
                                    trackForAddToPlaylist = toTrack(song)
                                },
                                onRemoveFromPlaylist = {
                                    scope.launch {
                                        database.musicDao().removeSongFromPlaylist(playlist.id, song.trackId)
                                        Toast.makeText(context, "Removed from playlist", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onDownload = {
                                    if (isPlayable) {
                                        downloadManager.downloadTrack(toTrack(song))
                                        Toast.makeText(context, "Downloading track...", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onShareAudio = {
                                    val result = downloadManager.shareDownloadedTrack(context, song.trackId)
                                    if (result.isFailure) {
                                        Toast.makeText(context, "Download the track first to share its audio file!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        } else {
                            PlaylistCompactRowItem(
                                index = index + 1,
                                song = song,
                                isPlayable = isPlayable,
                                isCurrent = isCurrent,
                                isSelectionMode = isSelectionMode,
                                isSelected = isSelected,
                                onClick = {
                                    if (isSelectionMode) {
                                        selectedTrackIds = if (isSelected) selectedTrackIds - song.trackId else selectedTrackIds + song.trackId
                                    } else if (isPlayable) {
                                        val track = toTrack(song)
                                        val queue = playableSongs.map { toTrack(it) }
                                        onTrackSelected(track, queue)
                                    } else {
                                        matchSingleTrack(song)
                                    }
                                },
                                onLongClick = {
                                    isSelectionMode = true
                                    selectedTrackIds = selectedTrackIds + song.trackId
                                },
                                onMatchClick = {
                                    matchSingleTrack(song)
                                }
                            )
                        }
                    }
                }
            }
        }

        // Floating Multi-Selection Action Bar (Sits elegantly above mini player!)
        if (isSelectionMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 78.dp, start = 16.dp, end = 16.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xF514171E))
                    .border(1.dp, dynamicTheme.primary.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
                    .padding(vertical = 8.dp, horizontal = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SelectionBottomActionItem(
                        icon = Icons.Rounded.Favorite,
                        label = "Like",
                        tint = Color(0xFFFF4081),
                        enabled = selectedTrackIds.isNotEmpty(),
                        onClick = {
                            val selectedSongs = sortedSongs.filter { selectedTrackIds.contains(it.trackId) }
                            scope.launch {
                                val entities = selectedSongs.map { song ->
                                    com.sruti.music.data.db.FavoriteEntity(
                                        trackId = song.trackId,
                                        title = song.title,
                                        artist = song.artist,
                                        album = song.album,
                                        durationSeconds = song.durationSeconds,
                                        mediaUriString = song.mediaUriString,
                                        artworkUriString = song.artworkUriString,
                                        artworkResId = song.artworkResId,
                                        isLocal = song.sourceId == "local",
                                        addedAt = System.currentTimeMillis()
                                    )
                                }
                                database.musicDao().insertFavorites(entities)
                                Toast.makeText(context, "Added ${entities.size} songs to Liked Songs ❤️", Toast.LENGTH_SHORT).show()
                                isSelectionMode = false
                                selectedTrackIds = emptySet()
                            }
                        }
                    )

                    SelectionBottomActionItem(
                        icon = Icons.Rounded.Download,
                        label = "Download",
                        tint = dynamicTheme.primary,
                        enabled = selectedTrackIds.isNotEmpty(),
                        onClick = {
                            val selectedSongs = sortedSongs.filter { selectedTrackIds.contains(it.trackId) }
                            var queued = 0
                            selectedSongs.forEach { song ->
                                if (!song.mediaUriString.isNullOrBlank()) {
                                    downloadManager.downloadTrack(toTrack(song))
                                    queued++
                                }
                            }
                            Toast.makeText(context, "Queued $queued tracks for download", Toast.LENGTH_SHORT).show()
                            isSelectionMode = false
                            selectedTrackIds = emptySet()
                        }
                    )

                    SelectionBottomActionItem(
                        icon = Icons.AutoMirrored.Rounded.QueueMusic,
                        label = "Queue",
                        tint = Color.White,
                        enabled = selectedTrackIds.isNotEmpty(),
                        onClick = {
                            val selectedTracks = sortedSongs
                                .filter { selectedTrackIds.contains(it.trackId) && !it.mediaUriString.isNullOrBlank() }
                                .map { toTrack(it) }
                            PlaybackStateManager.addTracksToQueue(selectedTracks)
                            isSelectionMode = false
                            selectedTrackIds = emptySet()
                            Toast.makeText(context, "Added ${selectedTracks.size} tracks to queue", Toast.LENGTH_SHORT).show()
                        }
                    )

                    SelectionBottomActionItem(
                        icon = Icons.Rounded.PlaylistAdd,
                        label = "Playlist",
                        tint = Color.White,
                        enabled = selectedTrackIds.isNotEmpty(),
                        onClick = {
                            val selectedTracks = sortedSongs
                                .filter { selectedTrackIds.contains(it.trackId) }
                                .map { toTrack(it) }
                            tracksForAddToPlaylist = selectedTracks
                            isSelectionMode = false
                            selectedTrackIds = emptySet()
                        }
                    )

                    if (playlist.id != -999L) {
                        SelectionBottomActionItem(
                            icon = Icons.Rounded.DeleteOutline,
                            label = "Remove",
                            tint = Color(0xFFFF5252),
                            enabled = selectedTrackIds.isNotEmpty(),
                            onClick = {
                                val toRemove = selectedTrackIds.toSet()
                                scope.launch {
                                    toRemove.forEach { trackId ->
                                        database.musicDao().removeSongFromPlaylist(playlist.id, trackId)
                                    }
                                    Toast.makeText(context, "Removed ${toRemove.size} songs", Toast.LENGTH_SHORT).show()
                                    isSelectionMode = false
                                    selectedTrackIds = emptySet()
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (trackForAddToPlaylist != null) {
        AddToPlaylistDialog(
            track = trackForAddToPlaylist!!,
            onDismiss = { trackForAddToPlaylist = null }
        )
    }

    if (tracksForAddToPlaylist != null) {
        AddToPlaylistDialog(
            tracks = tracksForAddToPlaylist!!,
            onDismiss = { tracksForAddToPlaylist = null }
        )
    }

    // Rename Dialog
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Playlist", color = Color.White) },
            text = {
                OutlinedTextField(
                    value = renameInputText,
                    onValueChange = { renameInputText = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = NeonPurple,
                        unfocusedBorderColor = Color(0x35FFFFFF)
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val newName = renameInputText.trim()
                        if (newName.isNotBlank()) {
                            playlistCurrentName = newName
                            scope.launch {
                                database.musicDao().updatePlaylistName(playlist.id, newName)
                            }
                        }
                        showRenameDialog = false
                    }
                ) {
                    Text("Save", color = NeonPurple)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel", color = Color.White)
                }
            },
            containerColor = Color(0xFF1E1433),
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete Playlist?", color = Color.White) },
            text = { Text("Are you sure you want to delete '$playlistCurrentName'? This cannot be undone.", color = GlassWhite.copy(0.75f)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            database.musicDao().deletePlaylist(playlist.id)
                            database.musicDao().deletePlaylistSongs(playlist.id)
                            showDeleteConfirmDialog = false
                            onBackClick()
                        }
                    }
                ) {
                    Text("Delete", color = Color(0xFFFF5252))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel", color = Color.White)
                }
            },
            containerColor = Color(0xFF1E1433),
            shape = RoundedCornerShape(20.dp)
        )
    }

    if (showImportSongsDialog) {
        ImportSongsToPlaylistDialog(
            playlistId = playlist.id,
            onDismiss = { showImportSongsDialog = false }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaylistTrackRowItem(
    index: Int,
    song: PlaylistSongEntity,
    isPlayable: Boolean,
    isCurrent: Boolean,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onMatchClick: () -> Unit = {},
    onPlayNext: () -> Unit = {},
    onAddToQueue: () -> Unit = {},
    onAddToPlaylist: () -> Unit = {},
    onRemoveFromPlaylist: () -> Unit = {},
    onDownload: () -> Unit = {},
    onShareAudio: () -> Unit = {}
) {
    val dynamicTheme = LocalDynamicTheme.current
    var showTrackMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isSelected) dynamicTheme.primary.copy(alpha = 0.20f)
                else if (isCurrent) dynamicTheme.primary.copy(alpha = 0.12f)
                else Color.Transparent
            )
            .then(
                if (isSelected) Modifier.border(1.dp, dynamicTheme.primary.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                else Modifier
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSelectionMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onClick() },
                colors = CheckboxDefaults.colors(
                    checkedColor = dynamicTheme.primary,
                    checkmarkColor = if (dynamicTheme.primary.luminance() > 0.55f) Color.Black else Color.White
                ),
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        // Track Artwork (clean rounded corners)
        val painter = rememberAsyncImagePainter(
            model = song.artworkUriString,
            error = painterResource(id = R.drawable.sruti_artwork_fallback),
            placeholder = painterResource(id = R.drawable.sruti_artwork_fallback)
        )
        Image(
            painter = painter,
            contentDescription = song.title,
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Title & Artist Info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                color = if (isCurrent) dynamicTheme.primary else if (isPlayable) Color.White else Color(0xFFA1A1AA),
                fontSize = 14.5.sp,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${song.artist} • ${if (song.album.isNotBlank()) song.album else "Single"}",
                color = Color(0xFF8E8E93),
                fontSize = 12.sp,
                maxLines = 1
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        if (isPlayable) {
            // Track Overflow Menu
            Box {
                IconButton(
                    onClick = { showTrackMenu = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "Track options",
                        tint = Color(0xFFA1A1AA),
                        modifier = Modifier.size(20.dp)
                    )
                }

                DropdownMenu(
                    expanded = showTrackMenu,
                    onDismissRequest = { showTrackMenu = false },
                    modifier = Modifier
                        .background(Color(0xFF0F1114))
                        .border(1.dp, dynamicTheme.primary.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                ) {
                    DropdownMenuItem(
                        text = { Text("Play Next", color = Color.White, fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Rounded.QueuePlayNext, contentDescription = null, tint = dynamicTheme.primary, modifier = Modifier.size(16.dp)) },
                        onClick = {
                            showTrackMenu = false
                            onPlayNext()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Add to Queue", color = Color.White, fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp)) },
                        onClick = {
                            showTrackMenu = false
                            onAddToQueue()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Add to Playlist", color = Color.White, fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Rounded.PlaylistAdd, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp)) },
                        onClick = {
                            showTrackMenu = false
                            onAddToPlaylist()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Download Track", color = Color.White, fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Rounded.Download, contentDescription = null, tint = dynamicTheme.primary, modifier = Modifier.size(16.dp)) },
                        onClick = {
                            showTrackMenu = false
                            onDownload()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Send / Share Audio File", color = Color.White, fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null, tint = dynamicTheme.primary, modifier = Modifier.size(16.dp)) },
                        onClick = {
                            showTrackMenu = false
                            onShareAudio()
                        }
                    )
                    HorizontalDivider(color = Color(0x25FFFFFF), modifier = Modifier.padding(vertical = 4.dp))
                    DropdownMenuItem(
                        text = { Text("Remove from Playlist", color = Color(0xFFFF5252), fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(16.dp)) },
                        onClick = {
                            showTrackMenu = false
                            onRemoveFromPlaylist()
                        }
                    )
                }
            }
        } else {
            // Interactive Auto-Match chip on unplayable item
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(dynamicTheme.primary.copy(alpha = 0.15f))
                    .border(0.8.dp, dynamicTheme.primary.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .clickable { onMatchClick() }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = "Match",
                    tint = dynamicTheme.primary,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Match",
                    color = dynamicTheme.primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaylistCompactRowItem(
    index: Int,
    song: PlaylistSongEntity,
    isPlayable: Boolean,
    isCurrent: Boolean,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onMatchClick: () -> Unit = {}
) {
    val dynamicTheme = LocalDynamicTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isSelected) dynamicTheme.primary.copy(alpha = 0.20f)
                else if (isCurrent) dynamicTheme.primary.copy(alpha = 0.12f)
                else Color.Transparent
            )
            .then(
                if (isSelected) Modifier.border(1.dp, dynamicTheme.primary.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                else Modifier
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSelectionMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onClick() },
                colors = CheckboxDefaults.colors(
                    checkedColor = dynamicTheme.primary,
                    checkmarkColor = if (dynamicTheme.primary.luminance() > 0.55f) Color.Black else Color.White
                ),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
        } else {
            Text(
                text = "$index",
                color = if (isCurrent) dynamicTheme.primary else Color(0xFF71717A),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(24.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                color = if (isCurrent) dynamicTheme.primary else if (isPlayable) Color.White else Color(0xFFA1A1AA),
                fontSize = 13.5.sp,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1
            )
            Text(
                text = song.artist,
                color = Color(0xFF8E8E93),
                fontSize = 11.5.sp,
                maxLines = 1
            )
        }

        if (isPlayable) {
            val mins = song.durationSeconds / 60
            val secs = song.durationSeconds % 60
            Text(
                text = if (song.durationSeconds > 0) String.format("%d:%02d", mins, secs) else "--:--",
                color = Color(0xFF71717A),
                fontSize = 11.5.sp
            )
        } else {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(dynamicTheme.primary.copy(alpha = 0.15f))
                    .clickable { onMatchClick() }
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Match",
                    color = dynamicTheme.primary,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun RowScope.SelectionBottomActionItem(
    icon: ImageVector,
    label: String,
    tint: Color,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (enabled) tint else Color(0x35FFFFFF),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            color = if (enabled) Color.White else Color(0x35FFFFFF),
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            softWrap = false
        )
    }
}
