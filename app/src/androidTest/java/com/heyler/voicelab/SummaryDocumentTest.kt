package com.heyler.voicelab

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SummaryDocumentTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    private val headings=listOf("Tamaño funcional","Criterios de calidad","Esfuerzo de desarrollo","Documentación del sistema","Respaldo de información","Entrevistas de auditoría")
    private val sections=headings.mapIndexed{index,title->OrganizedSection(title,"Apunte ficticio sobre ${title.lowercase()}. Se explica su propósito dentro de la evaluación del software.",listOf(index+2L))}
    private val organized=AssistantState(conversationKey=991,
        lines=listOf(SpeechLine(1,"Hoy vamos a estudiar métricas de software.",true))+sections.map{SpeechLine(it.sources.single(),it.text,true)},
        summary="Las métricas ayudan a evaluar el software. El análisis relaciona tamaño, calidad y esfuerzo con la revisión de los sistemas.",
        organization=ClassOrganization("Métricas de software",sections=sections))

    @Test fun completeSummaryCopiesEverySectionAndOpensItsOriginalSource(){
        var copied="";var opened:Long?=null
        rule.runOnUiThread{rule.activity.setContent{VoiceTheme{Surface(color=VoiceBackground){
            StudySummaryPanel(organized,{}, {opened=it.id},{_,_->},{},Modifier.fillMaxSize().statusBarsPadding(),onCopy={copied=it})
        }}}}
        rule.onNodeWithContentDescription("Abrir síntesis del resumen").performClick()
        capture("v-organized-summary-synthetic.png")
        rule.onNodeWithContentDescription("Copiar resumen").performClick()
        rule.runOnIdle{assertEquals(KnowledgeSummary.document(organized),copied);headings.forEach{assertTrue(copied.contains(it))}}
        rule.onAllNodesWithText("Entrevistas de auditoría").onLast().performScrollTo().assertIsDisplayed()
        rule.onNodeWithContentDescription("Ver fuente de Entrevistas de auditoría (1)").performScrollTo().performClick()
        rule.runOnIdle{assertEquals(7L,opened)}
    }
    @Test fun openSummaryUpdatesItsTitleAndContentAsTheConversationIsOrganized(){
        val live=mutableStateOf(AssistantState(conversationKey=991,lines=listOf(SpeechLine(1,"Hola, buenos días.",true)),summary="Aún no hay un tema definido."))
        rule.runOnUiThread{rule.activity.setContent{VoiceTheme{Surface(color=VoiceBackground){
            StudySummaryPanel(live.value,{}, {},{_,_->},{},Modifier.fillMaxSize().statusBarsPadding())
        }}}}
        rule.onNodeWithContentDescription("Abrir síntesis del resumen").performClick()
        rule.onAllNodesWithText("Desconocido").onLast().assertIsDisplayed()
        rule.runOnIdle{live.value=organized}
        rule.onAllNodesWithText("Desconocido").assertCountEquals(0)
        rule.onAllNodesWithText("Métricas de software").onLast().assertIsDisplayed()
        rule.onAllNodesWithText(organized.summary).onLast().assertIsDisplayed()
        rule.onAllNodesWithText("Criterios de calidad").onLast().performScrollTo().assertIsDisplayed()
    }
    private fun capture(name:String){
        rule.waitForIdle()
        val bitmap=rule.onRoot().captureToImage().asAndroidBitmap()
        val file=java.io.File(rule.activity.getExternalFilesDir(null),"ui-tests").apply{mkdirs()}.resolve(name)
        file.outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
    }
}
