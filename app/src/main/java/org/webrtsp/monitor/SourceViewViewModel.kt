package org.webrtsp.monitor

import android.os.SystemClock
import android.view.Surface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.random.Random

enum class PlaybackState {
    Loading,
    NoUrl,
    Idle,
    Preparing,
    Playing,
    Eos,
    Error,
}

@HiltViewModel
class SourceViewViewModel @Inject constructor(
    sourceRepository: SourceRepository
) : ViewModel() {
    companion object {
        private const val RECONNECT_MIN_DELAY = 3 // seconds
        private const val RECONNECT_MAX_DELAY = 5
    }

    private var _surface: Surface? = null
    private var _player: GStreamerPlayer? = null
    private val _reconnectFlow = MutableStateFlow(SystemClock.elapsedRealtime())

    @OptIn(ExperimentalCoroutinesApi::class)
    val playbackState: StateFlow<PlaybackState> = sourceRepository.urlFlow
        .combine(_reconnectFlow) { sourceUrl, _ ->
            sourceUrl
        }
        .flatMapLatest { sourceUrl ->
            callbackFlow {
                if(sourceUrl == null) {
                    send(PlaybackState.NoUrl)
                } else {
                    send(PlaybackState.Preparing)

                    val player = GStreamerPlayer(sourceUrl).also { player ->
                        _player = player
                    }

                    player.use { player ->
                        _surface?.also { surface ->
                            player.attachSurface(surface)
                        }

                        player.state.collect() { state ->
                            when(state) {
                                GStreamerPlayer.State.Idle -> PlaybackState.Idle
                                GStreamerPlayer.State.Preparing -> PlaybackState.Preparing
                                GStreamerPlayer.State.Playing -> PlaybackState.Playing
                                GStreamerPlayer.State.Eos -> PlaybackState.Eos
                                GStreamerPlayer.State.Error -> PlaybackState.Error
                            }.also { state ->
                                send(state)
                            }
                        }
                    }

                    _player = null

                    awaitClose()
                }
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            initialValue = PlaybackState.Loading,
        )

    init {
        viewModelScope.launch {
            playbackState.collect { playbackState ->
                if(arrayOf(PlaybackState.Eos, PlaybackState.Error).contains(playbackState)) {
                    delay(Random.nextLong(
                        RECONNECT_MIN_DELAY * 1000L,
                        RECONNECT_MAX_DELAY * 1000L))
                    _reconnectFlow.value = SystemClock.elapsedRealtime()
                }
            }
        }
    }

    fun surfaceCreated(surface: Surface) {
        _surface = surface
        _player?.attachSurface(surface)
    }

    fun surfaceDestroyed() {
        _surface = null
        _player?.detachSurface()
    }

    fun detachSurface() {
        _player?.detachSurface()
    }

    fun resumePlayback() {
        _player?.also { player ->
            _surface?.also { surface ->
                player.attachSurface(surface)
            }
        } ?: run {
            _reconnectFlow.value = SystemClock.elapsedRealtime()
        }
    }

    fun stop() {
        _player?.close()
        _player = null
    }
}
