package com.heyler.voicelab

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SummaryOrganizationTest {
    private val oldLine=SpeechLine(900,"El tamaño funcional mide las funciones del software.",true)
    private val newLine=SpeechLine(901,"La complejidad del software influye en el esfuerzo de desarrollo.",true)
    private val previous=AssistantState(lines=listOf(oldLine),summary="Se analiza el tamaño funcional del software.",
        organization=ClassOrganization("Métricas de software",sections=listOf(
            OrganizedSection("Tamaño funcional","Se mide el software por sus funciones.",listOf(900))),covered=mapOf(900L to oldLine.revision)))
    private fun response(reference:Int)=JSONObject().put("title","Métricas de software")
        .put("summary","Se relacionan el tamaño funcional y la complejidad con el esfuerzo.")
        .put("sections",JSONArray().put(JSONObject().put("title","Tamaño y esfuerzo del software")
            .put("text","El tamaño funcional representa las funciones. La complejidad influye en el esfuerzo.")
            .put("previous",JSONArray().put(reference)).put("sources",JSONArray().put(1))))
        .put("questions",JSONArray()).toString()
    @Test fun protocolKeepsPreviousAndNewSourceNamespacesSeparate(){
        val result=AnalysisProtocol.parse(response(1),listOf(newLine),previous)
        val section=result.organization.sections.single()
        assertEquals(listOf(900L,901L),section.sources)
        assertEquals(listOf("Tamaño funcional"),section.replaces)
        val merged=KnowledgeSummary.merge(previous.copy(lines=listOf(oldLine,newLine)),result.organization)
        assertEquals(1,merged.sections.size);assertEquals(section.text,merged.sections.single().text)
    }
    @Test fun unknownPreviousReferencesCannotRemoveAnExistingSection(){
        val result=AnalysisProtocol.parse(response(88),listOf(newLine),previous)
        assertTrue(result.organization.sections.single().replaces.isEmpty())
        assertEquals(listOf(901L),result.organization.sections.single().sources)
        assertEquals(2,KnowledgeSummary.merge(previous.copy(lines=listOf(oldLine,newLine)),result.organization).sections.size)
    }
    @Test fun promptNeverTruncatesANewSourceOrSuppliesPartialNotesForReplacement(){
        val notes=(1L..25L).map{OrganizedSection("Sección específica $it","Información completa $it. ".repeat(80),listOf(it))}
        val state=previous.copy(lines=(1L..25L).map{SpeechLine(it,"Información del software $it.",true)},organization=ClassOrganization("Métricas de software",sections=notes))
        val snapshot=(1L..6L).map{SpeechLine(100+it,"Dato nuevo $it. ".repeat(110),true)}
        val prompt=AnalysisProtocol.prompt(snapshot,state)
        assertTrue(prompt.length<=16000)
        assertTrue(prompt.contains("[P"))
        snapshot.forEach{assertTrue(prompt.contains(it.text))}
        notes.forEachIndexed{index,note->if(prompt.contains("[P${index+1}]"))assertTrue(prompt.contains(note.text))}
    }
}
