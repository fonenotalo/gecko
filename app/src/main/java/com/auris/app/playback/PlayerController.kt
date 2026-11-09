package com.auris.app.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.auris.app.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class ResolvedTrack(
    val track: Track,
    val uri: Uri,
)

data class PlayerSnapshot(
    val connected: Boolean = false,
    val currentMediaId: String? = null,
    val index: Int = 0,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_ALL,
    val queueSize: Int = 0,
    val quality: AudioQualityInfo? = null,
    val error: String? = null,
)

/**
 * Single connection to the background [PlaybackService]. UI and ViewModel talk
 * to the session only through this class.
 */
class PlayerController(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var controller: MediaController? = null
    private var connecting = false
    private var ticker: Job? = null

    private val _snapshot = MutableStateFlow(PlayerSnapshot())
    val snapshot: StateFlow<PlayerSnapshot> = _snapshot.asStateFlow()

    fun connect() {
        if (controller != null || connecting) return
        connecting = true
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener(
            {
                connecting = false
                try {
                    val mediaController = future.get()
                    controller = mediaController
                    attach(mediaController)
                } catch (error: Exception) {
                    _snapshot.update {
                        it.copy(error = "Não foi possível conectar à reprodução: ${error.message}")
                    }
                }
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    private fun attach(mediaController: MediaController) {
        mediaController.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                sync(player)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                _snapshot.update {
                    it.copy(
                        currentMediaId = mediaItem?.mediaId,
                        quality = null,
                        positionMs = 0L,
                    )
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                updateQuality(mediaController)
            }

            override fun onPlayerError(error: PlaybackException) {
                _snapshot.update {
                    it.copy(error = humanError(error), isBuffering = false)
                }
            }
        })
        sync(mediaController)
        updateQuality(mediaController)
        startTicker()
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                val player = controller
                if (player != null) {
                    if (player.isConnected) {
                        _snapshot.update { current ->
                            current.copy(
                                connected = true,
                                isPlaying = player.isPlaying,
                                isBuffering = player.playbackState == Player.STATE_BUFFERING,
                                positionMs = player.currentPosition.coerceAtLeast(0L),
                                durationMs = player.duration.takeIf { it > 0L } ?: current.durationMs,
                                bufferedMs = player.bufferedPosition.coerceAtLeast(0L),
                                queueSize = player.mediaItemCount,
                                shuffle = player.shuffleModeEnabled,
                                repeatMode = player.repeatMode,
                            )
                        }
                    } else {
                        _snapshot.update { it.copy(connected = false) }
                    }
                }
                delay(500L)
            }
        }
    }

    private fun sync(player: Player) {
        _snapshot.update { current ->
            current.copy(
                connected = true,
                currentMediaId = player.currentMediaItem?.mediaId,
                index = player.currentMediaItemIndex.coerceAtLeast(0),
                isPlaying = player.isPlaying,
                isBuffering = player.playbackState == Player.STATE_BUFFERING,
                positionMs = player.currentPosition.coerceAtLeast(0L),
                durationMs = player.duration.takeIf { it > 0L } ?: current.durationMs,
                bufferedMs = player.bufferedPosition.coerceAtLeast(0L),
                shuffle = player.shuffleModeEnabled,
                repeatMode = player.repeatMode,
                queueSize = player.mediaItemCount,
            )
        }
    }

    private fun updateQuality(mediaController: MediaController) {
        val audioGroup = mediaController.currentTracks.groups
            .firstOrNull { it.type == C.TRACK_TYPE_AUDIO && it.isSelected }
            ?: mediaController.currentTracks.groups.firstOrNull { it.type == C.TRACK_TYPE_AUDIO }

        var info: AudioQualityInfo? = null
        if (audioGroup != null) {
            for (index in 0 until audioGroup.length) {
                if (audioGroup.isTrackSupported(index)) {
                    info = AudioQualityInfo.fromFormat(audioGroup.getTrackFormat(index))
                    break
                }
            }
        }
        _snapshot.update { it.copy(quality = info) }
    }

    fun play(queue: List<ResolvedTrack>, startIndex: Int, shuffle: Boolean = false) {
        val player = controller ?: return
        if (queue.isEmpty()) return
        _snapshot.update { it.copy(error = null) }
        player.shuffleModeEnabled = shuffle
        player.setMediaItems(
            queue.map { it.toMediaItem() },
            startIndex.coerceIn(0, queue.lastIndex),
            0L,
        )
        player.prepare()
        player.play()
    }

    fun addToQueue(items: List<ResolvedTrack>, playNext: Boolean = false) {
        val player = controller ?: return
        if (items.isEmpty()) return
        if (player.mediaItemCount == 0) {
            player.setMediaItem(items.first().toMediaItem())
            player.prepare()
            return
        }
        if (playNext) {
            var index = player.currentMediaItemIndex + 1
            items.forEach { item ->
                player.addMediaItem(index.coerceAtMost(player.mediaItemCount), item.toMediaItem())
                index++
            }
        } else {
            player.addMediaItems(items.map { it.toMediaItem() })
        }
    }

    fun removeAt(index: Int) {
        val player = controller ?: return
        if (index in 0 until player.mediaItemCount) player.removeMediaItem(index)
    }

    fun seekToQueueItem(index: Int) {
        val player = controller ?: return
        if (index in 0 until player.mediaItemCount) {
            player.seekTo(index, 0L)
            player.play()
        }
    }

    fun togglePlayPause() {
        val player = controller ?: return
        _snapshot.update { it.copy(error = null) }
        if (player.playbackState == Player.STATE_IDLE) player.prepare()
        if (player.isPlaying) player.pause() else player.play()
    }

    fun next() {
        val player = controller ?: return
        if (player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
        } else if (player.repeatMode == Player.REPEAT_MODE_ALL && player.mediaItemCount > 0) {
            player.seekTo(0, 0L)
        }
    }

    fun previous() {
        val player = controller ?: return
        if (player.currentPosition > 3_000L) {
            player.seekTo(0L)
        } else if (player.hasPreviousMediaItem()) {
            player.seekToPreviousMediaItem()
        } else {
            player.seekTo(0L)
        }
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs.coerceAtLeast(0L))
    }

    fun seekToFraction(fraction: Float) {
        val player = controller ?: return
        val duration = player.duration.takeIf { it > 0L } ?: return
        player.seekTo((duration * fraction.coerceIn(0f, 1f)).toLong())
    }

    fun setShuffle(enabled: Boolean) {
        controller?.shuffleModeEnabled = enabled
    }

    fun toggleShuffle() {
        controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    }

    fun cycleRepeat() {
        val player = controller ?: return
        player.repeatMode = when (player.repeatMode) {
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_OFF
            else -> Player.REPEAT_MODE_ALL
        }
    }

    fun stop() {
        val player = controller ?: return
        player.stop()
        player.clearMediaItems()
    }

    fun clearError() {
        _snapshot.update { it.copy(error = null) }
    }

    fun disconnect() {
        ticker?.cancel()
        controller?.release()
        controller = null
        scope.cancel()
    }

    private fun humanError(error: PlaybackException): String = when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        -> "Sem conexão com a fonte de áudio. Verifique a rede ou baixe a música."
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ->
            "Arquivo não encontrado. Ele pode ter sido movido ou removido."
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FAILED,
        -> "Este codec não pôde ser reproduzido neste aparelho."
        else -> error.message ?: "Não foi possível reproduzir esta faixa."
    }
}

private fun ResolvedTrack.toMediaItem(): MediaItem =
    MediaItem.Builder()
        .setMediaId(track.id)
        .setUri(uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(track.title)
                .setArtist(track.displayArtist)
                .setAlbumTitle(track.displayAlbum)
                .setArtworkUri(track.artworkUri ?: track.artworkUrl?.let(Uri::parse))
                .setIsPlayable(true)
                .build(),
        )
        .build()
