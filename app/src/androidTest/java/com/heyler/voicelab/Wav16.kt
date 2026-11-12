package com.heyler.voicelab

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** WAV decoder needed only by synthetic instrumentation fixtures. */
internal object Wav16 {
    fun read(bytes: ByteArray): FloatArray {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        require(bytes.size >= 44 && String(bytes, 0, 4) == "RIFF" && String(bytes, 8, 4) == "WAVE") { "Se requiere WAV PCM mono 16 kHz." }
        var position = 12
        var valid = false
        while (position + 8 <= bytes.size) {
            val name = String(bytes, position, 4)
            val size = buffer.getInt(position + 4)
            require(size >= 0 && position.toLong() + 8 + size <= bytes.size) { "WAV incompleto." }
            val start = position + 8
            if (name == "fmt ") {
                require(size >= 16)
                valid = buffer.getShort(start).toInt() == 1 && buffer.getShort(start + 2).toInt() == 1 && buffer.getInt(start + 4) == 16000 && buffer.getShort(start + 14).toInt() == 16
            }
            if (name == "data") {
                require(valid && size % 2 == 0)
                return FloatArray(size / 2) { buffer.getShort(start + it * 2) / 32768f }
            }
            position = start + size + (size % 2)
        }
        error("El WAV no contiene muestras PCM.")
    }
}
