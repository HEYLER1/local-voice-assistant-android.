package com.heyler.voicelab
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
class StudySummaryTest{
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    @Test fun reviewHidesExplanationAndSourceKeepsOriginalId(){
        var opened:Long?=null
        val explanation="La turbulencia del aire puede producir esas franjas."
        val state=AssistantState(summary="La clase trata sobre los eclipses solares.",lines=listOf(SpeechLine(1901,"Un eclipse solar ocurre cuando la Luna pasa entre la Tierra y el Sol.",true)),answers=listOf(LiveAnswer("review",1901,1,"¿Por qué vemos bandas de sombra?",explanation,"Lista")))
        rule.runOnUiThread{rule.activity.setContent{VoiceTheme{Surface(color=VoiceBackground){
            Column(Modifier.fillMaxSize().statusBarsPadding().padding(20.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
                StudySummaryPanel(state,{}, {opened=it.id},{_,_->},{})
            }
        }}}}
        rule.onNodeWithText("Conceptos").performScrollTo().performClick()
        rule.onNodeWithText("Un eclipse solar").assertIsDisplayed()
        rule.onNodeWithText("Repaso").performScrollTo().performClick()
        rule.onNodeWithText(explanation).assertDoesNotExist()
        rule.onNodeWithText("Mostrar explicación").performClick()
        rule.onNodeWithText(explanation).assertIsDisplayed()
        rule.onNodeWithText("Ocultar explicación").performClick()
        rule.onNodeWithText(explanation).assertDoesNotExist()
        rule.onNodeWithText("Ideas").performScrollTo().performClick()
        rule.onNodeWithText("Ver en el texto ↗").performClick()
        rule.runOnIdle{assertEquals(1901L,opened)}
    }
    @Test fun classTasksAndExamKeepTheirSourceAndSaveAsTask(){
        var savedText="";var savedSource=""
        val homework="Como tarea para el viernes, resolver los ejercicios 1 y 2."
        val explanation="El trabajo de una fuerza se calcula multiplicando fuerza por desplazamiento."
        val state=AssistantState(summary="La clase estudia el trabajo mecánico y la energía.",lines=listOf(
            SpeechLine(2201,"Hoy vamos a estudiar el trabajo mecánico y la energía.",true),
            SpeechLine(2202,explanation,true),SpeechLine(2203,"Esto es una pregunta de examen.",true),
            SpeechLine(2204,"¿Cómo se calcula el trabajo?",true),SpeechLine(2205,homework,true)))
        rule.runOnUiThread{rule.activity.setContent{VoiceTheme{Surface(color=VoiceBackground){
            Column(Modifier.fillMaxSize().statusBarsPadding().padding(20.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
                StudySummaryPanel(state,{}, {},{text,source->savedText=text;savedSource=source},{})
            }
        }}}}
        rule.onNodeWithText("Tareas 1").performClick()
        rule.onNodeWithText(homework).assertIsDisplayed()
        rule.onNodeWithText("Plazo citado · para el viernes").assertIsDisplayed()
        capture("v-student-tasks-synthetic.png")
        rule.onNodeWithText("Guardar como tarea").performClick()
        rule.runOnIdle{assertEquals(homework,savedText);assertTrue(savedSource.startsWith("Tarea de clase"))}
        rule.onNodeWithText("Examen 1").performClick()
        rule.onNodeWithText("Esto es una pregunta de examen.").assertIsDisplayed()
        rule.onNodeWithText("Ver contexto de la indicación").performClick()
        rule.onNodeWithText(explanation).assertIsDisplayed()
        rule.onNodeWithText("¿Cómo se calcula el trabajo?").assertIsDisplayed()
        capture("v-student-exam-synthetic.png")
    }
    private fun capture(name:String){
        rule.mainClock.advanceTimeBy(500);rule.waitForIdle()
        val bitmap=rule.onRoot().captureToImage()
        val path=java.io.File(rule.activity.getExternalFilesDir(null),"ui-tests").apply{mkdirs()}.resolve(name)
        path.outputStream().use{bitmap.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
    }
}
