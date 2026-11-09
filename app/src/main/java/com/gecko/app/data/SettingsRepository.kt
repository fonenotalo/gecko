package com.gecko.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.gecko.app.model.AppSettings
import com.gecko.app.model.AudioQuality
import com.gecko.app.model.DEFAULT_YT_MUSIC_API
import com.gecko.app.model.ThemeMode
import com.gecko.app.model.ThemeStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.geckoDataStore: DataStore<Preferences> by preferencesDataStore(name = "gecko_settings")

class SettingsRepository(private val context: Context) {

    val settings: Flow<AppSettings> = context.geckoDataStore.data
        .catch { throwable ->
            if (throwable is IOException) emit(emptyPreferences()) else throw throwable
        }
        .map { it.toAppSettings() }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.geckoDataStore.edit { prefs ->
            prefs.writeSettings(transform(prefs.toAppSettings()))
        }
    }

    private fun Preferences.toAppSettings(): AppSettings {
        val storedApi = this[Keys.YT_MUSIC_URL].orEmpty().trim()
        val storedStream = this[Keys.YT_MUSIC_STREAM_URL].orEmpty().trim()
        val storedWasBridge = storedApi.isNotBlank() && !storedApi.isDirectYouTubeMusicApi()
        val apiUrl = if (storedApi.isBlank() || storedWasBridge) {
            DEFAULT_YT_MUSIC_API
        } else {
            storedApi
        }
        val streamUrl = when {
            storedWasBridge -> storedApi
            storedStream.isNotBlank() -> storedStream
            else -> ""
        }
        return AppSettings(
            themeMode = this[Keys.THEME_MODE]?.let { stored ->
                ThemeMode.entries.firstOrNull { it.name == stored }
            } ?: ThemeMode.SYSTEM,
            themeStyle = this[Keys.THEME_STYLE]?.let { stored ->
                ThemeStyle.entries.firstOrNull { it.name == stored }
            } ?: ThemeStyle.MODERN,
            colorFromArtwork = this[Keys.COLOR_FROM_ART] ?: true,
            preferredQuality = AudioQuality.fromStorage(this[Keys.PREFERRED_QUALITY]),
            losslessFirst = this[Keys.LOSSLESS_FIRST] ?: true,
            streamWhenOnline = this[Keys.STREAM_WHEN_ONLINE] ?: true,
            smartDownloads = this[Keys.SMART_DOWNLOADS] ?: true,
            wifiOnlyDownloads = this[Keys.WIFI_ONLY_DOWNLOADS] ?: true,
            downloadQuality = AudioQuality.fromStorage(this[Keys.DOWNLOAD_QUALITY]).let {
                if (it.rank < AudioQuality.LOSSLESS.rank) AudioQuality.LOSSLESS else it
            },
            ytMusicUrl = apiUrl,
            ytMusicStreamUrl = streamUrl,
            ytMusicToken = this[Keys.YT_MUSIC_TOKEN].orEmpty(),
            scrobbleEnabled = this[Keys.SCROBBLE_ENABLED] ?: false,
            lastFmApiKey = this[Keys.LASTFM_API_KEY].orEmpty(),
            lastFmApiSecret = this[Keys.LASTFM_API_SECRET].orEmpty(),
            lastFmSessionKey = this[Keys.LASTFM_SESSION_KEY].orEmpty(),
            discordEnabled = this[Keys.DISCORD_ENABLED] ?: false,
            discordBridgeUrl = this[Keys.DISCORD_BRIDGE_URL].orEmpty(),
        )
    }

    private fun String.isDirectYouTubeMusicApi(): Boolean =
        contains("/youtubei/v1", ignoreCase = true) ||
            contains("music.youtube.com", ignoreCase = true) ||
            contains("youtubei", ignoreCase = true)

    private fun androidx.datastore.preferences.core.MutablePreferences.writeSettings(settings: AppSettings) {
        this[Keys.THEME_MODE] = settings.themeMode.name
        this[Keys.THEME_STYLE] = settings.themeStyle.name
        this[Keys.COLOR_FROM_ART] = settings.colorFromArtwork
        this[Keys.PREFERRED_QUALITY] = settings.preferredQuality.storageKey
        this[Keys.LOSSLESS_FIRST] = settings.losslessFirst
        this[Keys.STREAM_WHEN_ONLINE] = settings.streamWhenOnline
        this[Keys.SMART_DOWNLOADS] = settings.smartDownloads
        this[Keys.WIFI_ONLY_DOWNLOADS] = settings.wifiOnlyDownloads
        this[Keys.DOWNLOAD_QUALITY] = settings.downloadQuality.storageKey
        this[Keys.YT_MUSIC_URL] = settings.ytMusicUrl
        this[Keys.YT_MUSIC_STREAM_URL] = settings.ytMusicStreamUrl
        this[Keys.YT_MUSIC_TOKEN] = settings.ytMusicToken
        this[Keys.SCROBBLE_ENABLED] = settings.scrobbleEnabled
        this[Keys.LASTFM_API_KEY] = settings.lastFmApiKey
        this[Keys.LASTFM_API_SECRET] = settings.lastFmApiSecret
        this[Keys.LASTFM_SESSION_KEY] = settings.lastFmSessionKey
        this[Keys.DISCORD_ENABLED] = settings.discordEnabled
        this[Keys.DISCORD_BRIDGE_URL] = settings.discordBridgeUrl
    }

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val THEME_STYLE = stringPreferencesKey("theme_style")
        val COLOR_FROM_ART = booleanPreferencesKey("color_from_art")
        val PREFERRED_QUALITY = stringPreferencesKey("preferred_quality")
        val LOSSLESS_FIRST = booleanPreferencesKey("lossless_first")
        val STREAM_WHEN_ONLINE = booleanPreferencesKey("stream_when_online")
        val SMART_DOWNLOADS = booleanPreferencesKey("smart_downloads")
        val WIFI_ONLY_DOWNLOADS = booleanPreferencesKey("wifi_only_downloads")
        val DOWNLOAD_QUALITY = stringPreferencesKey("download_quality")
        val YT_MUSIC_URL = stringPreferencesKey("yt_music_url")
        val YT_MUSIC_STREAM_URL = stringPreferencesKey("yt_music_stream_url")
        val YT_MUSIC_TOKEN = stringPreferencesKey("yt_music_token")
        val SCROBBLE_ENABLED = booleanPreferencesKey("scrobble_enabled")
        val LASTFM_API_KEY = stringPreferencesKey("lastfm_api_key")
        val LASTFM_API_SECRET = stringPreferencesKey("lastfm_api_secret")
        val LASTFM_SESSION_KEY = stringPreferencesKey("lastfm_session_key")
        val DISCORD_ENABLED = booleanPreferencesKey("discord_enabled")
        val DISCORD_BRIDGE_URL = stringPreferencesKey("discord_bridge_url")
    }
}
