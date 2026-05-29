package org.webrtsp.monitor

import android.net.Uri
import androidx.core.net.toUri

typealias SourceId = String

fun Uri.toSourceId(): String {
    return "${scheme}://${authority}"
}

data class Source (
    val endpoint: Uri,
    val user: String?,
    val password: String?,
    val name: String?,
) {
    val id: String = endpoint.toSourceId()
}

val Source?.id: String?
    get() = this?.let { this.id }

fun SourceEntity.toSource(): Source {
    return Source(
        this.endpoint.toUri(),
        this.userName,
        this.password,
        this.name,
    )
}

fun ONVIFDiscoverer.Camera.toSource(): Source {
    return Source(
        this.endpoint,
        null,
        null,
        this.name,
    )
}
