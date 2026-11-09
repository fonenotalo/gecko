package com.auris.app.data

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import coil.imageLoader
import coil.request.ImageRequest
import com.auris.app.model.AlbumPalette
import com.auris.app.model.Track
import com.auris.app.util.safeFileId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette

/**
 * Resolves artwork for every kind of track: MediaStore artwork, embedded
 * artwork, generated demo art or a remote URL. Results are cached on disk.
 */
class ArtworkStore(private val context: Context) {

    private val memoryCache = ConcurrentHashMap<String, String>()
    private val cacheDir = File(context.cacheDir, "auris_artwork").apply { mkdirs() }

    suspend fun artworkFor(track: Track): Uri? = withContext(Dispatchers.IO) {
        memoryCache[track.id]?.let { return@withContext Uri.parse(it) }

        val direct = track.artworkUri ?: track.artworkUrl?.takeIf { it.isNotBlank() }?.let(Uri::parse)
        if (direct != null && (direct.scheme == "http" || direct.scheme == "https" || canRead(direct))) {
            memoryCache[track.id] = direct.toString()
            return@withContext direct
        }

        if (track.uri != Uri.EMPTY) {
            extractEmbedded(track)?.let {
                memoryCache[track.id] = it.toString()
                return@withContext it
            }
        }
        null
    }

    private fun canRead(uri: Uri): Boolean = try {
        context.contentResolver.openInputStream(uri)?.use { true } ?: false
    } catch (_: Exception) {
        false
    }

    private fun extractEmbedded(track: Track): Uri? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, track.uri)
            val bytes = retriever.embeddedPicture ?: return null
            val file = File(cacheDir, "${safeFileId(track.id)}.jpg")
            file.writeBytes(bytes)
            Uri.fromFile(file)
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }
}

object AlbumPaletteExtractor {

    suspend fun extract(context: Context, uri: Uri): AlbumPalette? = withContext(Dispatchers.IO) {
        try {
            val request = ImageRequest.Builder(context)
                .data(uri)
                .size(160)
                .allowHardware(false)
                .build()
            val result = context.imageLoader.execute(request)
            val bitmap: Bitmap = result.drawable?.toBitmap(96, 96) ?: return@withContext null
            val palette = Palette.from(bitmap)
                .clearFilters()
                .maximumColorCount(24)
                .generate()

            val swatches = listOfNotNull(
                palette.vibrantSwatch,
                palette.lightVibrantSwatch,
                palette.dominantSwatch,
                palette.mutedSwatch,
                palette.darkVibrantSwatch,
            )
            val seed = swatches.firstOrNull()?.rgb ?: return@withContext null
            buildPalette(seed)
        } catch (_: Exception) {
            null
        }
    }

    fun fallback(seedText: String): AlbumPalette {
        val hue = abs(seedText.hashCode()) % 360
        val seed = hsl(hue.toFloat(), 0.48f, 0.55f)
        return buildPalette(seed)
    }

    private fun buildPalette(seed: Int): AlbumPalette {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(seed, hsl)
        val hue = hsl[0]
        val saturation = hsl[1].coerceIn(0.35f, 0.78f)
        val primary = hsl(hue, saturation, 0.62f)
        val secondary = hsl((hue + 38f) % 360f, (saturation * 0.75f).coerceAtLeast(0.3f), 0.58f)
        val tertiary = hsl((hue + 186f) % 360f, (saturation * 0.7f).coerceAtLeast(0.28f), 0.60f)
        return AlbumPalette(primary = primary, secondary = secondary, tertiary = tertiary, seed = seed)
    }

    private fun hsl(hue: Float, saturation: Float, lightness: Float): Int =
        ColorUtils.HSLToColor(floatArrayOf(hue, saturation, lightness))
}
