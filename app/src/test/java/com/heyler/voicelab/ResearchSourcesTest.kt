package com.heyler.voicelab

import org.junit.Assert.*
import org.junit.Test

class ResearchSourcesTest {
    @Test fun onlyRetrievedPublicArticleLinksCanBeOpened(){
        assertTrue(TopicResearch.validUrl("https://es.wikipedia.org/wiki/Eclipse_solar"))
        listOf("javascript:alert(1)","https://es.wikipedia.org.evil.test/wiki/test","http://es.wikipedia.org/wiki/test","https://user:password@es.wikipedia.org/wiki/test","https://127.0.0.1/wiki/test").forEach{assertFalse(it,TopicResearch.validUrl(it))}
    }
    @Test fun copiedKnowledgeIncludesProvenanceAndAttribution(){
        val source=ResearchSource("R1","Concepto","https://es.wikipedia.org/wiki/Concepto","Texto público.",1)
        val map=ResearchedMap("Tema","Síntesis",listOf(MapBranch("Definición",listOf(MapPoint("Idea","Explicación","research",references=listOf("R1"))))),listOf(source),emptyMap(),1)
        val text=ResearchedMapDocument.text(map)
        assertTrue(text.contains("Ampliación: R1"));assertTrue(text.contains(source.url));assertTrue(text.contains(source.license))
    }
}
