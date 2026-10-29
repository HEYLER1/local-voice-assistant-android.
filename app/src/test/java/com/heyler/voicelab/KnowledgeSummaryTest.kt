package com.heyler.voicelab

import org.junit.Assert.*
import org.junit.Test

class KnowledgeSummaryTest {
    private val lines=listOf(SpeechLine(1,"Hoy vamos a estudiar métricas de software.",true),
        SpeechLine(2,"El tamaño funcional mide las funciones del sistema.",true),
        SpeechLine(3,"La calidad se evalúa mediante criterios definidos.",true),
        SpeechLine(4,"La complejidad influye en el esfuerzo del desarrollo.",true))
    private val original=AssistantState(lines=lines,summary="Las métricas ayudan a evaluar el software.",
        organization=ClassOrganization("Métricas de software",sections=listOf(
            OrganizedSection("Tamaño funcional","Se miden las funciones.",listOf(2)),
            OrganizedSection("Calidad del software","Se evalúa con criterios.",listOf(3))),covered=lines.associate{it.id to it.revision}))
    @Test fun aNewBlockRewritesASectionInsteadOfStackingOldParagraphs(){
        val result=KnowledgeSummary.merge(original,ClassOrganization(sections=listOf(
            OrganizedSection("Tamaño funcional","Las funciones determinan el tamaño y se relacionan con el esfuerzo.",listOf(4),listOf("Tamaño funcional")))))
        assertEquals(2,result.sections.size)
        assertEquals("Las funciones determinan el tamaño y se relacionan con el esfuerzo.",result.sections[0].text)
        assertEquals(listOf(2L,4L),result.sections[0].sources)
        assertEquals(original.organization.sections[1],result.sections[1])
    }
    @Test fun unreferencedLegacyUpdatesDoNotDiscardPreviousNotes(){
        val result=KnowledgeSummary.merge(original,ClassOrganization(sections=listOf(
            OrganizedSection("Tamaño funcional","Se añade una precisión sobre el esfuerzo.",listOf(4)))))
        assertTrue(result.sections[0].text.contains("Se miden las funciones."))
        assertTrue(result.sections[0].text.contains("Se añade una precisión"))
    }
    @Test fun explicitConsolidationCanRenameAndUniteNotesWithoutLosingSources(){
        val result=KnowledgeSummary.merge(original,ClassOrganization(sections=listOf(
            OrganizedSection("Evaluación del software","El tamaño y la calidad requieren criterios definidos.",emptyList(),listOf("Tamaño funcional","Calidad del software")))))
        assertEquals(1,result.sections.size)
        assertEquals(listOf(2L,3L),result.sections.single().sources)
        assertTrue(result.sections.single().replaces.isEmpty())
    }
    @Test fun earlierTopicsAreNotEvictedWhenAConversationGrows(){
        val notes=(1L..25L).map{OrganizedSection("Tema específico $it","Apunte $it",listOf(it))}
        val state=original.copy(lines=(1L..26L).map{SpeechLine(it,"Contenido específico $it.",true)},organization=ClassOrganization(sections=notes))
        val result=KnowledgeSummary.merge(state,ClassOrganization(sections=listOf(OrganizedSection("Tema nuevo","Otro apunte.",listOf(26)))))
        assertEquals(26,result.sections.size);assertEquals(notes.first(),result.sections.first())
    }
    @Test fun changedOrMissingSourcesCannotBeReusedAsValidPreviousKnowledge(){
        val changed=original.copy(lines=lines.map{if(it.id==2L)it.copy(text="Texto corregido.",revision=it.revision+1)else it})
        assertEquals(listOf("Calidad del software"),KnowledgeSummary.previousSections(changed).map{it.title})
        assertTrue(KnowledgeSummary.previousSections(original.copy(lines=emptyList())).isEmpty())
    }
    @Test fun headingsDescribeContentAndTitlesWaitForAnEstablishedTopic(){
        listOf("Tema 1","Introducción","Resumen","Desarrollo","¿Cómo se mide?").forEach{assertEquals("",KnowledgeSummary.heading(it))}
        assertEquals("Factores del esfuerzo",KnowledgeSummary.heading("Factores del esfuerzo:"))
        assertEquals("Desconocido",KnowledgeSummary.title(AssistantState(lines=listOf(SpeechLine(1,"Hola, muchas gracias.",true)))))
        assertEquals("Métricas de software",KnowledgeSummary.title(original))
    }
    @Test fun theReadableDocumentIncludesTitleSummaryAndEverySubtitle(){
        val text=KnowledgeSummary.document(original)
        assertTrue(text.startsWith("Métricas de software\n\n"))
        assertTrue(text.contains(original.summary))
        original.organization.sections.forEach{assertTrue(text.contains(it.title+"\n"+it.text))}
    }
    @Test fun aGlobalSummaryTitleCanBroadenWhileTheSavedChatNameRemainsStable(){
        val passage="Las métricas de software permiten medir el tamaño y el esfuerzo de desarrollo. La auditoría de sistemas evalúa controles y procesos. Se relacionan estos temas para evaluar un sistema mediante evidencias y criterios explícitos, en vez de considerar únicamente la cantidad de código escrita."
        val state=original.copy(automaticTitle="Métricas de software",lines=listOf(SpeechLine(9,passage,true)),
            organization=ClassOrganization("Métricas de software y auditoría",sections=listOf(OrganizedSection("Evaluación de sistemas",passage,listOf(9)))))
        assertEquals("Métricas de software y auditoría",KnowledgeSummary.title(state))
        assertEquals("Métricas de software",ConversationNaming.display(state))
    }
}
