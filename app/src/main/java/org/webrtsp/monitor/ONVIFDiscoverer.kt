package org.webrtsp.monitor

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class ONVIFDiscoverer(): AutoCloseable {
    companion object {
        init {
            System.loadLibrary("Monitor.native")
        }
    }

    private var _nativeHandle: Long? = jniOpen().let { if(it == 0L) null else it }

    enum class State(val id: Int) {
        Idle(0),
        Scanning(1),
        Error(2),
    }
    private val _state = MutableStateFlow<State>(
        if(_nativeHandle == null)
            State.Idle
        else
            State.Error
    )
    var state = _state.asStateFlow()

    private external fun jniOpen(): Long
    private external fun jniClose(nativeHandle: Long)
    private external fun jniDiscover(nativeHandle: Long)

    // may be called from worker thread
    private fun onStateChangedJni(state: Int) {
        State.entries.find { it.id == state }?.also { state ->
            _state.value = state
        }
    }

    private fun onDiscoveredJni(endpoint: String) {
    }

    fun discover() {
        _nativeHandle?.also { jniDiscover(it) }
    }

    override fun close() {
        _nativeHandle?.also { jniClose(it) }
        _nativeHandle = null
    }
}
