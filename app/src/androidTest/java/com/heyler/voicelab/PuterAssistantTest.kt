package com.heyler.voicelab

import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

class PuterAssistantTest {
    @Test fun sameContextAndInstructionsReachTheSelectedPuterTransport()=runBlocking{
        var sent:JSONArray?=null;var tokens=0;var result=""
        val provider=PuterAssistant(object:PuterChatTransport{
            override suspend fun generate(messages:JSONArray,maxTokens:Int,onText:(String)->Unit){sent=messages;tokens=maxTokens;onText("Una ");onText("explicación.")}
        })
        provider.generate("CONTEXTO: estudiamos los eclipses."){result+=it}
        assertEquals("Una explicación.",result)
        assertTrue(sent!!.getJSONObject(0).getString("content").contains("estudiantes"))
        assertEquals("CONTEXTO: estudiamos los eclipses.",sent!!.getJSONObject(1).getString("content"))
        assertEquals(1536,tokens)
    }
    @Test fun modelMapUsesOneContextualRequestAndDoesNotAskForWebLookup()=runBlocking{
        var calls=0;var system="";var prompt=""
        val provider=PuterAssistant(object:PuterChatTransport{
            override suspend fun generate(messages:JSONArray,maxTokens:Int,onText:(String)->Unit){
                calls++;system=messages.getJSONObject(0).getString("content");prompt=messages.getJSONObject(1).getString("content");onText(ResearchFixtures.response())
            }
        })
        val result=provider.researchedMap("Eclipses solares",ResearchFixtures.lines,emptyList(),ResearchFixtures.state())
        assertEquals(1,calls);assertEquals(8,result.branches.size)
        assertTrue(result.references.isEmpty());assertTrue(result.branches.flatMap{it.points}.all{it.origin=="model"})
        assertTrue(prompt.contains("ANÁLISIS PREVIO"));assertTrue(system.contains("sin consultar páginas web"))
        assertFalse(prompt.contains("ARTÍCULOS CONSULTADOS"))
    }
}
