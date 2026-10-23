package com.sruti.music.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.sruti.music.R
import com.sruti.music.auth.AccountProfileDialog
import com.sruti.music.auth.FirebaseAuthRepository
import com.sruti.music.components.AmbientBackground
import com.sruti.music.components.FloatingBottomNav
import com.sruti.music.components.NavigationTab
import com.sruti.music.data.LocalAudioRepository
import com.sruti.music.data.Track
import com.sruti.music.data.UserPreferencesRepository
import com.sruti.music.data.db.PlaylistEntity
import com.sruti.music.data.db.SrutiDatabase
import com.sruti.music.playback.PlaybackStateManager
import com.sruti.music.theme.LocalDynamicTheme
import com.sruti.music.theme.NeonPurple
import com.sruti.music.theme.PureBlack

@Composable
fun MainScreen(themeId: String = "pure_black") {
    val context = LocalContext.current
    val prefsRepo = remember { UserPreferencesRepository(context) }
    val authRepo = remember { FirebaseAuthRepository.getInstance(context) }
    val localRepo = remember { LocalAudioRepository(context) }
    val database = remember { SrutiDatabase.getDatabase(context) }

    val userProfile by authRepo.userProfile.collectAsState()
    val backgroundStyle by prefsRepo.backgroundStyle.collectAsState(initial = "solid_dark")
    val backgroundIntensity by prefsRepo.backgroundIntensity.collectAsState(initial = "balanced")

    var currentTab by remember { mutableStateOf(NavigationTab.HOME) }
    var previousTab by remember { mutableStateOf(NavigationTab.HOME) }
    var isNowPlayingOpen by remember { mutableStateOf(false) }
    var selectedPlaylist by remember { mutableStateOf<PlaylistEntity?>(null) }
    var isSpotifyImportOpen by remember { mutableStateOf(false) }
    var isAccountDialogOpen by remember { mutableStateOf(false) }

    val playbackState by PlaybackStateManager.state.collectAsState()

    // Counts for Account Profile Dialog
    var localCount by remember { mutableIntStateOf(0) }
    val favEntities by database.musicDao().getAllFavorites().collectAsState(initial = emptyList())
    val playlists by database.musicDao().getAllPlaylists().collectAsState(initial = emptyList())

    LaunchedEffect(Unit) {
        val tracks = localRepo.queryDeviceAudio()
        localCount = tracks.size
    }

    // 1. Back gesture from Now Playing
    BackHandler(enabled = isNowPlayingOpen) {
        isNowPlayingOpen = false
    }

    // 2. Back gesture from Spotify Import
    BackHandler(enabled = !isNowPlayingOpen && isSpotifyImportOpen) {
        isSpotifyImportOpen = false
    }

    // 3. Back gesture from Playlist Details
    BackHandler(enabled = !isNowPlayingOpen && !isSpotifyImportOpen && selectedPlaylist != null) {
        selectedPlaylist = null
    }

    // 4. Back gesture from other tabs: Navigate to previous tab or Home
    BackHandler(enabled = !isNowPlayingOpen && !isSpotifyImportOpen && selectedPlaylist == null && currentTab != NavigationTab.HOME) {
        currentTab = previousTab.takeIf { it != currentTab } ?: NavigationTab.HOME
        previousTab = NavigationTab.HOME
    }

    fun navigateToTab(tab: NavigationTab) {
        if (currentTab != tab) {
            previousTab = currentTab
            currentTab = tab
        }
    }

    // Single Authoritative Ambient Background for Entire App
    AmbientBackground(
        themeId = themeId,
        backgroundStyle = backgroundStyle,
        backgroundIntensity = backgroundIntensity,
        currentTrack = playbackState.currentTrack
    ) {
        if (isNowPlayingOpen) {
            NowPlayingScreen(
                onBackClick = { isNowPlayingOpen = false }
            )
        } else if (isSpotifyImportOpen) {
            SpotifyImportScreen(
                onBackClick = { isSpotifyImportOpen = false },
                onPlaylistCreated = { entity ->
                    isSpotifyImportOpen = false
                    selectedPlaylist = entity
                }
            )
        } else if (selectedPlaylist != null) {
            PlaylistDetailScreen(
                playlist = selectedPlaylist!!,
                onBackClick = { selectedPlaylist = null },
                onTrackSelected = { track, queue ->
                    PlaybackStateManager.playTrack(track, queue)
                    isNowPlayingOpen = true
                }
            )

            // Persistent Mini-Player on Playlist Detail screen
            if (playbackState.currentTrack.id.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding()
                        .padding(bottom = 14.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    MiniPlayer(
                        track = playbackState.currentTrack,
                        isPlaying = playbackState.isPlaying,
                        onPlayPause = { PlaybackStateManager.togglePlayPause() },
                        onNext = { PlaybackStateManager.skipToNext() },
                        onClick = { isNowPlayingOpen = true }
                    )
                }
            }
        } else {
            // Main Tab Content (4-Destination Architecture)
            AnimatedContent(
                targetState = currentTab,
                transitionSpec = {
                    fadeIn(animationSpec = tween(200)) togetherWith fadeOut(animationSpec = tween(200))
                },
                label = "tab_navigation"
            ) { tab ->
                when (tab) {
                    NavigationTab.HOME -> {
                        HomeScreen(
                            onCardClick = { card ->
                                if (card.tracks.isNotEmpty()) {
                                    val trackToPlay = card.tracks.first()
                                    PlaybackStateManager.playTrack(trackToPlay, card.tracks)
                                    isNowPlayingOpen = true
                                } else {
                                    navigateToTab(NavigationTab.LIBRARY)
                                }
                            },
                            onSearchClick = {
                                navigateToTab(NavigationTab.SEARCH)
                            },
                            onNavigateLibrary = {
                                navigateToTab(NavigationTab.LIBRARY)
                            },
                            onPlaylistClick = { playlist ->
                                selectedPlaylist = playlist
                            },
                            onSettingsClick = {
                                navigateToTab(NavigationTab.SETTINGS)
                            }
                        )
                    }
                    NavigationTab.SEARCH -> {
                        SearchScreen(
                            onTrackClick = { track, queue ->
                                PlaybackStateManager.playTrack(track, queue)
                                isNowPlayingOpen = true
                            },
                            onPlaylistClick = { playlist ->
                                selectedPlaylist = playlist
                            }
                        )
                    }
                    NavigationTab.CREATE -> {
                        CreateScreen(
                            onPlaylistClick = { playlist ->
                                selectedPlaylist = playlist
                            },
                            onImportSpotifyClick = {
                                isSpotifyImportOpen = true
                            },
                            onSearchClick = {
                                navigateToTab(NavigationTab.SEARCH)
                            }
                        )
                    }
                    NavigationTab.LIBRARY -> {
                        LibraryScreen(
                            onTrackClick = { track, queue ->
                                PlaybackStateManager.playTrack(track, queue)
                                isNowPlayingOpen = true
                            },
                            onPlaylistClick = { playlist ->
                                selectedPlaylist = playlist
                            },
                            onSearchClick = {
                                navigateToTab(NavigationTab.SEARCH)
                            },
                            onAccountClick = {
                                isAccountDialogOpen = true
                            },
                            onSettingsClick = {
                                navigateToTab(NavigationTab.SETTINGS)
                            }
                        )
                    }
                    NavigationTab.SETTINGS -> {
                        SettingsAppearanceScreen(
                            onBackClick = {
                                currentTab = NavigationTab.HOME
                            }
                        )
                    }
                }
            }

            // Persistent Mini-Player (Positioned immediately above navigation capsule, zero overlap)
            if (playbackState.currentTrack.id.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding()
                        .padding(bottom = 82.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    MiniPlayer(
                        track = playbackState.currentTrack,
                        isPlaying = playbackState.isPlaying,
                        onPlayPause = { PlaybackStateManager.togglePlayPause() },
                        onNext = { PlaybackStateManager.skipToNext() },
                        onClick = { isNowPlayingOpen = true }
                    )
                }
            }

            // Floating Bottom Navigation Capsule
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                FloatingBottomNav(
                    currentTab = currentTab,
                    onTabSelected = { navigateToTab(it) }
                )
            }
        }
    }

    if (isAccountDialogOpen) {
        AccountProfileDialog(
            userProfile = userProfile,
            deviceTracksCount = localCount,
            favoriteTracksCount = favEntities.size,
            playlistsCount = playlists.size,
            onOpenSettings = {
                isAccountDialogOpen = false
                navigateToTab(NavigationTab.SETTINGS)
            },
            onDismiss = { isAccountDialogOpen = false }
        )
    }
}

@Composable
private fun MiniPlayer(
    track: Track,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dynamicTheme = LocalDynamicTheme.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(60.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xF2120E1C))
            .border(
                width = 1.dp,
                color = dynamicTheme.primary.copy(alpha = 0.35f),
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
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
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, dynamicTheme.primary.copy(alpha = 0.45f), RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = track.artist,
                    color = Color(0xFFA1A1AA),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1
                )
            }

            // Play/Pause Action
            IconButton(
                onClick = onPlayPause,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = dynamicTheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Next Action
            IconButton(
                onClick = onNext,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = "Next",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
