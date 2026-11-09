package com.gecko.app.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.gecko.app.model.Track
import com.gecko.app.model.TrackSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalMusicRepository(private val context: Context) {

    val audioPermission: String
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED

    suspend fun loadTracks(): List<Track> = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext emptyList()

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.MIME_TYPE,
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND " +
            "${MediaStore.Audio.Media.DURATION} >= 10000"
        val sort = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        val result = mutableListOf<Track>()
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            sort,
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val trackColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val yearColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
            val addedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val albumId = cursor.getLong(albumIdColumn)
                val mime = cursor.getString(mimeColumn)
                val trackNumber = cursor.getInt(trackColumn) % 1000
                val duration = cursor.getLong(durationColumn)
                result += Track(
                    id = "local:$id",
                    title = cursor.getString(titleColumn)?.takeUnless { it.isBlank() } ?: "Faixa sem título",
                    artist = cursor.getString(artistColumn).orEmpty(),
                    album = cursor.getString(albumColumn).orEmpty(),
                    albumId = albumId,
                    durationMs = duration,
                    trackNumber = trackNumber,
                    year = cursor.getInt(yearColumn),
                    dateAdded = cursor.getLong(addedColumn) * 1000L,
                    mimeType = mime,
                    uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
                    artworkUri = ContentUris.withAppendedId(ALBUM_ART_URI, albumId),
                    source = TrackSource.LOCAL,
                    isLosslessHint = isLosslessMime(mime),
                    qualityHint = qualityHint(mime),
                )
            }
        }
        result
    }

    private fun isLosslessMime(mime: String?): Boolean {
        val value = mime?.lowercase().orEmpty()
        return value in setOf(
            "audio/flac",
            "audio/x-flac",
            "audio/wav",
            "audio/x-wav",
            "audio/wave",
            "audio/alac",
            "audio/x-alac",
            "audio/raw",
        )
    }

    private fun qualityHint(mime: String?): String? = when (mime?.lowercase()) {
        "audio/flac", "audio/x-flac" -> "FLAC"
        "audio/wav", "audio/x-wav", "audio/wave" -> "WAV"
        "audio/alac", "audio/x-alac" -> "ALAC"
        "audio/mp4", "audio/m4a", "audio/x-m4a" -> "M4A"
        "audio/mpeg" -> "MP3"
        "audio/ogg" -> "OGG"
        "audio/opus" -> "OPUS"
        "audio/aac" -> "AAC"
        "audio/x-ms-wma" -> "WMA"
        else -> mime?.substringAfter('/')?.uppercase()
    }

    private companion object {
        val ALBUM_ART_URI: Uri = Uri.parse("content://media/external/audio/albumart")
    }
}
