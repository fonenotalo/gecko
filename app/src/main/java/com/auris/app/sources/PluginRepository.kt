package com.auris.app.sources

import android.content.Context
import android.net.Uri
import com.auris.app.model.AudioQuality
import com.auris.app.model.Track
import com.auris.app.model.TrackSource
import com.auris.app.util.safeFileId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder

data class SourcePlugin(
    val id: String,
    val name: String,
    val version: String,
    val baseUrl: String,
    val apiKey: String,
    val supportsLossless: Boolean,
    val searchPath: String,
    val streamPath: String,
    val fileName: String,
)

/**
 * Optional lossless source add-ons. A plugin is a small JSON manifest dropped
 * into the app's private plugins folder. No code is executed, which keeps the
 * attack surface small and the app store-friendly.
 */
class PluginRepository(
    private val context: Context,
    private val client: OkHttpClient,
) {

    private val dir = File(context.filesDir, "auris_plugins").apply { mkdirs() }
    private val _plugins = MutableStateFlow<List<SourcePlugin>>(emptyList())
    val plugins: StateFlow<List<SourcePlugin>> = _plugins.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        _plugins.value = dir.listFiles()
            ?.filter { it.isFile && it.extension.equals("json", ignoreCase = true) }
            ?.mapNotNull(::parsePlugin)
            ?.sortedBy { it.name.lowercase() }
            ?: emptyList()
    }

    suspend fun import(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.contentResolver.openInputStream(uri)
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: return@withContext false
            val json = JSONObject(text)
            val id = json.optString("id").ifBlank { "plugin_${System.currentTimeMillis()}" }
            val file = File(dir, "${safeFileId(id)}.json")
            file.writeText(text)
            reload()
            _plugins.value.any { it.id == id }
        }.getOrDefault(false)
    }

    fun remove(plugin: SourcePlugin) {
        runCatching { File(dir, plugin.fileName).delete() }
        reload()
    }

    suspend fun search(query: String): List<Track> = coroutineScope {
        _plugins.value.map { plugin ->
            async {
                withTimeoutOrNull(10_000L) {
                    searchPlugin(plugin, query)
                }.orEmpty()
            }
        }.awaitAll().flatten()
    }

    suspend fun resolve(track: Track, quality: AudioQuality): String? {
        val plugin = _plugins.value.firstOrNull { it.id == track.providerId } ?: return null
        val remoteId = track.remoteId ?: return null
        val path = plugin.streamPath
            .replace("{id}", URLEncoder.encode(remoteId, "UTF-8"))
            .replace("{quality}", quality.storageKey)
        val url = join(plugin.baseUrl, path)
        return withContext(Dispatchers.IO) {
            withTimeoutOrNull(20_000L) {
                runCatching {
                    val request = request(url, plugin.apiKey)
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            null
                        } else {
                            RemoteJson.streamUrl(response.body?.string().orEmpty(), plugin.baseUrl)
                        }
                    }
                }.getOrNull()
            }
        }
    }

    private suspend fun searchPlugin(plugin: SourcePlugin, query: String): List<Track> =
        withContext(Dispatchers.IO) {
            runCatching {
                val path = plugin.searchPath.replace(
                    "{query}",
                    URLEncoder.encode(query, "UTF-8"),
                )
                val request = request(join(plugin.baseUrl, path), plugin.apiKey)
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext emptyList()
                    RemoteJson.parseTracks(
                        body = response.body?.string().orEmpty(),
                        source = TrackSource.PLUGIN,
                        providerId = plugin.id,
                        baseUrl = plugin.baseUrl,
                    )
                }
            }.getOrDefault(emptyList())
        }

    private fun parsePlugin(file: File): SourcePlugin? = runCatching {
        val json = JSONObject(file.readText())
        val baseUrl = json.optString("baseUrl").trim().trimEnd('/')
        if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) return null
        SourcePlugin(
            id = json.optString("id").ifBlank { file.nameWithoutExtension },
            name = json.optString("name").ifBlank { file.nameWithoutExtension },
            version = json.optString("version").ifBlank { "1.0" },
            baseUrl = baseUrl,
            apiKey = json.optString("apiKey"),
            supportsLossless = json.optBoolean("supportsLossless", true),
            searchPath = json.optString("searchPath", "/api/search?q={query}"),
            streamPath = json.optString("streamPath", "/api/stream/{id}?quality={quality}"),
            fileName = file.name,
        )
    }.getOrNull()

    private fun request(url: String, apiKey: String): Request {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", "Auris/1.0 (Android)")
            .header("Accept", "application/json")
        if (apiKey.isNotBlank()) {
            builder.header("Authorization", "Bearer $apiKey")
            builder.header("X-API-Key", apiKey)
        }
        return builder.build()
    }

    private fun join(baseUrl: String, path: String): String =
        baseUrl.trimEnd('/') + "/" + path.trimStart('/')
}
