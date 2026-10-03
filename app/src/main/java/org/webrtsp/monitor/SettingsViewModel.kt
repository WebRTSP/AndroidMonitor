package org.webrtsp.monitor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.webrtsp.monitor.restreamer.CredentialsState
import org.webrtsp.monitor.restreamer.ReStreamerSettingsRepository
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val _settingsRepository: SettingsRepository,
    private val _reStreamerSettingsRepository: ReStreamerSettingsRepository,
) : ViewModel() {
    data class UiState(
        val trackMotion: Boolean,
        val keepScreenOn: Boolean,
        val reStreamerEnabled: Boolean,
        val credentialsState: CredentialsState,
        val agentId: String,
    )

    val uiState =
        combine(
            _settingsRepository.settingsFlow,
            _reStreamerSettingsRepository.settingsFlow,
            _reStreamerSettingsRepository.credentialsStateFlow,
        ) { settings, reStreamerSettings, credentialsState ->
            DelayedValue.Ready(
                UiState(
                    settings.trackMotion,
                    settings.keepScreenOn,
                    reStreamerSettings.reStreamerEnabled,
                    credentialsState,
                    reStreamerSettings.credentials?.agentId ?: String(),
                )
            )
        }
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

    fun setReStreamerEnabled(reStreamerEnabled: Boolean) {
        viewModelScope.launch {
            _reStreamerSettingsRepository.setReStreamerEnabled(reStreamerEnabled)
        }
    }
}
