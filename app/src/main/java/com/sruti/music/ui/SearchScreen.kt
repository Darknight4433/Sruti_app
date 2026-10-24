package com.sruti.music.ui

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
import com.sruti.music.data.UserMusicTaste
import com.sruti.music.data.UserTasteDetector
import com.sruti.music.data.db.PlaylistEntity
import com.sruti.music.data.db.SrutiDatabase
import com.sruti.music.online.MusicSourceRegistry
import com.sruti.music.online.SaavnMusicSource
import com.sruti.music.playback.PlaybackStateManager
import com.sruti.music.theme.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class SearchFilterCategory(
    val id: String,
    val label: String,
    val defaultQuery: String,
    val title: String,
    val subtitle: String,
    val searchTag: String = ""
)

@Composable
fun SearchScreen(
    onTrackClick: (Track, List<Track>) -> Unit,
    onPlaylistClick: (PlaylistEntity) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val localRepo = remember { LocalAudioRepository(context) }
    val database = remember { SrutiDatabase.getDatabase(context) }
    val dynamicTheme = LocalDynamicTheme.current

    // Observe user listening data from Room DB
    val favoriteEntities by database.musicDao().getAllFavorites().collectAsState(initial = emptyList())
    val historyEntities by database.musicDao().getRecentHistory().collectAsState(initial = emptyList())
    val playlistSongs by database.musicDao().getAllPlaylistSongs().collectAsState(initial = emptyList())
    val favoriteTrackIds = remember(favoriteEntities) { favoriteEntities.map { it.trackId }.toSet() }

    // Detect user taste dynamically based on listening history & library
    val userTaste = remember(historyEntities, favoriteEntities, playlistSongs) {
        UserTasteDetector.detectTaste(historyEntities, favoriteEntities, playlistSongs)
    }

    // Genre & category filter options
    val categories = remember(userTaste) {
        listOf(
            SearchFilterCategory(
                id = "suggested",
                label = "Suggested ✨",
                defaultQuery = userTaste.searchQuery,
                title = userTaste.titleLabel,
                subtitle = userTaste.subtitle,
                searchTag = ""
            ),
            SearchFilterCategory(
                id = "devotional",
                label = "Devotional 🙏",
                defaultQuery = "Top Devotional Bhajans",
                title = "Devotional & Spiritual 🙏",
                subtitle = "Peaceful bhajans, aarties, and sacred mantras",
                searchTag = "devotional"
            ),
            SearchFilterCategory(
                id = "jpop",
                label = "J-Pop 🌸",
                defaultQuery = "J-Pop Hits",
                title = "J-Pop & Anime 🌸",
                subtitle = "Trending Japanese pop & anime soundtracks",
                searchTag = "j-pop"
            ),
            SearchFilterCategory(
                id = "kpop",
                label = "K-Pop ✨",
                defaultQuery = "K-Pop Top Hits",
                title = "K-Pop Hits ✨",
                subtitle = "Global Korean pop chartbusters",
                searchTag = "k-pop"
            ),
            SearchFilterCategory(
                id = "bollywood",
                label = "Bollywood 🎬",
                defaultQuery = "Bollywood Top Hits",
                title = "Bollywood Hits 🎬",
                subtitle = "Chart-topping Bollywood & Hindi melodies",
                searchTag = "bollywood"
            ),
            SearchFilterCategory(
                id = "lofi",
                label = "Lo-Fi ☕",
                defaultQuery = "Lo-Fi Chill Beats",
                title = "Lo-Fi & Chill ☕",
                subtitle = "Relaxing beats for focus, study, and calm",
                searchTag = "lo-fi"
            ),
            SearchFilterCategory(
                id = "carnatic",
                label = "Classical 🪕",
                defaultQuery = "Carnatic Classical Hits",
                title = "Classical & Carnatic 🪕",
                subtitle = "Soulful classical ragas and instrumental music",
                searchTag = "classical"
            ),
            SearchFilterCategory(
                id = "south",
                label = "South Indian 🌴",
                defaultQuery = "South Indian Top Hits",
                title = "South Indian Hits 🌴",
                subtitle = "Tamil, Telugu, Malayalam & Kannada melodies",
                searchTag = "south"
            ),
            SearchFilterCategory(
                id = "punjabi",
                label = "Punjabi 🪘",
                defaultQuery = "Punjabi Top Hits",
                title = "Punjabi Hits 🪘",
                subtitle = "High-energy Punjabi chartbusters",
                searchTag = "punjabi"
            ),
            SearchFilterCategory(
                id = "pop",
                label = "Pop 🌟",
                defaultQuery = "Global Pop Hits",
                title = "Global Pop 🌟",
                subtitle = "International hits and trending pop",
                searchTag = "pop"
            ),
            SearchFilterCategory(
                id = "rock",
                label = "Rock ⚡",
                defaultQuery = "Classic Rock Hits",
                title = "Rock & Metal ⚡",
                subtitle = "Electrifying rock, alternative, and metal classics",
                searchTag = "rock"
            ),
            SearchFilterCategory(
                id = "device",
                label = "Device Music 📱",
                defaultQuery = "",
                title = "Device Audio 📱",
                subtitle = "Offline audio tracks saved on your phone",
                searchTag = ""
            )
        )
    }

    var selectedCategoryId by remember { mutableStateOf("suggested") }
    val activeCategory = remember(selectedCategoryId, categories) {
        categories.find { it.id == selectedCategoryId } ?: categories.first()
    }

    var searchQuery by remember { mutableStateOf("") }
    var selectedTypeFilter by remember { mutableStateOf("All") }
    val typeFilters = listOf("All", "Songs", "Artists", "Albums")

    var isSearching by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<Track>>(emptyList()) }
    var searchError by remember { mutableStateOf<String?>(null) }

    var categoryTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var isCategoryLoading by remember { mutableStateOf(true) }

    var selectedTrackForMenu by remember { mutableStateOf<Track?>(null) }
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }

    // Load category / suggested tracks when active category or taste query changes
    LaunchedEffect(selectedCategoryId, activeCategory.defaultQuery) {
        if (activeCategory.id == "device") {
            isCategoryLoading = true
            val local = runCatching { localRepo.queryDeviceAudio() }.getOrDefault(emptyList())
            categoryTracks = local
            isCategoryLoading = false
        } else {
            isCategoryLoading = true
            try {
                val source = SaavnMusicSource()
                val queryToRun = activeCategory.defaultQuery
                val result = source.search(queryToRun).getOrDefault(emptyList()).map { it.toTrack() }
                if (result.isNotEmpty()) {
                    categoryTracks = result.take(16)
                } else {
                    val fallback = source.search("Top Songs").getOrDefault(emptyList()).map { it.toTrack() }
                    categoryTracks = fallback.take(16)
                }
            } catch (e: Exception) {
                categoryTracks = emptyList()
            } finally {
                isCategoryLoading = false
            }
        }
    }

    // Debounced search when search query or filter changes
    var searchJob by remember { mutableStateOf<Job?>(null) }
    LaunchedEffect(searchQuery, selectedCategoryId, selectedTypeFilter) {
        val query = searchQuery.trim()
        if (query.isBlank()) {
            searchResults = emptyList()
            isSearching = false
            searchError = null
            return@LaunchedEffect
        }

        searchJob?.cancel()
        searchJob = scope.launch {
            delay(300)
            isSearching = true
            searchError = null

            try {
                if (selectedCategoryId == "device") {
                    val local = localRepo.queryDeviceAudio()
                    searchResults = local.filter {
                        it.title.contains(query, ignoreCase = true) ||
                        it.artist.contains(query, ignoreCase = true) ||
                        it.album.contains(query, ignoreCase = true)
                    }
                } else {
                    // Enrich search query with active category context if appropriate
                    val effectiveQuery = if (activeCategory.searchTag.isNotEmpty() &&
                        !query.contains(activeCategory.searchTag, ignoreCase = true)
                    ) {
                        "$query ${activeCategory.searchTag}"
                    } else {
                        query
                    }

                    val online = MusicSourceRegistry.searchAllSources(effectiveQuery)
                    var mapped = online.map { it.toTrack() }

                    // If enriched query returned few items, also search raw query
                    if (mapped.size < 4 && effectiveQuery != query) {
                        val rawResults = MusicSourceRegistry.searchAllSources(query).map { it.toTrack() }
                        mapped = (mapped + rawResults).distinctBy { it.id }
                    }

                    // Also search local device files
                    val local = runCatching { localRepo.queryDeviceAudio() }.getOrDefault(emptyList())
                    val matchingLocal = local.filter {
                        it.title.contains(query, ignoreCase = true) ||
                        it.artist.contains(query, ignoreCase = true)
                    }
                    val combined = (mapped + matchingLocal).distinctBy { it.id }

                    // Apply secondary type filter
                    searchResults = when (selectedTypeFilter) {
                        "Songs" -> combined
                        "Artists" -> combined.filter { it.artist.contains(query, ignoreCase = true) }
                        "Albums" -> combined.filter { it.album.contains(query, ignoreCase = true) }
                        else -> combined
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
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(14.dp))

        // Title Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Search",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    text = "DISCOVER & FILTER GENRES",
                    color = Color(0xFFA1A1AA),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
            }
        }

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
                            text = if (activeCategory.id == "device") "Search offline device audio..." else "Search ${activeCategory.label.replace("✨", "").replace("🙏", "").replace("🌸", "").trim()} songs, artists...",
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
                        cursorBrush = SolidColor(dynamicTheme.primary),
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

        Spacer(modifier = Modifier.height(12.dp))

        // Genre / Category Filter Chips Row
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(categories) { category ->
                val isSelected = selectedCategoryId == category.id
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isSelected) dynamicTheme.primary else Color(0xFF181B1F))
                        .border(
                            width = 1.dp,
                            color = if (isSelected) dynamicTheme.primary else Color(0x1AFFFFFF),
                            shape = RoundedCornerShape(20.dp)
                        )
                        .clickable { selectedCategoryId = category.id }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = category.label,
                        color = if (isSelected) Color.Black else Color(0xFFA1A1AA),
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        // Secondary Type Filter Chips (when search is active)
        if (searchQuery.isNotBlank() && selectedCategoryId != "device") {
            Spacer(modifier = Modifier.height(10.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(typeFilters) { type ->
                    val isSelected = selectedTypeFilter == type
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isSelected) Color(0x28FFFFFF) else Color(0xFF111315))
                            .border(
                                width = 1.dp,
                                color = if (isSelected) Color(0x55FFFFFF) else Color(0x10FFFFFF),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable { selectedTypeFilter = type }
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = type,
                            color = if (isSelected) Color.White else Color(0xFF71717A),
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Main Search Content
        if (searchQuery.isBlank()) {
            // Default view: Dynamic Suggested / Category Content + Explore Moods
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 160.dp)
            ) {
                // Section Header: Dynamic Taste Title & Play All Button
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = activeCategory.title,
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = activeCategory.subtitle,
                                color = Color(0xFF71717A),
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }

                        if (categoryTracks.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(dynamicTheme.primary.copy(alpha = 0.15f))
                                    .border(1.dp, dynamicTheme.primary.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                                    .clickable {
                                        onTrackClick(categoryTracks.first(), categoryTracks)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Rounded.PlayArrow,
                                        contentDescription = "Play All",
                                        tint = dynamicTheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Play All",
                                        color = dynamicTheme.primary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                if (isCategoryLoading) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = dynamicTheme.primary,
                                modifier = Modifier.size(26.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    }
                } else if (categoryTracks.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (activeCategory.id == "device") "No device audio found. Add songs to your device storage." else "No songs found for this category. Check your internet connection.",
                                color = Color(0xFF71717A),
                                fontSize = 13.sp
                            )
                        }
                    }
                } else {
                    items(categoryTracks) { track ->
                        SearchTrackRow(
                            track = track,
                            onClick = { onTrackClick(track, categoryTracks) },
                            onMoreClick = { selectedTrackForMenu = track }
                        )
                    }
                }

                // Explore Genres & Moods Grid
                item {
                    Spacer(modifier = Modifier.height(28.dp))
                    Text(
                        text = "Explore Genres & Moods",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tap to switch instant suggestions",
                        color = Color(0xFF71717A),
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    // Row 1: Devotional & J-Pop
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        GenreMoodCard(
                            title = "Devotional 🙏",
                            subtitle = "Mantras & Bhajans",
                            gradientColors = listOf(Color(0xFF854D0E), Color(0xFF451A03)),
                            modifier = Modifier.weight(1f)
                        ) {
                            selectedCategoryId = "devotional"
                        }
                        GenreMoodCard(
                            title = "J-Pop 🌸",
                            subtitle = "Anime & Japanese Hits",
                            gradientColors = listOf(Color(0xFF9D174D), Color(0xFF4C0519)),
                            modifier = Modifier.weight(1f)
                        ) {
                            selectedCategoryId = "jpop"
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Row 2: K-Pop & Bollywood
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        GenreMoodCard(
                            title = "K-Pop ✨",
                            subtitle = "Korean Pop Anthems",
                            gradientColors = listOf(Color(0xFF6B21A8), Color(0xFF2E1065)),
                            modifier = Modifier.weight(1f)
                        ) {
                            selectedCategoryId = "kpop"
                        }
                        GenreMoodCard(
                            title = "Bollywood 🎬",
                            subtitle = "Hindi Melodies & Hits",
                            gradientColors = listOf(Color(0xFF991B1B), Color(0xFF450A0A)),
                            modifier = Modifier.weight(1f)
                        ) {
                            selectedCategoryId = "bollywood"
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Row 3: Lo-Fi & Classical
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        GenreMoodCard(
                            title = "Lo-Fi ☕",
                            subtitle = "Chill, Study & Relax",
                            gradientColors = listOf(Color(0xFF1E3A8A), Color(0xFF0F172A)),
                            modifier = Modifier.weight(1f)
                        ) {
                            selectedCategoryId = "lofi"
                        }
                        GenreMoodCard(
                            title = "Classical 🪕",
                            subtitle = "Carnatic & Instrumental",
                            gradientColors = listOf(Color(0xFF065F46), Color(0xFF022C22)),
                            modifier = Modifier.weight(1f)
                        ) {
                            selectedCategoryId = "carnatic"
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
                        CircularProgressIndicator(
                            color = dynamicTheme.primary,
                            modifier = Modifier.size(32.dp),
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Searching catalogue...",
                            color = Color(0xFFA1A1AA),
                            fontSize = 13.sp
                        )
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
                        Icon(
                            Icons.Rounded.CloudOff,
                            contentDescription = null,
                            tint = Color(0xFF71717A),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = searchError ?: "Error searching",
                            color = Color(0xFFA1A1AA),
                            fontSize = 13.sp
                        )
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
                            Text("Retry", color = dynamicTheme.primary)
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
                        Icon(
                            Icons.Rounded.SearchOff,
                            contentDescription = null,
                            tint = Color(0xFF71717A),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "No results found for \"$searchQuery\"",
                            color = Color(0xFFA1A1AA),
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Check your spelling or tap another category chip",
                            color = Color(0xFF71717A),
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 160.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${searchResults.size} results",
                                color = Color(0xFFA1A1AA),
                                fontSize = 12.5.sp
                            )
                            if (activeCategory.id != "suggested") {
                                Text(
                                    text = "Filtered by ${activeCategory.label.trim()}",
                                    color = dynamicTheme.primary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
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
                Text(
                    text = track.title,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
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
                    Text("Close", color = dynamicTheme.primary)
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
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp, horizontal = 4.dp),
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
private fun GenreMoodCard(
    title: String,
    subtitle: String,
    gradientColors: List<Color>,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(72.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.linearGradient(gradientColors))
            .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Column(verticalArrangement = Arrangement.Center) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 11.sp,
                maxLines = 1
            )
        }
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

