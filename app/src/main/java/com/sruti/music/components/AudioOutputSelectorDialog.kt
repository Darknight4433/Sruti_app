package com.sruti.music.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.sruti.music.playback.AudioRoute
import com.sruti.music.playback.AudioRouteType
import com.sruti.music.theme.GlassWhite
import com.sruti.music.theme.NeonPurple

@Composable
fun AudioOutputSelectorDialog(
    currentRoute: AudioRoute,
    availableRoutes: List<AudioRoute>,
    onRouteSelect: (AudioRoute) -> Unit,
    onOpenSystemSwitcher: () -> Unit,
    onDismissRequest: () -> Unit
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(
                    brush = Brush.verticalGradient(
                        listOf(
                            Color(0xFA261845),
                            Color(0xF0120B24)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(Color(0x60FFFFFF), Color(0x18FFFFFF))
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
                .padding(22.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0x25BA2DF7)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = getRouteIcon(currentRoute.type),
                                contentDescription = null,
                                tint = NeonPurple,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Audio Output",
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = currentRoute.name,
                                color = NeonPurple,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    IconButton(onClick = onDismissRequest) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close",
                            tint = GlassWhite.copy(alpha = 0.7f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = "AVAILABLE OUTPUT DEVICES",
                    color = GlassWhite.copy(alpha = 0.65f),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Routes List
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(availableRoutes, key = { "${it.id}_${it.name}" }) { route ->
                        val isSelected = route.name == currentRoute.name || route.isCurrent
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isSelected) Color(0x28BA2DF7) else Color(0x14FFFFFF))
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) NeonPurple.copy(alpha = 0.7f) else Color(0x20FFFFFF),
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable { onRouteSelect(route) }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = getRouteIcon(route.type),
                                contentDescription = null,
                                tint = if (isSelected) NeonPurple else Color.White,
                                modifier = Modifier.size(22.dp)
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = route.name,
                                    color = Color.White,
                                    fontSize = 14.5.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                )
                                Text(
                                    text = when (route.type) {
                                        AudioRouteType.SPEAKER -> "Internal device speaker"
                                        AudioRouteType.WIRED_HEADPHONES -> "3.5mm / wired audio jack"
                                        AudioRouteType.BLUETOOTH -> "Bluetooth wireless playback"
                                        AudioRouteType.USB -> "USB-C digital output"
                                        else -> "Standard audio route"
                                    },
                                    color = GlassWhite.copy(alpha = 0.65f),
                                    fontSize = 11.5.sp
                                )
                            }

                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = "Active",
                                    tint = NeonPurple,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // System Output Switcher Button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            brush = Brush.horizontalGradient(
                                listOf(
                                    NeonPurple.copy(alpha = 0.35f),
                                    NeonPurple.copy(alpha = 0.15f)
                                )
                            )
                        )
                        .border(1.dp, Color(0x40FFFFFF), RoundedCornerShape(14.dp))
                        .clickable { onOpenSystemSwitcher() }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Open System Audio Panel",
                            color = Color.White,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

fun getRouteIcon(type: AudioRouteType): ImageVector {
    return when (type) {
        AudioRouteType.SPEAKER -> Icons.Rounded.VolumeUp
        AudioRouteType.WIRED_HEADPHONES -> Icons.Rounded.Headphones
        AudioRouteType.BLUETOOTH -> Icons.Rounded.Bluetooth
        AudioRouteType.USB -> Icons.Rounded.Usb
        else -> Icons.Rounded.Speaker
    }
}
