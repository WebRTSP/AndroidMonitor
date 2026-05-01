package org.webrtsp.monitor

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class ONVIFDiscoverer(): AutoCloseable {
    companion object {
        init {
            System.loadLibrary("Monitor.native")
        }

        const val TAG = "ONVIFDiscoverer"
    }

    private var _nativeHandle: Long? = jniOpen().let { if(it == 0L) null else it }

    enum class State(val id: Int) {
        Preparing(0),
        Scanning(1),
        Done(2),
        Error(3),
    }
    private val _state = MutableStateFlow<State>(
        if(_nativeHandle == null)
            State.Preparing
        else
            State.Error
    )
    var state = _state.asStateFlow()

    private external fun jniOpen(): Long
    private external fun jniClose(nativeHandle: Long)

    // may be called from worker thread
    private fun onStateChangedJni(state: Int) {
        State.entries.find { it.id == state }?.also { state ->
            Log.i(TAG, "!!!!!!!!!!!!!!!! $state")
            _state.value = state
        }
    }

    private fun onDiscoveredJni(endpoint: String) {
        Log.i(TAG, "!!!!!!!!!!!!!!!! $endpoint")
    }

    override fun close() {
        _nativeHandle?.also { jniClose(it) }
        _nativeHandle = null
    }
}
