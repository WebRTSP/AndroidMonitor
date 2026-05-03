package org.webrtsp.monitor

import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.net.URI


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
        val endpoint: URI,
        val scopes: List<Scope>,
    )

    private var _nativeHandle: Long? = jniOpen().let { if(it == 0L) null else it }

    private val _state = MutableStateFlow<State>(
        if(_nativeHandle == null)
            State.Preparing
        else
            State.Error
    )
    val state = _state.asStateFlow()

    private val _discovered = MutableStateFlow(persistentMapOf<String, Camera>())
    val discovered = _discovered.asStateFlow()

    private external fun jniOpen(): Long
    private external fun jniClose(nativeHandle: Long)

    // may be called from worker thread
    private fun onStateChangedJni(state: Int) {
        State.entries.find { it.id == state }?.also { state ->
            _state.value = state
        }
    }

    // may be called from worker thread
    private fun onDiscoveredJni(endpoint: String, scopes: String) {
        val uri = URI(endpoint)
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
                    Scope(list[0], list[1])
            }

        _discovered.update { discovered ->
            discovered.put(uri.authority, Camera(uri, scopes))
        }
    }

    override fun close() {
        _nativeHandle?.also { jniClose(it) }
        _nativeHandle = null
    }
}
