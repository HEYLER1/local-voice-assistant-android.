package com.heyler.voicelab
import org.junit.Assert.assertEquals
import org.junit.Test
class ReadableAnswerTest {
    @Test fun formulaDisplaysAsPlainText() { assertEquals("Área: A = π × r².", ReadableAnswer.format("Área: ${'$'} A = \\pi \\times r^2 ${'$'}.")) }
    @Test fun ordinaryTextIsPreserved() { assertEquals("Cuesta $5 y es importante.", ReadableAnswer.format("Cuesta $5 y es importante.")) }
}
