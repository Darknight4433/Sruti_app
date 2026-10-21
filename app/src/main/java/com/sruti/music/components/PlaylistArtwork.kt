package com.sruti.music.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.sruti.music.data.db.PlaylistEntity
import com.sruti.music.theme.NeonPurple

/**
 * Single Authoritative Playlist Artwork Composable across Sruti.
 * 
 * Ensures consistent rendering across:
 * - Home Screen Playlist Cards
 * - Library / Music Screen Playlist Rows
 * - Playlist Details Screen Header
 * - Create / Select Playlist Dialogs
 * 
 * Never falls back to the currently playing track's artwork.
 * Uses a deliberate, deterministic branded glass fallback with the playlist's initial
 * letter when no custom artwork exists or when an image fails to load.
 */
@Composable
fun PlaylistArtwork(
    playlist: PlaylistEntity,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 14.dp,
    borderWidth: Dp = 1.dp,
    borderColor: Color = NeonPurple.copy(alpha = 0.5f),
    contentScale: ContentScale = ContentScale.Crop
) {
    PlaylistArtwork(
        artworkModel = playlist.artworkUri ?: playlist.artworkUrl,
        playlistName = playlist.name,
        modifier = modifier,
        cornerRadius = cornerRadius,
        borderWidth = borderWidth,
        borderColor = borderColor,
        contentScale = contentScale
    )
}

@Composable
fun PlaylistArtwork(
    artworkModel: Any?,
    playlistName: String = "",
    playlistTitle: String = playlistName,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 14.dp,
    borderWidth: Dp = 1.dp,
    borderColor: Color = NeonPurple.copy(alpha = 0.5f),
    contentScale: ContentScale = ContentScale.Crop
) {
    val effectiveName = if (playlistTitle.isNotBlank()) playlistTitle else playlistName
    val context = LocalContext.current
    val cleanModel = artworkModel?.let {
        if (it is String && it.isBlank()) null else it
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .border(borderWidth, borderColor, RoundedCornerShape(cornerRadius)),
        contentAlignment = Alignment.Center
    ) {
        if (cleanModel != null) {
            val painter = rememberAsyncImagePainter(
                model = ImageRequest.Builder(context)
                    .data(cleanModel)
                    .crossfade(true)
                    .build()
            )

            Image(
                painter = painter,
                contentDescription = effectiveName,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale
            )

            when (painter.state) {
                is AsyncImagePainter.State.Loading -> {
                    CircularProgressIndicator(
                        color = NeonPurple,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                }
                is AsyncImagePainter.State.Error, AsyncImagePainter.State.Empty -> {
                    PlaylistBrandedFallback(playlistName = effectiveName)
                }
                else -> {}
            }
        } else {
            PlaylistBrandedFallback(playlistName = effectiveName)
        }
    }
}

@Composable
fun PlaylistBrandedFallback(
    playlistName: String,
    modifier: Modifier = Modifier
) {
    val cleanName = playlistName.trim()
    val initial = cleanName.firstOrNull()?.uppercaseChar()?.toString() ?: "♪"

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF2E154F),
                        Color(0xFF140C28),
                        Color(0xFF3B1059)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            color = NeonPurple,
            fontSize = 24.sp,
            fontWeight = FontWeight.Black
        )
    }
}
