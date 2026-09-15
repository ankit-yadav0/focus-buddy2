package com.example.ui.helper

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.rememberAsyncImagePainter

@Composable
fun WallpaperBox(
    modifier: Modifier = Modifier,
    opacity: Float? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val wallpaperUriString = remember {
        context.getSharedPreferences("focuss_buddy_settings", Context.MODE_PRIVATE)
            .getString("custom_wallpaper_uri", null)
    }
    val wallpaperOpacity = opacity ?: remember {
        context.getSharedPreferences("focuss_buddy_settings", Context.MODE_PRIVATE)
            .getFloat("wallpaper_opacity", 0.5f)
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        if (!wallpaperUriString.isNullOrEmpty()) {
            val uri = remember(wallpaperUriString) {
                try {
                    Uri.parse(wallpaperUriString)
                } catch (e: Exception) {
                    null
                }
            }

            if (uri != null) {
                val painter = rememberAsyncImagePainter(
                    model = coil.request.ImageRequest.Builder(LocalContext.current)
                        .data(uri)
                        .crossfade(false)
                        .allowHardware(true)
                        .build()
                )
                Image(
                    painter = painter,
                    contentDescription = "Custom Wallpaper Background",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alpha = wallpaperOpacity
                )
                // Premium subtle dark overlay scaled by opacity to ensure maximum readability and M3 accessibility compliance
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = (1f - wallpaperOpacity).coerceAtLeast(0.3f)))
                )
            } else {
                // Fallback to theme background if URI parsing fails
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                )
            }
        } else {
            // Default background fallback
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            )
        }
        content()
    }
}
