package com.sruti.music.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.sruti.music.R
import com.sruti.music.components.AudioOutputSelectorDialog
import com.sruti.music.components.GlassCircleButton
import com.sruti.music.components.QueueDrawerSheet
import com.sruti.music.components.WaveformVisualizer
import com.sruti.music.components.getRouteIcon
import com.sruti.music.playback.AudioOutputManager
import com.sruti.music.playback.PlaybackStateManager
import com.sruti.music.playback.RepeatState
import com.sruti.music.theme.GlassWhite
import com.sruti.music.theme.LocalDynamicTheme
import com.sruti.music.theme.NeonPurple

@Composable
fun NowPlayingScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by PlaybackStateManager.state.collectAsState()
    val context = LocalContext.current
    val dynamicTheme = LocalDynamicTheme.current
    val audioOutputManager = remember { AudioOutputManager(context) }
    val audioOutputState by audioOutputManager.state.collectAsState()
    val downloadManager = remember { com.sruti.music.online.SrutiDownloadManager.getInstance(context) }
    val downloadsMap by downloadManager.downloads.collectAsState()
    val downloadRecord = downloadsMap[state.currentTrack.id]
    val isDownloaded = downloadManager.isTrackDownloaded(state.currentTrack.id)

    var showAudioOutputDialog by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var showRemoveDownloadConfirm by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            audioOutputManager.release()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // Top Navigation Bar: Back Chevron, "Now Playing" Title, Favorite Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            GlassCircleButton(
                icon = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                contentDescription = "Back",
                iconSize = 28.dp,
                onClick = onBackClick
            )

            Text(
                text = "Now Playing",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )

            GlassCircleButton(
                icon = if (state.isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = "Favorite",
                tint = if (state.isLiked) dynamicTheme.primary else Color.White,
                onClick = { PlaybackStateManager.toggleLike() }
            )
        }

        // Responsive Center Artwork Area (Scales smoothly to available height, never pushes controls off-screen)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .sizeIn(minWidth = 160.dp, minHeight = 160.dp, maxWidth = 240.dp, maxHeight = 240.dp)
                    .aspectRatio(1f),
                contentAlignment = Alignment.Center
            ) {
                // Ambient glow behind artwork adapting to song palette
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(26.dp))
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    dynamicTheme.primary.copy(alpha = 0.55f),
                                    dynamicTheme.secondary.copy(alpha = 0.25f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                // Rounded artwork
                Box(
                    modifier = Modifier
                        .fillMaxSize(0.96f)
                        .clip(RoundedCornerShape(22.dp))
                        .border(
                            width = 1.dp,
                            brush = Brush.verticalGradient(
                                listOf(
                                    Color(0x40FFFFFF),
                                    Color(0x10FFFFFF)
                                )
                            ),
                            shape = RoundedCornerShape(22.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    val painter = rememberAsyncImagePainter(
                        model = state.currentTrack.artworkUriString ?: state.currentTrack.artworkResId,
                        error = painterResource(id = R.drawable.sruti_artwork_fallback),
                        placeholder = painterResource(id = R.drawable.sruti_artwork_fallback)
                    )
                    Image(
                        painter = painter,
                        contentDescription = state.currentTrack.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }

        // Song Title & Artist
        Text(
            text = state.currentTrack.title,
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = state.currentTrack.artist,
            color = GlassWhite.copy(alpha = 0.70f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Waveform Visualizer & Progress
        val progress = if (state.totalDurationSeconds > 0) {
            (state.currentPositionSeconds.toFloat() / state.totalDurationSeconds.toFloat()).coerceIn(0f, 1f)
        } else 0f

        WaveformVisualizer(
            progress = progress,
            onSeek = { fraction ->
                val newSeconds = (fraction * state.totalDurationSeconds).toInt()
                PlaybackStateManager.seekTo(newSeconds)
            },
            modifier = Modifier.fillMaxWidth(),
            playedColor = dynamicTheme.primary
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Elapsed and Remaining Timestamps
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatTime(state.currentPositionSeconds),
                color = GlassWhite.copy(alpha = 0.70f),
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Normal
            )
            Text(
                text = formatTime(state.totalDurationSeconds),
                color = GlassWhite.copy(alpha = 0.70f),
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Normal
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Action Buttons Row: Audio Output, Download, Playlist, and Queue (Weighted to fit perfectly)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Audio Output Route Pill
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0x18FFFFFF))
                    .border(1.dp, Color(0x30FFFFFF), RoundedCornerShape(10.dp))
                    .clickable {
                        audioOutputManager.refresh()
                        showAudioOutputDialog = true
                    }
                    .padding(horizontal = 4.dp, vertical = 7.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = getRouteIcon(audioOutputState.currentRoute.type),
                        contentDescription = "Output Route",
                        tint = dynamicTheme.accent,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = when {
                            audioOutputState.currentRoute.name.contains("Speaker", ignoreCase = true) -> "Speaker"
                            audioOutputState.currentRoute.name.contains("Headphones", ignoreCase = true) -> "Headphones"
                            audioOutputState.currentRoute.name.contains("Bluetooth", ignoreCase = true) -> "Bluetooth"
                            else -> audioOutputState.currentRoute.name.take(8)
                        },
                        color = Color.White,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }

            // Download Button Pill
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isDownloaded) dynamicTheme.primary.copy(alpha = 0.22f) else Color(0x18FFFFFF))
                    .border(1.dp, if (isDownloaded) dynamicTheme.primary else Color(0x30FFFFFF), RoundedCornerShape(10.dp))
                    .clickable {
                        when {
                            isDownloaded -> {
                                showRemoveDownloadConfirm = true
                            }
                            downloadRecord?.status == com.sruti.music.online.DownloadStatus.DOWNLOADING -> {
                                downloadManager.cancelDownload(state.currentTrack.id)
                                android.widget.Toast.makeText(context, "Download cancelled", android.widget.Toast.LENGTH_SHORT).show()
                            }
                            else -> {
                                val streamUrl = state.currentTrack.mediaUriString
                                if (streamUrl.isNullOrBlank() || (!streamUrl.startsWith("http://") && !streamUrl.startsWith("https://"))) {
                                    android.widget.Toast.makeText(context, "Downloads aren't available for this source", android.widget.Toast.LENGTH_SHORT).show()
                                } else {
                                    downloadManager.downloadTrack(state.currentTrack)
                                        .onFailure {
                                            android.widget.Toast.makeText(context, it.message ?: "Download unavailable", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                }
                            }
                        }
                    }
                    .padding(horizontal = 4.dp, vertical = 7.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (downloadRecord?.status == com.sruti.music.online.DownloadStatus.DOWNLOADING) {
                        CircularProgressIndicator(
                            color = dynamicTheme.primary,
                            modifier = Modifier.size(11.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "${(downloadRecord.progress * 100).toInt()}%",
                            color = dynamicTheme.primary,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    } else if (isDownloaded) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = "Downloaded",
                            tint = dynamicTheme.primary,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "Offline",
                            color = dynamicTheme.primary,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = "Download",
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "Download",
                            color = Color.White,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }
            }

            // Add to Playlist Pill
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0x18FFFFFF))
                    .border(1.dp, Color(0x30FFFFFF), RoundedCornerShape(10.dp))
                    .clickable { showAddToPlaylistDialog = true }
                    .padding(horizontal = 4.dp, vertical = 7.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlaylistAdd,
                        contentDescription = "Add to Playlist",
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "Playlist",
                        color = Color.White,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }

            // Up Next Queue Button
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (state.isSmartShuffleEnabled) dynamicTheme.primary.copy(alpha = 0.22f) else Color(0x18FFFFFF))
                    .border(
                        1.dp,
                        if (state.isSmartShuffleEnabled) dynamicTheme.primary else Color(0x30FFFFFF),
                        RoundedCornerShape(10.dp)
                    )
                    .clickable { showQueueSheet = true }
                    .padding(horizontal = 4.dp, vertical = 7.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                        contentDescription = "Queue",
                        tint = if (state.isSmartShuffleEnabled) dynamicTheme.primary else Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "Queue (${state.queue.size})",
                        color = Color.White,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Playback Controls Row: Shuffle, Previous, Play/Pause, Next, Repeat
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Shuffle
            IconButton(onClick = { PlaybackStateManager.toggleShuffle() }) {
                Icon(
                    imageVector = Icons.Rounded.Shuffle,
                    contentDescription = "Shuffle",
                    tint = if (state.isShuffleEnabled) dynamicTheme.primary else GlassWhite.copy(alpha = 0.85f),
                    modifier = Modifier.size(24.dp)
                )
            }

            // Previous
            IconButton(onClick = { PlaybackStateManager.playPrevious() }) {
                Icon(
                    imageVector = Icons.Rounded.SkipPrevious,
                    contentDescription = "Previous",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }

            // Translucent Frosted Glass Play/Pause Button with Dynamic Theme Accent
            Box(
                modifier = Modifier
                    .size(66.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.radialGradient(
                            listOf(
                                dynamicTheme.primary.copy(alpha = 0.45f),
                                Color(0x28FFFFFF)
                            )
                        )
                    )
                    .border(
                        width = 1.5.dp,
                        brush = Brush.linearGradient(
                            listOf(
                                dynamicTheme.primary.copy(alpha = 0.85f),
                                Color(0x40FFFFFF)
                            )
                        ),
                        shape = CircleShape
                    )
                    .clickable { PlaybackStateManager.togglePlayPause() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (state.isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(34.dp)
                )
            }

            // Next
            IconButton(onClick = { PlaybackStateManager.playNext() }) {
                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = "Next",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }

            // Repeat (Cycles: OFF -> ALL -> ONE -> OFF)
            IconButton(onClick = { PlaybackStateManager.cycleRepeatMode() }) {
                Icon(
                    imageVector = when (state.repeatState) {
                        RepeatState.ONE -> Icons.Rounded.RepeatOne
                        RepeatState.ALL -> Icons.Rounded.Repeat
                        RepeatState.OFF -> Icons.Rounded.Repeat
                    },
                    contentDescription = "Repeat",
                    tint = if (state.repeatState != RepeatState.OFF) dynamicTheme.primary else GlassWhite.copy(alpha = 0.85f),
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
    }

    // Audio Output Dialog
    if (showAudioOutputDialog) {
        AudioOutputSelectorDialog(
            currentRoute = audioOutputState.currentRoute,
            availableRoutes = audioOutputState.availableRoutes,
            onRouteSelect = { selectedRoute ->
                audioOutputManager.selectRoute(selectedRoute)
                showAudioOutputDialog = false
            },
            onOpenSystemSwitcher = {
                audioOutputManager.openSystemOutputSwitcher()
                showAudioOutputDialog = false
            },
            onDismissRequest = { showAudioOutputDialog = false }
        )
    }

    // Queue Sheet
    if (showQueueSheet) {
        QueueDrawerSheet(
            queue = state.queue,
            currentTrack = state.currentTrack,
            currentIndex = state.currentQueueIndex,
            isSmartShuffle = state.isSmartShuffleEnabled,
            onTrackSelect = { track ->
                PlaybackStateManager.playTrack(track, state.queue)
            },
            onRemoveItem = { index ->
                PlaybackStateManager.removeFromQueue(index)
            },
            onToggleSmartShuffle = {
                PlaybackStateManager.toggleSmartShuffle()
            },
            onDismissRequest = { showQueueSheet = false }
        )
    }

    if (showAddToPlaylistDialog) {
        AddToPlaylistDialog(
            track = state.currentTrack,
            onDismiss = { showAddToPlaylistDialog = false }
        )
    }

    if (showRemoveDownloadConfirm) {
        AlertDialog(
            onDismissRequest = { showRemoveDownloadConfirm = false },
            title = { Text("Remove Download?", color = Color.White) },
            text = { Text("This will delete the offline audio file from your device.", color = GlassWhite.copy(0.7f)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        downloadManager.deleteDownloadedTrack(state.currentTrack.id)
                        showRemoveDownloadConfirm = false
                        android.widget.Toast.makeText(context, "Download removed", android.widget.Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Remove", color = Color(0xFFFF5252))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveDownloadConfirm = false }) {
                    Text("Keep", color = Color.White)
                }
            },
            containerColor = Color(0xFF1E1433),
            shape = RoundedCornerShape(20.dp)
        )
    }
}

private fun formatTime(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}
