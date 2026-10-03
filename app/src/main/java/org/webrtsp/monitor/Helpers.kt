package org.webrtsp.monitor

import kotlin.math.ceil

sealed interface DelayedValue<out T> {
    object Loading: DelayedValue<Nothing>
    data class Ready<T> (val value: T) : DelayedValue<T>
}

const val CROCKFORD_BASE32_ALPHABET = "0123456789abcdefghjkmnpqrstvwxyz"

fun Base32Encode(data: ByteArray): String {
    val resultSize = ceil(data.size / 5f * 8).toInt()

    return buildString {
        var high = 0u
        for(i in 0..<resultSize) {
            val sourceOffset = (i + 1) * 5 / 8

            val inByte = if(sourceOffset < data.size)
                data.elementAt(sourceOffset).toUByte().toUInt()
            else
                0u

            var outOffset = 0u
            when(i % 8) {
                0 -> {
                    outOffset = inByte shr 3
                    high = (inByte and 0x7u) shl 2
                }
                1 -> {
                    outOffset = high or ((inByte and 0xC0u) shr 6)
                    high = (inByte and 0x3Eu) shr 1
                }
                2 -> {
                    outOffset = high
                    high = (inByte and 0x1u) shl 4
                }
                3 -> {
                    outOffset = high or ((inByte and 0xF0u) shr 4)
                    high = (inByte and 0xFu) shl 1
                }
                4 -> {
                    outOffset = high or ((inByte and 0x80u) shr 7)
                    high = (inByte and 0x7Cu) shr 2
                }
                5 -> {
                    outOffset = high
                    high = (inByte and 0x3u) shl 3
                }
                6 -> {
                    outOffset = high or ((inByte and 0xE0u) shr 5)
                    high = (inByte and 0x1Fu)
                }
                7 -> outOffset = high
            }

            append(CROCKFORD_BASE32_ALPHABET.elementAt(outOffset.toInt()))
        }
    }
}
