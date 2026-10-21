package com.sruti.music.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import coil.request.ImageRequest
import com.sruti.music.R
import com.sruti.music.auth.AccountProfileDialog
import com.sruti.music.auth.FirebaseAuthRepository
import com.sruti.music.components.PlaylistArtwork
import com.sruti.music.data.FeaturedCard
import com.sruti.music.data.LocalAudioRepository
import com.sruti.music.data.Track
import com.sruti.music.data.db.PlaylistEntity
import com.sruti.music.data.db.SrutiDatabase
import com.sruti.music.data.db.toTrack
import com.sruti.music.online.SaavnMusicSource
import com.sruti.music.online.SrutiDownloadManager
import com.sruti.music.playback.PlaybackStateManager
import com.sruti.music.theme.NeonPurple
import com.sruti.music.theme.PureBlack
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    onCardClick: (FeaturedCard) -> Unit,
    onSearchClick: () -> Unit,
    onNavigateLibrary: () -> Unit = {},
    onPlaylistClick: (PlaylistEntity) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val authRepo = remember { FirebaseAuthRepository.getInstance(context) }
    val localRepo = remember { LocalAudioRepository(context) }
    val database = remember { SrutiDatabase.getDatabase(context) }
    val downloadManager = remember { SrutiDownloadManager.getInstance(context) }

    val userProfile by authRepo.userProfile.collectAsState()
    val scrollState = rememberScrollState()

    var showProfileDialog by remember { mutableStateOf(false) }

    var deviceTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var downloadedTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    val playlists by database.musicDao().getAllPlaylists().collectAsState(initial = emptyList())
    val favoriteEntities by database.musicDao().getAllFavorites().collectAsState(initial = emptyList())
    val historyEntities by database.musicDao().getRecentHistory().collectAsState(initial = emptyList())

    // Convert history entities to tracks
    val recentTracks = remember(historyEntities) {
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

    // Load authentic local and downloaded tracks
    LaunchedEffect(Unit) {
        val devTracks = localRepo.queryDeviceAudio()
        deviceTracks = devTracks
        downloadedTracks = downloadManager.getDownloadedTracks()
    }

    // Made For You Mixes
    val madeForYouMixes = remember {
        listOf(
            MixItem("Chill Mix", "For late nights", listOf(Color(0xFF1E3A8A), Color(0xFF0F172A)), "Chill Relax"),
            MixItem("Focus Mix", "Stay in the zone", listOf(Color(0xFF065F46), Color(0xFF064E3B)), "Focus Study"),
            MixItem("Indie Mix", "Discover more", listOf(Color(0xFF7C2D12), Color(0xFF431407)), "Indie Acoustic"),
            MixItem("Energy Boost", "Power workout", listOf(Color(0xFF991B1B), Color(0xFF450A0A)), "Workout Energy")
        )
    }

    fun playMix(mix: MixItem) {
        scope.launch {
            val source = SaavnMusicSource()
            val result = source.search(mix.searchQuery).getOrDefault(emptyList()).map { it.toTrack() }
            if (result.isNotEmpty()) {
                PlaybackStateManager.playTrack(result.first(), result)
            } else {
                val fallback = (recentTracks + downloadedTracks + deviceTracks).distinctBy { it.id }
                if (fallback.isNotEmpty()) {
                    PlaybackStateManager.playTrack(fallback.first(), fallback)
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PureBlack)
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
            .verticalScroll(scrollState)
    ) {
        Spacer(modifier = Modifier.height(14.dp))

        // Top Navigation Area matching Phone 1 reference
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Sruti",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    text = "YOUR MUSIC, YOUR WAY",
                    color = Color(0xFFA1A1AA),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Search Action
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

                // Account Action
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF111315))
                        .border(1.dp, if (userProfile.isAuthenticated) NeonPurple else Color(0x18FFFFFF), CircleShape)
                        .clickable { showProfileDialog = true },
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
                            contentDescription = "Sign In",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Liked Songs Quick Access Card
        if (favoriteEntities.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFF3B125E), Color(0xFF1B112B))
                        )
                    )
                    .border(1.dp, NeonPurple.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                    .clickable {
                        val favTracks = favoriteEntities.map { it.toTrack() }
                        if (favTracks.isNotEmpty()) {
                            PlaybackStateManager.playTrack(favTracks.first(), favTracks)
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Brush.linearGradient(listOf(Color(0xFFBA2DF7), Color(0xFF7B1FA2)))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Favorite,
                            contentDescription = "Liked Songs",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Liked Songs",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${favoriteEntities.size} tracks • Tap to play",
                            color = Color(0xFFA1A1AA),
                            fontSize = 12.sp
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(NeonPurple),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }

        // ==========================================
        // SECTION 1: Continue Listening
        // ==========================================
        val continueCandidates = remember(recentTracks, favoriteEntities, downloadedTracks, deviceTracks) {
            val list = (recentTracks + favoriteEntities.map { it.toTrack() } + downloadedTracks + deviceTracks)
                .distinctBy { it.id }
            list.take(8)
        }

        if (continueCandidates.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Continue listening",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "See All",
                    color = NeonPurple,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { onNavigateLibrary() }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(continueCandidates) { track ->
                    HomeSquareTrackCard(
                        track = track,
                        onClick = { PlaybackStateManager.playTrack(track, continueCandidates) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // ==========================================
        // SECTION 2: Made For You
        // ==========================================
        Text(
            text = "Made for you",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(madeForYouMixes) { mix ->
                MixCard(
                    mix = mix,
                    onClick = { playMix(mix) }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ==========================================
        // SECTION 3: Recently Played
        // ==========================================
        if (recentTracks.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recently played",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "See All",
                    color = NeonPurple,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { onNavigateLibrary() }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(recentTracks.take(10)) { track ->
                    HomeSquareTrackCard(
                        track = track,
                        onClick = { PlaybackStateManager.playTrack(track, recentTracks) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // ==========================================
        // SECTION 4: Your Playlists
        // ==========================================
        if (playlists.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Your Playlists",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "See All",
                    color = NeonPurple,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { onNavigateLibrary() }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(playlists) { playlist ->
                    HomePlaylistCard(
                        playlist = playlist,
                        onClick = { onPlaylistClick(playlist) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Bottom space for mini-player and bottom navigation
        Spacer(modifier = Modifier.height(160.dp))
    }

    if (showProfileDialog) {
        AccountProfileDialog(
            userProfile = userProfile,
            deviceTracksCount = deviceTracks.size,
            favoriteTracksCount = favoriteEntities.size,
            playlistsCount = playlists.size,
            onDismiss = { showProfileDialog = false }
        )
    }
}

private data class MixItem(
    val title: String,
    val subtitle: String,
    val gradientColors: List<Color>,
    val searchQuery: String
)

@Composable
private fun MixCard(
    mix: MixItem,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(125.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.linearGradient(mix.gradientColors))
            .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        contentAlignment = Alignment.BottomStart
    ) {
        Column {
            Text(
                text = mix.title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = mix.subtitle,
                color = Color(0xFFA1A1AA),
                fontSize = 11.5.sp,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun HomeSquareTrackCard(
    track: Track,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(120.dp)
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
                .size(120.dp)
                .clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = track.title,
            color = Color.White,
            fontSize = 13.5.sp,
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
private fun HomePlaylistCard(
    playlist: PlaylistEntity,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(120.dp)
            .clickable(onClick = onClick)
    ) {
        PlaylistArtwork(
            playlist = playlist,
            modifier = Modifier
                .size(120.dp)
                .clip(RoundedCornerShape(12.dp))
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = playlist.name,
            color = Color.White,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
        Text(
            text = "Playlist",
            color = Color(0xFFA1A1AA),
            fontSize = 11.5.sp,
            maxLines = 1
        )
    }
}
