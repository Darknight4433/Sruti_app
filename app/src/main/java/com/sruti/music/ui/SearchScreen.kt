package com.sruti.music.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.sruti.music.R
import com.sruti.music.data.LocalAudioRepository
import com.sruti.music.data.Track
import com.sruti.music.data.db.SrutiDatabase
import com.sruti.music.online.MusicSourceRegistry
import com.sruti.music.online.SaavnMusicSource
import com.sruti.music.playback.PlaybackStateManager
import com.sruti.music.theme.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SearchScreen(
    onTrackClick: (Track, List<Track>) -> Unit,
    onPlaylistClick: (com.sruti.music.data.db.PlaylistEntity) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val localRepo = remember { LocalAudioRepository(context) }
    val database = remember { SrutiDatabase.getDatabase(context) }
    val favoriteEntities by database.musicDao().getAllFavorites().collectAsState(initial = emptyList())
    val favoriteTrackIds = remember(favoriteEntities) { favoriteEntities.map { it.trackId }.toSet() }

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") }
    val filters = listOf("All", "Songs", "Artists", "Albums", "Playlists", "Device Music")

    var isSearching by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<Track>>(emptyList()) }
    var searchError by remember { mutableStateOf<String?>(null) }

    var trendingTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var isTrendingLoading by remember { mutableStateOf(true) }

    var selectedTrackForMenu by remember { mutableStateOf<Track?>(null) }
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }

    // Load Trending tracks on launch
    LaunchedEffect(Unit) {
        scope.launch {
            try {
                isTrendingLoading = true
                val source = SaavnMusicSource()
                val trendingResult = source.search("Trending Hits")
                val tracks = trendingResult.getOrDefault(emptyList()).map { it.toTrack() }
                if (tracks.isNotEmpty()) {
                    trendingTracks = tracks.take(12)
                } else {
                    val fallback = source.search("Top Songs").getOrDefault(emptyList()).map { it.toTrack() }
                    trendingTracks = fallback.take(12)
                }
            } catch (e: Exception) {
                // If network fails, trending will be empty gracefully
            } finally {
                isTrendingLoading = false
            }
        }
    }

    // Debounced search
    var searchJob by remember { mutableStateOf<Job?>(null) }
    LaunchedEffect(searchQuery, selectedFilter) {
        val query = searchQuery.trim()
        if (query.isBlank()) {
            searchResults = emptyList()
            isSearching = false
            searchError = null
            return@LaunchedEffect
        }

        searchJob?.cancel()
        searchJob = scope.launch {
            delay(350) // Debounce typing
            isSearching = true
            searchError = null

            try {
                if (selectedFilter == "Device Music") {
                    val local = localRepo.queryDeviceAudio()
                    searchResults = local.filter {
                        it.title.contains(query, ignoreCase = true) ||
                        it.artist.contains(query, ignoreCase = true) ||
                        it.album.contains(query, ignoreCase = true)
                    }
                } else {
                    val online = MusicSourceRegistry.searchAllSources(query)
                    var mapped = online.map { it.toTrack() }

                    if (selectedFilter == "Device Music" || selectedFilter == "All") {
                        val local = runCatching { localRepo.queryDeviceAudio() }.getOrDefault(emptyList())
                        val matchingLocal = local.filter {
                            it.title.contains(query, ignoreCase = true) ||
                            it.artist.contains(query, ignoreCase = true)
                        }
                        mapped = (mapped + matchingLocal).distinctBy { it.id }
                    }

                    // Apply filter
                    searchResults = when (selectedFilter) {
                        "Songs" -> mapped
                        "Artists" -> mapped.filter { it.artist.contains(query, ignoreCase = true) }
                        "Albums" -> mapped.filter { it.album.contains(query, ignoreCase = true) }
                        else -> mapped
                    }
                }
            } catch (e: Exception) {
                searchError = "Could not load results. Please check your internet connection."
            } finally {
                isSearching = false
            }
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

        // Title: Search
        Text(
            text = "Search",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Search Input Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF111315))
                .border(1.dp, Color(0x18FFFFFF), RoundedCornerShape(24.dp))
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
                            fontSize = 14.sp
                        )
                    }
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        textStyle = TextStyle(
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        cursorBrush = SolidColor(NeonPurple),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { searchQuery = "" },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Clear",
                            tint = Color(0xFFA1A1AA),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Filter Chips Row
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(filters) { filter ->
                val isSelected = selectedFilter == filter
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isSelected) NeonPurple else Color(0xFF181B1F))
                        .border(
                            width = 1.dp,
                            color = if (isSelected) NeonPurple else Color(0x1AFFFFFF),
                            shape = RoundedCornerShape(20.dp)
                        )
                        .clickable { selectedFilter = filter }
                        .padding(horizontal = 16.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = filter,
                        color = if (isSelected) Color.Black else Color(0xFFA1A1AA),
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Main Search Content
        if (searchQuery.isBlank()) {
            // Default view: Trending + Browse by mood
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 160.dp)
            ) {
                // Section: Trending
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Trending",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "See All",
                            color = NeonPurple,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable {
                                searchQuery = "Trending Hits"
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                if (isTrendingLoading) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = NeonPurple, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                    }
                } else if (trendingTracks.isEmpty()) {
                    item {
                        Text(
                            text = "Trending songs will appear when connected online.",
                            color = Color(0xFF71717A),
                            fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                } else {
                    items(trendingTracks) { track ->
                        SearchTrackRow(
                            track = track,
                            onClick = { onTrackClick(track, trendingTracks) },
                            onMoreClick = { selectedTrackForMenu = track }
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Browse by mood",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MoodCard(title = "Chill", gradientColors = listOf(Color(0xFF1E3A8A), Color(0xFF0F172A)), modifier = Modifier.weight(1f)) {
                            searchQuery = "Chill Relax"
                        }
                        MoodCard(title = "Workout", gradientColors = listOf(Color(0xFFB91C1C), Color(0xFF18181B)), modifier = Modifier.weight(1f)) {
                            searchQuery = "Workout Motivation"
                        }
                        MoodCard(title = "Focus", gradientColors = listOf(Color(0xFF065F46), Color(0xFF064E3B)), modifier = Modifier.weight(1f)) {
                            searchQuery = "Focus Study"
                        }
                    }
                }
            }
        } else {
            // Search Active View
            if (isSearching) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = NeonPurple, modifier = Modifier.size(32.dp), strokeWidth = 2.5.dp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Searching online catalogue...", color = Color(0xFFA1A1AA), fontSize = 13.sp)
                    }
                }
            } else if (searchError != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.CloudOff, contentDescription = null, tint = Color(0xFF71717A), modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(searchError ?: "Error searching", color = Color(0xFFA1A1AA), fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                val current = searchQuery
                                searchQuery = ""
                                searchQuery = current
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF181B1F)),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Retry", color = NeonPurple)
                        }
                    }
                }
            } else if (searchResults.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.SearchOff, contentDescription = null, tint = Color(0xFF71717A), modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("No results found for \"$searchQuery\"", color = Color(0xFFA1A1AA), fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Check your spelling or try another keyword", color = Color(0xFF71717A), fontSize = 12.sp)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 160.dp)
                ) {
                    item {
                        Text(
                            text = "${searchResults.size} results",
                            color = Color(0xFFA1A1AA),
                            fontSize = 12.5.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    items(searchResults) { track ->
                        SearchTrackRow(
                            track = track,
                            onClick = { onTrackClick(track, searchResults) },
                            onMoreClick = { selectedTrackForMenu = track }
                        )
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
                    TrackMenuOption(icon = Icons.AutoMirrored.Rounded.QueueMusic, label = "Play Next") {
                        PlaybackStateManager.playTracksNext(listOf(track))
                        selectedTrackForMenu = null
                    }
                    // Add to Queue
                    TrackMenuOption(icon = Icons.Rounded.PlaylistAdd, label = "Add to Queue") {
                        PlaybackStateManager.addTracksToQueue(listOf(track))
                        selectedTrackForMenu = null
                    }
                    // Add to Playlist
                    TrackMenuOption(icon = Icons.Rounded.Add, label = "Add to Playlist...") {
                        showAddToPlaylistDialog = true
                    }
                    // Favorite Toggle
                    val isFav = favoriteTrackIds.contains(track.id)
                    TrackMenuOption(
                        icon = if (isFav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        label = if (isFav) "Remove from Favorites" else "Add to Favorites"
                    ) {
                        PlaybackStateManager.toggleLikeTrack(track, isFav)
                        android.widget.Toast.makeText(
                            context,
                            if (!isFav) "Added to Favorites ❤️" else "Removed from Favorites",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
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
private fun SearchTrackRow(
    track: Track,
    onClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val painter = rememberAsyncImagePainter(
            model = track.artworkUriString ?: track.artworkResId,
            error = painterResource(id = if (track.artworkResId != 0) track.artworkResId else R.drawable.sruti_artwork_fallback),
            placeholder = painterResource(id = if (track.artworkResId != 0) track.artworkResId else R.drawable.sruti_artwork_fallback)
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
private fun MoodCard(
    title: String,
    gradientColors: List<Color>,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(72.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.linearGradient(gradientColors))
            .border(1.dp, Color(0x18FFFFFF), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun TrackMenuOption(
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
