package com.auris.app.di

import android.app.Application
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.auris.app.data.ArtworkStore
import com.auris.app.data.DemoContent
import com.auris.app.data.DownloadRepository
import com.auris.app.data.LocalMusicRepository
import com.auris.app.data.LyricsRepository
import com.auris.app.data.RecentStore
import com.auris.app.data.SettingsRepository
import com.auris.app.integrations.DiscordPresenceClient
import com.auris.app.integrations.LastFmClient
import com.auris.app.playback.PlayerController
import com.auris.app.sources.PluginRepository
import com.auris.app.sources.YouTubeMusicBridge
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
    val downloads = DownloadRepository(app, httpClient)
    val recent = RecentStore(app)
    val plugins = PluginRepository(app, httpClient)
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
