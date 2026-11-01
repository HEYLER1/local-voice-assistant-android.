package com.heyler.voicelab

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Synthetic educational input; uses the already imported model, never a remote service. */
class LocalAssistantLiveTest{
    @Test fun importedQwenRunsQuestionsSummaryAndMapInTheMainAssistant()=runBlocking{
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue("Import Qwen3 before this opt-in native test",File(context.filesDir,"model.litertlm").exists())
        val prefs=context.getSharedPreferences("assistant-options",0);val before=prefs.all
        val vm=AssistantViewModel(context.applicationContext as Application);val store=ViewModelStore();store.put("native-local",vm)
        var network=0
        val started=android.os.SystemClock.elapsedRealtime()
        val report=org.json.JSONObject().put("model","imported Qwen3 .litertlm")
        fun checkpoint(stage:String){
            report.put("stage",stage).put("elapsed_ms",android.os.SystemClock.elapsedRealtime()-started).put("network_calls",network)
                .put("question",vm.state.value.answers.lastOrNull()?.text.orEmpty()).put("first_text_ms",vm.state.value.answers.lastOrNull()?.firstTextMs)
                .put("summary",vm.state.value.summary).put("summary_error",vm.state.value.summaryError).put("topic",KnowledgeSummary.title(vm.state.value))
                .put("sections",vm.state.value.organization.sections.size).put("branches",vm.state.value.organization.map?.branches?.size?:0)
            File(context.getExternalFilesDir(null),"ui-tests").apply{mkdirs()}.resolve("v-qwen-main-chat-synthetic.json").writeText(report.toString(2))
        }
        vm.online=object:OnlineAssistant(){override suspend fun generate(prompt:String,summary:Boolean,analysis:Boolean,map:Boolean,onText:(String)->Unit){network++;error("No network is allowed in local mode")}}
        val native=(vm.local as LocalAssistant).transport
        vm.local=LocalAssistant(LocalTextTransport{prompt,instruction,tokens,onText->
            val raw=StringBuilder()
            try{native.generate(prompt,instruction,tokens){raw.append(it);onText(it)}}
            finally{File(context.getExternalFilesDir(null),"ui-tests").apply{mkdirs()}.resolve(if(instruction.contains("mapa conceptual"))"v-qwen-map-raw-synthetic.txt"else if(instruction.contains("Resume y organiza"))"v-qwen-analysis-raw-synthetic.txt"else "v-qwen-answer-raw-synthetic.txt").writeText(raw.toString())}
        })
        suspend fun await(check:()->Boolean)=withTimeout(180000){while(!check())delay(100)}
        try{
            vm.temporaryConversation();vm.setOnlineAnswers(true);vm.setAnswers(false);vm.setSummary(false);vm.setTranscriptionOnly(false);vm.setAnswerEngine("qwen3")
            vm.state.value=vm.state.value.copy(lines=listOf(SpeechLine(1301,"Hoy estudiamos los eclipses solares. Un eclipse solar ocurre cuando la Luna se interpone entre la Tierra y el Sol. En un eclipse total, la Luna cubre todo el disco solar. En un eclipse parcial, oculta solo una parte. La inclinación de la órbita lunar explica por qué no hay un eclipse cada mes.",true)))
            vm.ask("¿Qué es un eclipse solar?")
            await{vm.state.value.answers.lastOrNull()?.let{it.status.startsWith("Lista")||it.status.startsWith("No se completó")}==true}
            assertTrue(vm.state.value.answers.last().status+": "+vm.state.value.answers.last().text,vm.state.value.answers.last().status.startsWith("Lista"))
            assertTrue(vm.state.value.answers.last().text.contains("Luna",true));assertFalse(vm.state.value.answers.last().text.contains("Relaciona la respuesta"));assertTrue(vm.state.value.languageReady)
            checkpoint("question_completed")
            vm.requestSummary();await{vm.state.value.summary.isNotBlank()||vm.state.value.summaryError.isNotBlank()}
            checkpoint("summary_completed")
            assertTrue(vm.state.value.summaryError,vm.state.value.summaryError.isBlank());assertTrue(vm.state.value.summary,vm.state.value.summary.contains("Luna",true));assertFalse(vm.state.value.summary.contains("síntesis fiel"))
            await{!vm.state.value.generating}
            vm.researchSummary();await{vm.state.value.organization.map!=null||vm.state.value.summaryError.isNotBlank()}
            checkpoint("map_completed")
            assertTrue(vm.state.value.summaryError,vm.state.value.summaryError.isBlank());assertTrue(vm.state.value.organization.map!!.branches.size>=2)
            assertEquals(0,network)
        }finally{
            val closed=CompletableDeferred<Unit>();vm.releaseModels{closed.complete(Unit)};withTimeout(15000){closed.await()};vm.background();store.clear()
            prefs.edit().apply{for(key in listOf("answer-engine","online-answers","answers","summary","transcription-only"))when(val value=before[key]){is String->putString(key,value);is Boolean->putBoolean(key,value);else->remove(key)}}.commit()
        }
    }
}
