package com.auris.app.ui.components

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.auris.app.model.Track
import com.auris.app.ui.LocalAppContainer
import kotlin.math.abs
import androidx.compose.ui.graphics.luminance

@Composable
fun TrackArtwork(
    track: Track,
    modifier: Modifier = Modifier,
    corner: Int = 16,
    elevated: Boolean = false,
) {
    val container = LocalAppContainer.current
    var uri by remember(track.id) {
        mutableStateOf(track.artworkUri ?: track.artworkUrl?.let(Uri::parse))
    }
    LaunchedEffect(track.id, track.artworkUri, track.artworkUrl) {
        val resolved = container.artwork.artworkFor(track)
        if (resolved != null) uri = resolved
    }

    val shape = RoundedCornerShape(corner.dp)
    val shadow = if (elevated) {
        Modifier.shadow(
            elevation = 26.dp,
            shape = shape,
            ambientColor = Color.Black.copy(alpha = 0.45f),
            spotColor = Color.Black.copy(alpha = 0.55f),
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .then(shadow)
            .clip(shape)
            .background(placeholderColors(track.title + track.artist)),
        contentAlignment = Alignment.Center,
    ) {
        val model = uri
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = "Capa de ${track.displayAlbum}",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            ArtworkPlaceholder(track.title + track.artist)
        }
    }
}

/** Artwork for albums and artists, which only carry a [android.net.Uri]. */
@Composable
fun ArtworkImage(
    uri: Uri?,
    seed: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    corner: Int = 16,
    elevated: Boolean = false,
) {
    val shape = RoundedCornerShape(corner.dp)
    val shadow = if (elevated) {
        Modifier.shadow(
            elevation = 26.dp,
            shape = shape,
            ambientColor = Color.Black.copy(alpha = 0.45f),
            spotColor = Color.Black.copy(alpha = 0.55f),
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .then(shadow)
            .clip(shape)
            .background(placeholderColors(seed)),
        contentAlignment = Alignment.Center,
    ) {
        if (uri != null) {
            AsyncImage(
                model = uri,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            ArtworkPlaceholder(seed)
        }
    }
}

@Composable
private fun ArtworkPlaceholder(seed: String) {
    val colors = placeholderColors(seed)
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(colors[0], colors[1]))),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = size.minDimension * 0.42f
            drawCircle(
                color = Color.White.copy(alpha = 0.08f),
                radius = radius,
                center = Offset(size.width * 0.78f, size.height * 0.25f),
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.10f),
                radius = radius * 0.55f,
                center = Offset(size.width * 0.18f, size.height * 0.78f),
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.22f),
                radius = radius * 0.9f,
                center = Offset(size.width * 0.55f, size.height * 0.5f),
                style = Stroke(width = 1.5f),
            )
        }
        Icon(
            imageVector = Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = Color.White.copy(alpha = if (dark) 0.82f else 0.9f),
            modifier = Modifier.size(34.dp),
        )
    }
}

private fun placeholderColors(seedValue: String): List<Color> {
    val seed = abs(seedValue.hashCode())
    val hue = (seed % 360).toFloat()
    return listOf(
        Color.hsl(hue, 0.46f, 0.34f),
        Color.hsl((hue + 46f) % 360f, 0.58f, 0.52f),
    )
}
