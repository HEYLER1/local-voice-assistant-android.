package com.heyler.voicelab
import org.junit.Assert.*
import org.junit.Test
class ConversationRulesTest {
    @Test fun questionsInsideNarration(){val q=ConversationRules.questions("Estamos filmando un eclipse. ¿Por qué el hemisferio norte recibe más eclipses totales que el hemisferio sur? Luego viajamos a Burgos.");assertEquals(1,q.size);assertTrue(q[0].contains("hemisferio norte"));assertFalse(q[0].contains("Burgos"))}
    @Test fun unpunctuatedQuestion(){assertEquals(1,ConversationRules.questions("y cómo es que todavía no sabemos qué causan esas bandas de sombra en el suelo").size)}
    @Test fun noisyFragmentsAreNotQuestions(){for(text in listOf("¿Por qué el episodio norte?","¿a qué la hay?","¿estás siendo tapado?","¿Pero qué raro?","que es perfecto para la totalidad y nos reuniremos con la nasa","no sé si se explica"))assertTrue(text,ConversationRules.questions(text).isEmpty())}
    @Test fun shortMeaningfulDefinitions(){assertEquals(1,ConversationRules.questions("¿Qué es la gravedad?").size);assertEquals(1,ConversationRules.questions("¿Cómo funciona la fotosíntesis?").size);assertTrue(ConversationRules.questions("¿Qué es esto?").isEmpty())}
    @Test fun narrationAfterQuestionIsExcluded(){val q=ConversationRules.questions("cómo es que todavía no sabemos qué causan esas bandas de sombra en el suelo pero qué raro okay vamos camino a burgos");assertEquals(1,q.size);assertTrue(q[0].endsWith("suelo?"))}
    @Test fun spansKeepTheOriginOfSplitQuestions(){val text="Estamos estudiando. ¿Cómo se calcula el área de un círculo?";assertEquals(text.indexOf("¿"),ConversationRules.spans(text).single().start)}
    @Test fun statementsDoNotTriggerAnswers(){assertTrue(ConversationRules.questions("El eclipse ocurre cuando la Luna tapa al Sol.").isEmpty())}
    @Test fun extractedQuotesRemainLiteral(){val text="El eclipse solar ocurre cuando la Luna pasa delante del Sol.";assertEquals(listOf(text),ConversationRules.salient(text))}
    @Test fun incompleteNoiseQuestionsAreRejected(){for(q in listOf("¿que hay un ?","¿Qué es un?","¿Por qué es la?","¿Cómo se calcula el área de un?"))assertTrue(q,ConversationRules.questions(q).isEmpty())}
    @Test fun consecutiveCompleteQuestionsSurvive(){assertEquals(2,ConversationRules.questions("¿Qué es la gravedad? ¿Cómo funciona la fotosíntesis?").size)}
    @Test fun teachingQuestionsAboutQuantityAndPurposeAreDetected(){
        assertEquals(3,ConversationRules.questions("¿Cuántas líneas de código tiene un sistema grande? ¿Para qué sirve documentar un sistema? ¿Qué diferencia existe entre métricas cualitativas y cuantitativas?").size)
    }
    @Test fun subordinateQuestionWordsInsideAnExplanationDoNotTrigger(){
        assertTrue(ConversationRules.questions("Hemos definido qué es un sistema de gestión y hemos dicho que son instrumentos de carácter voluntario.").isEmpty())
        assertTrue(ConversationRules.questions("La contribución es materia legal y la normativa peruana es muy exigente.").isEmpty())
    }
    @Test fun unpunctuatedQuestionsNeedAQuestionLeadIn(){
        val transcript="Me preguntaban sus compañeros justamente no cómo podemos hacer para tomar previsiones pero el Ministerio de Trabajo revisa los reportes."
        val found=ConversationRules.questions(transcript)
        assertEquals(1,found.size)
        assertTrue(found.single().contains("cómo podemos hacer para tomar previsiones"))
        assertFalse(found.single().contains("Ministerio"))
    }
    @Test fun spokenQuestionDoesNotAbsorbItsAnswerOrFollowingExplanation(){
        val transcript="¿Por qué la empresa tiene equipos en movimiento? Porque los accidentes pueden ocurrir. Entonces se revisan los controles."
        val found=ConversationRules.questions(transcript)
        assertEquals(1,found.size)
        assertEquals("¿Por qué la empresa tiene equipos en movimiento?",found.single())
    }
    @Test fun clarificationQuestionIsSeparatedFromItsExplanation(){
        val text="En esta parte tenemos un incremento qué significa esto que no solamente ojo no solamente que es el hecho real de cuántos accidentes ocurren."
        assertEquals(listOf("¿qué significa esto?"),ConversationRules.questions(text))
    }
    @Test fun coordinatedQuestionsAreSplitAtTheSecondQuestionFrame(){
        val text="Ahora qué es lo que debo priorizar en qué es lo que debo centrarme pues obviamente revisar lo rojo."
        val found=ConversationRules.questions(text)
        assertEquals(2,found.size)
        assertTrue(found[0].contains("debo priorizar"))
        assertTrue(found[1].contains("debo centrarme"))
        assertFalse(found.any{it.contains("revisar lo rojo")})
    }
    @Test fun commonASRFalseStartIsPolishedWithoutChangingTheQuotedSource(){
        val spans=ConversationRules.spans("Ahora de qué es lo que yo debo preocuparme en qué es lo que yo debo centrarme pues revisar lo rojo.")
        assertEquals(spans.toString(),2,spans.size)
        assertEquals("¿de qué debo preocuparme?",spans[0].text)
        assertEquals("de qué es lo que yo debo preocuparme",spans[0].sourceText)
        assertEquals("¿en qué debo centrarme?",spans[1].text)
    }
}
