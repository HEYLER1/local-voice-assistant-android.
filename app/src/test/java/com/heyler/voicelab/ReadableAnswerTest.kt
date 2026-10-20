package com.heyler.voicelab
import org.junit.Assert.assertEquals
import org.junit.Test
class ReadableAnswerTest {
    @Test fun formulaDisplaysAsPlainText() { assertEquals("Área: A = π × r².", ReadableAnswer.format("Área: ${'$'} A = \\pi \\times r^2 ${'$'}.")) }
    @Test fun ordinaryTextIsPreserved() { assertEquals("Cuesta $5 y es importante.", ReadableAnswer.format("Cuesta $5 y es importante.")) }
    @Test fun onlinePhysicsAnswerPreservesFormulaNumbersAndUnits(){assertEquals("W = 10 N × 3 m = 30 J",ReadableAnswer.format("\\(W = 10\\ \\text{N} \\times 3\\ \\text{m} = 30\\ \\text{J}\\)"))}
    @Test fun inlineAndHeadingMarkupRemainReadable(){assertEquals("Ejemplo\nF y θ",ReadableAnswer.format("### Ejemplo\n**\\(F\\)** y \\theta"))}
}
