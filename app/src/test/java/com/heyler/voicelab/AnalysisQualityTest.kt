package com.heyler.voicelab

import org.junit.Assert.*
import org.junit.Test

class AnalysisQualityTest {
    @Test fun rejectsIncompleteAndUnresolvedQuestions(){
        listOf("¿que hay un?","¿cómo se mide?","¿qué es para medir eso?","¿por qué es cualitativo?","¿por qué una porque ya los lenguajes se auto generan incluye librerías y nos vemos es que somos eh cercanos al usuario generalmente generan a tu lenguaje?").forEach{assertFalse(it,AnalysisEvidence.coherent(it))}
    }
    @Test fun acceptsContextualizedQuestionsAcrossDomains(){
        listOf("¿Cómo se mide el tamaño de un sistema de software?","¿Qué diferencia existe entre métricas cualitativas y cuantitativas?","¿Por qué se guardan los respaldos en ubicaciones diferentes?","¿Qué es la gravedad?","¿Cómo se evalúa la seguridad ciudadana?","¿Qué factores se consideran al estimar el costo de desarrollo?","¿Dónde se guardan las copias de seguridad?","¿Cuándo utiliza el equipo preguntas abiertas en las entrevistas?").forEach{assertTrue(it,AnalysisEvidence.coherent(it))}
    }
    @Test fun semanticDedupDoesNotMergeDifferentPurposes(){
        assertTrue(AnalysisEvidence.sameQuestion("¿Cómo se mide el tamaño de un sistema?","¿Cómo medir el tamaño de un sistema?"))
        assertFalse(AnalysisEvidence.sameQuestion("¿Cómo se mide el costo de un sistema?","¿Cómo se mide el tamaño de un sistema?"))
        assertFalse(AnalysisEvidence.sameQuestion("¿Qué son las métricas cualitativas?","¿Qué son las métricas cuantitativas?"))
    }
    @Test fun evidenceMustExistAndExplanationIsNotAnInterrogative(){
        assertEquals("cuántas líneas de código tiene un sistema",AnalysisEvidence.literal("El docente pregunta cuántas líneas de código tiene un sistema grande.","cuántas líneas de código tiene un sistema"))
        assertNull(AnalysisEvidence.literal("Hablamos de métricas.","¿Qué es COCOMO II?"))
        assertFalse(AnalysisEvidence.hasQuestion("Porque ya se utilizan librerías y componentes reutilizables."))
    }
    @Test fun respectsFreeServiceCooldownHeaders(){
        assertEquals(30000L,OnlineBudget.retryMillis("10"))
        assertEquals(120000L,OnlineBudget.retryMillis("120"))
        assertEquals(60000L,OnlineBudget.retryMillis("bad"))
        assertEquals(30000L,OnlineBudget.retryMillis("Thu, 01 Jan 1970 00:00:40 GMT",10000))
        assertEquals(86400000L,OnlineBudget.retryMillis("99999999"))
    }
}
