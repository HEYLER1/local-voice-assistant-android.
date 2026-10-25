package com.heyler.voicelab

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SummaryCanvasTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    private val homework="Como tarea para el viernes, resolver los ejercicios 1 y 2."
    private val state=AssistantState(
        lines=listOf(SpeechLine(1,"Hoy vamos a estudiar las métricas de software.",true),SpeechLine(2,"El tamaño funcional es una medida de las funciones del sistema.",true),SpeechLine(3,"La calidad se evalúa con criterios e indicadores definidos.",true),SpeechLine(4,homework,true),SpeechLine(5,"Esto es una pregunta de examen.",true)),
        summary="Las métricas permiten evaluar el tamaño, la calidad y el esfuerzo de un sistema. La clase distingue medidas funcionales y criterios de calidad.",
        organization=ClassOrganization("Métricas de software",listOf("Tamaño","Calidad"),listOf(OrganizedSection("Tamaño funcional","Mide las funciones que ofrece el sistema.",listOf(2)),OrganizedSection("Calidad del software","Se evalúa con criterios e indicadores definidos.",listOf(3)))),
        answers=listOf(LiveAnswer("review",2,1,"¿Cómo se mide el tamaño funcional?","Se identifican las funciones del sistema.","Lista"))
    )
    private fun show(onSource:(Long)->Unit={},onSave:(String,String)->Unit={_,_->}){
        rule.runOnUiThread{rule.activity.setContent{VoiceTheme{
            Scaffold(containerColor=VoiceBackground,topBar={Column(Modifier.statusBarsPadding()){ConversationTopBar(2,{},{},{})}},bottomBar={ChatVoiceComposer(false,false,0f,true,{},"",{},{})}){padding->
                LiveConversation(state, {onSource(it.id)},onSave,{},{},{},{},{},modifier=Modifier.padding(padding),section=2)
            }
        }}}
    }
    @Test fun canvasMovesZoomsAndOpensSourceBackedNotesInBothViews(){
        var opened:Long?=null
        show(onSource={opened=it})
        rule.onAllNodesWithText("Resumen").assertCountEquals(1)
        rule.onNodeWithText("TEMA PRINCIPAL").assertDoesNotExist()
        rule.onNodeWithText("SÍNTESIS").assertDoesNotExist()
        rule.onNodeWithText("Arrastra y amplía").assertDoesNotExist()
        val viewport=rule.onNodeWithContentDescription("Lienzo del resumen").fetchSemanticsNode().boundsInRoot
        val screen=rule.onRoot().fetchSemanticsNode().boundsInRoot
        assertEquals(screen.width,viewport.width,2f)
        assertTrue(viewport.height>screen.height*.55f)
        capture("v-clean-summary-canvas-synthetic.png")
        val topic=rule.onNodeWithContentDescription("Abrir síntesis del resumen")
        val before=topic.fetchSemanticsNode().boundsInRoot.left
        rule.onNodeWithContentDescription("Lienzo del resumen").performTouchInput{swipe(Offset(width*.50f,height*.31f),Offset(width*.68f,height*.36f),400)}
        assertTrue(topic.fetchSemanticsNode().boundsInRoot.left>before+4f)
        rule.onNodeWithContentDescription("Restablecer vista del resumen").performClick()
        assertEquals(before,topic.fetchSemanticsNode().boundsInRoot.left,2f)
        rule.onNodeWithContentDescription("Acercar resumen").performClick()
        rule.onNodeWithContentDescription("Lienzo del resumen").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"Zoom 125%"))
        rule.onNodeWithContentDescription("Restablecer vista del resumen").performClick()
        rule.onNodeWithContentDescription("Lienzo del resumen").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"Zoom 100%"))
        rule.onNodeWithContentDescription("Abrir Tamaño funcional").performClick()
        rule.onNodeWithText("Mide las funciones que ofrece el sistema.").assertIsDisplayed()
        rule.onNodeWithText("Ver fuente en la transcripción ↗").performClick()
        rule.runOnIdle{assertEquals(2L,opened)}
        rule.onNodeWithContentDescription("Ampliar lienzo del resumen").performClick()
        rule.onNodeWithContentDescription("Cerrar lienzo ampliado").assertIsDisplayed()
        rule.onNode(hasContentDescription("Abrir Calidad del software") and hasAnyAncestor(isDialog())).performClick()
        rule.onNodeWithText("Se evalúa con criterios e indicadores definidos.").assertIsDisplayed()
        rule.onNodeWithContentDescription("Cerrar detalle del resumen").performClick()
        rule.onNodeWithContentDescription("Cerrar lienzo ampliado").performClick()
        rule.onNodeWithContentDescription("Ampliar lienzo del resumen").assertIsDisplayed()
    }
    @Test fun taskBranchKeepsLiteralTimingAndUserChosenSave(){
        var text="";var source=""
        show(onSave={t,s->text=t;source=s})
        rule.onNodeWithContentDescription("Abrir Por preparar").performClick()
        rule.onNodeWithText(homework).assertIsDisplayed()
        rule.onNodeWithText("Plazo citado · para el viernes").assertIsDisplayed()
        rule.onNodeWithText("Guardar como tarea").performClick()
        rule.runOnIdle{assertEquals(homework,text);assertTrue(source.startsWith("Tarea de clase"))}
        rule.onNodeWithContentDescription("Abrir síntesis del resumen").performClick()
        rule.onNodeWithText("Guardar resumen").performClick()
        rule.runOnIdle{assertEquals(state.summary,text);assertTrue(source.startsWith("Resumen generado"))}
    }
    private fun capture(name:String){
        rule.waitForIdle();val bitmap=rule.onRoot().captureToImage()
        val file=java.io.File(rule.activity.getExternalFilesDir(null),"ui-tests").apply{mkdirs()}.resolve(name)
        file.outputStream().use{bitmap.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
    }
}
