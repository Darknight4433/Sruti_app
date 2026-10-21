package com.sruti.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sruti.music.components.PlaylistArtwork
import com.sruti.music.data.Track
import com.sruti.music.data.LocalAudioRepository
import com.sruti.music.data.db.PlaylistEntity
import com.sruti.music.data.db.PlaylistSongEntity
import com.sruti.music.data.db.SrutiDatabase
import com.sruti.music.data.db.toTrack
import com.sruti.music.playback.PlaybackStateManager
import com.sruti.music.theme.LocalDynamicTheme
import com.sruti.music.theme.NeonPurple
import com.sruti.music.theme.PureBlack
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader

@Composable
fun CreateScreen(
    onPlaylistClick: (PlaylistEntity) -> Unit,
    onImportSpotifyClick: () -> Unit,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val database = remember { SrutiDatabase.getDatabase(context) }
    val localRepo = remember { LocalAudioRepository(context) }
    val dynamicTheme = LocalDynamicTheme.current

    val playlists by database.musicDao().getAllPlaylists().collectAsState(initial = emptyList())
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var playlistToRename by remember { mutableStateOf<PlaylistEntity?>(null) }
    var playlistToDelete by remember { mutableStateOf<PlaylistEntity?>(null) }
    var selectedPlaylistForMenu by remember { mutableStateOf<PlaylistEntity?>(null) }

    // File picker launcher for M3U / audio playlist files
    val m3uPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val reader = BufferedReader(InputStreamReader(inputStream))
                    val importedTitles = mutableListOf<String>()
                    reader.useLines { lines ->
                        lines.forEach { line ->
                            val trimmed = line.trim()
                            if (trimmed.startsWith("#EXTINF:", ignoreCase = true)) {
                                val titlePart = trimmed.substringAfter(",").trim()
                                if (titlePart.isNotBlank()) importedTitles.add(titlePart)
                            } else if (trimmed.isNotBlank() && !trimmed.startsWith("#")) {
                                val name = trimmed.substringAfterLast("/").substringAfterLast("\\").substringBeforeLast(".")
                                if (name.isNotBlank()) importedTitles.add(name)
                            }
                        }
                    }

                    if (importedTitles.isNotEmpty()) {
                        val newPlaylistId = database.musicDao().insertPlaylist(
                            PlaylistEntity(
                                name = "Imported Playlist",
                                createdAt = System.currentTimeMillis()
                            )
                        )
                        val entities = importedTitles.mapIndexed { idx, title ->
                            val parts = if (title.contains("-")) title.split("-", limit = 2) else listOf("Unknown Artist", title)
                            val artist = if (parts.size > 1) parts[0].trim() else "Various Artists"
                            val trackTitle = if (parts.size > 1) parts[1].trim() else parts[0].trim()
                            PlaylistSongEntity(
                                playlistId = newPlaylistId,
                                trackId = "m3u_${System.currentTimeMillis()}_$idx",
                                title = trackTitle,
                                artist = artist,
                                album = "Imported Playlist",
                                durationSeconds = 210,
                                mediaUriString = null,
                                artworkUriString = null,
                                sourceId = "m3u_file",
                                sortOrder = idx,
                                addedAt = System.currentTimeMillis()
                            )
                        }
                        database.musicDao().insertPlaylistSongs(entities)
                        val created = database.musicDao().getPlaylistById(newPlaylistId)
                        Toast.makeText(context, "Imported ${importedTitles.size} songs from playlist file!", Toast.LENGTH_SHORT).show()
                        if (created != null) onPlaylistClick(created)
                    } else {
                        Toast.makeText(context, "No songs found in the playlist file", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Failed to import playlist file: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(14.dp))

        // Top Header: "Create" + Search Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Create",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )

            IconButton(
                onClick = onSearchClick,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF111315))
                    .border(1.dp, Color(0x18FFFFFF), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = "Search",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Action 1: New Playlist Card
        CreateActionCard(
            title = "New Playlist",
            subtitle = "Create your own playlist",
            icon = Icons.Rounded.Add,
            iconTint = dynamicTheme.primary,
            iconBoxBg = Color(0xFF181B1F),
            onClick = { showCreatePlaylistDialog = true }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Action 2: Import from Spotify / Link Card
        CreateActionCard(
            title = "Import from Spotify / Link",
            subtitle = "Paste a playlist link to import tracks",
            icon = Icons.Rounded.MusicNote,
            iconTint = Color(0xFF1DB954),
            iconBoxBg = Color(0xFF181B1F),
            onClick = onImportSpotifyClick
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Action 3: Import All Device Music Card
        CreateActionCard(
            title = "Import All Device Music",
            subtitle = "Create playlist with all local music files",
            icon = Icons.Rounded.FolderZip,
            iconTint = dynamicTheme.primary,
            iconBoxBg = Color(0xFF181B1F),
            onClick = {
                scope.launch {
                    val tracks = localRepo.queryDeviceAudio()
                    if (tracks.isEmpty()) {
                        Toast.makeText(context, "No local music found on device", Toast.LENGTH_SHORT).show()
                    } else {
                        val newPlaylistId = database.musicDao().insertPlaylist(
                            PlaylistEntity(
                                name = "All Device Music",
                                artworkUrl = tracks.firstOrNull()?.artworkUriString,
                                createdAt = System.currentTimeMillis()
                            )
                        )
                        val songEntities = tracks.mapIndexed { idx, track ->
                            PlaylistSongEntity(
                                playlistId = newPlaylistId,
                                trackId = track.id,
                                title = track.title,
                                artist = track.artist,
                                album = track.album,
                                durationSeconds = track.durationSeconds,
                                mediaUriString = track.mediaUriString,
                                artworkUriString = track.artworkUriString,
                                artworkResId = track.artworkResId,
                                sourceId = "local",
                                sortOrder = idx,
                                addedAt = System.currentTimeMillis()
                            )
                        }
                        database.musicDao().insertPlaylistSongs(songEntities)
                        val pl = database.musicDao().getPlaylistById(newPlaylistId)
                        Toast.makeText(context, "Imported ${tracks.size} songs from device!", Toast.LENGTH_SHORT).show()
                        if (pl != null) onPlaylistClick(pl)
                    }
                }
            }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Action 4: Import Playlist File (.m3u) Card
        CreateActionCard(
            title = "Import Playlist File (.m3u)",
            subtitle = "Select an .m3u or .m3u8 playlist file from storage",
            icon = Icons.AutoMirrored.Rounded.QueueMusic,
            iconTint = NeonPurple,
            iconBoxBg = Color(0xFF181B1F),
            onClick = {
                try {
                    m3uPickerLauncher.launch("*/*")
                } catch (e: Exception) {
                    Toast.makeText(context, "Could not open file picker", Toast.LENGTH_SHORT).show()
                }
            }
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Section: Recent Playlists
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Recent playlists",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            if (playlists.isNotEmpty()) {
                Text(
                    text = "See All",
                    color = dynamicTheme.primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (playlists.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF111315))
                    .border(1.dp, Color(0x15FFFFFF), RoundedCornerShape(16.dp))
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Rounded.PlaylistAdd,
                        contentDescription = null,
                        tint = Color(0xFF71717A),
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No playlists created yet",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Tap New Playlist or Import from Spotify above",
                        color = Color(0xFFA1A1AA),
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 160.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(playlists) { playlist ->
                    var songCount by remember(playlist.id) { mutableIntStateOf(0) }
                    var totalDurationSec by remember(playlist.id) { mutableIntStateOf(0) }

                    LaunchedEffect(playlist.id) {
                        val songs = database.musicDao().getSongsForPlaylist(playlist.id).firstOrNull() ?: emptyList()
                        songCount = songs.size
                        totalDurationSec = songs.sumOf { it.durationSeconds }
                    }

                    val durText = if (totalDurationSec > 0) {
                        val hrs = totalDurationSec / 3600
                        val mins = (totalDurationSec % 3600) / 60
                        if (hrs > 0) "$hrs hr $mins min" else "$mins min"
                    } else "0 min"

                    val subtitle = "$songCount tracks • $durText"

                    RecentPlaylistRow(
                        playlist = playlist,
                        subtitle = subtitle,
                        onClick = { onPlaylistClick(playlist) },
                        onMoreClick = { selectedPlaylistForMenu = playlist }
                    )
                }
            }
        }
    }

    if (showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreatePlaylistDialog = false },
            onPlaylistCreated = { newEntity ->
                showCreatePlaylistDialog = false
                onPlaylistClick(newEntity)
            }
        )
    }

    // Playlist Contextual Menu Dialog
    if (selectedPlaylistForMenu != null) {
        val pl = selectedPlaylistForMenu!!
        AlertDialog(
            onDismissRequest = { selectedPlaylistForMenu = null },
            containerColor = Color(0xFF111315),
            title = {
                Text(text = pl.name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            },
            text = {
                Column {
                    // Play All
                    CreateMenuOption(icon = Icons.Rounded.PlayArrow, label = "Play All") {
                        scope.launch {
                            val songs = database.musicDao().getSongsForPlaylist(pl.id).firstOrNull() ?: emptyList()
                            val tracks = songs.map { song ->
                                Track(
                                    id = song.trackId,
                                    title = song.title,
                                    artist = song.artist,
                                    album = song.album,
                                    durationText = "",
                                    durationSeconds = song.durationSeconds,
                                    artworkUriString = song.artworkUriString,
                                    mediaUriString = song.mediaUriString
                                )
                            }
                            if (tracks.isNotEmpty()) {
                                PlaybackStateManager.playTrack(tracks.first(), tracks)
                            }
                            selectedPlaylistForMenu = null
                        }
                    }
                    // Shuffle
                    CreateMenuOption(icon = Icons.Rounded.Shuffle, label = "Shuffle") {
                        scope.launch {
                            val songs = database.musicDao().getSongsForPlaylist(pl.id).firstOrNull() ?: emptyList()
                            val tracks = songs.map { song ->
                                Track(
                                    id = song.trackId,
                                    title = song.title,
                                    artist = song.artist,
                                    album = song.album,
                                    durationText = "",
                                    durationSeconds = song.durationSeconds,
                                    artworkUriString = song.artworkUriString,
                                    mediaUriString = song.mediaUriString
                                )
                            }.shuffled()
                            if (tracks.isNotEmpty()) {
                                PlaybackStateManager.playTrack(tracks.first(), tracks)
                            }
                            selectedPlaylistForMenu = null
                        }
                    }
                    // Delete
                    CreateMenuOption(icon = Icons.Rounded.DeleteOutline, label = "Delete Playlist", tint = Color(0xFFFF5252)) {
                        playlistToDelete = pl
                        selectedPlaylistForMenu = null
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedPlaylistForMenu = null }) {
                    Text("Close", color = dynamicTheme.primary)
                }
            }
        )
    }

    if (playlistToDelete != null) {
        val pl = playlistToDelete!!
        AlertDialog(
            onDismissRequest = { playlistToDelete = null },
            containerColor = Color(0xFF111315),
            title = { Text("Delete \"${pl.name}\"?", color = Color.White) },
            text = { Text("This will delete the playlist and its saved tracks from Sruti.", color = Color(0xFFA1A1AA)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        database.musicDao().deletePlaylist(pl.id)
                        database.musicDao().deletePlaylistSongs(pl.id)
                        playlistToDelete = null
                    }
                }) {
                    Text("Delete", color = Color(0xFFFF5252))
                }
            },
            dismissButton = {
                TextButton(onClick = { playlistToDelete = null }) {
                    Text("Cancel", color = Color.White)
                }
            }
        )
    }
}

@Composable
private fun CreateActionCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    iconBoxBg: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF111315))
            .border(1.dp, Color(0x18FFFFFF), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconBoxBg)
                .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = iconTint,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = Color(0xFFA1A1AA),
                fontSize = 12.5.sp
            )
        }
    }
}

@Composable
private fun RecentPlaylistRow(
    playlist: PlaylistEntity,
    subtitle: String,
    onClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PlaylistArtwork(
            playlist = playlist,
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(10.dp))
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = Color(0xFFA1A1AA),
                fontSize = 12.5.sp,
                maxLines = 1
            )
        }

        IconButton(onClick = onMoreClick, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = "Options",
                tint = Color(0xFF71717A),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun CreateMenuOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color = Color.White,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(14.dp))
        Text(text = label, color = tint, fontSize = 14.sp)
    }
}
