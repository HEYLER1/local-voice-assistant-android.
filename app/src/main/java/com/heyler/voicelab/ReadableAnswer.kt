package com.heyler.voicelab

/** Presentation only: preserve the model output separately for review. */
object ReadableAnswer {
    fun format(text: String): String = text
        .replace("\\pi", "π").replace("\\times", "×").replace("\\cdot", "·")
        .replace("\\approx", "≈").replace("\\mathrm{m}", "m").replace("\\text{m}", "m").replace("\\,", " ")
        .replace("\\(", "").replace("\\)", "").replace("\\[", "").replace("\\]", "")
        .replace(Regex("\\\\(?:text|mathrm|mathbf|mathit)\\{([^{}]*)\\}")) { it.groupValues[1] }
        .replace(Regex("\\\\mathbf\\s+([a-zA-Z])")) { it.groupValues[1] }
        .replace("\\theta", "θ").replace("\\cos", "cos").replace("\\circ", "°").replace("\\ ", " ")
        .replace(Regex("(?m)^\\s*#{1,6} +"), "")
        .replace(Regex("\\*\\*([^*]+)\\*\\*")) { it.groupValues[1] }
        .replace("^{2}", "²").replace("^2", "²")
        .replace(Regex("(?s)\\${'$'}([^${'$'}]+)\\${'$'}")) { it.groupValues[1].trim() }
}
