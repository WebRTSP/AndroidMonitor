package org.webrtsp.monitor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val _settingsRepository: SettingsRepository
) : ViewModel() {
   val trackMotion = _settingsRepository.trackMotionFlow
        .map { DelayedValue.Ready(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DelayedValue.Loading
        )

    val keepScreenOn = _settingsRepository.keepScreenOnFlow
        .map { DelayedValue.Ready(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DelayedValue.Loading
        )

    fun setTrackMotion(trackMotion: Boolean) {
        viewModelScope.launch {
            _settingsRepository.setTrackMotion(trackMotion)
        }
    }

    fun setKeepScreenOn(keepScreenOn: Boolean) {
        viewModelScope.launch {
            _settingsRepository.setKeepScreenOn(keepScreenOn)
        }
    }
}
