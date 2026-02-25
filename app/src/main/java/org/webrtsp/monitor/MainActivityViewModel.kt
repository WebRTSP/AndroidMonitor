package org.webrtsp.monitor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MainActivityViewModel @Inject constructor(
    private val _permissionsRepository: PermissionsRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {
    val keepScreenOn = settingsRepository.keepScreenOnFlow
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val hasSourceFlow = settingsRepository.activeSourceFlow
        .map { url -> DelayedValue.Ready(url != null) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            initialValue = DelayedValue.Loading
        )

    private val _fullScreenIntentPermissionRequested =
        settingsRepository.fullScreenIntentPermissionRequested
            .map { requested -> DelayedValue.Ready(requested) }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                initialValue = DelayedValue.Loading
            )

    private val _showFullScreenIntentRequestFlow = MutableStateFlow(false)
    val showFullScreenIntentRequestFlow = _showFullScreenIntentRequestFlow
        .combine(_fullScreenIntentPermissionRequested) { showRequest, alreadyRequested ->
            when(alreadyRequested) {
                DelayedValue.Loading -> false
                is DelayedValue.Ready<Boolean> -> {
                    showRequest &&
                    !alreadyRequested.value &&
                    !_permissionsRepository.fullScreenIntentAllowed
                }
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    fun maybeRequestFullScreenIntentPermission() {
        _showFullScreenIntentRequestFlow.value = true
    }

    fun requestFullScreenIntentPermission() {
        if(!_showFullScreenIntentRequestFlow.value)
            return

        _showFullScreenIntentRequestFlow.value = false

        _permissionsRepository.requestFullScreenIntentPermission()
    }
}
