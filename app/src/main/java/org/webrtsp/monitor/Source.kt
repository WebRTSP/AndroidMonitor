package org.webrtsp.monitor

import android.net.Uri
import androidx.core.net.toUri
import org.webrtsp.monitor.onvif.ONVIFDiscoverer

typealias UrlOrigin = String

fun Uri.toOrigin(): UrlOrigin {
    return "${scheme}://${authority}"
}

data class Source (
    val id: SourceId?, // id in Room db
    val url: Uri,
    val origin: SourceOrigin,
    val userName: String?,
    val password: String?,
    val name: String?,
    val urn: String?,
    val online: Boolean?,
)

val Source?.online get() = this?.online ?: false
val Source?.onvif get() = this?.origin == SourceOrigin.WsDiscovery
val Source?.maybeOnvif get() = this?.url?.scheme?.startsWith("http", true) ?: false
val Source?.userDefined get() = this?.origin == SourceOrigin.User
fun Source.isTheSameAs(other: Source): Boolean {
    return when {
        (id == null) != (other.id == null) -> false
        id != null && other.id != null -> id == other.id
        onvif == other.onvif -> url.toOrigin() == other.url.toOrigin()
        else -> false
    }
}

fun SourceEntity.toSource(): Source {
    return Source(
        id,
        url.toUri(),
        origin,
        userName,
        password,
        name,
        urn,
        false,
    )
}

fun ONVIFDiscoverer.Camera.toSource(): Source {
    return Source(
        null,
        endpoint,
        SourceOrigin.WsDiscovery,
        null,
        null,
        name,
        urn,
        true,
    )
}
