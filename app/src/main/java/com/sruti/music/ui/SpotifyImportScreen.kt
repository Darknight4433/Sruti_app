package com.sruti.music.ui

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.sruti.music.R
import com.sruti.music.data.db.PlaylistEntity
import com.sruti.music.data.db.SrutiDatabase
import com.sruti.music.online.SpotifyImportResult
import com.sruti.music.online.SpotifyPlaylistImporter
import com.sruti.music.theme.NeonPurple
import com.sruti.music.theme.PureBlack
import kotlinx.coroutines.launch

@Composable
fun SpotifyImportScreen(
    onBackClick: () -> Unit,
    onPlaylistCreated: (PlaylistEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val importer = remember { SpotifyPlaylistImporter(context) }
    val database = remember { SrutiDatabase.getDatabase(context) }

    var playlistUrl by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var progressText by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var importResult by remember { mutableStateOf<SpotifyImportResult?>(null) }

    fun pasteFromClipboard() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val text = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()
        if (!text.isNullOrBlank()) {
            playlistUrl = text.trim()
        } else {
            Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
        }
    }

    fun startImport() {
        val url = playlistUrl.trim()
        if (url.isBlank()) {
            errorMessage = "Please enter or paste a valid Spotify playlist link."
            return
        }

        isLoading = true
        progressText = "Connecting to Spotify..."
        errorMessage = null
        importResult = null

        scope.launch {
            val result = importer.importPlaylist(url) { loaded, total ->
                progressText = if (total > 0 && total != Int.MAX_VALUE) {
                    "Importing tracks: $loaded / $total"
                } else {
                    "Imported $loaded tracks..."
                }
            }
            isLoading = false
            result.onSuccess { res ->
                importResult = res
            }.onFailure { e ->
                errorMessage = e.localizedMessage ?: "Failed to import playlist. Ensure it is public."
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 18.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // Top Navigation Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF181B1F))
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            Text(
                text = "Import from Spotify",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            )

            // Spotify Brand Icon
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1DB954)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = "Spotify",
                    tint = Color.Black,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Instructions Card matching Phone 8 reference
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF111315))
                .border(1.dp, Color(0x18FFFFFF), RoundedCornerShape(16.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            InstructionStep(
                stepNumber = "1",
                title = "Open Spotify",
                description = "Go to the playlist you want to import."
            )
            InstructionStep(
                stepNumber = "2",
                title = "Copy link",
                description = "Tap ••• and select \"Copy link\"."
            )
            InstructionStep(
                stepNumber = "3",
                title = "Paste below",
                description = "We'll fetch the tracks and match them."
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // URL Input Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF111315))
                .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Link,
                    contentDescription = "Link",
                    tint = Color(0xFFA1A1AA),
                    modifier = Modifier.size(22.dp)
                )

                Spacer(modifier = Modifier.width(10.dp))

                Box(modifier = Modifier.weight(1f)) {
                    if (playlistUrl.isEmpty()) {
                        Text(
                            text = "Paste Spotify playlist link here",
                            color = Color(0xFF71717A),
                            fontSize = 13.5.sp
                        )
                    }
                    BasicTextField(
                        value = playlistUrl,
                        onValueChange = {
                            playlistUrl = it
                            errorMessage = null
                        },
                        textStyle = TextStyle(
                            color = Color.White,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Normal
                        ),
                        cursorBrush = SolidColor(NeonPurple),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (playlistUrl.isEmpty()) {
                    TextButton(
                        onClick = { pasteFromClipboard() },
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text("Paste", color = NeonPurple, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    IconButton(
                        onClick = { playlistUrl = "" },
                        modifier = Modifier.size(28.dp)
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

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = errorMessage ?: "",
                color = Color(0xFFFF5252),
                fontSize = 12.5.sp,
                lineHeight = 16.sp
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Import Button
        Button(
            onClick = { startImport() },
            colors = ButtonDefaults.buttonColors(containerColor = NeonPurple),
            shape = RoundedCornerShape(26.dp),
            enabled = !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = progressText ?: "Fetching tracks...",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            } else {
                Text("Import Playlist", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }

        // Result Card if imported successfully
        if (importResult != null) {
            val result = importResult!!
            Spacer(modifier = Modifier.height(20.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF111315))
                    .border(1.dp, Color(0x40A855F7), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val painter = rememberAsyncImagePainter(
                        model = result.artworkUrl,
                        error = painterResource(id = R.drawable.sruti_artwork_fallback),
                        placeholder = painterResource(id = R.drawable.sruti_artwork_fallback)
                    )
                    Image(
                        painter = painter,
                        contentDescription = result.playlistName,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Crop
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = result.playlistName,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${result.tracks.size} tracks imported to Room",
                            color = Color(0xFFA1A1AA),
                            fontSize = 12.5.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Stats breakdown
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ImportBadge(label = "Total Tracks", count = "${result.tracks.size}", color = Color.White, modifier = Modifier.weight(1f))
                    ImportBadge(label = "Saved in Library", count = "Saved", color = NeonPurple, modifier = Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        scope.launch {
                            val entity = database.musicDao().getPlaylistById(result.playlistId)
                            if (entity != null) {
                                onPlaylistCreated(entity)
                            } else {
                                onBackClick()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF181B1F)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Open Imported Playlist", color = NeonPurple, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Honest Footer Note matching Reference Phone 8
        Text(
            text = "Note: We import playlist metadata and try to match tracks with available sources. Not all tracks may be playable.",
            color = Color(0xFF71717A),
            fontSize = 11.5.sp,
            lineHeight = 15.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(40.dp))
    }
}

@Composable
private fun InstructionStep(
    stepNumber: String,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF181B1F))
                .border(1.dp, Color(0x18FFFFFF), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stepNumber,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column {
            Text(
                text = title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                color = Color(0xFFA1A1AA),
                fontSize = 12.5.sp
            )
        }
    }
}

@Composable
private fun ImportBadge(
    label: String,
    count: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF181B1F))
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = count, color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(1.dp))
            Text(text = label, color = Color(0xFF71717A), fontSize = 10.5.sp)
        }
    }
}
