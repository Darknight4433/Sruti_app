package com.sruti.music.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.luminance
import com.sruti.music.playback.PlaybackStateManager
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import coil.compose.AsyncImage
import com.sruti.music.R
import com.sruti.music.auth.AccountProfileDialog
import com.sruti.music.components.GlassCircleButton
import com.sruti.music.theme.GlassWhite
import com.sruti.music.theme.LocalDynamicTheme
import com.sruti.music.theme.NeonPurple

data class ThemeItem(
    val id: String,
    val name: String,
    val description: String,
    val primaryColor: Color,
    val secondaryColor: Color
)

data class BackgroundStyleOption(
    val id: String,
    val title: String,
    val subtitle: String
)

data class IntensityOption(
    val id: String,
    val title: String,
    val subtitle: String
)

@Composable
fun SettingsAppearanceScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefsRepo = remember { com.sruti.music.data.UserPreferencesRepository(context) }
    val scope = rememberCoroutineScope()

    val selectedThemeId by prefsRepo.selectedThemeId.collectAsState(initial = "pure_black")
    val selectedBgStyle by prefsRepo.backgroundStyle.collectAsState(initial = "solid_dark")
    val selectedBgIntensity by prefsRepo.backgroundIntensity.collectAsState(initial = "balanced")
    val authRepo = remember { com.sruti.music.auth.FirebaseAuthRepository.getInstance(context) }
    val userProfile by authRepo.userProfile.collectAsState()
    var showAccountDialog by remember { mutableStateOf(false) }

    val playbackState by PlaybackStateManager.state.collectAsState()
    val currentTrack = playbackState.currentTrack
    val dynamicTheme = LocalDynamicTheme.current

    val bgStyles = remember {
        listOf(
            BackgroundStyleOption(
                id = "dynamic",
                title = "Dynamic Artwork",
                subtitle = "App background dynamically adapts to the current track's album artwork and palette."
            ),
            BackgroundStyleOption(
                id = "sruti_gradient",
                title = "Sruti Gradient",
                subtitle = "Signature luminous purple/blue ambient gradient with glowing radial blooms."
            ),
            BackgroundStyleOption(
                id = "solid_dark",
                title = "Solid Dark",
                subtitle = "Clean, minimal deep dark night canvas for maximum battery efficiency and focus."
            )
        )
    }

    val intensityLevels = remember {
        listOf(
            IntensityOption(
                id = "subtle",
                title = "Subtle",
                subtitle = "Gentle artwork tint with deeper protective scrim for maximum readability."
            ),
            IntensityOption(
                id = "balanced",
                title = "Balanced (Default)",
                subtitle = "Optimal saturation, harmonious contrast and signature glass aesthetics."
            ),
            IntensityOption(
                id = "immersive",
                title = "Immersive",
                subtitle = "Vivid, rich color blooms and pronounced album art presence."
            )
        )
    }

    val themes = remember {
        listOf(
            ThemeItem(
                id = "pure_black",
                name = "Pure Black (Default)",
                description = "Pitch black OLED surfaces, clean white accents, zero eye strain and maximum contrast.",
                primaryColor = Color(0xFFFFFFFF),
                secondaryColor = Color(0xFF71717A)
            ),
            ThemeItem(
                id = "midnight_neon",
                name = "Midnight Neon",
                description = "Midnight navy, rich violet/blue gradients and translucent glassmorphism.",
                primaryColor = NeonPurple,
                secondaryColor = Color(0xFF4169F5)
            ),
            ThemeItem(
                id = "cyber_cyan",
                name = "Cyber Purple",
                description = "Deep obsidian night surfaces with luminous purple highlights.",
                primaryColor = NeonPurple,
                secondaryColor = Color(0xFF6B21A8)
            ),
            ThemeItem(
                id = "aurora",
                name = "Sruti Aurora",
                description = "Vibrant violet, magenta and pink accents with expressive night-time glow.",
                primaryColor = Color(0xFFFF2A85),
                secondaryColor = NeonPurple
            ),
            ThemeItem(
                id = "minimal_dark",
                name = "Minimal Dark",
                description = "Flatter dark surfaces, subtle borders and focus on speed and readability.",
                primaryColor = Color(0xFF9E9E9E),
                secondaryColor = Color(0xFF2A2A2A)
            )
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassCircleButton(
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back",
                onClick = onBackClick
            )

            Spacer(modifier = Modifier.width(16.dp))

            Text(
                text = "Appearance & Themes",
                color = Color.White,
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            // 1. Sruti Brand Identity Card
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            brush = Brush.verticalGradient(
                                listOf(
                                    Color(0x30251A44),
                                    Color(0x50120D22)
                                )
                            )
                        )
                        .border(1.dp, Color(0x35FFFFFF), RoundedCornerShape(20.dp))
                        .padding(18.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.sruti_logo),
                            contentDescription = "Sruti Logo",
                            modifier = Modifier.size(52.dp)
                        )

                        Spacer(modifier = Modifier.width(16.dp))

                        Column {
                            Text(
                                text = "SRUTI MUSIC",
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Dynamic Glassmorphism Music",
                                color = dynamicTheme.primary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // 2. User Firebase Account Card
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0x18FFFFFF))
                        .border(1.dp, Color(0x25FFFFFF), RoundedCornerShape(18.dp))
                        .clickable { showAccountDialog = true }
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!userProfile.photoUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = userProfile.photoUrl,
                                contentDescription = "User Avatar",
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .border(1.5.dp, dynamicTheme.primary.copy(alpha = 0.6f), CircleShape)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(dynamicTheme.primary.copy(alpha = 0.8f), Color(0xFF7C3AED).copy(alpha = 0.8f))
                                        )
                                    )
                                    .border(1.5.dp, Color(0x60FFFFFF), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = userProfile.initials,
                                    color = Color.White,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (!userProfile.displayName.isNullOrBlank()) userProfile.displayName!! else if (userProfile.isAuthenticated) "Sruti Member" else "Guest Mode",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = userProfile.email?.ifBlank { null }
                                    ?: if (userProfile.isAuthenticated) "Firebase Authenticated Account" else "Tap to Sign In or Create Profile",
                                color = if (userProfile.isAuthenticated) dynamicTheme.primary else GlassWhite.copy(alpha = 0.7f),
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Normal
                            )
                        }

                        Icon(
                            imageVector = Icons.Rounded.AccountCircle,
                            contentDescription = "Account Settings",
                            tint = if (userProfile.isAuthenticated) dynamicTheme.primary else GlassWhite.copy(alpha = 0.6f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // 3. WHOLE APP ADAPTIVE BACKGROUND SECTION
            item {
                Text(
                    text = "Whole App Background",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            item {
                val isDynamicActive = selectedBgStyle == "dynamic"
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            brush = if (isDynamicActive) {
                                Brush.verticalGradient(
                                    listOf(dynamicTheme.primary.copy(alpha = 0.22f), Color(0x181028))
                                )
                            } else {
                                Brush.verticalGradient(
                                    listOf(Color(0x14FFFFFF), Color(0x0AFFFFFF))
                                )
                            }
                        )
                        .border(
                            width = 1.2.dp,
                            color = if (isDynamicActive) dynamicTheme.primary else Color(0x22FFFFFF),
                            shape = RoundedCornerShape(20.dp)
                        )
                        .padding(18.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (isDynamicActive) Brush.linearGradient(
                                                listOf(dynamicTheme.primary, dynamicTheme.accent)
                                            ) else Brush.linearGradient(
                                                listOf(Color(0xFF282834), Color(0xFF1E1E28))
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.GraphicEq,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                Column {
                                    Text(
                                        text = "Adaptive Song Background",
                                        color = Color.White,
                                        fontSize = 15.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (isDynamicActive) "Active • Whole app adapting to song" else "Disabled • Static Background",
                                        color = if (isDynamicActive) dynamicTheme.primary else GlassWhite.copy(alpha = 0.65f),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            Switch(
                                checked = isDynamicActive,
                                onCheckedChange = { isChecked ->
                                    scope.launch {
                                        prefsRepo.saveBackgroundStyle(if (isChecked) "dynamic" else "solid_dark")
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = dynamicTheme.primary,
                                    uncheckedThumbColor = Color(0xFFA1A1AA),
                                    uncheckedTrackColor = Color(0xFF27272A)
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Changes the entire app background (Home, Library, Search, etc.) in real time to match the thumbnail, album artwork, and color palette of whatever song is playing.",
                            color = GlassWhite.copy(alpha = 0.75f),
                            fontSize = 12.5.sp,
                            lineHeight = 17.sp
                        )

                        // Live Playing Track Preview Card
                        if (isDynamicActive) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0x35000000))
                                    .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(14.dp))
                                    .padding(12.dp)
                            ) {
                                if (currentTrack.id.isNotBlank()) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        AsyncImage(
                                            model = currentTrack.artworkUriString?.takeIf { it.isNotBlank() }
                                                ?: if (currentTrack.artworkResId != 0) currentTrack.artworkResId else R.drawable.sruti_artwork_fallback,
                                            contentDescription = "Currently Playing Artwork",
                                            modifier = Modifier
                                                .size(46.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .border(1.dp, dynamicTheme.primary.copy(alpha = 0.8f), RoundedCornerShape(8.dp)),
                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                        )

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .clip(CircleShape)
                                                        .background(Color(0xFF10B981))
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "CURRENT THUMBNAIL APPLIED",
                                                    color = Color(0xFF10B981),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    letterSpacing = 0.8.sp
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = currentTrack.title,
                                                color = Color.White,
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1
                                            )
                                            Text(
                                                text = currentTrack.artist,
                                                color = GlassWhite.copy(alpha = 0.70f),
                                                fontSize = 11.5.sp,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                } else {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Rounded.MusicNote,
                                            contentDescription = null,
                                            tint = dynamicTheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Ready • Play any song to watch whole app background adapt live",
                                            color = GlassWhite.copy(alpha = 0.75f),
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4. INTENSITY CONTROL (Visible when Adaptive Background is Active)
            if (selectedBgStyle == "dynamic") {
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Adaptive Background Intensity",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                items(intensityLevels, key = { it.id }) { level ->
                    val isSelected = level.id == selectedBgIntensity
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (isSelected) dynamicTheme.primary.copy(alpha = 0.18f) else Color(0x10FFFFFF))
                            .border(
                                width = 1.dp,
                                color = if (isSelected) dynamicTheme.primary else Color(0x1DFFFFFF),
                                shape = RoundedCornerShape(18.dp)
                            )
                            .clickable {
                                scope.launch { prefsRepo.saveBackgroundIntensity(level.id) }
                            }
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = level.title,
                                    color = if (isSelected) Color.White else GlassWhite,
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = level.subtitle,
                                    color = GlassWhite.copy(alpha = 0.65f),
                                    fontSize = 12.sp,
                                    lineHeight = 15.sp
                                )
                            }

                            if (isSelected) {
                                Spacer(modifier = Modifier.width(10.dp))
                                Icon(
                                    imageVector = Icons.Rounded.Tune,
                                    contentDescription = "Active",
                                    tint = dynamicTheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                // Static Background Options when Adaptive Background is Disabled
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Static Background Styles",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                items(bgStyles.filter { it.id != "dynamic" }, key = { it.id }) { style ->
                    val isSelected = style.id == selectedBgStyle
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (isSelected) dynamicTheme.primary.copy(alpha = 0.18f) else Color(0x12FFFFFF))
                            .border(
                                width = 1.dp,
                                color = if (isSelected) dynamicTheme.primary else Color(0x22FFFFFF),
                                shape = RoundedCornerShape(18.dp)
                            )
                            .clickable {
                                scope.launch { prefsRepo.saveBackgroundStyle(style.id) }
                            }
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = style.title,
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = style.subtitle,
                                    color = GlassWhite.copy(alpha = 0.70f),
                                    fontSize = 12.5.sp,
                                    lineHeight = 16.sp
                                )
                            }

                            if (isSelected) {
                                Spacer(modifier = Modifier.width(10.dp))
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = "Selected",
                                    tint = dynamicTheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 5. APP THEMES SECTION
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Color Themes",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            items(themes, key = { it.id }) { themeItem ->
                val isSelected = themeItem.id == selectedThemeId
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (isSelected) dynamicTheme.primary.copy(alpha = 0.18f) else Color(0x12FFFFFF))
                        .border(
                            width = 1.dp,
                            color = if (isSelected) dynamicTheme.primary else Color(0x22FFFFFF),
                            shape = RoundedCornerShape(18.dp)
                        )
                        .clickable {
                            scope.launch { prefsRepo.saveSelectedThemeId(themeItem.id) }
                        }
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Palette preview indicator
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(themeItem.primaryColor, themeItem.secondaryColor)
                                    )
                                )
                                .border(1.dp, Color(0x60FFFFFF), CircleShape)
                        )

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = themeItem.name,
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = themeItem.description,
                                color = GlassWhite.copy(alpha = 0.70f),
                                fontSize = 12.5.sp,
                                lineHeight = 16.sp
                            )
                        }

                        if (isSelected) {
                            Spacer(modifier = Modifier.width(10.dp))
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = "Selected",
                                tint = dynamicTheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }

            // 6. CUSTOM ACCENT COLOR PALETTE
            item {
                Spacer(modifier = Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Palette,
                        contentDescription = null,
                        tint = dynamicTheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Custom Accent Palette",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Pick your own choice of accent color on top of pure pitch black OLED background.",
                    color = GlassWhite.copy(alpha = 0.70f),
                    fontSize = 12.5.sp,
                    lineHeight = 16.sp
                )
            }

            item {
                CustomColorPickerCard(
                    selectedThemeId = selectedThemeId,
                    onSelectCustomColor = { hex ->
                        scope.launch { prefsRepo.saveSelectedThemeId("custom_$hex") }
                    }
                )
            }

            // App Attribution & Branding
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Sruti Music",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "v1.1.0 • Made by Kavexa",
                        color = dynamicTheme.primary,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Crafted for pure, immersive audio listening",
                        color = GlassWhite.copy(alpha = 0.5f),
                        fontSize = 11.sp
                    )
                }
            }
        }
    }

    if (showAccountDialog) {
        AccountProfileDialog(
            userProfile = userProfile,
            onDismiss = { showAccountDialog = false }
        )
    }
}

data class CustomColorSwatch(val name: String, val hex: String, val color: Color)

@Composable
fun CustomColorPickerCard(
    selectedThemeId: String,
    onSelectCustomColor: (String) -> Unit
) {
    val dynamicTheme = LocalDynamicTheme.current
    val isCustomActive = selectedThemeId.startsWith("custom_")
    val activeHex = if (isCustomActive) selectedThemeId.removePrefix("custom_").uppercase() else ""

    var spectrumHue by remember { mutableFloatStateOf(180f) }
    var directHexInput by remember { mutableStateOf("") }
    var inputError by remember { mutableStateOf<String?>(null) }

    val spectrumColor = remember(spectrumHue) {
        val hsv = floatArrayOf(spectrumHue, 0.95f, 1.0f)
        Color(android.graphics.Color.HSVToColor(hsv))
    }
    val spectrumHex = remember(spectrumHue) {
        val hsv = floatArrayOf(spectrumHue, 0.95f, 1.0f)
        val argb = android.graphics.Color.HSVToColor(hsv)
        String.format("%06X", 0xFFFFFF and argb)
    }

    val swatches = remember {
        listOf(
            CustomColorSwatch("Crimson", "FF3B30", Color(0xFFFF3B30)),
            CustomColorSwatch("Sunset Coral", "FF6B6B", Color(0xFFFF6B6B)),
            CustomColorSwatch("Orange Flame", "FF9500", Color(0xFFFF9500)),
            CustomColorSwatch("Amber Gold", "F59E0B", Color(0xFFF59E0B)),
            CustomColorSwatch("Electric Yellow", "FACC15", Color(0xFFFACC15)),
            CustomColorSwatch("Acid Lime", "84CC16", Color(0xFF84CC16)),
            CustomColorSwatch("Neon Emerald", "10B981", Color(0xFF10B981)),
            CustomColorSwatch("Mint", "00E676", Color(0xFF00E676)),
            CustomColorSwatch("Cyber Cyan", "00E5FF", Color(0xFF00E5FF)),
            CustomColorSwatch("Sky Blue", "0EA5E9", Color(0xFF0EA5E9)),
            CustomColorSwatch("Electric Blue", "38BDF8", Color(0xFF38BDF8)),
            CustomColorSwatch("Royal Blue", "3B82F6", Color(0xFF3B82F6)),
            CustomColorSwatch("Deep Indigo", "6366F1", Color(0xFF6366F1)),
            CustomColorSwatch("Purple Neon", "A855F7", Color(0xFFA855F7)),
            CustomColorSwatch("Vivid Magenta", "D946EF", Color(0xFFD946EF)),
            CustomColorSwatch("Hot Pink", "FF2D55", Color(0xFFFF2D55)),
            CustomColorSwatch("Flamingo", "FB7185", Color(0xFFFB7185)),
            CustomColorSwatch("Pure White", "FFFFFF", Color(0xFFFFFFFF))
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF12141A))
            .border(
                width = 1.dp,
                color = if (isCustomActive) dynamicTheme.primary else Color(0xFF27272A),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(18.dp)
    ) {
        Column {
            // Header with active indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(if (isCustomActive) dynamicTheme.primary else Color(0xFF71717A))
                            .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isCustomActive) "Active: #$activeHex" else "Choose Your Color",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (isCustomActive) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(dynamicTheme.primary.copy(alpha = 0.20f))
                            .border(1.dp, dynamicTheme.primary, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "CUSTOM ACTIVE",
                            color = dynamicTheme.primary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 1. Swatches Grid (arranged in rows of 6)
            Text(
                text = "Vibrant Color Swatches",
                color = GlassWhite.copy(alpha = 0.75f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(8.dp))

            swatches.chunked(6).forEach { rowSwatches ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    rowSwatches.forEach { swatch ->
                        val isThisSelected = isCustomActive && activeHex.equals(swatch.hex, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(swatch.color)
                                .border(
                                    width = if (isThisSelected) 2.5.dp else 1.dp,
                                    color = if (isThisSelected) Color.White else Color(0x35FFFFFF),
                                    shape = CircleShape
                                )
                                .clickable {
                                    onSelectCustomColor(swatch.hex)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isThisSelected) {
                                val isBright = swatch.color.luminance() > 0.5f
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = "Selected",
                                    tint = if (isBright) Color.Black else Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Interactive Spectrum / Hue Slider (Full color spectrum)
            Text(
                text = "Spectrum Color Slider",
                color = GlassWhite.copy(alpha = 0.75f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Rainbow gradient track behind the slider
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Red,
                                Color.Yellow,
                                Color.Green,
                                Color.Cyan,
                                Color.Blue,
                                Color.Magenta,
                                Color.Red
                            )
                        )
                    )
            )

            Slider(
                value = spectrumHue,
                onValueChange = { spectrumHue = it },
                valueRange = 0f..360f,
                colors = SliderDefaults.colors(
                    thumbColor = spectrumColor,
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = (-6).dp)
            )

            // Live preview from spectrum slider
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = (-6).dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(spectrumColor)
                            .border(1.5.dp, Color.White, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "#$spectrumHex",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = {
                        onSelectCustomColor(spectrumHex)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = spectrumColor),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    val isBright = spectrumColor.luminance() > 0.5f
                    Text(
                        text = "Apply Slider Color",
                        color = if (isBright) Color.Black else Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 3. Direct Hex Input
            Text(
                text = "Or Enter Any Hex Color Code",
                color = GlassWhite.copy(alpha = 0.75f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = directHexInput,
                    onValueChange = { input ->
                        directHexInput = input.filter { it.isLetterOrDigit() || it == '#' }.take(7)
                        inputError = null
                    },
                    placeholder = {
                        Text(
                            text = "#00E5FF",
                            color = GlassWhite.copy(alpha = 0.4f),
                            fontSize = 13.sp
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = dynamicTheme.primary,
                        unfocusedBorderColor = Color(0xFF27272A),
                        focusedContainerColor = Color(0xFF0C0E12),
                        unfocusedContainerColor = Color(0xFF0C0E12)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                )

                Spacer(modifier = Modifier.width(10.dp))

                Button(
                    onClick = {
                        val clean = directHexInput.removePrefix("#").trim()
                        if (clean.length == 6) {
                            try {
                                android.graphics.Color.parseColor("#$clean")
                                onSelectCustomColor(clean.uppercase())
                                directHexInput = ""
                                inputError = null
                            } catch (_: Exception) {
                                inputError = "Invalid hex code"
                            }
                        } else {
                            inputError = "Enter 6-digit hex"
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E2129)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .border(1.dp, Color(0xFF3F3F46), RoundedCornerShape(12.dp))
                ) {
                    Text("Apply", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }

            if (inputError != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = inputError!!,
                    color = Color(0xFFFF5252),
                    fontSize = 11.5.sp
                )
            }
        }
    }
}
