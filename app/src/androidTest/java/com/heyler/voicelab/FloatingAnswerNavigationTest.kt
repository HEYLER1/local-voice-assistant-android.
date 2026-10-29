package com.heyler.voicelab

import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class FloatingAnswerNavigationTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    @Test fun aFloatingMessageOpensItsAnswerAndCanBeOpenedAgain(){
        val request=mutableStateOf<String?>(null)
        val target=LiveAnswer("target",1,1,"¿Cómo se mide el tamaño del software?","La respuesta seleccionada desde la burbuja.","Lista")
        rule.runOnUiThread{
            val vm=rule.activity.assistant;vm.temporaryConversation()
            vm.state.value=AssistantState(answers=listOf(LiveAnswer("other",2,1,"¿Qué son las pruebas unitarias?","Otra respuesta.","Lista"),target))
            rule.activity.setContent{VoiceTheme{AssistantScreen(vm,{},{},{},{},{},{},requestedAnswerId=request.value,onAnswerRequestHandled={request.value=null})}}
        }
        repeat(2){
            rule.runOnIdle{request.value="target"}
            rule.onNodeWithText(target.text).assertIsDisplayed()
            rule.runOnIdle{assertNull(request.value)}
            rule.onNodeWithText("Texto",useUnmergedTree=true).performClick()
            rule.onNodeWithText(target.text).assertDoesNotExist()
        }
    }
}
