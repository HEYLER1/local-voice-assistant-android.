package com.heyler.voicelab

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Explicitly run this smoke test: it sends only the fixed fictional text below. */
class OnlineLiveServiceTest{
    @Test fun realSummaryOnly()=runBlocking{
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val line=SpeechLine(1,"En esta clase ficticia estudiamos los eclipses solares. La Luna se sitúa entre la Tierra y el Sol y puede ocultar su luz. Distinguimos eclipses totales y parciales según la parte del disco solar que queda cubierta. La inclinación de la órbita lunar impide que se produzca un eclipse cada mes.",true)
        val assistant=OnlineAssistant();runCatching{assistant.warmConnection()}
        val start=System.nanoTime()
        val result=assistant.analyze(listOf(line),AssistantState(lines=listOf(line)))
        assertTrue(result.summary.isNotBlank());assertTrue(result.organization.sections.isNotEmpty())
        val report=JSONObject().put("fixture","fictional-astronomy").put("durationMs",(System.nanoTime()-start)/1e6)
            .put("summary",result.summary).put("title",result.organization.title).put("sections",result.organization.sections.size)
        File(context.getExternalFilesDir(null),"ui-tests").apply{mkdirs()}.resolve("output-budget-summary.json").writeText(report.toString(2))
    }
    @Test fun realAnonymousQuestionAndSummary()=runBlocking{
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val prefs=context.getSharedPreferences("assistant-options",0)
        val before=prefs.all
        val vm=AssistantViewModel(context.applicationContext as Application);val store=ViewModelStore();store.put("online-smoke",vm)
        suspend fun waitFor(check:()->Boolean){withTimeout(120000){while(!check())delay(100)}}
        try{
            vm.temporaryConversation();vm.setOnlineAnswers(true);vm.setAnswers(true);vm.setAnswerEngine("pollinations");vm.setTranscriptionOnly(false);vm.requestSpacingMs=0
            vm.observe(2110,"En esta clase ficticia estudiamos los eclipses solares. La Luna se sitúa entre la Tierra y el Sol y puede ocultar su luz. Distinguimos eclipses totales y parciales según la parte del disco solar que queda cubierta. La inclinación de la órbita lunar impide que se produzca un eclipse cada mes. ¿Por qué ocurre un eclipse solar?",true)
            waitFor{vm.state.value.answers.any{it.status.startsWith("Lista")||it.status.startsWith("No se completó")}}
            val answer=vm.state.value.answers.single()
            assertTrue(answer.status,answer.status.startsWith("Lista"))
            assertTrue(answer.text,answer.text.contains("Luna",true));assertFalse(vm.state.value.languageReady)
            assertTrue(vm.state.value.summary.isBlank());vm.requestSummary();waitFor{!vm.state.value.summaryPending};assertEquals(vm.state.value.summaryError,"",vm.state.value.summaryError)
            assertTrue(vm.state.value.summary.isNotBlank())
            val report=JSONObject().put("fixture","astronomy-fictional").put("provider",OnlineAssistant.NAME)
                .put("question",answer.question).put("answer",answer.text).put("firstTextMs",answer.firstTextMs)
                .put("summary",vm.state.value.summary).put("localLlmLoaded",vm.state.value.languageReady)
            File(context.getExternalFilesDir(null),"ui-tests").apply{mkdirs()}.resolve("v-online-synthetic.json").writeText(report.toString(2))
        }finally{
            vm.background();store.clear();prefs.edit().clear().apply{before.forEach{(key,value)->when(value){is String->putString(key,value);is Boolean->putBoolean(key,value);is Long->putLong(key,value);is Int->putInt(key,value)}}}.commit()
        }
    }
}
