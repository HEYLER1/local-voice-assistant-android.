package com.heyler.voicelab

import org.junit.Assert.*
import org.junit.Test

class SummaryQualityTest{
    @Test fun screenshotPromptLeakIsRejected(){
        assertFalse(SummaryQuality.usable("## TEMA PREVIO: #### ## SISTEMAS DE ECLIPOS SOLARES #### ## ¿Qué nos dice?"))
        assertEquals("",SummaryQuality.display("NOTAS PREVIAS: devuelve solo el título"))
        assertTrue(SummaryQuality.usable("La Luna se interpone entre la Tierra y el Sol."))
    }
    @Test fun fragmentHeadingsAreNotConcepts(){
        listOf("cómo","y en el factor más importante para eso","pero la órbita de la Tierra alrededor del Sol también").forEach{assertEquals("",SummaryQuality.heading(it))}
        assertEquals("Órbita lunar",SummaryQuality.heading("### **Órbita lunar**"))
    }
    @Test fun fastestAvailableQwen35UsesQueueAndSpeed(){
        val chosen=HordeModelPolicy.choose(listOf(HordeModel("Qwen3.5-9B",1,30.0,900.0),HordeModel("Qwen3.5-4B",2,20.0,0.0),HordeModel("Qwen3.5-2B",0,99.0,0.0),HordeModel("Other-Model",10,999.0,0.0)))
        assertEquals("Qwen3.5-4B",chosen!!.name)
    }
    @Test fun noFallbackOrInventedAvailability(){
        assertNull(HordeModelPolicy.choose(listOf(HordeModel("Qwen3-8B",1,30.0,0.0),HordeModel("Qwen3.5-4B",1,Double.NaN,0.0))))
    }
}
