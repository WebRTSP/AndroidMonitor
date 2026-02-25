package org.webrtsp.monitor

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MotionEventRepository @Inject constructor() {
    private val _motionDetectedFlow = MutableSharedFlow<UrlOrigin>()
    val motionDetectedFlow = _motionDetectedFlow.asSharedFlow()

    suspend  fun emitMotionDetected(sourceOrigin: UrlOrigin) {
        _motionDetectedFlow.emit(sourceOrigin)
    }
}
