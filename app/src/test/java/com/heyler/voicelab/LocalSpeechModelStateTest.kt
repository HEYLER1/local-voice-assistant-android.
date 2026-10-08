package com.heyler.voicelab
import org.junit.Assert.*
import org.junit.Test
class LocalSpeechModelStateTest{
    @Test fun spanishIsRequestedOnlyWhenMissingAndNotPending(){
        val base=LocalSpeechModelState(available=true,supported=listOf("es-ES","en-US"))
        assertTrue(base.needsSpanish())
        assertFalse(base.copy(installed=listOf("es-ES")).needsSpanish())
        assertFalse(base.copy(pending=listOf("es-ES")).needsSpanish())
        assertFalse(base.copy(available=false).needsSpanish())
        assertFalse(base.copy(supported=listOf("en-US")).needsSpanish())
        assertTrue(base.copy(installed=listOf("es-US")).needsSpanish())
        assertEquals("es-US",base.copy(supported=listOf("es-US")).spanishLanguage())
    }
}
