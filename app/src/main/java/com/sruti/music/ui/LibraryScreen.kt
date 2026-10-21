package com.sruti.music.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.sruti.music.R
import com.sruti.music.components.PlaylistArtwork
import com.sruti.music.data.LocalAudioRepository
import com.sruti.music.data.Track
import com.sruti.music.data.db.PlaylistEntity
import com.sruti.music.data.db.SrutiDatabase
import com.sruti.music.data.db.toTrack
import com.sruti.music.auth.FirebaseAuthRepository
import com.sruti.music.online.SrutiDownloadManager
import com.sruti.music.playback.PlaybackStateManager
import com.sruti.music.theme.NeonPurple
import com.sruti.music.theme.PureBlack
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

@Composable
fun LibraryScreen(
    onTrackClick: (Track, List<Track>) -> Unit,
    onPlaylistClick: (PlaylistEntity) -> Unit = {},
    onSearchClick: () -> Unit = {},
    onAccountClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val localRepo = remember { LocalAudioRepository(context) }
    val database = remember { SrutiDatabase.getDatabase(context) }
    val downloadManager = remember { SrutiDownloadManager.getInstance(context) }
    val authRepo = remember { FirebaseAuthRepository.getInstance(context) }
    val userProfile by authRepo.userProfile.collectAsState()

    val audioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED
        )
    }

    var localTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var downloadedTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    val playlists by database.musicDao().getAllPlaylists().collectAsState(initial = emptyList())
    val favoriteEntities by database.musicDao().getAllFavorites().collectAsState(initial = emptyList())
    val historyEntities by database.musicDao().getRecentHistory().collectAsState(initial = emptyList())

    // Convert favorite entities to full Track objects
    val favoriteTracks = remember(favoriteEntities) {
        favoriteEntities.map { it.toTrack() }
    }

    // Convert history entities to tracks
    val historyTracks = remember(historyEntities) {
        historyEntities.map { h ->
            val m = h.durationSeconds / 60
            val s = h.durationSeconds % 60
            val dur = if (h.durationSeconds > 0) String.format("%d:%02d", m, s) else "3:30"
            Track(
                id = h.trackId,
                title = h.title,
                artist = h.artist,
                album = h.album,
                durationText = dur,
                durationSeconds = h.durationSeconds,
                artworkUriString = h.artworkUriString,
                mediaUriString = h.mediaUriString,
                artworkResId = if (h.artworkResId != 0) h.artworkResId else R.drawable.sruti_artwork_fallback
            )
        }
    }

    // Selected Shortcut
    var selectedShortcut by remember { mutableStateOf("Liked Songs") }
    var selectedTrackForMenu by remember { mutableStateOf<Track?>(null) }
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
        if (isGranted) {
            scope.launch {
                localTracks = localRepo.queryDeviceAudio()
            }
        }
    }

    // Load local and downloaded tracks
    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            localTracks = localRepo.queryDeviceAudio()
        }
        downloadedTracks = downloadManager.getDownloadedTracks()
    }

    val recentCarousels = remember(favoriteTracks, downloadedTracks, localTracks) {
        (favoriteTracks + downloadedTracks + localTracks).distinctBy { it.id }.take(10)
    }
    val topHistory = remember(historyTracks) { historyTracks.take(8) }

    // Random Mix Trigger
    fun playRandomMix() {
        val candidates = (favoriteTracks + downloadedTracks + localTracks).distinctBy { it.id }
        if (candidates.isNotEmpty()) {
            val shuffled = candidates.shuffled()
            PlaybackStateManager.playTrack(shuffled.first(), shuffled)
            Toast.makeText(context, "Playing Random Mix", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "No songs in library yet to shuffle", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PureBlack)
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(14.dp))

        // Top Navigation Header: "Library" + Search & Account icons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Library",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

                // Account Action with authentic Google photo or initials
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF111315))
                        .border(1.dp, if (userProfile.isAuthenticated) NeonPurple else Color(0x18FFFFFF), CircleShape)
                        .clickable { onAccountClick() },
                    contentAlignment = Alignment.Center
                ) {
                    if (!userProfile.photoUrl.isNullOrBlank()) {
                        val painter = rememberAsyncImagePainter(model = userProfile.photoUrl)
                        Image(
                            painter = painter,
                            contentDescription = "Avatar",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else if (userProfile.isAuthenticated) {
                        Text(
                            text = userProfile.initials,
                            color = NeonPurple,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.PersonOutline,
                            contentDescription = "Account",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 160.dp)
        ) {
            // ==========================================
            // 2-COLUMN SHORTCUT GRID (Phone 4 Reference)
            // ==========================================
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Row 1
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LibraryShortcutItem(
                            label = "Random Mix",
                            icon = Icons.Rounded.Shuffle,
                            iconTint = Color(0xFF20D5F2),
                            isSelected = false,
                            modifier = Modifier.weight(1f),
                            onClick = { playRandomMix() }
                        )
                        LibraryShortcutItem(
                            label = "Recently Played",
                            icon = Icons.Rounded.History,
                            iconTint = Color(0xFF3D7EFF),
                            isSelected = selectedShortcut == "Recently Played",
                            modifier = Modifier.weight(1f),
                            onClick = { selectedShortcut = "Recently Played" }
                        )
                    }

                    // Row 2
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LibraryShortcutItem(
                            label = "Generations",
                            icon = Icons.Rounded.AutoAwesome,
                            iconTint = Color(0xFFB366FF),
                            isSelected = selectedShortcut == "Generations",
                            modifier = Modifier.weight(1f),
                            onClick = { selectedShortcut = "Generations" }
                        )
                        LibraryShortcutItem(
                            label = "Downloads",
                            icon = Icons.Rounded.Download,
                            iconTint = Color(0xFFFF4D88),
                            isSelected = selectedShortcut == "Downloads",
                            modifier = Modifier.weight(1f),
                            onClick = { selectedShortcut = "Downloads" }
                        )
                    }

                    // Row 3
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LibraryShortcutItem(
                            label = "Recently Added",
                            icon = Icons.Rounded.Star,
                            iconTint = Color(0xFFFFCC00),
                            isSelected = selectedShortcut == "Recently Added",
                            modifier = Modifier.weight(1f),
                            onClick = { selectedShortcut = "Recently Added" }
                        )
                        LibraryShortcutItem(
                            label = "Device Music",
                            icon = Icons.Rounded.Smartphone,
                            iconTint = Color(0xFF20D5F2),
                            isSelected = selectedShortcut == "Device Music",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                if (!hasPermission) {
                                    permissionLauncher.launch(audioPermission)
                                }
                                selectedShortcut = "Device Music"
                            }
                        )
                    }

                    // Row 4
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LibraryShortcutItem(
                            label = "Liked Songs",
                            icon = Icons.Rounded.Favorite,
                            iconTint = Color(0xFF22C55E),
                            isSelected = selectedShortcut == "Liked Songs",
                            modifier = Modifier.weight(1f),
                            onClick = { selectedShortcut = "Liked Songs" }
                        )
                        LibraryShortcutItem(
                            label = "Playlists",
                            icon = Icons.AutoMirrored.Rounded.QueueMusic,
                            iconTint = Color(0xFFFF8800),
                            isSelected = selectedShortcut == "Playlists",
                            modifier = Modifier.weight(1f),
                            onClick = { selectedShortcut = "Playlists" }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            // ==========================================
            // Horizontal Carousel: Recently Added
            // ==========================================
            if (recentCarousels.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Recently Added",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "See All",
                            color = NeonPurple,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable { selectedShortcut = "Recently Added" }
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(recentCarousels) { track ->
                            SquareArtworkCard(
                                track = track,
                                onClick = { onTrackClick(track, recentCarousels) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            // ==========================================
            // Horizontal Carousel: Most Played (History)
            // ==========================================
            if (historyTracks.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Most Played",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "See All",
                            color = NeonPurple,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable { selectedShortcut = "Recently Played" }
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(topHistory) { track ->
                            SquareArtworkCard(
                                track = track,
                                onClick = { onTrackClick(track, topHistory) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            // ==========================================
            // Dynamic Active Section Based on Selected Shortcut
            // ==========================================
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = selectedShortcut,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            when (selectedShortcut) {
                "Liked Songs" -> {
                    if (favoriteTracks.isEmpty()) {
                        item {
                            EmptyLibraryState(
                                icon = Icons.Rounded.FavoriteBorder,
                                title = "No liked songs yet",
                                subtitle = "Tap the heart on any online song, search result, or local track to see it here."
                            )
                        }
                    } else {
                        items(favoriteTracks) { track ->
                            LibraryTrackRow(
                                track = track,
                                onClick = { onTrackClick(track, favoriteTracks) },
                                onMoreClick = { selectedTrackForMenu = track }
                            )
                        }
                    }
                }

                "Downloads" -> {
                    if (downloadedTracks.isEmpty()) {
                        item {
                            EmptyLibraryState(
                                icon = Icons.Rounded.DownloadDone,
                                title = "No downloaded tracks",
                                subtitle = "Download songs from Now Playing or Search to listen offline."
                            )
                        }
                    } else {
                        items(downloadedTracks) { track ->
                            LibraryTrackRow(
                                track = track,
                                onClick = { onTrackClick(track, downloadedTracks) },
                                onMoreClick = { selectedTrackForMenu = track }
                            )
                        }
                    }
                }

                "Device Music" -> {
                    if (!hasPermission) {
                        item {
                            Button(
                                onClick = { permissionLauncher.launch(audioPermission) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF181B1F)),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) {
                                Text("Grant Permission to Access Local Audio", color = NeonPurple)
                            }
                        }
                    } else if (localTracks.isEmpty()) {
                        item {
                            EmptyLibraryState(
                                icon = Icons.Rounded.MusicOff,
                                title = "No local audio found",
                                subtitle = "Copy audio files to your device storage to view them here."
                            )
                        }
                    } else {
                        items(localTracks) { track ->
                            LibraryTrackRow(
                                track = track,
                                onClick = { onTrackClick(track, localTracks) },
                                onMoreClick = { selectedTrackForMenu = track }
                            )
                        }
                    }
                }

                "Playlists" -> {
                    if (playlists.isEmpty()) {
                        item {
                            EmptyLibraryState(
                                icon = Icons.AutoMirrored.Rounded.QueueMusic,
                                title = "No playlists yet",
                                subtitle = "Create your own playlist or import from Spotify in the Create tab."
                            )
                        }
                    } else {
                        items(playlists) { playlist ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onPlaylistClick(playlist) }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                PlaylistArtwork(
                                    playlist = playlist,
                                    modifier = Modifier
                                        .size(50.dp)
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
                                        text = "Saved Playlist",
                                        color = Color(0xFFA1A1AA),
                                        fontSize = 12.sp
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Rounded.ChevronRight,
                                    contentDescription = "Open",
                                    tint = Color(0xFF71717A)
                                )
                            }
                        }
                    }
                }

                "Recently Played" -> {
                    if (historyTracks.isEmpty()) {
                        item {
                            EmptyLibraryState(
                                icon = Icons.Rounded.History,
                                title = "No recent history",
                                subtitle = "Play some music to build your listening history."
                            )
                        }
                    } else {
                        items(historyTracks) { track ->
                            LibraryTrackRow(
                                track = track,
                                onClick = { onTrackClick(track, historyTracks) },
                                onMoreClick = { selectedTrackForMenu = track }
                            )
                        }
                    }
                }

                "Recently Added" -> {
                    val allAdded = (favoriteTracks + downloadedTracks + localTracks).distinctBy { it.id }
                    if (allAdded.isEmpty()) {
                        item {
                            EmptyLibraryState(
                                icon = Icons.Rounded.StarBorder,
                                title = "Nothing added yet",
                                subtitle = "Songs you download or favorite will be listed here."
                            )
                        }
                    } else {
                        items(allAdded) { track ->
                            LibraryTrackRow(
                                track = track,
                                onClick = { onTrackClick(track, allAdded) },
                                onMoreClick = { selectedTrackForMenu = track }
                            )
                        }
                    }
                }

                "Generations" -> {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            GenerationCard(title = "90s Golden Era", description = "Evergreen chartbusters & classics") {
                                onSearchClick()
                            }
                            GenerationCard(title = "2000s Nostalgia", description = "The decade of melody & pop") {
                                onSearchClick()
                            }
                            GenerationCard(title = "2010s Modern Hits", description = "Viral anthems and electronic beats") {
                                onSearchClick()
                            }
                        }
                    }
                }
            }
        }
    }

    // Track Overflow Menu Dialog
    if (selectedTrackForMenu != null) {
        val track = selectedTrackForMenu!!
        AlertDialog(
            onDismissRequest = { selectedTrackForMenu = null },
            containerColor = Color(0xFF111315),
            title = {
                Text(text = track.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            },
            text = {
                Column {
                    Text(text = track.artist, color = Color(0xFFA1A1AA), fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(16.dp))

                    // Play Next
                    LibraryMenuOption(icon = Icons.AutoMirrored.Rounded.QueueMusic, label = "Play Next") {
                        PlaybackStateManager.playTracksNext(listOf(track))
                        selectedTrackForMenu = null
                    }
                    // Add to Queue
                    LibraryMenuOption(icon = Icons.Rounded.PlaylistAdd, label = "Add to Queue") {
                        PlaybackStateManager.addTracksToQueue(listOf(track))
                        selectedTrackForMenu = null
                    }
                    // Add to Playlist
                    LibraryMenuOption(icon = Icons.Rounded.Add, label = "Add to Playlist...") {
                        showAddToPlaylistDialog = true
                    }
                    // Favorite Toggle
                    LibraryMenuOption(
                        icon = if (track.isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        label = if (track.isLiked) "Remove from Favorites" else "Add to Favorites"
                    ) {
                        PlaybackStateManager.toggleLikeTrack(track, track.isLiked)
                        selectedTrackForMenu = null
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedTrackForMenu = null }) {
                    Text("Close", color = NeonPurple)
                }
            }
        )
    }

    if (showAddToPlaylistDialog && selectedTrackForMenu != null) {
        AddToPlaylistDialog(
            track = selectedTrackForMenu!!,
            onDismiss = {
                showAddToPlaylistDialog = false
                selectedTrackForMenu = null
            }
        )
    }
}

@Composable
private fun LibraryShortcutItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) Color(0xFF18232C) else Color(0xFF111315))
            .border(
                width = 1.dp,
                color = if (isSelected) NeonPurple else Color(0x18FFFFFF),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                color = if (isSelected) NeonPurple else Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun SquareArtworkCard(
    track: Track,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(115.dp)
            .clickable(onClick = onClick)
    ) {
        val context = LocalContext.current
        val painter = rememberAsyncImagePainter(
            model = ImageRequest.Builder(context)
                .data(track.artworkUriString?.takeIf { it.isNotBlank() } ?: (if (track.artworkResId != 0) track.artworkResId else R.drawable.sruti_artwork_fallback))
                .error(R.drawable.sruti_artwork_fallback)
                .placeholder(R.drawable.sruti_artwork_fallback)
                .crossfade(true)
                .build()
        )
        Image(
            painter = painter,
            contentDescription = track.title,
            modifier = Modifier
                .size(115.dp)
                .clip(RoundedCornerShape(10.dp)),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = track.title,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
        Text(
            text = track.artist,
            color = Color(0xFFA1A1AA),
            fontSize = 11.5.sp,
            maxLines = 1
        )
    }
}

@Composable
private fun LibraryTrackRow(
    track: Track,
    onClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val context = LocalContext.current
        val painter = rememberAsyncImagePainter(
            model = ImageRequest.Builder(context)
                .data(track.artworkUriString?.takeIf { it.isNotBlank() } ?: (if (track.artworkResId != 0) track.artworkResId else R.drawable.sruti_artwork_fallback))
                .error(R.drawable.sruti_artwork_fallback)
                .placeholder(R.drawable.sruti_artwork_fallback)
                .crossfade(true)
                .build()
        )
        Image(
            painter = painter,
            contentDescription = track.title,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                color = Color.White,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = track.artist,
                color = Color(0xFFA1A1AA),
                fontSize = 12.sp,
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
private fun EmptyLibraryState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF111315))
            .border(1.dp, Color(0x15FFFFFF), RoundedCornerShape(14.dp))
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFF71717A),
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = Color(0xFFA1A1AA),
                fontSize = 11.5.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}

@Composable
private fun GenerationCard(
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF111315))
            .border(1.dp, Color(0x18FFFFFF), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = description, color = Color(0xFFA1A1AA), fontSize = 12.sp)
            }
            Icon(imageVector = Icons.Rounded.ChevronRight, contentDescription = null, tint = NeonPurple)
        }
    }
}

@Composable
private fun LibraryMenuOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(14.dp))
        Text(text = label, color = Color.White, fontSize = 14.sp)
    }
}
