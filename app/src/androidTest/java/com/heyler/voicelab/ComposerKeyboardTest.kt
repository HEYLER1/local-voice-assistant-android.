package com.heyler.voicelab

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Checks focus, draft and keyboard requests; does not assert a real IME is displayed. */
class ComposerKeyboardTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    @Test fun repeatedTapsRequestKeyboardAndKeepDraft(){
        var sent=""
        var shows=0
        var hides=0
        val keyboard=object:SoftwareKeyboardController{
            override fun show(){shows++}
            override fun hide(){hides++}
        }
        rule.runOnUiThread{rule.activity.setContent{
            VoiceTheme{
                CompositionLocalProvider(LocalSoftwareKeyboardController provides keyboard){
                    var draft by remember{mutableStateOf("")}
                    Box(Modifier.fillMaxSize(),contentAlignment=Alignment.BottomCenter){ChatVoiceComposer(false,false,0f,true,{},draft,{draft=it},{sent=draft;draft=""})}
                }
            }
        }}
        val field=rule.onNodeWithContentDescription("Mensaje para V")
        repeat(3){
            val previous=shows
            field.performTouchInput{click()}
            rule.waitForIdle()
            field.assertIsFocused()
            rule.waitUntil(5000){shows>previous}
            field.assertIsFocused().performTextInput("hola")
            rule.runOnIdle{keyboard.hide()}
        }
        field.assertTextContains("holaholahola")
        rule.onNodeWithContentDescription("Enviar mensaje").performTouchInput{click()}
        field.assertIsNotFocused()
        rule.runOnIdle{assertEquals("holaholahola",sent);assertTrue(hides>=4)}
        val previous=shows
        field.performTouchInput{click()}
            rule.waitForIdle()
        rule.waitUntil(5000){shows>previous}
        field.assertIsFocused().performTextInput("nuevo")
        field.assertTextContains("nuevo")
        rule.onNodeWithContentDescription("Iniciar escucha").performTouchInput{click()}
        field.assertIsNotFocused()
    }
}
