package com.heyler.voicelab

import java.text.Normalizer
import kotlin.math.ceil
import kotlin.math.min

object Metrics {
    fun words(text: String): List<String> = Regex("[\\p{L}\\p{N}]+")
        .findAll(Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), ""))
        .map { it.value }.toList()
    fun errors(reference: String, hypothesis: String): Int {
        val a = words(reference); val b = words(hypothesis)
        var row = IntArray(b.size + 1) { it }
        a.forEachIndexed { i, word ->
            val next = IntArray(b.size + 1); next[0] = i + 1
            for (j in b.indices) next[j + 1] = min(min(next[j] + 1, row[j + 1] + 1), row[j] + if (word == b[j]) 0 else 1)
            row = next
        }
        return row.last()
    }
    fun percentile(values: List<Double>, p: Double): Double? {
        require(p in 0.0..1.0)
        if (values.isEmpty()) return null
        return values.sorted()[(ceil(p * values.size).toInt() - 1).coerceIn(0, values.lastIndex)]
    }
}
