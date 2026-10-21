package com.sruti.music.ui

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.rememberAsyncImagePainter
import com.sruti.music.R
import com.sruti.music.data.LocalAudioRepository
import com.sruti.music.data.Track
import com.sruti.music.data.db.PlaylistSongEntity
import com.sruti.music.data.db.SrutiDatabase
import com.sruti.music.online.SpotifyPlaylistImporter
import com.sruti.music.theme.GlassWhite
import com.sruti.music.theme.NeonPurple
import com.sruti.music.theme.PureBlack
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun ImportSongsToPlaylistDialog(
    playlistId: Long,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val database = remember { SrutiDatabase.getDatabase(context) }
    val localRepo = remember { LocalAudioRepository(context) }
    val importer = remember { SpotifyPlaylistImporter(context) }

    var selectedTab by remember { mutableStateOf(0) } // 0: Fast Options, 1: Select Device Songs, 2: From Link
    var localTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var selectedTrackIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var searchQuery by remember { mutableStateOf("") }
    var playlistLinkInput by remember { mutableStateOf("") }
    var isLinkImporting by remember { mutableStateOf(false) }
    var isLocalLoading by remember { mutableStateOf(true) }

    val favEntities by database.musicDao().getAllFavorites().collectAsState(initial = emptyList())

    LaunchedEffect(Unit) {
        val tracks = localRepo.queryDeviceAudio()
        localTracks = tracks
        isLocalLoading = false
    }

    val filteredTracks = remember(localTracks, searchQuery) {
        if (searchQuery.isBlank()) localTracks
        else localTracks.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
                    it.artist.contains(searchQuery, ignoreCase = true)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .clip(RoundedCornerShape(26.dp))
                .background(Color(0xFF111315))
                .border(1.dp, Color(0x35FFFFFF), RoundedCornerShape(26.dp))
                .padding(20.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Header: Title + Close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Add Songs to Playlist",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close", tint = GlassWhite)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Tabs: "Quick Add", "Pick Songs", "From Link"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF181B1F))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("Quick Add", "Pick Device Songs", "From Link").forEachIndexed { index, tabTitle ->
                        val isSelected = selectedTab == index
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) NeonPurple else Color.Transparent)
                                .clickable { selectedTab = index }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tabTitle,
                                color = if (isSelected) Color.White else Color(0xFFA1A1AA),
                                fontSize = 11.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                when (selectedTab) {
                    0 -> {
                        // ==========================================
                        // TAB 0: QUICK ADD (All Device / Liked Songs)
                        // ==========================================
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // Quick Option 1: Import All Device Songs
                            QuickOptionCard(
                                title = "Import All Device Audio",
                                subtitle = if (isLocalLoading) "Checking local audio..." else "${localTracks.size} audio files on device",
                                icon = Icons.Rounded.FolderZip,
                                onClick = {
                                    scope.launch {
                                        if (localTracks.isEmpty()) {
                                            Toast.makeText(context, "No audio files found on device", Toast.LENGTH_SHORT).show()
                                        } else {
                                            val existingCount = database.musicDao().getSongsForPlaylist(playlistId).first().size
                                            val entities = localTracks.mapIndexed { idx, track ->
                                                PlaylistSongEntity(
                                                    playlistId = playlistId,
                                                    trackId = track.id,
                                                    title = track.title,
                                                    artist = track.artist,
                                                    album = track.album,
                                                    durationSeconds = track.durationSeconds,
                                                    mediaUriString = track.mediaUriString,
                                                    artworkUriString = track.artworkUriString,
                                                    artworkResId = track.artworkResId,
                                                    sourceId = "local",
                                                    sortOrder = existingCount + idx,
                                                    addedAt = System.currentTimeMillis()
                                                )
                                            }
                                            database.musicDao().insertPlaylistSongs(entities)
                                            Toast.makeText(context, "Added ${entities.size} songs from device!", Toast.LENGTH_SHORT).show()
                                            onDismiss()
                                        }
                                    }
                                }
                            )

                            // Quick Option 2: Import All Liked Songs
                            QuickOptionCard(
                                title = "Import All Liked Songs",
                                subtitle = "${favEntities.size} favorite songs saved",
                                icon = Icons.Rounded.Favorite,
                                onClick = {
                                    scope.launch {
                                        if (favEntities.isEmpty()) {
                                            Toast.makeText(context, "No liked songs yet to import", Toast.LENGTH_SHORT).show()
                                        } else {
                                            val existingCount = database.musicDao().getSongsForPlaylist(playlistId).first().size
                                            val entities = favEntities.mapIndexed { idx, fav ->
                                                PlaylistSongEntity(
                                                    playlistId = playlistId,
                                                    trackId = fav.trackId,
                                                    title = fav.title,
                                                    artist = fav.artist,
                                                    album = fav.album,
                                                    durationSeconds = fav.durationSeconds,
                                                    mediaUriString = fav.mediaUriString,
                                                    artworkUriString = fav.artworkUriString,
                                                    artworkResId = fav.artworkResId,
                                                    sourceId = if (fav.isLocal) "local" else "online",
                                                    sortOrder = existingCount + idx,
                                                    addedAt = System.currentTimeMillis()
                                                )
                                            }
                                            database.musicDao().insertPlaylistSongs(entities)
                                            Toast.makeText(context, "Added ${entities.size} liked songs!", Toast.LENGTH_SHORT).show()
                                            onDismiss()
                                        }
                                    }
                                }
                            )
                        }
                    }

                    1 -> {
                        // ==========================================
                        // TAB 1: PICK INDIVIDUAL DEVICE SONGS
                        // ==========================================
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Search filter box
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF181B1F))
                                    .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 12.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.Search, contentDescription = null, tint = Color(0xFFA1A1AA), modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    BasicTextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                                        cursorBrush = SolidColor(NeonPurple),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Action: Select All / Deselect All
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${selectedTrackIds.size} of ${filteredTracks.size} selected",
                                    color = Color(0xFFA1A1AA),
                                    fontSize = 12.sp
                                )
                                TextButton(
                                    onClick = {
                                        selectedTrackIds = if (selectedTrackIds.size == filteredTracks.size) emptySet()
                                        else filteredTracks.map { it.id }.toSet()
                                    }
                                ) {
                                    Text(
                                        text = if (selectedTrackIds.size == filteredTracks.size) "Deselect All" else "Select All",
                                        color = NeonPurple,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(filteredTracks) { track ->
                                    val isSelected = selectedTrackIds.contains(track.id)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (isSelected) Color(0x25A855F7) else Color(0xFF181B1F))
                                            .clickable {
                                                selectedTrackIds = if (isSelected) selectedTrackIds - track.id
                                                else selectedTrackIds + track.id
                                            }
                                            .padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = isSelected,
                                            onCheckedChange = { checked ->
                                                selectedTrackIds = if (checked) selectedTrackIds + track.id
                                                else selectedTrackIds - track.id
                                            },
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = NeonPurple,
                                                uncheckedColor = Color(0x60FFFFFF)
                                            )
                                        )

                                        Spacer(modifier = Modifier.width(8.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(text = track.title, color = Color.White, fontSize = 13.5.sp, maxLines = 1)
                                            Text(text = track.artist, color = Color(0xFFA1A1AA), fontSize = 11.5.sp, maxLines = 1)
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Button(
                                onClick = {
                                    scope.launch {
                                        val toAdd = localTracks.filter { selectedTrackIds.contains(it.id) }
                                        val existingCount = database.musicDao().getSongsForPlaylist(playlistId).first().size
                                        val entities = toAdd.mapIndexed { idx, track ->
                                            PlaylistSongEntity(
                                                playlistId = playlistId,
                                                trackId = track.id,
                                                title = track.title,
                                                artist = track.artist,
                                                album = track.album,
                                                durationSeconds = track.durationSeconds,
                                                mediaUriString = track.mediaUriString,
                                                artworkUriString = track.artworkUriString,
                                                artworkResId = track.artworkResId,
                                                sourceId = "local",
                                                sortOrder = existingCount + idx,
                                                addedAt = System.currentTimeMillis()
                                            )
                                        }
                                        database.musicDao().insertPlaylistSongs(entities)
                                        Toast.makeText(context, "Added ${entities.size} songs!", Toast.LENGTH_SHORT).show()
                                        onDismiss()
                                    }
                                },
                                enabled = selectedTrackIds.isNotEmpty(),
                                colors = ButtonDefaults.buttonColors(containerColor = NeonPurple),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().height(46.dp)
                            ) {
                                Text("Add ${selectedTrackIds.size} Selected Songs", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    2 -> {
                        // ==========================================
                        // TAB 2: IMPORT FROM PLAYLIST LINK
                        // ==========================================
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = "Paste a Spotify playlist link to import its tracks directly into this playlist:",
                                color = Color(0xFFA1A1AA),
                                fontSize = 13.sp
                            )

                            OutlinedTextField(
                                value = playlistLinkInput,
                                onValueChange = { playlistLinkInput = it },
                                placeholder = { Text("https://open.spotify.com/playlist/...", fontSize = 12.sp) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = NeonPurple,
                                    unfocusedBorderColor = Color(0x35FFFFFF)
                                )
                            )

                            Button(
                                onClick = {
                                    val url = playlistLinkInput.trim()
                                    if (url.isBlank()) return@Button
                                    isLinkImporting = true
                                    scope.launch {
                                        val result = importer.importPlaylist(url)
                                        isLinkImporting = false
                                        result.onSuccess { res ->
                                            val existingCount = database.musicDao().getSongsForPlaylist(playlistId).first().size
                                            val entities = res.tracks.mapIndexed { idx, track ->
                                                PlaylistSongEntity(
                                                    playlistId = playlistId,
                                                    trackId = "spotify_${res.playlistId}_$idx",
                                                    title = track.title,
                                                    artist = track.artist,
                                                    album = track.album,
                                                    durationSeconds = track.durationSeconds,
                                                    mediaUriString = track.audioPreviewUrl,
                                                    artworkUriString = track.artworkUrl,
                                                    sourceId = "spotify_preview",
                                                    sortOrder = existingCount + idx,
                                                    addedAt = System.currentTimeMillis()
                                                )
                                            }
                                            database.musicDao().insertPlaylistSongs(entities)
                                            Toast.makeText(context, "Imported ${res.tracks.size} tracks into playlist!", Toast.LENGTH_SHORT).show()
                                            onDismiss()
                                        }.onFailure { err ->
                                            Toast.makeText(context, err.message ?: "Failed to import", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                enabled = !isLinkImporting && playlistLinkInput.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(containerColor = NeonPurple),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().height(46.dp)
                            ) {
                                if (isLinkImporting) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Fetching playlist tracks...", color = Color.White, fontSize = 13.sp)
                                } else {
                                    Text("Fetch & Add to Playlist", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickOptionCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF181B1F))
            .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0x24A855F7)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = title, tint = NeonPurple, modifier = Modifier.size(22.dp))
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = Color.White, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = subtitle, color = Color(0xFFA1A1AA), fontSize = 12.sp)
        }

        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = Color(0xFF71717A))
    }
}
