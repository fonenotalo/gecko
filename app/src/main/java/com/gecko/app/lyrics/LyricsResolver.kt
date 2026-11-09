package com.gecko.app.lyrics

import com.gecko.app.data.Lyrics
import com.gecko.app.data.LyricsRepository
import com.gecko.app.model.AppSettings
import com.gecko.app.model.LyricsProviderId
import com.gecko.app.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Consulta o cache local antes dos provedores remotos. As letras remotas
 * encontradas são salvas no armazenamento privado do app e ficam disponíveis
 * na próxima abertura da faixa.
 */
class LyricsResolver(
    private val local: LyricsRepository,
    private val providers: List<LyricsProvider>,
) {

    suspend fun resolve(track: Track, settings: AppSettings): Lyrics? {
        val cached = withContext(Dispatchers.IO) {
            runCatching { local.load(track.id) }.getOrNull()
        }
        if (cached != null) return cached

        val enabled = providers.filter { provider ->
            provider.id.storageKey in settings.lyricsProviders
        }
        for (provider in enabled) {
            val result = runCatching {
                withContext(Dispatchers.IO) { provider.resolve(track) }
            }.getOrNull() ?: continue
            if (result.rawText.isBlank()) continue

            return withContext(Dispatchers.IO) {
                runCatching {
                    local.save(track.id, result.rawText)
                    val parsed = local.load(track.id) ?: return@withContext null
                    parsed.copy(
                        source = if (parsed.synced) {
                            "${providerTitle(result.provider)} • sincronizada"
                        } else {
                            providerTitle(result.provider)
                        },
                    )
                }.getOrNull()
            }
        }
        return null
    }

    suspend fun clearCached(track: Track) {
        withContext(Dispatchers.IO) {
            runCatching { local.delete(track.id) }
        }
    }

    private fun providerTitle(provider: LyricsProviderId): String =
        LyricsProviderId.entries.firstOrNull { it == provider }?.title ?: "Provedor de letras"
}
