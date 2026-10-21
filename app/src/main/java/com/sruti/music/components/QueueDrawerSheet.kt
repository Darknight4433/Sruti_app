package com.sruti.music.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.rememberAsyncImagePainter
import com.sruti.music.data.Track
import com.sruti.music.playback.PlaybackStateManager
import com.sruti.music.theme.GlassWhite
import com.sruti.music.theme.NeonPurple

@Composable
fun QueueDrawerSheet(
    queue: List<Track>,
    currentTrack: Track,
    currentIndex: Int,
    isSmartShuffle: Boolean,
    onTrackSelect: (Track) -> Unit,
    onRemoveItem: (Int) -> Unit,
    onToggleSmartShuffle: () -> Unit,
    onDismissRequest: () -> Unit
) {
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismissRequest) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .clip(RoundedCornerShape(26.dp))
                .background(Color(0xF8111315))
                .border(
                    width = 1.dp,
                    color = Color(0x22FFFFFF),
                    shape = RoundedCornerShape(26.dp)
                )
                .padding(20.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Playback Queue",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${queue.size} songs in queue",
                            color = GlassWhite.copy(alpha = 0.7f),
                            fontSize = 12.sp
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Smart Shuffle button
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSmartShuffle) Color(0x35B625FF) else Color(0x18FFFFFF))
                                .border(
                                    1.dp,
                                    if (isSmartShuffle) NeonPurple else Color(0x28FFFFFF),
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { onToggleSmartShuffle() }
                                .padding(horizontal = 9.dp, vertical = 5.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.AutoAwesome,
                                    contentDescription = "Smart Shuffle",
                                    tint = if (isSmartShuffle) NeonPurple else Color.White,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isSmartShuffle) "Smart On" else "Smart",
                                    color = if (isSmartShuffle) NeonPurple else Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        IconButton(onClick = onDismissRequest, modifier = Modifier.size(28.dp)) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Close",
                                tint = GlassWhite.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Now Playing Track Highlight
                Text(
                    text = "NOW PLAYING",
                    color = NeonPurple,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF181B1F))
                        .border(1.dp, NeonPurple.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val painter = rememberAsyncImagePainter(
                        model = currentTrack.artworkUriString ?: currentTrack.artworkResId,
                        error = painterResource(id = currentTrack.artworkResId),
                        placeholder = painterResource(id = currentTrack.artworkResId)
                    )
                    Image(
                        painter = painter,
                        contentDescription = currentTrack.title,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Crop
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentTrack.title,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            text = currentTrack.artist,
                            color = GlassWhite.copy(alpha = 0.75f),
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }

                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = "Playing",
                        tint = NeonPurple,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Up Next Header with Clear Upcoming
                val upcomingTracks = remember(queue, currentIndex) {
                    if (currentIndex + 1 < queue.size) queue.subList(currentIndex + 1, queue.size) else emptyList()
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "UP NEXT (${upcomingTracks.size})",
                        color = GlassWhite.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    if (upcomingTracks.isNotEmpty()) {
                        Text(
                            text = "Clear Upcoming",
                            color = Color(0xFFFF5252),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { showClearConfirmDialog = true }
                                .padding(4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (upcomingTracks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No upcoming tracks in queue.\nAdd tracks from any playlist, search or library!",
                            color = GlassWhite.copy(alpha = 0.5f),
                            fontSize = 12.5.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(upcomingTracks, key = { relIdx, item -> "${item.id}_${currentIndex + 1 + relIdx}" }) { relIdx, track ->
                            val actualIndex = currentIndex + 1 + relIdx

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0x10FFFFFF))
                                    .border(1.dp, Color(0x18FFFFFF), RoundedCornerShape(14.dp))
                                    .clickable { onTrackSelect(track) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Order number
                                Text(
                                    text = "${relIdx + 1}",
                                    color = GlassWhite.copy(alpha = 0.5f),
                                    fontSize = 11.sp,
                                    modifier = Modifier.width(20.dp)
                                )

                                // Artwork
                                val painter = rememberAsyncImagePainter(
                                    model = track.artworkUriString ?: track.artworkResId,
                                    error = painterResource(id = track.artworkResId),
                                    placeholder = painterResource(id = track.artworkResId)
                                )
                                Image(
                                    painter = painter,
                                    contentDescription = track.title,
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )

                                Spacer(modifier = Modifier.width(10.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = track.title,
                                        color = Color.White,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = track.artist,
                                        color = GlassWhite.copy(alpha = 0.65f),
                                        fontSize = 11.5.sp,
                                        maxLines = 1
                                    )
                                }

                                // Reordering controls: Move Up
                                if (relIdx > 0) {
                                    IconButton(
                                        onClick = { PlaybackStateManager.moveQueueItem(actualIndex, actualIndex - 1) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.ArrowUpward,
                                            contentDescription = "Move Up",
                                            tint = NeonPurple,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }

                                // Move Down
                                if (relIdx < upcomingTracks.lastIndex) {
                                    IconButton(
                                        onClick = { PlaybackStateManager.moveQueueItem(actualIndex, actualIndex + 1) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.ArrowDownward,
                                            contentDescription = "Move Down",
                                            tint = NeonPurple,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }

                                // Remove
                                IconButton(
                                    onClick = { onRemoveItem(actualIndex) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Remove",
                                        tint = GlassWhite.copy(alpha = 0.45f),
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text("Clear Upcoming Queue?", color = Color.White) },
            text = { Text("The currently playing song will keep playing, and all upcoming songs will be cleared.", color = GlassWhite.copy(0.7f)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        PlaybackStateManager.clearUpcomingQueue()
                        showClearConfirmDialog = false
                    }
                ) {
                    Text("Clear All", color = Color(0xFFFF5252))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("Cancel", color = Color.White)
                }
            },
            containerColor = Color(0xFF1E1433),
            shape = RoundedCornerShape(20.dp)
        )
    }
}
