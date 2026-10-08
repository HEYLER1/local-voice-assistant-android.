package com.heyler.voicelab
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
class SidebarOptionsTest{
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    @Test fun entryOptionsRenameAndDeleteWithoutOpeningChat(){
        var renamed:Pair<Long,String>?=null
        var deleted:Long?=null
        var opened=false
        rule.runOnUiThread{rule.activity.setContent{VoiceTheme{
            ChatSidebar(AssistantState(history=listOf(ConversationEntry(1801,"Clase de ejemplo",0))),0,{},{},{opened=true},{},{},onRename={id,title->renamed=id to title},onDelete={deleted=it})
        }}}
        rule.onNodeWithContentDescription("Opciones de Clase de ejemplo").performClick()
        rule.onNodeWithText("Renombrar",useUnmergedTree=true).performClick()
        rule.runOnIdle{assertEquals(1801L,renamed?.first);assertEquals("Clase de ejemplo",renamed?.second);assertFalse(opened)}
        rule.onNodeWithContentDescription("Opciones de Clase de ejemplo").performClick()
        rule.onNodeWithText("Eliminar",useUnmergedTree=true).performClick()
        rule.runOnIdle{assertEquals(1801L,deleted);assertFalse(opened)}
    }
}
