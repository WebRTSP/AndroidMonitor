package org.webrtsp.monitor.restreamer

import android.net.Uri

class WebRTSPClient(
    serverUrl: Uri,
    clientId: String,
    agentId: String?,
    accessToken: String?,
): AutoCloseable {
    companion object {
        init {
            System.loadLibrary("Monitor.native")
        }

        const val TAG = "ONVIFEventsChecker"
    }
    enum class State(val id: Int) {
        Disconnected (0),
        Connecting (1),
        Connected (2),
        Error (3),
    }

    private external fun jniOpen(
        serverUrl: String,
        clientId: String,
        agentId: String?,
        accessToken: String?,
    ): Long
    private external fun jniClose(nativeHandle: Long)

    private var _nativeHandle: Long? = jniOpen(
        serverUrl.toString(),
        clientId,
        agentId,
        accessToken,
    ).let { if(it == 0L) null else it }

    override fun close() {
        _nativeHandle?.also { jniClose(it) }
        _nativeHandle = null
    }
}
