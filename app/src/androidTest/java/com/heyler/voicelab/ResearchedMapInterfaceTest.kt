package com.heyler.voicelab

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ResearchedMapInterfaceTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    @Test fun conceptsAreVisibleOnTheMapAndSourcesRemainAvailableInDetails(){
        var copied=""
        rule.runOnUiThread{rule.activity.setContent{VoiceTheme{Surface(color=VoiceBackground){
            StudySummaryPanel(ResearchFixtures.state(),{}, {},{_,_->},{},Modifier.fillMaxSize().statusBarsPadding(),onCopy={copied=it})
        }}}}
        rule.onNodeWithContentDescription("Abrir ¿Qué son?").assertIsDisplayed()
        rule.onNodeWithText("Alineación de los astros").assertExists()
        rule.onNodeWithText("Un eclipse solar ocurre cuando la Luna se interpone entre la Tierra y el Sol.").assertExists()
        capture("v-researched-eclipse-map-synthetic.png")
        val topic=rule.onNodeWithContentDescription("Abrir síntesis del resumen")
        val before=topic.fetchSemanticsNode().boundsInRoot.left
        rule.onNodeWithContentDescription("Lienzo del resumen").performTouchInput{swipe(Offset(width*.50f,height*.45f),Offset(width*.65f,height*.52f),350)}
        assertTrue(topic.fetchSemanticsNode().boundsInRoot.left>before)
        rule.onNodeWithContentDescription("Restablecer vista del resumen").performClick()
        rule.onNodeWithContentDescription("Abrir ¿Qué son?").performClick()
        rule.onNodeWithText("Ampliación de IA · sin verificación externa").assertIsDisplayed()
        rule.onNodeWithText("Eclipse solar ↗").assertDoesNotExist()
        rule.onNodeWithContentDescription("Cerrar detalle del resumen").performClick()
        rule.onNodeWithContentDescription("Abrir síntesis del resumen").performClick()
        rule.onNodeWithContentDescription("Copiar resumen").performClick()
        rule.runOnIdle{assertTrue(copied.contains("Frecuencia anual"));assertTrue(copied.contains("Ampliación de IA"))}
    }
    @Test fun researchIsAnExplicitActionAndDoesNotStartFromOpeningThePage(){
        var requested=0
        rule.runOnUiThread{rule.activity.setContent{VoiceTheme{Surface(color=VoiceBackground){
            StudySummaryPanel(ResearchFixtures.state().copy(organization=ResearchFixtures.state().organization.copy(map=null)),{}, {},{_,_->},{},Modifier.fillMaxSize(),onResearch={requested++})
        }}}}
        rule.runOnIdle{assertEquals(0,requested)}
        rule.onNodeWithContentDescription("Ampliar y organizar mapa").performClick()
        rule.runOnIdle{assertEquals(1,requested)}
    }
    private fun capture(name:String){
        rule.waitForIdle();val bitmap=rule.onRoot().captureToImage().asAndroidBitmap()
        val file=java.io.File(rule.activity.getExternalFilesDir(null),"ui-tests").apply{mkdirs()}.resolve(name)
        file.outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
    }
}
