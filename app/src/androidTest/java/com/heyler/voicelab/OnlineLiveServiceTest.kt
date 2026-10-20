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
    @Test fun realAnonymousQuestionAndSummary()=runBlocking{
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val prefs=context.getSharedPreferences("assistant-options",0)
        val oldOnline=prefs.getBoolean("online-answers",true);val oldAnswers=prefs.getBoolean("answers",true)
        val vm=AssistantViewModel(context.applicationContext as Application);val store=ViewModelStore();store.put("online-smoke",vm)
        suspend fun waitFor(check:()->Boolean){withTimeout(90000){while(!check())delay(100)}}
        try{
            vm.temporaryConversation();vm.setOnlineAnswers(true);vm.setAnswers(true)
            vm.observe(2110,"En esta clase ficticia de astronomía estudiamos cómo la Luna pasa entre la Tierra y el Sol. ¿Por qué ocurre un eclipse solar?",true)
            waitFor{vm.state.value.answers.any{it.status.startsWith("Lista")||it.status.startsWith("No se completó")}}
            val answer=vm.state.value.answers.single()
            assertTrue(answer.status,answer.status.startsWith("Lista"))
            assertTrue(answer.text,answer.text.contains("Luna",true));assertFalse(vm.state.value.languageReady)
            vm.requestSummary();waitFor{vm.state.value.summary.isNotBlank()}
            assertTrue(vm.state.value.summary.isNotBlank())
            val report=JSONObject().put("fixture","astronomy-fictional").put("provider",OnlineAssistant.NAME)
                .put("question",answer.question).put("answer",answer.text).put("firstTextMs",answer.firstTextMs)
                .put("summary",vm.state.value.summary).put("localLlmLoaded",vm.state.value.languageReady)
            File(context.getExternalFilesDir(null),"ui-tests").apply{mkdirs()}.resolve("v-online-synthetic.json").writeText(report.toString(2))
        }finally{
            vm.background();store.clear();prefs.edit().putBoolean("online-answers",oldOnline).putBoolean("answers",oldAnswers).apply()
        }
    }
}
