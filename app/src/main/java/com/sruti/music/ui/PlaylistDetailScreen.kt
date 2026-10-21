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
import kotlinx.coroutines.launch

enum class PlaylistSortOption(val displayName: String) {
    CUSTOM("Custom order"),
    TITLE("Title"),
    ARTIST("Artist"),
    ALBUM("Album"),
    RECENTLY_ADDED("Recently added"),
    DURATION("Duration")
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
        val initial = runCatching { PlaylistSortOption.valueOf(savedName ?: "") }.getOrDefault(PlaylistSortOption.CUSTOM)
        mutableStateOf(initial)
    }
    var viewMode by remember { mutableStateOf(PlaylistViewMode.LIST) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showOptionsMenu by remember { mutableStateOf(false) }

    // Multi-select mode state
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedTrackIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var trackForAddToPlaylist by remember { mutableStateOf<Track?>(null) }

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
            PlaylistSortOption.TITLE -> songs.sortedBy { it.title.lowercase() }
            PlaylistSortOption.ARTIST -> songs.sortedBy { it.artist.lowercase() }
            PlaylistSortOption.ALBUM -> songs.sortedBy { it.album.lowercase() }
            PlaylistSortOption.RECENTLY_ADDED -> songs.sortedByDescending { it.addedAt }
            PlaylistSortOption.DURATION -> songs.sortedBy { it.durationSeconds } // Shortest to longest
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Top Navigation Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
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

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Sort Menu Button
                Box {
                    GlassCircleButton(
                        icon = Icons.Rounded.Sort,
                        contentDescription = "Sort Options",
                        onClick = { showSortMenu = true }
                    )

                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false },
                        modifier = Modifier
                            .background(Color(0xF0120E1C))
                            .border(1.dp, dynamicTheme.primary.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                    ) {
                        Text(
                            text = "Sort by",
                            color = GlassWhite.copy(alpha = 0.6f),
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

                        HorizontalDivider(color = Color(0x25FFFFFF), modifier = Modifier.padding(vertical = 4.dp))

                        Text(
                            text = "View as",
                            color = GlassWhite.copy(alpha = 0.6f),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )

                        DropdownMenuItem(
                            text = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Rounded.Menu, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Compact", color = if (viewMode == PlaylistViewMode.COMPACT) dynamicTheme.primary else Color.White, fontSize = 13.5.sp)
                                    }
                                    if (viewMode == PlaylistViewMode.COMPACT) {
                                        Icon(Icons.Rounded.Check, contentDescription = null, tint = dynamicTheme.primary, modifier = Modifier.size(16.dp))
                                    }
                                }
                            },
                            onClick = {
                                viewMode = PlaylistViewMode.COMPACT
                                showSortMenu = false
                            }
                        )

                        DropdownMenuItem(
                            text = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Rounded.List, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("List", color = if (viewMode == PlaylistViewMode.LIST) dynamicTheme.primary else Color.White, fontSize = 13.5.sp)
                                    }
                                    if (viewMode == PlaylistViewMode.LIST) {
                                        Icon(Icons.Rounded.Check, contentDescription = null, tint = dynamicTheme.primary, modifier = Modifier.size(16.dp))
                                    }
                                }
                            },
                            onClick = {
                                viewMode = PlaylistViewMode.LIST
                                showSortMenu = false
                            }
                        )
                    }
                }

                // Playlist Management Options Menu
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
                            .background(Color(0xF0120E1C))
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
                            text = { Text("Select Tracks", color = Color.White, fontSize = 13.5.sp) },
                            leadingIcon = { Icon(Icons.Rounded.Checklist, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                showOptionsMenu = false
                                isSelectionMode = true
                                selectedTrackIds = emptySet()
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

        Spacer(modifier = Modifier.height(18.dp))

        // Multi-Selection Action Bar (if active)
        if (isSelectionMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(dynamicTheme.primary.copy(alpha = 0.22f))
                    .border(1.dp, dynamicTheme.primary, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${selectedTrackIds.size} Selected",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val selectedTracks = sortedSongs
                                .filter { selectedTrackIds.contains(it.trackId) && !it.mediaUriString.isNullOrBlank() }
                                .map { toTrack(it) }
                            PlaybackStateManager.addTracksToQueue(selectedTracks)
                            isSelectionMode = false
                            selectedTrackIds = emptySet()
                            Toast.makeText(context, "Added ${selectedTracks.size} tracks to queue", Toast.LENGTH_SHORT).show()
                        },
                        enabled = selectedTrackIds.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = dynamicTheme.primary),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("Add to Queue", color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            isSelectionMode = false
                            selectedTrackIds = emptySet()
                        },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("Cancel", color = Color.White, fontSize = 11.5.sp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Playlist Header Card with Shared Authoritative PlaylistArtwork
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .border(1.dp, Color(0x30FFFFFF), RoundedCornerShape(18.dp))
            ) {
                PlaylistArtwork(
                    artworkModel = playlistCustomArtwork,
                    playlistTitle = playlistCurrentName,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.width(18.dp))

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
                    color = GlassWhite.copy(alpha = 0.70f),
                    fontSize = 12.5.sp
                )
                Spacer(modifier = Modifier.height(6.dp))

                // Availability Badges
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0x35BA2DF7))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${playableSongs.size} Playable",
                            color = NeonPurple,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (unplayableCount > 0) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0x30FF5252))
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "$unplayableCount Unmatched",
                                color = Color(0xFFFF8A80),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Action Buttons Row: Play All, Shuffle, Add Songs, Auto-Match
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Play Button
            Button(
                onClick = {
                    if (playableSongs.isNotEmpty()) {
                        val first = toTrack(playableSongs.first())
                        val queue = playableSongs.map { toTrack(it) }
                        onTrackSelected(first, queue)
                    }
                },
                enabled = playableSongs.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = dynamicTheme.primary),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Play All",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            // Shuffle Button
            OutlinedButton(
                onClick = {
                    if (playableSongs.isNotEmpty()) {
                        val shuffled = playableSongs.shuffled().map { toTrack(it) }
                        onTrackSelected(shuffled.first(), shuffled)
                    }
                },
                enabled = playableSongs.isNotEmpty(),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x35FFFFFF)),
                modifier = Modifier.height(46.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Shuffle,
                    contentDescription = "Shuffle",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Add Songs Button
            OutlinedButton(
                onClick = { showImportSongsDialog = true },
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, dynamicTheme.primary.copy(alpha = 0.6f)),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = dynamicTheme.primary.copy(alpha = 0.12f)),
                modifier = Modifier.height(46.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "Add Songs",
                    tint = dynamicTheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add", color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
            }

            // Match Unmatched Tracks Online Button
            if (unplayableCount > 0) {
                Button(
                    onClick = {
                        if (!isMatchingInProgress) {
                            isMatchingInProgress = true
                            scope.launch {
                                val unmatchedList = songs.filter { it.mediaUriString.isNullOrBlank() }
                                var resolvedCount = 0
                                unmatchedList.forEachIndexed { idx, song ->
                                    matchingProgressText = "Matching ${idx + 1}/${unmatchedList.size}: ${song.title}"
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
                                        resolvedCount++
                                    }
                                }
                                matchingProgressText = "Matched $resolvedCount songs!"
                                isMatchingInProgress = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonPurple),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.height(46.dp)
                ) {
                    if (isMatchingInProgress) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Rounded.Search, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Match Audio", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        if (matchingProgressText != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = matchingProgressText ?: "",
                color = NeonPurple,
                fontSize = 11.5.sp,
                maxLines = 1
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Tracks List Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "TRACKS (${sortedSongs.size}) — ${sortOption.displayName.uppercase()}",
                color = GlassWhite.copy(alpha = 0.6f),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            Text(
                text = if (viewMode == PlaylistViewMode.COMPACT) "Compact View" else "List View",
                color = GlassWhite.copy(alpha = 0.5f),
                fontSize = 11.sp
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Track List Items or Empty State
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
                        color = GlassWhite.copy(alpha = 0.6f),
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
                verticalArrangement = Arrangement.spacedBy(if (viewMode == PlaylistViewMode.COMPACT) 6.dp else 10.dp),
                contentPadding = PaddingValues(bottom = 140.dp)
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
                                }
                            },
                            onLongClick = {
                                isSelectionMode = true
                                selectedTrackIds = selectedTrackIds + song.trackId
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
                                }
                            },
                            onLongClick = {
                                isSelectionMode = true
                                selectedTrackIds = selectedTrackIds + song.trackId
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
    onPlayNext: () -> Unit = {},
    onAddToQueue: () -> Unit = {},
    onAddToPlaylist: () -> Unit = {},
    onRemoveFromPlaylist: () -> Unit = {},
    onDownload: () -> Unit = {}
) {
    val dynamicTheme = LocalDynamicTheme.current
    var showTrackMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isSelected) dynamicTheme.primary.copy(alpha = 0.25f) else if (isCurrent) dynamicTheme.primary.copy(alpha = 0.15f) else Color(0x12FFFFFF))
            .border(
                1.dp,
                if (isSelected) dynamicTheme.primary else if (isCurrent) dynamicTheme.primary.copy(alpha = 0.7f) else Color(0x20FFFFFF),
                RoundedCornerShape(14.dp)
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSelectionMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onClick() },
                colors = CheckboxDefaults.colors(
                    checkedColor = dynamicTheme.primary,
                    checkmarkColor = Color.Black
                ),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        // Track Artwork
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
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, Color(0x25FFFFFF), RoundedCornerShape(10.dp)),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                color = if (isCurrent) dynamicTheme.primary else if (isPlayable) Color.White else GlassWhite.copy(0.5f),
                fontSize = 14.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${song.artist} • ${if (song.album.isNotBlank()) song.album else "Single"}",
                color = GlassWhite.copy(alpha = if (isPlayable) 0.70f else 0.40f),
                fontSize = 12.sp,
                maxLines = 1
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        if (isPlayable) {
            // Track Overflow Menu
            Box {
                IconButton(
                    onClick = { showTrackMenu = true },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "Track options",
                        tint = GlassWhite.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }

                DropdownMenu(
                    expanded = showTrackMenu,
                    onDismissRequest = { showTrackMenu = false },
                    modifier = Modifier
                        .background(Color(0xF0120E1C))
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
                        leadingIcon = { Icon(Icons.Rounded.Download, contentDescription = null, tint = NeonPurple, modifier = Modifier.size(16.dp)) },
                        onClick = {
                            showTrackMenu = false
                            onDownload()
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
            // Muted badge for unplayable/metadata-only item
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0x25FFFFFF))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "No audio",
                    color = GlassWhite.copy(alpha = 0.5f),
                    fontSize = 10.sp
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
    onLongClick: () -> Unit = {}
) {
    val dynamicTheme = LocalDynamicTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) dynamicTheme.primary.copy(alpha = 0.25f) else if (isCurrent) dynamicTheme.primary.copy(alpha = 0.15f) else Color(0x0EFFFFFF))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSelectionMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onClick() },
                colors = CheckboxDefaults.colors(
                    checkedColor = dynamicTheme.primary,
                    checkmarkColor = Color.Black
                ),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
        } else {
            Text(
                text = "$index",
                color = if (isCurrent) dynamicTheme.primary else GlassWhite.copy(0.5f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(24.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                color = if (isCurrent) NeonPurple else if (isPlayable) Color.White else GlassWhite.copy(0.5f),
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
            Text(
                text = song.artist,
                color = GlassWhite.copy(alpha = 0.55f),
                fontSize = 11.5.sp,
                maxLines = 1
            )
        }

        val mins = song.durationSeconds / 60
        val secs = song.durationSeconds % 60
        Text(
            text = if (song.durationSeconds > 0) String.format("%d:%02d", mins, secs) else "--:--",
            color = GlassWhite.copy(alpha = 0.5f),
            fontSize = 11.5.sp
        )
    }
}
