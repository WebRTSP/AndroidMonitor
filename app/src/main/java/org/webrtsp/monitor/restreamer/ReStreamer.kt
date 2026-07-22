package org.webrtsp.monitor.restreamer

import android.net.Uri
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import kotlin.io.encoding.Base64

data class ReStreamSource(
    val id: String,
    val url: String,
    val userName: String?,
    val password: String?,
    val name: String?,
)

class ReStreamer(
    serverUrl: Uri,
    clientId: String,
    agentId: String?,
    accessToken: String?,
): AutoCloseable {
    companion object {
        init {
            System.loadLibrary("Monitor.native")
        }

        const val TAG = "ReStreamer"
    }
    enum class State(val id: Int) {
        Disconnected (0),
        Connecting (1),
        Connected (2),
        Error (3),
    }

    private var _nativeHandle: Long? = jniOpen(
        getTrustedCAs(),
        serverUrl.toString(),
        clientId,
        agentId,
        accessToken,
    ).let { if(it == 0L) null else it }

    private val _state = MutableStateFlow<State>(
        if(_nativeHandle == null)
            State.Error
        else
            State.Disconnected
    )
    val state = _state.asStateFlow()

    private external fun jniOpen(
        trustedCAs: ByteBuffer,
        serverUrl: String,
        clientId: String,
        agentId: String?,
        accessToken: String?,
    ): Long
    private external fun jniClose(nativeHandle: Long)
    private external fun jniUpdateSources(nativeHandle: Long, sources: Array<ReStreamSource>)

    // may be called from worker thread
    private fun onStateChangedJni(state: Int) {
        State.entries.find { it.id == state }?.also { state ->
            Log.d(TAG, "state: $state")
            _state.value = state
        }
    }

    // may be called from worker thread
    private fun onRegisteredJni(agendId: String, accessToken: String?) {
        Log.d(TAG, "Registered. agentId: $agendId")
    }

    private fun getTrustedCAs() : ByteBuffer {
        val trustManagerFactory = TrustManagerFactory.getInstance(
            TrustManagerFactory.getDefaultAlgorithm()
        ).apply {
            init(null as KeyStore?)
        }

        val trustManager = trustManagerFactory.trustManagers
            .filterIsInstance<X509TrustManager>()
            .firstOrNull() ?: return ByteBuffer.allocateDirect(0)

        val s = trustManager.acceptedIssuers.size
        return buildString {
            trustManager.acceptedIssuers.forEach {
                append("-----BEGIN CERTIFICATE-----\n")
                append(Base64.encode(it.encoded))
                append("\n-----END CERTIFICATE-----\n")
            }
        }.let {
            ByteBuffer.allocateDirect(it.length).apply {
                put(it.toByteArray(Charsets.US_ASCII))
                flip()
            }
        }
    }

    override fun close() {
        _nativeHandle?.also { jniClose(it) }
        _nativeHandle = null
    }

    fun updateSources(sources: List<ReStreamSource>) {
        _nativeHandle?.also { nativeHandle ->
            jniUpdateSources(nativeHandle, sources.toTypedArray())
        }
    }
}
