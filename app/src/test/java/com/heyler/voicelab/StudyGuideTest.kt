package com.heyler.voicelab
import org.junit.Assert.*
import org.junit.Test
class StudyGuideTest{
    @Test fun splitsLiteralDefinitionsAndKeepsSource(){
        val definition="Un eclipse solar ocurre cuando la Luna pasa entre la Tierra y el Sol."
        val number="En el ejercicio la totalidad dura 45 segundos."
        val guide=StudyGuide.build(listOf(SpeechLine(19,"$definition $number ¿Por qué aparecen bandas de sombra?",true)))
        assertEquals(listOf(definition,number),guide.ideas.map{it.text})
        assertTrue(guide.ideas.all{it.sourceId==19L})
        assertEquals("Un eclipse solar",guide.concepts.single().term)
        assertEquals(number,guide.figures.single().text)
    }
    @Test fun deduplicatesWithoutInventingDefinitionsOrChangingDecimals(){
        val phrase="El resultado del ejercicio fue 3.14 metros cuadrados."
        val guide=StudyGuide.build(listOf(SpeechLine(1,phrase,true),SpeechLine(2,phrase,true)))
        assertEquals(1,guide.ideas.size);assertEquals(phrase,guide.figures.single().text)
        assertTrue(guide.concepts.isEmpty())
    }
    @Test fun emptyOrOnlyQuestionsProducesNoStudyFacts(){
        assertTrue(StudyGuide.build(emptyList()).ideas.isEmpty())
        val guide=StudyGuide.build(listOf(SpeechLine(1,"¿Cómo se calcula el área de un círculo?",true)))
        assertTrue(guide.ideas.isEmpty());assertTrue(guide.concepts.isEmpty());assertTrue(guide.figures.isEmpty())
    }
}
