package org.webrtsp.monitor.onvif

import android.net.Uri
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow


class ONVIFEventsChecker(
    url: Uri,
    userName: String?,
    password: String?,
): AutoCloseable {
    companion object {
        init {
            System.loadLibrary("Monitor.native")
        }

        const val TAG = "ONVIFEventsChecker"
    }

    enum class State(val id: Int) {
        Idle(0),
        Preparing(1),
        Checking(2),
        Error(3),
    }

    private var _nativeHandle: Long? = jniOpen(
        url.toString(),
        userName,
        password,
    ).let { if(it == 0L) null else it }

    private val _state = MutableStateFlow<State>(
        if(_nativeHandle == null)
            State.Error
        else
            State.Idle
    )
    val state = _state.asStateFlow()

    private val _motionDetectedFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val motionDetectedFlow = _motionDetectedFlow.asSharedFlow()

    private external fun jniOpen(
        url: String,
        userName: String?,
        password: String?,
    ): Long
    private external fun jniClose(nativeHandle: Long)
    private external fun jniCheckEvents(nativeHandle: Long)

    // may be called from worker thread
    private fun onStateChangedJni(state: Int) {
        State.entries.find { it.id == state }?.also { state ->
            if(state == State.Error)
                Log.d(TAG, "state: $state")
            _state.value = state
        }
    }

    // may be called from worker thread
    private fun onMotionDetectedJni() {
        Log.d(TAG, "motion detected!")

        _motionDetectedFlow.tryEmit(Unit)
    }

    fun checkEvents() {
        _nativeHandle?.also { jniCheckEvents(it) }
    }

    override fun close() {
        _nativeHandle?.also { jniClose(it) }
        _nativeHandle = null
    }
}
