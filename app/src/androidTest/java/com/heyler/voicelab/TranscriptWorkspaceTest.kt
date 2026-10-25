package com.heyler.voicelab

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class TranscriptWorkspaceTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    private val lines=listOf(SpeechLine(1,"Las métricas permiten evaluar el tamaño de un sistema.",true),SpeechLine(2,"Se comparan distintas medidas según el objetivo.",true))
    private fun show(content:@androidx.compose.runtime.Composable ()->Unit){
        rule.runOnUiThread{rule.activity.setContent{VoiceTheme{
            Scaffold(containerColor=VoiceBackground,topBar={Column(Modifier.statusBarsPadding()){ConversationTopBar(0,{},{},{})}},bottomBar={ChatVoiceComposer(false,false,0f,true,{},"",{},{})}){padding->
                Box(Modifier.fillMaxSize().padding(padding)){content()}
            }
        }}}
    }
    @Test fun copyActionReceivesOnlyTheDisplayedTranscriptAndRespectsOptionalVoiceLabels(){
        var copied=""
        val fixture=mutableStateOf(AssistantState(lines=lines))
        show{TranscriptWorkspace(fixture.value,{}, {copied=it})}
        rule.onNodeWithContentDescription("Copiar transcripción").performClick()
        rule.runOnIdle{assertEquals(lines.joinToString(" "){it.text},copied);fixture.value=fixture.value.copy(voiceLabels=true,lines=lines.map{it.copy(voice="Docente")})}
        rule.onNodeWithContentDescription("Copiar transcripción").performClick()
        rule.runOnIdle{assertEquals(lines.joinToString("\n\n"){"Docente\n${it.text}"},copied)}
    }
    @Test fun textTabShowsOnlyTranscriptAndCopyEvenWhenAnAnswerIsReady(){
        val state=AssistantState(lines=lines,answers=listOf(LiveAnswer("q",1,1,"¿Cómo se mide el tamaño de un sistema?","Una explicación que pertenece a Preguntas.","Lista")))
        show{LiveConversation(state,{}, {_,_->},{},{},{},{},{},section=0)}
        rule.onNodeWithText(lines.joinToString(" "){it.text}).assertIsDisplayed()
        rule.onNodeWithContentDescription("Copiar transcripción").assertIsDisplayed()
        listOf("Transcripción","PAUSADA","Editar / guardar","Siguiendo audio","RESPUESTA EN VIVO","Las preguntas relevantes aparecerán aquí.",state.answers.first().question,state.answers.first().text).forEach{rule.onNodeWithText(it).assertDoesNotExist()}
        capture("v-clean-transcript-synthetic.png")
    }
    @Test fun incomingTextDoesNotPullTheReaderAwayFromEarlierContent(){
        val fixture=mutableStateOf(AssistantState(listening=true,lines=(1..60).map{SpeechLine(it.toLong(),"Fragmento $it de una conversación ficticia para comprobar el desplazamiento del texto.",true)}))
        show{TranscriptWorkspace(fixture.value,{}, {})}
        val text=rule.onNodeWithContentDescription("Texto de la conversación")
        fun range()=text.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        rule.waitUntil(5000){range().maxValue()>1000f&&range().maxValue()-range().value()<5f}
        text.performTouchInput{swipeDown(startY=height*.25f,endY=height*.85f,durationMillis=300)}
        rule.waitForIdle()
        val previous=range().value()
        assertTrue(previous<range().maxValue()-50f)
        rule.runOnIdle{fixture.value=fixture.value.copy(lines=fixture.value.lines+SpeechLine(61,"Este fragmento nuevo llega mientras se está leyendo un pasaje anterior de la conversación.",true))}
        rule.waitForIdle()
        assertEquals(previous,range().value(),5f)
    }
    @Test fun aNewWrittenReplyBecomesVisibleAfterBrowsingStudyQuestions(){
        val fixture=mutableStateOf(AssistantState(answers=listOf(LiveAnswer("heard",1,1,"¿Cómo se mide el tamaño del software?","Se elige una medida según el objetivo.","Lista"),LiveAnswer("study",1,1,"¿Cómo comparar dos medidas de tamaño?","Explicación para repasar.","Lista",suggested=true))))
        val preferred=mutableStateOf<String?>(null)
        show{LiveConversation(fixture.value,{}, {_,_->},{},{},{},{},{},section=1,preferredAnswerId=preferred.value)}
        rule.onNodeWithText("Para repasar").performClick()
        rule.onNodeWithText("¿Cómo comparar dos medidas de tamaño?").performClick()
        rule.onNodeWithText("Explicación para repasar.").assertIsDisplayed()
        rule.runOnIdle{
            fixture.value=fixture.value.copy(answers=fixture.value.answers+LiveAnswer("typed",null,0,"Explícame los puntos de función","Una respuesta a la nueva consulta escrita.","Lista"))
            preferred.value="typed"
        }
        rule.onNodeWithText("Explícame los puntos de función").assertIsDisplayed()
        rule.onNodeWithText("Una respuesta a la nueva consulta escrita.").assertIsDisplayed()
        rule.onNodeWithText("Explicación para repasar.").assertDoesNotExist()
    }
    private fun capture(name:String){
        rule.waitForIdle();val bitmap=rule.onRoot().captureToImage()
        val file=java.io.File(rule.activity.getExternalFilesDir(null),"ui-tests").apply{mkdirs()}.resolve(name)
        file.outputStream().use{bitmap.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
    }
}
