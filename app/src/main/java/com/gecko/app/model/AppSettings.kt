package com.gecko.app.model

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

enum class ThemeStyle {
    MODERN,
    CLASSIC_IPOD,
}

enum class LyricsProviderId(
    val storageKey: String,
    val title: String,
    val subtitle: String,
) {
    SIMPMUSIC(
        "simpmusic",
        "SimpMusic Lyrics",
        "Banco comunitário gratuito, com letras sincronizadas e pesquisa por título e artista",
    ),
}

val DEFAULT_LYRICS_PROVIDERS: Set<String> = setOf(LyricsProviderId.SIMPMUSIC.storageKey)

/**
 * The quality the user *asks for* when a remote source is used. The quality that
 * is actually decoded is always read from the player and shown as-is.
 */
enum class AudioQuality(
    val storageKey: String,
    val title: String,
    val subtitle: String,
    val rank: Int,
) {
    DATA_SAVER("saver", "Economia", "Menor consumo de dados", 0),
    NORMAL("normal", "Padrão", "Até 128 kbps", 1),
    HIGH("high", "Alta", "Até 320 kbps", 2),
    LOSSLESS("lossless", "Lossless", "FLAC, ALAC ou WAV", 3),
    HI_RES("hires", "Hi-Res Lossless", "24-bit / 96 kHz ou mais", 4),
    ;

    val isLossless: Boolean get() = rank >= LOSSLESS.rank

    companion object {
        fun fromStorage(value: String?): AudioQuality =
            entries.firstOrNull { it.storageKey == value } ?: LOSSLESS
    }
}

const val DEFAULT_YT_MUSIC_API = "https://music.youtube.com/youtubei/v1/"

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val themeStyle: ThemeStyle = ThemeStyle.MODERN,
    val colorFromArtwork: Boolean = true,
    val preferredQuality: AudioQuality = AudioQuality.LOSSLESS,
    val losslessFirst: Boolean = true,
    val streamWhenOnline: Boolean = true,
    val smartDownloads: Boolean = true,
    val wifiOnlyDownloads: Boolean = true,
    val downloadQuality: AudioQuality = AudioQuality.LOSSLESS,
    val ytMusicUrl: String = DEFAULT_YT_MUSIC_API,
    val ytMusicStreamUrl: String = "",
    val ytMusicToken: String = "",
    val lyricsProviders: Set<String> = DEFAULT_LYRICS_PROVIDERS,
    val scrobbleEnabled: Boolean = false,
    val lastFmApiKey: String = "",
    val lastFmApiSecret: String = "",
    val lastFmSessionKey: String = "",
    val discordEnabled: Boolean = false,
    val discordBridgeUrl: String = "",
    val discordToken: String = "",
)

/** Colors extracted from the current album artwork. Stored as ARGB ints. */
data class AlbumPalette(
    val primary: Int,
    val secondary: Int,
    val tertiary: Int,
    val seed: Int,
)
