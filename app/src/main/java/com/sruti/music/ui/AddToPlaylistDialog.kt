package com.sruti.music.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.sruti.music.components.PlaylistArtwork
import com.sruti.music.data.Track
import com.sruti.music.data.db.PlaylistEntity
import com.sruti.music.data.db.PlaylistSongEntity
import com.sruti.music.data.db.SrutiDatabase
import com.sruti.music.theme.GlassWhite
import com.sruti.music.theme.LocalDynamicTheme
import com.sruti.music.theme.NeonPurple
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun AddToPlaylistDialog(
    track: Track,
    onDismiss: () -> Unit
) {
    AddToPlaylistDialog(tracks = listOf(track), onDismiss = onDismiss)
}

@Composable
fun AddToPlaylistDialog(
    tracks: List<Track>,
    onDismiss: () -> Unit
) {
    if (tracks.isEmpty()) {
        onDismiss()
        return
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val database = remember { SrutiDatabase.getDatabase(context) }
    val dynamicTheme = LocalDynamicTheme.current

    var playlists by remember { mutableStateOf<List<PlaylistEntity>>(emptyList()) }
    var playlistExistingMap by remember { mutableStateOf<Map<Long, Set<String>>>(emptyMap()) }
    var showCreateDialog by remember { mutableStateOf(false) }

    val trackIds = remember(tracks) { tracks.map { it.id }.toSet() }

    // Load playlists and check which playlists already contain these tracks
    LaunchedEffect(trackIds) {
        val allPlaylists = database.musicDao().getAllPlaylists().first()
        playlists = allPlaylists
        val existingMap = mutableMapOf<Long, Set<String>>()
        for (pl in allPlaylists) {
            val songs = database.musicDao().getSongsForPlaylist(pl.id).first()
            val existingSet = songs.map { it.trackId }.toSet()
            val overlap = trackIds.intersect(existingSet)
            if (overlap.isNotEmpty()) {
                existingMap[pl.id] = overlap
            }
        }
        playlistExistingMap = existingMap
    }

    if (showCreateDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreateDialog = false },
            onPlaylistCreated = { newPlaylist ->
                showCreateDialog = false
                scope.launch {
                    val currentCount = database.musicDao().getSongsForPlaylist(newPlaylist.id).first().size
                    val entities = tracks.mapIndexed { idx, t ->
                        PlaylistSongEntity(
                            playlistId = newPlaylist.id,
                            trackId = t.id,
                            title = t.title,
                            artist = t.artist,
                            album = t.album,
                            durationSeconds = t.durationSeconds,
                            mediaUriString = t.mediaUriString,
                            artworkUriString = t.artworkUriString,
                            artworkResId = t.artworkResId,
                            sourceId = t.source,
                            sortOrder = currentCount + idx,
                            addedAt = System.currentTimeMillis()
                        )
                    }
                    database.musicDao().insertPlaylistSongs(entities)
                    Toast.makeText(context, "Added ${entities.size} songs to new playlist!", Toast.LENGTH_SHORT).show()
                    onDismiss()
                }
            }
        )
        return
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.72f)
                .clip(RoundedCornerShape(26.dp))
                .background(Color(0xF8111315))
                .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(26.dp))
                .padding(20.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Add to Playlist",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (tracks.size == 1) tracks.first().title else "${tracks.size} songs selected",
                            color = dynamicTheme.primary,
                            fontSize = 12.5.sp,
                            maxLines = 1
                        )
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close",
                            tint = GlassWhite
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // "+ Create New Playlist" Action Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0x22FFFFFF))
                        .border(1.dp, dynamicTheme.primary.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                        .clickable { showCreateDialog = true }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(dynamicTheme.primary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "New",
                            tint = dynamicTheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Create New Playlist",
                        color = Color.White,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (playlists.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No playlists found.\nCreate one above to organize your music!",
                            color = GlassWhite.copy(alpha = 0.6f),
                            fontSize = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(playlists, key = { it.id }) { pl ->
                            val existingInPlaylist = playlistExistingMap[pl.id] ?: emptySet()
                            val existingCount = existingInPlaylist.size
                            val isAllAlreadyAdded = existingCount >= tracks.size
                            val isPartiallyAdded = existingCount in 1 until tracks.size
                            val remainingToAdd = tracks.size - existingCount

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        if (isAllAlreadyAdded) dynamicTheme.primary.copy(alpha = 0.16f)
                                        else if (isPartiallyAdded) dynamicTheme.primary.copy(alpha = 0.09f)
                                        else Color(0x15FFFFFF)
                                    )
                                    .border(
                                        1.dp,
                                        if (isAllAlreadyAdded) dynamicTheme.primary.copy(alpha = 0.5f)
                                        else if (isPartiallyAdded) dynamicTheme.primary.copy(alpha = 0.35f)
                                        else Color(0x20FFFFFF),
                                        RoundedCornerShape(14.dp)
                                    )
                                    .clickable {
                                        if (isAllAlreadyAdded) {
                                            val msg = if (tracks.size == 1) "Already in '${pl.name}'" else "All selected songs are already in '${pl.name}'"
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        } else {
                                            scope.launch {
                                                val currentSongs = database.musicDao().getSongsForPlaylist(pl.id).first()
                                                val toAddTracks = if (isPartiallyAdded) {
                                                    tracks.filter { it.id !in existingInPlaylist }
                                                } else {
                                                    tracks
                                                }

                                                val entities = toAddTracks.mapIndexed { idx, t ->
                                                    PlaylistSongEntity(
                                                        playlistId = pl.id,
                                                        trackId = t.id,
                                                        title = t.title,
                                                        artist = t.artist,
                                                        album = t.album,
                                                        durationSeconds = t.durationSeconds,
                                                        mediaUriString = t.mediaUriString,
                                                        artworkUriString = t.artworkUriString,
                                                        artworkResId = t.artworkResId,
                                                        sourceId = t.source,
                                                        sortOrder = currentSongs.size + idx,
                                                        addedAt = System.currentTimeMillis()
                                                    )
                                                }
                                                database.musicDao().insertPlaylistSongs(entities)
                                                playlistExistingMap = playlistExistingMap + (pl.id to (existingInPlaylist + toAddTracks.map { it.id }))
                                                val msg = if (entities.size == 1) "Added '${entities.first().title}' to '${pl.name}'!" else "Added ${entities.size} songs to '${pl.name}'!"
                                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                                onDismiss()
                                            }
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                PlaylistArtwork(
                                    artworkModel = pl.artworkUri ?: pl.artworkUrl,
                                    playlistTitle = pl.name,
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                )

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = pl.name,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1
                                    )
                                    val statusText = when {
                                        tracks.size == 1 && isAllAlreadyAdded -> "Already in playlist"
                                        tracks.size == 1 -> "Tap to add"
                                        isAllAlreadyAdded -> "All ${tracks.size} songs already in playlist"
                                        isPartiallyAdded -> "$existingCount of ${tracks.size} already in playlist • Tap to add $remainingToAdd more"
                                        else -> "Tap to add all ${tracks.size} songs"
                                    }
                                    Text(
                                        text = statusText,
                                        color = if (isAllAlreadyAdded || isPartiallyAdded) dynamicTheme.primary else GlassWhite.copy(alpha = 0.6f),
                                        fontSize = 11.sp,
                                        maxLines = 1
                                    )
                                }

                                if (isAllAlreadyAdded) {
                                    Icon(
                                        imageVector = Icons.Rounded.CheckCircle,
                                        contentDescription = "Already in playlist",
                                        tint = dynamicTheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                } else if (isPartiallyAdded) {
                                    Icon(
                                        imageVector = Icons.Rounded.AddCircle,
                                        contentDescription = "Add remaining",
                                        tint = dynamicTheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.AddCircleOutline,
                                        contentDescription = "Add all",
                                        tint = GlassWhite.copy(alpha = 0.6f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
