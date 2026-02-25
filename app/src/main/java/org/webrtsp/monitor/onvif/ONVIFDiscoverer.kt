package org.webrtsp.monitor.onvif

import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update


class ONVIFDiscoverer(): AutoCloseable {
    companion object {
        init {
            System.loadLibrary("Monitor.native")
        }

        const val TAG = "ONVIFDiscoverer"
    }

    enum class State(val id: Int) {
        Preparing(0),
        Scanning(1),
        Done(2),
        Error(3),
    }

    data class Scope (
        val name: String,
        val value: String,
    )
    data class Camera (
        val urn: String,
        val endpoint: Uri,
        val name: String?,
        val scopes: List<Scope>,
    )

    private var _nativeHandle: Long? = jniOpen().let { if(it == 0L) null else it }

    private val _state = MutableStateFlow<State>(
        if(_nativeHandle == null)
            State.Error
        else
            State.Preparing
    )
    val state = _state.asStateFlow()

    private val _discovered = MutableStateFlow(persistentListOf<Camera>())
    val discovered = _discovered.asStateFlow()

    private external fun jniOpen(): Long
    private external fun jniClose(nativeHandle: Long)

    // may be called from worker thread
    private fun onStateChangedJni(state: Int) {
        State.entries.find { it.id == state }?.also { state ->
            Log.d(TAG, "state: $state")
            _state.value = state
        }
    }

    // may be called from worker thread
    private fun onDiscoveredJni(urn: String, endpoint: String, scopes: String) {
        val uri = endpoint.toUri()
        val scopes = scopes.split(' ')
            .map {
                val prefix = "onvif://www.onvif.org/"
                if(it.startsWith(prefix))
                    it.substring(prefix.length)
                else
                    String()
            }
            .filter { it.isNotEmpty() }
            .map {
                val list = it.split("/", limit = 2)
                if(list.size == 1)
                    Scope(list[0], String())
                else
                    Scope(list[0], Uri.decode(list[1]))
            }

        val name = scopes.find { it.name == "name" }?.value
        val camera = Camera(urn, uri, name, scopes)
        Log.d(TAG, "discovered: $camera")

        _discovered.update { discovered ->
            discovered.adding(camera)
        }
    }

    override fun close() {
        _nativeHandle?.also { jniClose(it) }
        _nativeHandle = null
    }
}
