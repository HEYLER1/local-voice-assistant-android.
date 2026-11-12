package com.heyler.voicelab

import android.app.Application
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelStore
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class QwenSettingsTest{
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    @Test fun localEngineAndImportAreAvailableInTheMainSettings(){
        val prefs=rule.activity.getSharedPreferences("assistant-options",0);val old=prefs.getString("answer-engine",null)
        val vm=AssistantViewModel(rule.activity.application as Application);val holder=ViewModelStore();holder.put("settings",vm)
        var imported=false
        try{
            rule.runOnUiThread{
                vm.temporaryConversation();vm.setAnswerEngine("pollinations")
                rule.activity.setContent{VoiceTheme{AssistantScreen(vm,{}, {},{imported=true},initialTab=2)}}
            }
            rule.onNodeWithText("Todo local").performScrollTo().performClick()
            rule.runOnIdle{assertEquals("qwen3",vm.state.value.answerEngine)}
            val label=if(vm.state.value.localModelAvailable)"Cambiar modelo local"else "Importar modelo local"
            rule.onNodeWithText(label).performScrollTo().performClick()
            rule.runOnIdle{assertTrue(imported)}
            rule.onNodeWithText("Conectar Puter").assertDoesNotExist()
            rule.onNodeWithText("Automático",useUnmergedTree=true).performScrollTo().performClick()
            rule.runOnIdle{assertEquals("auto",vm.state.value.answerEngine)}
        }finally{
            rule.runOnUiThread{vm.background();holder.clear()}
            prefs.edit().apply{if(old==null)remove("answer-engine")else putString("answer-engine",old)}.commit()
        }
    }
}
