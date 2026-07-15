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
    val maybeOnvif: Boolean,
    val accessToken: String,
)

data class Credentials(
    val agentId: String,
    val accessToken: String,
)

class ReStreamer(
    serverUrl: Uri,
    clientId: String,
    credentials: Credentials?,
): AutoCloseable {
    companion object {
        init {
            System.loadLibrary("Monitor.native")
        }

        const val TAG = "ReStreamer"
    }

    private var _nativeHandle: Long?

    private val _credentials = MutableStateFlow<Credentials?>(credentials)
    val credentials = _credentials.asStateFlow()

    enum class State(val id: Int) {
        Disconnected (0),
        Connecting (1),
        Connected (2),
        Error (3),
    }

    private val _state = MutableStateFlow<State>(State.Disconnected)
    val state = _state.asStateFlow()

    private external fun jniOpen(
        trustedCAs: ByteBuffer,
        serverUrl: String,
        clientId: String,
        agentId: String?,
        accessToken: String?,
    ): Long
    private external fun jniClose(nativeHandle: Long)
    // Had to separate from jniOpen to give members a chance to be initialized
    // before the first JNI callback from the worker thread
    private external fun jniRun(nativeHandle: Long)
    private external fun jniUpdateSources(nativeHandle: Long, sources: Array<ReStreamSource>)

    init {
        _nativeHandle = jniOpen(
            getTrustedCAs(),
            serverUrl.toString(),
            clientId,
            credentials?.agentId,
            credentials?.accessToken,
            ).let {
                if(it == 0L) null else it
            }.also { nativeHandle ->
                if(nativeHandle == null)
                    _state.value = State.Error
            }?.also { nativeHandle ->
                jniRun(nativeHandle)
            }
    }

    // may be called from worker thread
    private fun onStateChangedJni(state: Int) {
        State.entries.find { it.id == state }?.also { state ->
            Log.d(TAG, "state: $state")
            _state.value = state
        }
    }

    // may be called from worker thread
    private fun onCredentialsJni(agendId: String, accessToken: String) {
        Log.d(TAG, "Connected. agentId: $agendId")
        _credentials.tryEmit(Credentials(agendId, accessToken))
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
