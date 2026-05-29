package org.webrtsp.monitor

import android.net.Uri

sealed interface DelayedValue<out T> {
    object Loading: DelayedValue<Nothing>
    data class Ready<T> (val value: T) : DelayedValue<T>
}
