package org.webrtsp.monitor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import javax.inject.Inject


@HiltViewModel
class MotionViewActivityViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    motionEventRepository: MotionEventRepository,
    private val _motionEventHandler: MotionEventHandler,
) : ViewModel() {
    var notificationId: Int? = null
        set(value) {
            field?.also { id ->
                _motionEventHandler.cancelNotification(id)
            }
            field = value
        }

    val hasSourceFlow = settingsRepository.activeSourceFlow
        .map { url -> DelayedValue.Ready(url != null) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            initialValue = DelayedValue.Loading
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val keepActiveFlow = motionEventRepository.motionDetectedFlow
        .map { Unit }
        .onStart { emit(Unit) }
        .transformLatest {
            emit(true)
            delay(settingsRepository.motionPreviewDurationFlow.first())
            emit(false)
        }

    private fun cancelNotification() {
        notificationId = null
    }

    override fun onCleared() {
        cancelNotification()
    }
}
