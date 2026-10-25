package com.heyler.voicelab

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class FolderInterfaceTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    @Test fun projectsContainClassesAndKeepMoveAndCreationExplicit(){
        var moved:Long?=null;var opened:Long?=null;var added=false;var newInProject:Long?=null
        val state=AssistantState(folders=listOf(ConversationFolder(10,"Seguridad ciudadana"),ConversationFolder(20,"Entrevistas")),history=listOf(ConversationEntry(1,"Clase 1 · Prevención",System.currentTimeMillis(),folderId=10),ConversationEntry(2,"Entrevista de ejemplo",System.currentTimeMillis(),folderId=20))+List(10){ConversationEntry(100L+it,"Conversación ${it+1}",0)})
        rule.runOnUiThread{rule.activity.setContent{VoiceTheme{Surface(color=VoiceBackground){
            ChatSidebar(state,0,{}, {}, {opened=it}, {}, {},onMove={moved=it},onCreateFolder={added=true},onNewInFolder={newInProject=it})
        }}}}
        rule.onNodeWithText("Seguridad ciudadana").performClick()
        rule.onNodeWithText("Clase 1 · Prevención").assertIsDisplayed()
        rule.onNodeWithText("Carpetas").performClick()
        rule.onNodeWithText("Seguridad ciudadana").assertDoesNotExist()
        rule.onNodeWithText("Clase 1 · Prevención").assertDoesNotExist()
        rule.onNodeWithText("Conversación 1").assertIsDisplayed()
        rule.onNodeWithText("Carpetas").performClick()
        rule.onNodeWithText("Clase 1 · Prevención").assertIsDisplayed()
        rule.onNodeWithText("Entrevista de ejemplo").assertDoesNotExist()
        rule.mainClock.advanceTimeBy(700)
        capture("v-folders-compact-synthetic.png")
        rule.onNodeWithContentDescription("Opciones de Clase 1 · Prevención").performClick()
        rule.onNodeWithText("Mover a carpeta").performClick()
        rule.runOnIdle{assertEquals(1L,moved);assertNull(opened)}
        rule.onNodeWithContentDescription("Nueva conversación en Seguridad ciudadana").performClick()
        rule.runOnIdle{assertEquals(10L,newInProject);assertNull(opened)}
        rule.onNodeWithText("Clase 1 · Prevención").performClick()
        rule.runOnIdle{assertEquals(1L,opened)}
        rule.onNodeWithText("Seguridad ciudadana").performClick()
        rule.onNodeWithText("Clase 1 · Prevención").assertDoesNotExist()
        rule.onNodeWithContentDescription("Crear carpeta").performClick();rule.runOnIdle{assertTrue(added)}
    }
    @Test fun searchIsHiddenUntilRequestedAndFindsClassesInsideCollapsedProjects(){
        val state=AssistantState(folders=listOf(ConversationFolder(10,"Seguridad ciudadana"),ConversationFolder(20,"Entrevistas")),history=listOf(ConversationEntry(1,"Clase de prevención",0,folderId=10),ConversationEntry(2,"Entrevista laboral",0,folderId=20),ConversationEntry(3,"Ideas sueltas",0)))
        var globalNew=false
        rule.runOnUiThread{rule.activity.setContent{VoiceTheme{
            ChatSidebar(state,0,{}, {globalNew=true}, {}, {}, {})
        }}}
        rule.onNodeWithContentDescription("Texto de búsqueda").assertDoesNotExist()
        rule.onNodeWithText("Hoy").assertDoesNotExist()
        rule.onNodeWithText("Ayer").assertDoesNotExist()
        rule.onNodeWithText("Últimos 7 días").assertDoesNotExist()
        rule.onNodeWithText("Ideas sueltas").assertIsDisplayed()
        rule.onNodeWithText("Clase de prevención").assertDoesNotExist()
        rule.onNodeWithContentDescription("Opciones de conversación").assertDoesNotExist()
        rule.onNodeWithText("Conversaciones").performClick()
        rule.onNodeWithText("Ideas sueltas").assertDoesNotExist()
        rule.onNodeWithText("Seguridad ciudadana").assertIsDisplayed()
        rule.onNodeWithContentDescription("Buscar conversaciones").performClick()
        rule.onNodeWithContentDescription("Texto de búsqueda").assertIsFocused().performTextInput("prevención")
        rule.onNodeWithText("Clase de prevención").assertIsDisplayed()
        rule.onNodeWithText("Entrevistas").assertDoesNotExist()
        rule.onNodeWithContentDescription("Cerrar búsqueda").performClick()
        rule.onNodeWithContentDescription("Texto de búsqueda").assertDoesNotExist()
        rule.onNodeWithText("Clase de prevención").assertDoesNotExist()
        rule.onNodeWithText("Ideas sueltas").assertDoesNotExist()
        rule.onNodeWithText("Conversaciones").performClick()
        rule.onNodeWithText("Ideas sueltas").assertIsDisplayed()
        rule.onNodeWithContentDescription("Nueva conversación").performClick()
        rule.runOnIdle{assertTrue(globalNew)}
    }
    @Test fun currentProjectOpensAutomaticallyAndAnEmptyProjectCanStartItsFirstClass(){
        val state=AssistantState(folderId=10,conversationKey=1,folders=listOf(ConversationFolder(10,"Matemáticas"),ConversationFolder(20,"Entrevistas")),history=listOf(ConversationEntry(1,"Álgebra",0,folderId=10)))
        var project:Long?=null
        rule.runOnUiThread{rule.activity.setContent{VoiceTheme{
            ChatSidebar(state,0,{}, {}, {}, {}, {},onNewInFolder={project=it})
        }}}
        rule.onNodeWithText("Álgebra").assertIsDisplayed()
        rule.onNodeWithText("Entrevistas").performClick()
        rule.onNodeWithText("＋ Nueva conversación").performClick()
        rule.runOnIdle{assertEquals(20L,project)}
    }
    @Test fun organizedSectionsExposeSourcesAndSuggestedQuestionsAreLabeled(){
        val line=SpeechLine(1,"cómo se mide el tamaño de un sistema de software",true)
        val state=AssistantState(lines=listOf(line),summary="Un ejemplo ficticio sobre métricas.",organization=ClassOrganization("Métricas de software",listOf("Tamaño"),listOf(OrganizedSection("Cómo medir el tamaño","Se pueden comparar distintas medidas según el objetivo.",listOf(1)))))
        var source:Long?=null
        rule.runOnUiThread{rule.activity.setContent{VoiceTheme{Surface(color=VoiceBackground){Column(Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState())){StudySummaryPanel(state,{}, {source=it.id},{_,_->},{})}}}}}
        rule.onNodeWithText("Métricas de software").assertIsDisplayed()
        rule.onNodeWithContentDescription("Abrir Cómo medir el tamaño").performClick()
        rule.onNodeWithText("Se pueden comparar distintas medidas según el objetivo.").assertIsDisplayed()
        rule.onNodeWithText("Ver fuente en la transcripción ↗").performClick()
        rule.runOnIdle{assertEquals(1L,source)}
        capture("v-organized-notes-synthetic.png")
    }
    @Test fun inferredReviewQuestionsStaySeparateFromHeardQuestions(){
        val actual="¿Cómo se mide el tamaño de un software?";val study="¿Cómo se relaciona el tamaño con el esfuerzo?"
        val state=AssistantState(answers=listOf(LiveAnswer("heard",1,1,actual,"Se utilizan métricas de tamaño.","Lista",evidence="cómo se mide el tamaño de un software"),LiveAnswer("study",1,1,study,"Explicación adicional para repasar.","Lista",evidence="Se utilizan métricas",suggested=true)))
        rule.runOnUiThread{rule.activity.setContent{VoiceTheme{Surface(color=VoiceBackground){LiveConversation(state,{}, {_,_->},{},{},{},{},{},section=1)}}}}
        rule.onNodeWithText("Para repasar").performClick()
        rule.onNodeWithText(study).performClick()
        rule.onNodeWithText("SUGERIDA PARA REPASAR").assertIsDisplayed()
        rule.onNodeWithText("Explicación adicional para repasar.").assertIsDisplayed()
        capture("v-contextual-questions-synthetic.png")
    }
    @Test fun answersExpandInlineWithoutDuplicateTitlesAndKeepSelectionWhenAnotherArrives(){
        val first=LiveAnswer("first",1,1,"¿Cómo se mide el tamaño del software?","Se pueden usar puntos de función para medir su tamaño funcional.","Lista",evidence="medir el tamaño funcional")
        val second=LiveAnswer("second",2,1,"¿Cómo se estima el esfuerzo de desarrollo?","Se estima el trabajo necesario según alcance y complejidad.","Lista",evidence="estimar el esfuerzo")
        val fixture=mutableStateOf(AssistantState(answers=listOf(first,second)))
        rule.runOnUiThread{rule.activity.setContent{VoiceTheme{Surface(color=VoiceBackground){LiveConversation(fixture.value,{}, {_,_->},{},{},{},{},{},section=1)}}}}
        rule.onAllNodesWithText(first.question).assertCountEquals(1)
        rule.onNodeWithText(first.text).assertIsDisplayed()
        rule.onNodeWithText("Fuente: «${first.evidence}»").assertDoesNotExist()
        rule.onNodeWithText("Fuente").performClick()
        rule.onNodeWithText("Fuente: «${first.evidence}»").assertIsDisplayed()
        rule.onNodeWithText(second.question).performClick()
        rule.onNodeWithText(first.text).assertDoesNotExist()
        rule.onNodeWithText(second.text).assertIsDisplayed()
        rule.runOnIdle{fixture.value=fixture.value.copy(answers=listOf(LiveAnswer("new",3,1,"¿Cómo se evalúa la calidad del software?","Se definen criterios e indicadores.","Lista"))+fixture.value.answers)}
        rule.onAllNodesWithText(second.question).assertCountEquals(1)
        rule.onNodeWithText(second.text).assertIsDisplayed()
        rule.onNodeWithText(second.question).performClick()
        rule.onNodeWithText(second.text).assertDoesNotExist()
        rule.onNodeWithText(second.question).performClick()
        rule.onNodeWithText(second.text).assertIsDisplayed()
        capture("v-inline-questions-synthetic.png")
    }
    private fun capture(name:String){rule.waitForIdle();val bitmap=rule.onRoot().captureToImage();val file=java.io.File(rule.activity.getExternalFilesDir(null),"ui-tests").apply{mkdirs()}.resolve(name);file.outputStream().use{bitmap.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}}
}
