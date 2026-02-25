package org.webrtsp.monitor.onvif

import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update


class ONVIFUrlFetcher(
    url: Uri,
    userName: String?,
    password: String?,
): AutoCloseable {
    companion object {
        init {
            System.loadLibrary("Monitor.native")
        }

        const val TAG = "ONVIFUrlFetcher"
    }

    enum class State(val id: Int) {
        Preparing(0),
        Fetching(1),
        Done(2),
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
            State.Preparing
    )
    val state = _state.asStateFlow()

    private val _fetched = MutableStateFlow(persistentListOf<Uri>())
    val fetched = _fetched.asStateFlow()

    private external fun jniOpen(
        url: String,
        userName: String?,
        password: String?,
    ): Long
    private external fun jniClose(nativeHandle: Long)

    // may be called from worker thread
    private fun onStateChangedJni(state: Int) {
        State.entries.find { it.id == state }?.also { state ->
            Log.d(TAG, "state: $state")
            _state.value = state
        }
    }

    // may be called from worker thread
    private fun onUrlFetchedJni(url: String) {
        val url = url.toUri()
        Log.d(TAG, "fetched: $url")

        _fetched.update { discovered ->
            discovered.adding(url)
        }
    }

    override fun close() {
        _nativeHandle?.also { jniClose(it) }
        _nativeHandle = null
    }
}
