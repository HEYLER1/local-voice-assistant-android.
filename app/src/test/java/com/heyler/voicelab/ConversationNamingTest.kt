package com.heyler.voicelab

import org.junit.Assert.*
import org.junit.Test

class ConversationNamingTest {
    @Test fun greetingsNoiseAndPartialTopicsKeepUnknown(){
        listOf("", "Hola, buenos días, mucho gusto.", "Mucha frecuencia solo unas veces.").forEach{text->
            assertEquals("Desconocido",ConversationNaming.display(AssistantState(lines=listOf(SpeechLine(1,text,true)))))
        }
        assertEquals("Desconocido",ConversationNaming.display(AssistantState(lines=listOf(SpeechLine(1,"Hoy vamos a estudiar métricas de software.",false)))))
    }
    @Test fun aFinalExplicitTopicProvidesANameWithoutAnOnlineRequest(){
        val state=AssistantState(lines=listOf(SpeechLine(1,"Hola, buenos días.",true),SpeechLine(2,"Hoy vamos a estudiar métricas de software.",true)))
        assertEquals("Métricas de software",ConversationNaming.display(state))
    }
    @Test fun anAnalysisTitleNeedsEnoughRelevantSourceMaterial(){
        val org=ClassOrganization("Métricas de software",sections=listOf(OrganizedSection("Tamaño","Un apunte.",listOf(1))))
        val short=AssistantState(organization=org,lines=listOf(SpeechLine(1,"Métricas de software.",true)))
        assertEquals("Desconocido",ConversationNaming.display(short))
        val text="Las métricas de software permiten medir tamaño, calidad y esfuerzo. Se compara el número de líneas de código con los puntos de función, porque las bibliotecas y los componentes reutilizables cambian la cantidad de código necesaria para implementar una funcionalidad."
        val supported=short.copy(lines=listOf(SpeechLine(1,text,true)))
        assertEquals("Métricas de software",ConversationNaming.display(supported))
        assertEquals("Desconocido",ConversationNaming.display(supported.copy(organization=org.copy(title="Biología molecular"))))
    }
    @Test fun aConfirmedNameRemainsStableAndManualNamesTakePrecedence(){
        val state=AssistantState(automaticTitle="Métricas de software",organization=ClassOrganization("Auditoría informática"),lines=listOf(SpeechLine(1,"Pasamos a auditoría informática.",true)))
        assertEquals("Métricas de software",ConversationNaming.display(state))
        assertEquals("Mi curso",ConversationNaming.display(state.copy(conversationTitle="Mi curso")))
        assertEquals("Desconocido",ConversationNaming.display(AssistantState()))
    }
    @Test fun aShortSubjectIsEnoughWithoutASubtitle(){
        val title="Patrones de los eclipses solares: frecuencia y diferencias entre hemisferios"
        val text="Se describen patrones de los eclipses solares. El grupo compara sus rutas, la frecuencia con que se repiten y su distribución entre ambos hemisferios. Estos aspectos forman parte del mismo tema; no son asuntos independientes que deban enumerarse en el nombre de la conversación."
        val state=AssistantState(lines=listOf(SpeechLine(1,text,true)),organization=ClassOrganization(title,sections=listOf(OrganizedSection("Patrones",text,listOf(1)))))
        assertEquals("Patrones de los eclipses solares",ConversationNaming.display(state))
        assertEquals("Patrones de los eclipses solares",ConversationNaming.saved("",title))
        assertEquals(title,ConversationNaming.saved(title,""))
    }
    @Test fun longPhrasesWaitForAConciseTitleInsteadOfBeingCutMidSentence(){
        val title="Relación entre frecuencia de eclipses solares y su distribución entre hemisferios"
        val state=AssistantState(automaticTitle=title)
        assertEquals("Desconocido",ConversationNaming.display(state))
        assertEquals(title,ConversationNaming.display(state.copy(conversationTitle=title)))
        val explicit=AssistantState(lines=listOf(SpeechLine(1,"Hoy vamos a estudiar la relación entre frecuencia de eclipses solares y su distribución entre hemisferios.",true)))
        assertEquals("Desconocido",ConversationNaming.display(explicit))
    }
}
