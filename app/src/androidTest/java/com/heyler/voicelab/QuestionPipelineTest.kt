package com.heyler.voicelab

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Measures transcript detection and queueing without making a network request. */
class QuestionPipelineTest {
    @Test fun splitQuestionGetsOneContextualAnswerWithoutNetworkWait()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val prefs=context.getSharedPreferences("assistant-options",0)
        val before=prefs.all
        val vm=AssistantViewModel(context.applicationContext as Application)
        val holder=ViewModelStore().also{it.put("question-pipeline",vm)}
        var capturedPrompt=""
        vm.online=object:OnlineAssistant(){
            override suspend fun generate(prompt:String,summary:Boolean,analysis:Boolean,map:Boolean,onText:(String)->Unit){
                capturedPrompt=prompt
                onText("El área de un círculo se calcula con A = π × r².")
            }
        }
        suspend fun waitForAnswer(){
            val deadline=SystemClock.elapsedRealtime()+5000
            while(vm.state.value.answers.none{it.status.startsWith("Lista")&&it.text.isNotBlank()}&&SystemClock.elapsedRealtime()<deadline) kotlinx.coroutines.delay(20)
        }
        try{
            vm.temporaryConversation();vm.setOnlineAnswers(true);vm.setAnswers(true);vm.setAnswerEngine("pollinations");vm.requestSpacingMs=0
            vm.observe(801,"Estamos estudiando geometría. ¿Cómo se calcula",false)
            vm.observe(802,"el área de un círculo?",true)
            waitForAnswer()

            val answer=vm.state.value.answers.single()
            assertEquals("¿Cómo se calcula el área de un círculo?",answer.question)
            assertTrue(answer.text.contains("A = π"))
            assertTrue(capturedPrompt.contains("Estamos estudiando geometría"))
            assertTrue(capturedPrompt.contains("el área de un círculo"))
            val firstTextMs=answer.firstTextMs?:error("No se midió la primera respuesta")
            println("QUESTION_PIPELINE_FAKE_TTFB_MS: $firstTextMs")
            assertTrue("Local filtering/queueing took $firstTextMs ms",firstTextMs<2000)
        }finally{
            vm.background();holder.clear()
            val editor=prefs.edit().clear()
            before.forEach{(key,value)->when(value){
                is String->editor.putString(key,value)
                is Boolean->editor.putBoolean(key,value)
                is Long->editor.putLong(key,value)
                is Int->editor.putInt(key,value)
            }}
            editor.commit()
        }
    }
}
