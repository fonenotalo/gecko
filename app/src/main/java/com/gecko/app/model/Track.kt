package com.gecko.app.model

import android.net.Uri

enum class TrackSource {
    LOCAL,
    DEMO,
    YOUTUBE_MUSIC,
    PLUGIN,
    ;

    val label: String
        get() = when (this) {
            LOCAL -> "Biblioteca local"
            DEMO -> "Demonstração Gecko"
            YOUTUBE_MUSIC -> "YouTube Music"
            PLUGIN -> "Complemento lossless"
        }

    val isRemote: Boolean
        get() = this == YOUTUBE_MUSIC || this == PLUGIN
}

/**
 * A single playable track. Remote tracks keep [uri] empty until a stream URL is
 * resolved by their source. Downloaded remote tracks point at the local file.
 */
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long = 0L,
    val durationMs: Long = 0L,
    val trackNumber: Int = 0,
    val year: Int = 0,
    val dateAdded: Long = 0L,
    val mimeType: String? = null,
    val uri: Uri = Uri.EMPTY,
    val artworkUri: Uri? = null,
    val artworkUrl: String? = null,
    val source: TrackSource = TrackSource.LOCAL,
    val providerId: String? = null,
    val remoteId: String? = null,
    val isLosslessHint: Boolean = false,
    val qualityHint: String? = null,
    val isDownloaded: Boolean = false,
    val isDemo: Boolean = false,
) {
    val displayArtist: String
        get() = artist.takeUnless { it.isBlank() || it == "<unknown>" } ?: "Artista desconhecido"

    val displayAlbum: String
        get() = album.takeUnless { it.isBlank() || it == "<unknown>" } ?: "Álbum desconhecido"
}

data class Album(
    val key: String,
    val title: String,
    val artist: String,
    val artworkUri: Uri?,
    val tracks: List<Track>,
) {
    val durationMs: Long get() = tracks.sumOf { it.durationMs }
}

data class Artist(
    val key: String,
    val name: String,
    val trackCount: Int,
    val artworkUri: Uri?,
    val tracks: List<Track>,
)
