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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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

    val selectedThemeId by prefsRepo.selectedThemeId.collectAsState(initial = "midnight_neon")
    val selectedBgStyle by prefsRepo.backgroundStyle.collectAsState(initial = "dynamic")
    val selectedBgIntensity by prefsRepo.backgroundIntensity.collectAsState(initial = "balanced")
    val authRepo = remember { com.sruti.music.auth.FirebaseAuthRepository.getInstance(context) }
    val userProfile by authRepo.userProfile.collectAsState()
    var showAccountDialog by remember { mutableStateOf(false) }

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
                id = "midnight_neon",
                name = "Midnight Neon (Default)",
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
                                color = NeonPurple,
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
                                    .border(1.5.dp, NeonPurple.copy(alpha = 0.6f), CircleShape)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(NeonPurple.copy(alpha = 0.8f), Color(0xFF7C3AED).copy(alpha = 0.8f))
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
                                color = if (userProfile.isAuthenticated) NeonPurple else GlassWhite.copy(alpha = 0.7f),
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Normal
                            )
                        }

                        Icon(
                            imageVector = Icons.Rounded.AccountCircle,
                            contentDescription = "Account Settings",
                            tint = if (userProfile.isAuthenticated) NeonPurple else GlassWhite.copy(alpha = 0.6f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // 3. BACKGROUND STYLE SECTION
            item {
                Text(
                    text = "Background Style",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            items(bgStyles, key = { it.id }) { style ->
                val isSelected = style.id == selectedBgStyle
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (isSelected) Color(0x28B625FF) else Color(0x12FFFFFF))
                        .border(
                            width = 1.dp,
                            color = if (isSelected) NeonPurple else Color(0x22FFFFFF),
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
                                tint = NeonPurple,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }

            // 4. INTENSITY CONTROL (Visible for Dynamic Artwork)
            if (selectedBgStyle == "dynamic") {
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Dynamic Artwork Intensity",
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
                            .background(if (isSelected) Color(0x25BA2DF7) else Color(0x10FFFFFF))
                            .border(
                                width = 1.dp,
                                color = if (isSelected) NeonPurple else Color(0x1DFFFFFF),
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
                                    tint = NeonPurple,
                                    modifier = Modifier.size(20.dp)
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
                        .background(if (isSelected) Color(0x28B625FF) else Color(0x12FFFFFF))
                        .border(
                            width = 1.dp,
                            color = if (isSelected) NeonPurple else Color(0x22FFFFFF),
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
                                tint = NeonPurple,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
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
