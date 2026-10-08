package com.heyler.voicelab
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
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
        rule.runOnUiThread{rule.activity.setContent{VoiceTheme{
            Column(Modifier.fillMaxSize().statusBarsPadding().padding(20.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
                StudySummaryPanel(state,{}, {opened=it.id},{_,_->},{})
            }
        }}}
        rule.onNodeWithText("Conceptos").performClick()
        rule.onNodeWithText("Un eclipse solar").assertIsDisplayed()
        rule.onNodeWithText("Repaso").performClick()
        rule.onNodeWithText(explanation).assertDoesNotExist()
        rule.onNodeWithText("Mostrar explicación").performClick()
        rule.onNodeWithText(explanation).assertIsDisplayed()
        rule.onNodeWithText("Ocultar explicación").performClick()
        rule.onNodeWithText(explanation).assertDoesNotExist()
        rule.onNodeWithText("Ideas").performClick()
        rule.onNodeWithText("Ver en el texto ↗").performClick()
        rule.runOnIdle{assertEquals(1901L,opened)}
    }
}
