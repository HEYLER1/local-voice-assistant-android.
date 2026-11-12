package com.heyler.voicelab

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class GoogleAuthSetupTest{
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()

    @Test fun missingFirebaseConfigShowsSetupInsteadOfPretendingToSignIn(){
        rule.onNodeWithText("Falta conectar Firebase").assertIsDisplayed()
        rule.onNodeWithText("Registra el paquete Android com.heyler.voicelab en un proyecto Firebase.").assertIsDisplayed()
        rule.onNodeWithText("google-services.json",substring=true).assertExists()
    }
}
