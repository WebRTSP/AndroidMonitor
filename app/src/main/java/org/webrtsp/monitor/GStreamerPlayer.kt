package org.webrtsp.monitor

import android.os.Handler
import android.os.Looper
import android.view.Surface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.URI


class GStreamerPlayer(
    url: URI,
    looper: Looper = Looper.myLooper()!!
): AutoCloseable {
    companion object {
        init {
            System.loadLibrary("Monitor.native")
        }
    }

    private var _handler = Handler(looper)
    private var _nativePlayer: Long? = jniOpen(url.toString()).let { if(it == 0L) null else it }
    private var _surface: Surface? = null

    enum class State(val id: Int) {
        Idle(0),
        Preparing(1),
        Playing(2),
        Eos(3),
        Error(4),
    }
    private val _state = MutableStateFlow<State>(
        if(_nativePlayer == null)
            State.Idle
        else
            State.Preparing
    )
    var state = _state.asStateFlow()

    private external fun jniOpen(url: String): Long
    private external fun jniClose(nativePlayer: Long)
    private external fun jniAttachSurface(nativePlayer: Long, surface: Surface)
    private external fun jniDetachSurface(nativePlayer: Long)

    // may be called from worker thread
    private fun onStateChangedJni(state: Int) {
        _handler.post {
            State.entries.find { it.id == state }?.also { state ->
                _state.value = state
                if(state == State.Eos || state == State.Error) {
                    close()
                }
            }
        }
    }

    fun attachSurface(surface: Surface) {
        if(surface == _surface) {
            return
        } else {
            detachSurface()
        }

        _surface = surface

        _nativePlayer?.also { nativePlayer ->
            jniAttachSurface(nativePlayer, surface)
        }
    }

    fun detachSurface() {
        if(_surface == null)
            return

        _surface = null

        _nativePlayer?.also { nativePlayer ->
            jniDetachSurface(nativePlayer)
        }
    }

    override fun close() {
        _nativePlayer?.also { jniClose(it) }
        _nativePlayer = null
    }
}
