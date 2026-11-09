package com.gecko.app.lyrics

import com.gecko.app.model.LyricsProviderId
import com.gecko.app.model.Track

/**
 * Uma letra encontrada por um provedor externo. [rawText] segue o formato
 * esperado por [com.gecko.app.data.LyricsRepository]: LRC sincronizado ou
 * texto simples.
 */
data class LyricsProviderResult(
    val provider: LyricsProviderId,
    val rawText: String,
)

/**
 * Fontes independentes de letras. Novos provedores podem ser adicionados aqui
 * e apareceram automaticamente na aba de provedores de letras.
 */
interface LyricsProvider {
    val id: LyricsProviderId

    suspend fun resolve(track: Track): LyricsProviderResult?
}
