package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.example.data.repository.ProfileBackgroundManager
import com.example.ui.theme.SpotifyGreen

/**
 * Visual hero profile header for HexShard Messenger.
 * Renders profile background (Image or WebM video) strictly behind:
 * - Avatar
 * - Name
 * - Username
 * - Status / Online indicator
 * Follows dark aesthetic with gradient scrim for maximum text legibility.
 */
@Composable
fun ProfileBackgroundHeader(
    name: String,
    username: String,
    phone: String,
    privateVirtualNumber: String,
    avatarUri: String?,
    isTelegramVerified: Boolean,
    backgroundPath: String?,
    backgroundType: String?,
    isUploadingBackground: Boolean,
    onAvatarClick: () -> Unit,
    onBackgroundActionClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    val hasBackground = !backgroundPath.isNullOrBlank()
    val isWebm = backgroundType?.contains("webm", ignoreCase = true) == true

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(260.dp)
            .clip(RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
    ) {
        // 1. BACKGROUND LAYER (WebM video, Image, or Default dark theme gradient)
        if (hasBackground) {
            val bgUrl = ProfileBackgroundManager.getPublicUrl(backgroundPath)
            if (isWebm) {
                WebmBackgroundPlayer(
                    videoUrl = bgUrl,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Image(
                    painter = rememberAsyncImagePainter(model = bgUrl),
                    contentDescription = strings.profileBackground,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF132219),
                                Color(0xFF0F1813),
                                Color(0xFF0A0F0D)
                            )
                        )
                    )
            )
        }

        // 2. SCRIM / GRADIENT OVERLAY (Guarantees contrast for text and avatar on any image/video)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0x66000000),
                            Color(0x99000000),
                            Color(0xF00D1115)
                        )
                    )
                )
        )

        // 3. TOP ACTION BAR (Set / Edit Profile Background Button)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .align(Alignment.TopEnd),
            horizontalArrangement = Arrangement.End
        ) {
            Surface(
                shape = CircleShape,
                color = Color(0x66000000),
                modifier = Modifier.size(40.dp)
            ) {
                IconButton(
                    onClick = onBackgroundActionClick,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("profile_background_action_button")
                ) {
                    Icon(
                        imageVector = if (hasBackground) Icons.Default.Wallpaper else Icons.Default.AddPhotoAlternate,
                        contentDescription = strings.profileBackground,
                        tint = if (hasBackground) SpotifyGreen else Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // 4. MAIN IDENTITY HEADER CONTENT
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Avatar with green accent border
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .border(BorderStroke(2.dp, SpotifyGreen), CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable(onClick = onAvatarClick)
                    .testTag("profile_avatar_box"),
                contentAlignment = Alignment.Center
            ) {
                if (avatarUri != null) {
                    Image(
                        painter = rememberAsyncImagePainter(model = avatarUri),
                        contentDescription = strings.profile,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        text = name.take(2).uppercase(),
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Display Name
            Text(
                text = name.ifBlank { "User" },
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            // Virtual number / Phone
            val displayVirtualNumber = if (privateVirtualNumber.isNotBlank()) privateVirtualNumber else phone
            if (displayVirtualNumber.isNotBlank()) {
                Text(
                    text = "${strings.privateNumberLabel}: $displayVirtualNumber",
                    color = SpotifyGreen,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Username
            Text(
                text = "@$username",
                color = Color(0xFFB3B3B3),
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Online indicator & Account Type Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                // Online indicator dot
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(SpotifyGreen)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = strings.onlineStatus,
                    color = SpotifyGreen,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.width(10.dp))

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF1B382B)
                ) {
                    Text(
                        text = if (isTelegramVerified) strings.telegramConnected else strings.phoneAccountBadge,
                        color = SpotifyGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }

        // 5. UPLOADING PROGRESS OVERLAY
        if (isUploadingBackground) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xCC000000)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = SpotifyGreen,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = strings.uploadingBackground,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
