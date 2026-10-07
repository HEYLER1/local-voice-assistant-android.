package com.heyler.voicelab

/** Presentation only: preserve the model output separately for review. */
object ReadableAnswer {
    fun format(text: String): String = text
        .replace("\\pi", "π").replace("\\times", "×").replace("\\cdot", "·")
        .replace("\\approx", "≈").replace("\\mathrm{m}", "m").replace("\\text{m}", "m").replace("\\,", " ")
        .replace("^{2}", "²").replace("^2", "²")
        .replace(Regex("(?s)\\${'$'}([^${'$'}]+)\\${'$'}")) { it.groupValues[1].trim() }
}
