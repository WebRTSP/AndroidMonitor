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
import org.webrtsp.monitor.onvif.ONVIFUrlFetchRepository
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
    settingsRepository: SettingsRepository,
    urlFetchRepository: ONVIFUrlFetchRepository,
) : ViewModel() {
    companion object {
        private const val RECONNECT_MIN_DELAY = 3 // seconds
        private const val RECONNECT_MAX_DELAY = 5
    }

    private var _surface: Surface? = null
    private var _player: GStreamerPlayer? = null
    private val _reconnectFlow = MutableStateFlow(SystemClock.elapsedRealtime())

    @OptIn(ExperimentalCoroutinesApi::class)
    val playbackState: StateFlow<PlaybackState> = settingsRepository.activeSourceFlow
        .combine(_reconnectFlow) { source, _ -> source }
        .flatMapLatest { source ->
            callbackFlow {
                if(source == null) {
                    send(PlaybackState.NoUrl)
                } else {
                    send(PlaybackState.Preparing)

                    val url = if(source.onvif || source.maybeOnvif) {
                        urlFetchRepository.fetchMediaUrl(
                            source.url,
                            source.userName,
                            source.password)
                    } else {
                       source.url
                    }

                    if(url != null) {
                        val player = GStreamerPlayer(url).also { player ->
                            _player = player
                        }

                        player.use { player ->
                            _surface?.also { surface ->
                                player.attachSurface(surface)
                            }

                            player.state.collect { state ->
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
                    } else {
                        send(PlaybackState.NoUrl)
                    }
                }

                awaitClose()
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
                if(
                    arrayOf(
                        PlaybackState.Eos,
                        PlaybackState.Error,
                        PlaybackState.NoUrl)
                    .contains(playbackState)
                ) {
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
