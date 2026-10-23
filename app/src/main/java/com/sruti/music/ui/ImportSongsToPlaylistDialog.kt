package com.sruti.music.ui

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.rememberAsyncImagePainter
import com.sruti.music.R
import com.sruti.music.data.LocalAudioRepository
import com.sruti.music.data.Track
import com.sruti.music.data.db.PlaylistSongEntity
import com.sruti.music.data.db.SrutiDatabase
import com.sruti.music.data.db.toTrack
import com.sruti.music.online.MusicSourceRegistry
import com.sruti.music.online.SpotifyPlaylistImporter
import com.sruti.music.online.SrutiDownloadManager
import com.sruti.music.theme.GlassWhite
import com.sruti.music.theme.LocalDynamicTheme
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
    val downloadManager = remember { SrutiDownloadManager.getInstance(context) }
    val importer = remember { SpotifyPlaylistImporter(context) }
    val dynamicTheme = LocalDynamicTheme.current

    // Navigation & Category states
    var selectedCategory by remember { mutableStateOf("All") } // "All", "Liked", "Device", "Downloads", "Spotify"
    var searchQuery by remember { mutableStateOf("") }
    var selectedTrackIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    // Track data sources
    var localTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var downloadedTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var onlineResults by remember { mutableStateOf<List<Track>>(emptyList()) }
    var isSearchingOnline by remember { mutableStateOf(false) }
    var isLocalLoading by remember { mutableStateOf(true) }

    // Spotify importer states
    var playlistLinkInput by remember { mutableStateOf("") }
    var isLinkImporting by remember { mutableStateOf(false) }

    val favEntities by database.musicDao().getAllFavorites().collectAsState(initial = emptyList())
    val favoriteTracks = remember(favEntities) {
        favEntities.map { it.toTrack() }
    }

    LaunchedEffect(Unit) {
        scope.launch {
            localTracks = runCatching { localRepo.queryDeviceAudio() }.getOrDefault(emptyList())
            downloadedTracks = runCatching { downloadManager.getDownloadedTracks() }.getOrDefault(emptyList())
            isLocalLoading = false
        }
    }

    // Combined unique library songs
    val allCandidateTracks = remember(localTracks, favoriteTracks, downloadedTracks, onlineResults) {
        (favoriteTracks + localTracks + downloadedTracks + onlineResults).distinctBy { it.id }
    }

    // Tracks filtered by Category
    val categoryTracks = remember(selectedCategory, allCandidateTracks, favoriteTracks, localTracks, downloadedTracks) {
        when (selectedCategory) {
            "Liked" -> favoriteTracks
            "Device" -> localTracks
            "Downloads" -> downloadedTracks
            else -> allCandidateTracks
        }
    }

    // Tracks filtered by Search Query
    val filteredTracks = remember(categoryTracks, searchQuery) {
        val q = searchQuery.trim()
        if (q.isBlank()) {
            categoryTracks
        } else {
            categoryTracks.filter {
                it.title.contains(q, ignoreCase = true) ||
                        it.artist.contains(q, ignoreCase = true) ||
                        it.album.contains(q, ignoreCase = true)
            }
        }
    }

    val isLightPrimary = dynamicTheme.primary.luminance() > 0.55f
    val buttonContentColor = if (isLightPrimary) Color.Black else Color.White

    // Done Action Handler: batch adds selected songs to playlist & dismisses
    fun addSelectedAndDismiss() {
        if (selectedTrackIds.isEmpty()) return
        scope.launch {
            val toAdd = allCandidateTracks.filter { selectedTrackIds.contains(it.id) }
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
                    sourceId = track.source,
                    sortOrder = existingCount + idx,
                    addedAt = System.currentTimeMillis()
                )
            }
            database.musicDao().insertPlaylistSongs(entities)
            Toast.makeText(context, "Added ${entities.size} songs to playlist!", Toast.LENGTH_SHORT).show()
            onDismiss()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(26.dp))
                .background(Color(0xFF0E1013))
                .border(1.dp, Color(0x28FFFFFF), RoundedCornerShape(26.dp))
                .padding(horizontal = 18.dp, vertical = 18.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 1. Top Header: Title, Selected Count, Done Button, Close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Add Songs to Playlist",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (selectedTrackIds.isNotEmpty()) {
                            Text(
                                text = "${selectedTrackIds.size} selected",
                                color = dynamicTheme.primary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (selectedTrackIds.isNotEmpty()) {
                            Button(
                                onClick = { addSelectedAndDismiss() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = dynamicTheme.primary,
                                    contentColor = buttonContentColor
                                ),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text(
                                    text = "Done (${selectedTrackIds.size})",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close", tint = GlassWhite)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 2. Clean, Spacious Search Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF16181D))
                        .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = "Search",
                            tint = Color(0xFFA1A1AA),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Search songs, artists, albums...",
                                    color = Color(0xFF71717A),
                                    fontSize = 13.5.sp
                                )
                            }
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                textStyle = TextStyle(color = Color.White, fontSize = 13.5.sp),
                                cursorBrush = SolidColor(dynamicTheme.primary),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.size(22.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Clear",
                                    tint = Color(0xFFA1A1AA),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 3. Category Filter Chips (Never cramped: clean scrollable row)
                val categories = listOf(
                    "All" to "All Songs (${allCandidateTracks.size})",
                    "Liked" to "Liked Songs (${favoriteTracks.size})",
                    "Device" to "Device (${localTracks.size})",
                    "Downloads" to "Downloads (${downloadedTracks.size})",
                    "Spotify" to "From Spotify"
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEach { (catKey, catLabel) ->
                        val isSelected = selectedCategory == catKey
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) dynamicTheme.primary else Color(0xFF16181D))
                                .border(
                                    1.dp,
                                    if (isSelected) dynamicTheme.primary else Color(0x22FFFFFF),
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { selectedCategory = catKey }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = catLabel,
                                color = if (isSelected) buttonContentColor else Color(0xFFA1A1AA),
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 4. Main Body: Spotify Importer OR Song Selection List
                if (selectedCategory == "Spotify") {
                    // Spotify Playlist Link Importer View
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "Paste a Spotify playlist link to fetch and add its tracks directly:",
                            color = Color(0xFFA1A1AA),
                            fontSize = 13.sp
                        )

                        OutlinedTextField(
                            value = playlistLinkInput,
                            onValueChange = { playlistLinkInput = it },
                            placeholder = { Text("https://open.spotify.com/playlist/...", fontSize = 12.sp, color = Color(0xFF71717A)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = dynamicTheme.primary,
                                unfocusedBorderColor = Color(0x35FFFFFF)
                            )
                        )

                        var linkProgressText by remember { mutableStateOf<String?>(null) }

                        Button(
                            onClick = {
                                val url = playlistLinkInput.trim()
                                if (url.isBlank()) return@Button
                                isLinkImporting = true
                                linkProgressText = "Connecting to Spotify..."
                                scope.launch {
                                    val result = importer.fetchPlaylistTracks(url) { loaded, total ->
                                        linkProgressText = if (total > 0 && total != Int.MAX_VALUE) {
                                            "Loading: $loaded / $total tracks..."
                                        } else {
                                            "Loaded $loaded tracks..."
                                        }
                                    }
                                    isLinkImporting = false
                                    result.onSuccess { res ->
                                        val spotifyKey = importer.extractPlaylistId(url) ?: System.currentTimeMillis().toString()
                                        val existingCount = database.musicDao().getSongsForPlaylist(playlistId).first().size
                                        val entities = res.tracks.mapIndexed { idx, track ->
                                            PlaylistSongEntity(
                                                playlistId = playlistId,
                                                trackId = "spotify_${spotifyKey}_$idx",
                                                title = track.title,
                                                artist = track.artist,
                                                album = track.album,
                                                durationSeconds = track.durationSeconds,
                                                mediaUriString = track.audioPreviewUrl,
                                                artworkUriString = track.artworkUrl ?: res.coverArt,
                                                sourceId = if (track.audioPreviewUrl != null) "spotify_preview" else "",
                                                sortOrder = existingCount + idx,
                                                addedAt = track.addedAtEpochMs
                                            )
                                        }
                                        database.musicDao().insertPlaylistSongs(entities)
                                        scope.launch {
                                            importer.resolveStreamsForTracks(playlistId, res.tracks, spotifyKey)
                                        }
                                        Toast.makeText(context, "Added ${res.tracks.size} tracks into playlist!", Toast.LENGTH_SHORT).show()
                                        onDismiss()
                                    }.onFailure { err ->
                                        Toast.makeText(context, err.message ?: "Failed to import", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            enabled = !isLinkImporting && playlistLinkInput.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = dynamicTheme.primary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(46.dp)
                        ) {
                            if (isLinkImporting) {
                                CircularProgressIndicator(color = buttonContentColor, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(linkProgressText ?: "Fetching playlist tracks...", color = buttonContentColor, fontSize = 13.sp)
                            } else {
                                Text("Fetch & Add to Playlist", color = buttonContentColor, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    // Song Picker List View
                    Column(modifier = Modifier.weight(1f)) {
                        // Sub-header: Count + Select/Deselect All
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${filteredTracks.size} songs available",
                                color = Color(0xFFA1A1AA),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )

                            if (filteredTracks.isNotEmpty()) {
                                TextButton(
                                    onClick = {
                                        val displayedIds = filteredTracks.map { it.id }.toSet()
                                        selectedTrackIds = if (selectedTrackIds.containsAll(displayedIds)) {
                                            selectedTrackIds - displayedIds
                                        } else {
                                            selectedTrackIds + displayedIds
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    val allSelected = filteredTracks.isNotEmpty() && selectedTrackIds.containsAll(filteredTracks.map { it.id })
                                    Text(
                                        text = if (allSelected) "Deselect Shown" else "Select All Shown",
                                        color = dynamicTheme.primary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Online Search Suggestion Bar if query is entered
                        if (searchQuery.isNotBlank()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF16181D))
                                    .border(1.dp, dynamicTheme.primary.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                                    .clickable {
                                        if (!isSearchingOnline) {
                                            isSearchingOnline = true
                                            scope.launch {
                                                val online = runCatching {
                                                    MusicSourceRegistry.searchAllSources(searchQuery.trim())
                                                }.getOrDefault(emptyList()).map { it.toTrack() }
                                                onlineResults = (onlineResults + online).distinctBy { it.id }
                                                isSearchingOnline = false
                                            }
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.CloudDownload, contentDescription = null, tint = dynamicTheme.primary, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (isSearchingOnline) "Searching online catalog..." else "Search online for '${searchQuery.trim()}'",
                                        color = Color.White,
                                        fontSize = 12.sp
                                    )
                                }
                                if (isSearchingOnline) {
                                    CircularProgressIndicator(color = dynamicTheme.primary, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Text("Find", color = dynamicTheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        if (isLocalLoading) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = dynamicTheme.primary, modifier = Modifier.size(28.dp))
                            }
                        } else if (filteredTracks.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Rounded.MusicOff,
                                        contentDescription = null,
                                        tint = Color(0xFF52525B),
                                        modifier = Modifier.size(40.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = if (searchQuery.isNotBlank()) "No songs found matching '$searchQuery'" else "No songs in this category",
                                        color = Color(0xFFA1A1AA),
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(filteredTracks, key = { it.id }) { track ->
                                    val isSelected = selectedTrackIds.contains(track.id)
                                    val isLiked = favoriteTracks.any { it.id == track.id }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(
                                                if (isSelected) dynamicTheme.primary.copy(alpha = 0.16f)
                                                else Color(0xFF14161B)
                                            )
                                            .border(
                                                1.dp,
                                                if (isSelected) dynamicTheme.primary.copy(alpha = 0.45f)
                                                else Color(0x18FFFFFF),
                                                RoundedCornerShape(12.dp)
                                            )
                                            .clickable {
                                                selectedTrackIds = if (isSelected) selectedTrackIds - track.id
                                                else selectedTrackIds + track.id
                                            }
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Checkbox
                                        Checkbox(
                                            checked = isSelected,
                                            onCheckedChange = { checked ->
                                                selectedTrackIds = if (checked) selectedTrackIds + track.id
                                                else selectedTrackIds - track.id
                                            },
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = dynamicTheme.primary,
                                                uncheckedColor = Color(0x60FFFFFF),
                                                checkmarkColor = buttonContentColor
                                            ),
                                            modifier = Modifier.size(24.dp)
                                        )

                                        Spacer(modifier = Modifier.width(10.dp))

                                        // Album artwork thumbnail
                                        Box(
                                            modifier = Modifier
                                                .size(42.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color(0xFF22242B))
                                        ) {
                                            if (!track.artworkUriString.isNullOrBlank()) {
                                                Image(
                                                    painter = rememberAsyncImagePainter(model = track.artworkUriString),
                                                    contentDescription = null,
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop
                                                )
                                            } else {
                                                Image(
                                                    painter = painterResource(id = track.artworkResId),
                                                    contentDescription = null,
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        // Title & Artist
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = track.title,
                                                    color = Color.White,
                                                    fontSize = 13.5.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    modifier = Modifier.weight(1f, fill = false)
                                                )
                                                if (isLiked) {
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Icon(
                                                        imageVector = Icons.Rounded.Favorite,
                                                        contentDescription = "Liked",
                                                        tint = Color(0xFFFF4081),
                                                        modifier = Modifier.size(13.dp)
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "${track.artist} • ${track.durationText}",
                                                color = Color(0xFFA1A1AA),
                                                fontSize = 11.5.sp,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 5. Sticky Bottom "Done" Action Bar (Visible whenever songs are selected)
                if (selectedTrackIds.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { addSelectedAndDismiss() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = dynamicTheme.primary,
                            contentColor = buttonContentColor
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = buttonContentColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Done • Add ${selectedTrackIds.size} Songs to Playlist",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp
                        )
                    }
                }
            }
        }
    }
}
