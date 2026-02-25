package org.webrtsp.monitor

sealed interface DelayedValue<out T> {
    object Loading: DelayedValue<Nothing>
    data class Ready<T> (val value: T) : DelayedValue<T>
}
