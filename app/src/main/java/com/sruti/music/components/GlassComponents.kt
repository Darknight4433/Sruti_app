package com.sruti.music.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sruti.music.theme.*

@Composable
fun AmbientBackground(
    themeId: String = "midnight_neon",
    backgroundStyle: String = "dynamic",
    backgroundIntensity: String = "balanced",
    currentTrack: com.sruti.music.data.Track? = null,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val context = LocalContext.current
    var dynamicData by remember { mutableStateOf(DynamicBackgroundManager.defaultArtworkData) }

    LaunchedEffect(currentTrack?.id, backgroundIntensity, backgroundStyle) {
        if (backgroundStyle == "dynamic" && currentTrack != null) {
            dynamicData = DynamicBackgroundManager.extractDataForTrack(
                context,
                currentTrack,
                backgroundIntensity
            )
        } else {
            dynamicData = DynamicBackgroundManager.defaultArtworkData
        }
    }

    val dynamicPalette = dynamicData.palette

    val animPrimary by animateColorAsState(
        targetValue = dynamicPalette.primaryColor,
        animationSpec = tween(650),
        label = "anim_primary"
    )
    val animSecondary by animateColorAsState(
        targetValue = dynamicPalette.secondaryColor,
        animationSpec = tween(650),
        label = "anim_secondary"
    )
    val animAccent by animateColorAsState(
        targetValue = dynamicPalette.accentColor,
        animationSpec = tween(650),
        label = "anim_accent"
    )
    val animDark by animateColorAsState(
        targetValue = dynamicPalette.darkBaseColor,
        animationSpec = tween(650),
        label = "anim_dark"
    )

    val (scrimTopAlpha, scrimBottomAlpha, artworkAlpha) = when (backgroundIntensity) {
        "subtle" -> Triple(0.55f, 0.85f, 0.35f)
        "immersive" -> Triple(0.20f, 0.55f, 0.70f)
        else -> Triple(0.35f, 0.70f, 0.52f) // "balanced" default
    }

    val gradientStops = when (themeId) {
        "cyber_cyan" -> arrayOf(
            0.00f to Color(0xFF007799),
            0.18f to Color(0xFF004D66),
            0.42f to Color(0xFF002E40),
            0.68f to Color(0xFF0D1F2D),
            0.88f to Color(0xFF08121C),
            1.00f to Color(0xFF050B12)
        )
        "aurora" -> arrayOf(
            0.00f to Color(0xFFC71585),
            0.18f to Color(0xFF8B008B),
            0.42f to Color(0xFF4B0082),
            0.68f to Color(0xFF2E0854),
            0.88f to Color(0xFF190430),
            1.00f to Color(0xFF0A0214)
        )
        "minimal_dark" -> arrayOf(
            0.00f to Color(0xFF282834),
            0.18f to Color(0xFF1E1E28),
            0.42f to Color(0xFF161620),
            0.68f to Color(0xFF101018),
            0.88f to Color(0xFF0B0B10),
            1.00f to Color(0xFF07070A)
        )
        else -> arrayOf(
            0.00f to Color(0xFF9E4ECB),
            0.18f to Color(0xFF6E2EA3),
            0.42f to Color(0xFF381F6D),
            0.68f to Color(0xFF1B2366),
            0.88f to Color(0xFF0F1238),
            1.00f to Color(0xFF0A0715)
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Midnight)
    ) {
        // Blurred Artwork Backdrop for Dynamic Mode
        val currentArtwork = dynamicData.blurredArtwork
        if (backgroundStyle == "dynamic" && currentArtwork != null) {
            Image(
                bitmap = currentArtwork,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(artworkAlpha),
                contentScale = ContentScale.Crop
            )
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            when (backgroundStyle) {
                "solid_dark" -> {
                    drawRect(
                        brush = Brush.verticalGradient(
                            listOf(Color(0xFF141122), Color(0xFF0A0814))
                        ),
                        size = size
                    )
                }
                "dynamic" -> {
                    // 1. Dynamic Vertical Gradient from track palette
                    drawRect(
                        brush = Brush.verticalGradient(
                            0.00f to animPrimary.copy(alpha = if (currentArtwork != null) 0.65f else 1.0f),
                            0.22f to animSecondary.copy(alpha = if (currentArtwork != null) 0.60f else 1.0f),
                            0.55f to Color(0xFF16102C).copy(alpha = 0.85f),
                            0.82f to Color(0xFF0E0A1D),
                            1.00f to animDark
                        ),
                        size = size
                    )

                    // 2. Soft radiant bloom with track accent
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                animAccent.copy(alpha = 0.60f),
                                animSecondary.copy(alpha = 0.28f),
                                Color.Transparent
                            ),
                            center = Offset(w * 0.50f, h * 0.12f),
                            radius = w * 0.90f
                        ),
                        radius = w * 0.90f,
                        center = Offset(w * 0.50f, h * 0.12f)
                    )

                    // 3. Ambient bloom mid-right with track primary
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                animPrimary.copy(alpha = 0.45f),
                                Color.Transparent
                            ),
                            center = Offset(w * 0.85f, h * 0.55f),
                            radius = w * 0.75f
                        ),
                        radius = w * 0.75f,
                        center = Offset(w * 0.85f, h * 0.55f)
                    )

                    // 4. Subtle brand accent in lower left
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0x20BA2DF7),
                                Color.Transparent
                            ),
                            center = Offset(w * 0.15f, h * 0.80f),
                            radius = w * 0.60f
                        ),
                        radius = w * 0.60f,
                        center = Offset(w * 0.15f, h * 0.80f)
                    )

                    // 5. Protective dark translucent scrim ensuring 100% legibility
                    drawRect(
                        brush = Brush.verticalGradient(
                            listOf(
                                Color(0xFF0A0715).copy(alpha = scrimTopAlpha),
                                Color(0xFF0A0715).copy(alpha = scrimBottomAlpha)
                            )
                        ),
                        size = size
                    )
                }
                else -> { // "sruti_gradient"
                    // 1. Vertical Gradient
                    drawRect(
                        brush = Brush.verticalGradient(
                            *gradientStops
                        ),
                        size = size
                    )

                    // 2. Radiant Violet/Magenta Ambient Bloom at Top-Center
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0x95BA2DF7),
                                Color(0x458E20E0),
                                Color(0x008E20E0)
                            ),
                            center = Offset(w * 0.48f, h * 0.10f),
                            radius = w * 0.85f
                        ),
                        radius = w * 0.85f,
                        center = Offset(w * 0.48f, h * 0.10f)
                    )

                    // 3. Electric Blue Ambient Bloom at Mid-Right
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0x553D5CFF),
                                Color(0x20243AB5),
                                Color(0x00243AB5)
                            ),
                            center = Offset(w * 0.88f, h * 0.52f),
                            radius = w * 0.80f
                        ),
                        radius = w * 0.80f,
                        center = Offset(w * 0.88f, h * 0.52f)
                    )

                    // 4. Subtle Sruti Purple brand accent bloom in lower-left
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0x18BA2DF7),
                                Color(0x00BA2DF7)
                            ),
                            center = Offset(w * 0.15f, h * 0.78f),
                            radius = w * 0.60f
                        ),
                        radius = w * 0.60f,
                        center = Offset(w * 0.15f, h * 0.78f)
                    )
                }
            }
        }

        content()
    }
}

@Composable
fun GlassCircleButton(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    iconSize: Dp = 23.dp,
    tint: Color = Color.White,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                brush = Brush.verticalGradient(
                    listOf(
                        Color(0x28FFFFFF),
                        Color(0x10FFFFFF)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        Color(0x45FFFFFF),
                        Color(0x18FFFFFF)
                    )
                ),
                shape = CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
fun GlassPill(
    text: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) NeonPurple else Color(0x18FFFFFF),
        label = "pill_bg"
    )
    val textColor by animateColorAsState(
        targetValue = if (isSelected) Color.White else GlassWhite,
        label = "pill_text"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) Color(0x60FFFFFF) else Color(0x28FFFFFF),
        label = "pill_border"
    )

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(bgColor)
            .border(
                width = 1.dp,
                color = borderColor,
                shape = CircleShape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}

@Composable
fun GlassBadge(
    text: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(
                brush = Brush.verticalGradient(
                    listOf(
                        Color(0x38FFFFFF),
                        Color(0x18FFFFFF)
                    )
                )
            )
            .border(
                width = 1.dp,
                color = Color(0x45FFFFFF),
                shape = CircleShape
            )
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

enum class NavigationTab {
    HOME,
    SEARCH,
    CREATE,
    LIBRARY,
    SETTINGS
}

@Composable
fun FloatingBottomNav(
    currentTab: NavigationTab,
    onTabSelected: (NavigationTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(64.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(Color(0xF5111315))
            .border(
                width = 1.dp,
                color = Color(0x22FFFFFF),
                shape = RoundedCornerShape(32.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            NavTabItem(
                icon = Icons.Rounded.Home,
                label = "Home",
                isSelected = currentTab == NavigationTab.HOME,
                onClick = { onTabSelected(NavigationTab.HOME) }
            )

            NavTabItem(
                icon = Icons.Rounded.Search,
                label = "Search",
                isSelected = currentTab == NavigationTab.SEARCH,
                onClick = { onTabSelected(NavigationTab.SEARCH) }
            )

            NavTabItem(
                icon = Icons.Rounded.Add,
                label = "Create",
                isSelected = currentTab == NavigationTab.CREATE,
                onClick = { onTabSelected(NavigationTab.CREATE) }
            )

            NavTabItem(
                icon = Icons.AutoMirrored.Rounded.QueueMusic,
                label = "Library",
                isSelected = currentTab == NavigationTab.LIBRARY,
                onClick = { onTabSelected(NavigationTab.LIBRARY) }
            )
        }
    }
}

@Composable
private fun NavTabItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val activeColor = NeonPurple
    val inactiveColor = TextSecondary // #A1A1AA
    val tint = if (isSelected) activeColor else inactiveColor

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) Color(0x24A855F7) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            color = tint,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}

// Waveform Visualizer matching Phone 3 Now Playing exactly
@Composable
fun WaveformVisualizer(
    progress: Float, // 0.0f to 1.0f (e.g. 2:12 / 3:58 = ~0.55f)
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    // 46 symmetrical wave bar heights precisely sampled from reference
    val barRatios = remember {
        listOf(
            0.12f, 0.15f, 0.18f, 0.22f, 0.28f, 0.38f, 0.54f, 0.76f, 0.95f, 0.70f,
            0.48f, 0.32f, 0.24f, 0.28f, 0.42f, 0.62f, 0.80f, 0.52f, 0.32f, 0.24f,
            0.36f, 0.58f, 0.88f, 1.00f, 0.78f, 0.52f, 0.36f, 0.24f, 0.32f, 0.48f,
            0.64f, 0.78f, 0.90f, 0.68f, 0.48f, 0.32f, 0.26f, 0.40f, 0.58f, 0.46f,
            0.32f, 0.22f, 0.16f, 0.12f, 0.10f
        )
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val newProgress = (offset.x / size.width).coerceIn(0f, 1f)
                    onSeek(newProgress)
                }
            }
    ) {
        val totalBars = barRatios.size
        val barWidth = 3.6.dp.toPx()
        val spacing = (size.width - (totalBars * barWidth)) / (totalBars - 1).coerceAtLeast(1)
        val centerY = size.height / 2f
        val maxHeight = size.height * 0.96f

        for (i in 0 until totalBars) {
            val barX = i * (barWidth + spacing)
            val barFraction = i.toFloat() / (totalBars - 1)
            val isPlayed = barFraction <= progress

            val barH = (barRatios[i] * maxHeight).coerceAtLeast(4.dp.toPx())
            val top = centerY - (barH / 2f)
            val bottom = centerY + (barH / 2f)

            val color = if (isPlayed) {
                Color.White
            } else {
                Color(0x55DCD4F5)
            }

            drawLine(
                color = color,
                start = Offset(barX + barWidth / 2f, top),
                end = Offset(barX + barWidth / 2f, bottom),
                strokeWidth = barWidth,
                cap = StrokeCap.Round
            )
        }
    }
}
