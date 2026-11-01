package com.heyler.voicelab

import org.junit.Assert.*
import org.junit.Test

class SummaryReadinessTest {
    @Test fun greetingsAndRepetitionAreNotEnoughInformation(){
        assertFalse(SummaryReadiness.hasEnoughText("Hola, buenos días."))
        assertFalse(SummaryReadiness.hasEnoughText(List(40){"bueno entonces ahora"}.joinToString(" ")))
    }
    @Test fun aDevelopedExplanationCanBeSummarized(){
        assertTrue(SummaryReadiness.hasEnoughText("En esta clase estudiamos los eclipses solares. La Luna se sitúa entre la Tierra y el Sol y puede ocultar su luz. Distinguimos eclipses totales y parciales según la parte del disco solar que queda cubierta."))
    }
}
