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
import com.sruti.music.theme.NeonPurple
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun AddToPlaylistDialog(
    track: Track,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val database = remember { SrutiDatabase.getDatabase(context) }

    var playlists by remember { mutableStateOf<List<PlaylistEntity>>(emptyList()) }
    var existingPlaylistIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var confirmationMessage by remember { mutableStateOf<String?>(null) }

    // Load playlists and check which playlists already contain this track
    LaunchedEffect(track.id) {
        val allPlaylists = database.musicDao().getAllPlaylists().first()
        playlists = allPlaylists
        val containedSet = mutableSetOf<Long>()
        for (pl in allPlaylists) {
            val songs = database.musicDao().getSongsForPlaylist(pl.id).first()
            if (songs.any { it.trackId == track.id }) {
                containedSet.add(pl.id)
            }
        }
        existingPlaylistIds = containedSet
    }

    if (showCreateDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreateDialog = false },
            onPlaylistCreated = { newPlaylist ->
                showCreateDialog = false
                scope.launch {
                    val count = database.musicDao().getSongsForPlaylist(newPlaylist.id).first().size
                    database.musicDao().insertPlaylistSong(
                        PlaylistSongEntity(
                            playlistId = newPlaylist.id,
                            trackId = track.id,
                            title = track.title,
                            artist = track.artist,
                            album = track.album,
                            durationSeconds = track.durationSeconds,
                            mediaUriString = track.mediaUriString,
                            artworkUriString = track.artworkUriString,
                            artworkResId = track.artworkResId,
                            sourceId = track.source,
                            sortOrder = count,
                            addedAt = System.currentTimeMillis()
                        )
                    )
                    Toast.makeText(context, "Added to new playlist!", Toast.LENGTH_SHORT).show()
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
                            text = track.title,
                            color = NeonPurple,
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
                        .border(1.dp, NeonPurple.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                        .clickable { showCreateDialog = true }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(NeonPurple.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "New",
                            tint = NeonPurple,
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
                            val isAlreadyAdded = existingPlaylistIds.contains(pl.id)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (isAlreadyAdded) Color(0x20B625FF) else Color(0x15FFFFFF))
                                    .border(
                                        1.dp,
                                        if (isAlreadyAdded) NeonPurple.copy(alpha = 0.5f) else Color(0x20FFFFFF),
                                        RoundedCornerShape(14.dp)
                                    )
                                    .clickable {
                                        if (isAlreadyAdded) {
                                            Toast.makeText(context, "Already in '${pl.name}'", Toast.LENGTH_SHORT).show()
                                        } else {
                                            scope.launch {
                                                val currentSongs = database.musicDao().getSongsForPlaylist(pl.id).first()
                                                database.musicDao().insertPlaylistSong(
                                                    PlaylistSongEntity(
                                                        playlistId = pl.id,
                                                        trackId = track.id,
                                                        title = track.title,
                                                        artist = track.artist,
                                                        album = track.album,
                                                        durationSeconds = track.durationSeconds,
                                                        mediaUriString = track.mediaUriString,
                                                        artworkUriString = track.artworkUriString,
                                                        artworkResId = track.artworkResId,
                                                        sourceId = track.source,
                                                        sortOrder = currentSongs.size,
                                                        addedAt = System.currentTimeMillis()
                                                    )
                                                )
                                                existingPlaylistIds = existingPlaylistIds + pl.id
                                                Toast.makeText(context, "Added to '${pl.name}'!", Toast.LENGTH_SHORT).show()
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
                                    Text(
                                        text = if (isAlreadyAdded) "Already in playlist" else "Tap to add",
                                        color = if (isAlreadyAdded) NeonPurple else GlassWhite.copy(alpha = 0.6f),
                                        fontSize = 11.5.sp
                                    )
                                }

                                if (isAlreadyAdded) {
                                    Icon(
                                        imageVector = Icons.Rounded.CheckCircle,
                                        contentDescription = "Added",
                                        tint = NeonPurple,
                                        modifier = Modifier.size(20.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.AddCircleOutline,
                                        contentDescription = "Add",
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
