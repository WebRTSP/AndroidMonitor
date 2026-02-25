package org.webrtsp.monitor

import android.os.SystemClock
import android.view.Surface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.random.Random

enum class PlaybackState {
    Loading,
    Idle,
    Preparing,
    Playing,
    Eos,
    Error,
}

@HiltViewModel
class SourceViewViewModel @Inject constructor(
) : ViewModel() {
    private var _playbackJob: Job? = null
    private val _playbackState = MutableStateFlow<PlaybackState>(PlaybackState.Loading)
    val playbackState = _playbackState.asStateFlow()

    init {
        viewModelScope.launch {
            playbackState.collect { playbackState ->
                println("!!!!!!!!!!!!!!!!!!!! playbackState: $playbackState")
            }
        }
    }

    fun play() {
        if(_playbackJob != null)
            return

        _playbackJob = viewModelScope.launch {
            val stepDelay = 2000L
            PlaybackState.entries
            while(isActive) {
                for(step in arrayOf(PlaybackState.Preparing, PlaybackState.Playing, PlaybackState.Error)) {
                    _playbackState.value = step
                    delay(stepDelay)
                }
            }
        }
    }

    fun stop() {
        _playbackJob?.cancel()
        _playbackJob = null
    }
}
