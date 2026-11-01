package com.heyler.voicelab

import org.junit.Assert.*
import org.junit.Test

class StructuredReplyTest {
    private val lines=listOf(SpeechLine(1,"La clase ficticia explica los eclipses solares.",true))
    @Test fun malformedSummaryDoesNotExposeItsContentsInTheError(){
        val raw="""{"title":"Eclipses solares","summary":"Contenido privado de prueba","sections":[{"previous:[]}]}"""
        val error=runCatching{AnalysisProtocol.parse(raw,lines)}.exceptionOrNull()
        assertTrue(error is StructuredReplyException)
        assertEquals(SummaryIssues.INVALID_FORMAT,error!!.message)
        assertFalse(error.message!!.contains("Contenido privado"));assertNull(error.cause)
    }
    @Test fun completeObjectAndCodeFenceAreAcceptedButTruncatedOrExtraDataAreNot(){
        val raw=AnalysisFixtures.eclipse()
        assertTrue(AnalysisProtocol.parse("```json\n$raw\n```",lines).summary.isNotBlank())
        for(invalid in listOf(raw.dropLast(1),raw+" {}",raw+" EXTRA", "[1,2]")){
            assertTrue(runCatching{StructuredReply.objectValue(invalid)}.exceptionOrNull() is StructuredReplyException)
        }
    }
    @Test fun nestedJsonCannotBeDisplayedAsTheSummaryText(){
        val raw=org.json.JSONObject(AnalysisFixtures.eclipse()).put("summary",org.json.JSONObject().put("internal","datos"))
        assertTrue(runCatching{AnalysisProtocol.parse(raw.toString(),lines)}.exceptionOrNull() is StructuredReplyException)
    }
    @Test fun oldRawErrorsAreSanitizedBeforeDisplay(){
        val raw="Expected ':' after previous:[]} at character 2758 of {\"summary\":\"Datos de prueba\"}"
        assertEquals(SummaryIssues.INVALID_FORMAT,SummaryIssues.display(raw))
        assertEquals("No hay conexión.",SummaryIssues.display("No hay conexión."))
    }
}
