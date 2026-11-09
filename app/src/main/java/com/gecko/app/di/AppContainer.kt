package com.gecko.app.di

import android.app.Application
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.gecko.app.data.ArtworkStore
import com.gecko.app.data.DemoContent
import com.gecko.app.data.DownloadRepository
import com.gecko.app.data.LocalMusicRepository
import com.gecko.app.data.LyricsRepository
import com.gecko.app.data.RecentStore
import com.gecko.app.data.SettingsRepository
import com.gecko.app.integrations.DiscordPresenceClient
import com.gecko.app.integrations.LastFmClient
import com.gecko.app.lyrics.LyricsResolver
import com.gecko.app.lyrics.SimpMusicLyricsProvider
import com.gecko.app.playback.PlayerController
import com.gecko.app.sources.PluginRepository
import com.gecko.app.sources.YouTubeMusicApi
import com.gecko.app.sources.YouTubeMusicBridge
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Tiny manual DI container. No reflection-heavy framework, fast cold start. */
class AppContainer(private val app: Application) {

    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    val settings = SettingsRepository(app)
    val localMusic = LocalMusicRepository(app)
    val artwork = ArtworkStore(app)
    val lyrics = LyricsRepository(app)
    val lyricsResolver = LyricsResolver(
        local = lyrics,
        providers = listOf(SimpMusicLyricsProvider(httpClient)),
    )
    val downloads = DownloadRepository(app, httpClient)
    val recent = RecentStore(app)
    val plugins = PluginRepository(app, httpClient)
    val youTubeMusicApi = YouTubeMusicApi(httpClient)
    val youTubeMusic = YouTubeMusicBridge(httpClient)
    val player = PlayerController(app)
    val lastFm = LastFmClient(app, httpClient)
    val discord = DiscordPresenceClient(httpClient)
    val demo = DemoContent(app)

    fun isOnline(): Boolean {
        val manager = app.getSystemService(ConnectivityManager::class.java) ?: return false
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun isUnmetered(): Boolean {
        val manager = app.getSystemService(ConnectivityManager::class.java) ?: return false
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }
}
