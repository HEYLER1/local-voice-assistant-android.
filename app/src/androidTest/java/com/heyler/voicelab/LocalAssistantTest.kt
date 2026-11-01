package com.heyler.voicelab

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class LocalAssistantTest{
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private suspend fun withAssistant(block:suspend(AssistantViewModel)->Unit){
        val prefs=context.getSharedPreferences("assistant-options",0);val before=prefs.all
        val vm=AssistantViewModel(context.applicationContext as Application);val store=ViewModelStore();store.put("local",vm)
        try{vm.temporaryConversation();vm.setOnlineAnswers(true);vm.setAnswers(false);vm.setSummary(false);vm.setTranscriptionOnly(false);block(vm)}
        finally{
            vm.background();store.clear()
            prefs.edit().apply{for(key in listOf("answer-engine","online-answers","answers","summary","transcription-only")){
                when(val value=before[key]){is String->putString(key,value);is Boolean->putBoolean(key,value);else->remove(key)}
            }}.commit()
        }
    }
    private suspend fun await(check:()->Boolean)=withTimeout(10000){while(!check())delay(25)}
    @Test fun questionsSummaryAndMapUseLocalTransportWithNoOnlineFallback()=runBlocking{withAssistant{vm->
        var calls=0;var network=0
        vm.online=object:OnlineAssistant(){override suspend fun generate(prompt:String,summary:Boolean,analysis:Boolean,map:Boolean,onText:(String)->Unit){network++;error("Unexpected network")}}
        vm.local=LocalAssistant(LocalTextTransport{_,instruction,_,onText->calls++;onText(when{instruction.contains("mapa conceptual")->ResearchFixtures.response();instruction.contains("Resume y organiza")->AnalysisFixtures.eclipse();else->"La Luna pasa entre la Tierra y el Sol."})})
        vm.setAnswerEngine("qwen3")
        vm.state.value=vm.state.value.copy(lines=listOf(SpeechLine(21,"Hoy estudiamos los eclipses solares. ¿Por qué ocurre un eclipse solar? La Luna se interpone entre la Tierra y el Sol. En un eclipse total, la Luna cubre todo el disco solar. En un eclipse parcial, oculta solo una parte. La inclinación de la órbita lunar explica por qué no hay un eclipse cada mes.",true)))
        vm.ask("¿Por qué ocurre un eclipse solar?");await{vm.state.value.answers.lastOrNull()?.status?.startsWith("Lista")==true}
        assertTrue(vm.state.value.answers.last().status.contains("Qwen3 local"))
        vm.requestSummary();await{vm.state.value.summary.isNotBlank()&&!vm.state.value.generating}
        vm.state.value=vm.state.value.copy(organization=vm.state.value.organization.let{it.copy(sections=it.sections+OrganizedSection("Tipos de eclipse","Un eclipse solar puede ser total o parcial.",listOf(21L)))})
        vm.researchSummary();await{vm.state.value.organization.map!=null&&!vm.state.value.generating}
        assertEquals(2,calls);assertEquals(0,network);assertEquals("qwen3",context.getSharedPreferences("assistant-options",0).getString("answer-engine",null))
    }}
    @Test fun selectingLocalClearsOnlineQuotaWithoutDroppingTranscript()=runBlocking{withAssistant{vm->
        vm.setAnswerEngine("pollinations");vm.requestSpacingMs=0
        vm.online=object:OnlineAssistant(){override suspend fun generate(prompt:String,summary:Boolean,analysis:Boolean,map:Boolean,onText:(String)->Unit){throw OnlineQuotaException(60000)}}
        vm.ask("¿Qué es un eclipse solar?");await{vm.state.value.onlineNotice.contains("límite")}
        val line=SpeechLine(41,"La Luna se interpone entre la Tierra y el Sol durante un eclipse solar.",true)
        vm.state.value=vm.state.value.copy(lines=listOf(line))
        vm.local=LocalAssistant(LocalTextTransport{_,_,_,onText->onText("La Luna oculta el Sol.")})
        vm.setAnswerEngine("qwen3");vm.ask("¿Qué oculta el Sol?")
        await{vm.state.value.answers.last().status.startsWith("Lista")}
        assertEquals(listOf(line),vm.state.value.lines);assertTrue(vm.state.value.onlineNotice.isEmpty())
    }}
    @Test fun localFailurePreservesNotesAndDoesNotCallTheOnlineProvider()=runBlocking{withAssistant{vm->
        var network=0
        vm.online=object:OnlineAssistant(){override suspend fun generate(prompt:String,summary:Boolean,analysis:Boolean,map:Boolean,onText:(String)->Unit){network++}}
        vm.local=LocalAssistant(LocalTextTransport{_,_,_,_->throw IOException("Modelo local no disponible")})
        vm.setAnswerEngine("qwen3");vm.state.value=ResearchFixtures.state().copy(answerEngine="qwen3",autoAnswers=false,autoSummary=false)
        val previous=vm.state.value.organization.map;val summary=vm.state.value.summary
        vm.requestSummary();await{vm.state.value.summaryError.contains("no disponible")}
        assertEquals(previous,vm.state.value.organization.map);assertEquals(summary,vm.state.value.summary);assertEquals(0,network)
    }}
    @Test fun compactAnalysisRetainsLiteralSourceValidation()=runBlocking{
        var received="";var instruction=""
        val data=org.json.JSONObject(AnalysisFixtures.eclipse())
        data.getJSONArray("sections").getJSONObject(0).put("previous",org.json.JSONArray().put(1))
        val local=LocalAssistant(LocalTextTransport{prompt,system,_,out->received=prompt;instruction=system;out(data.toString())})
        val line=SpeechLine(99,"La clase explica por qué ocurre un eclipse solar y cómo se alinean la Tierra, la Luna y el Sol.",true)
        val prior=AssistantState(lines=listOf(line),organization=ClassOrganization(sections=listOf(OrganizedSection("Apunte anterior","Una idea anterior que debe conservarse.",listOf(99L)))))
        val result=local.analyze(listOf(line),prior)
        assertEquals(99L,result.questions.single().source);assertEquals(listOf(99L),result.organization.sections.single().sources);assertTrue(result.organization.sections.single().replaces.isEmpty())
        assertTrue(received.contains(line.text));assertTrue(instruction.contains("transcripción"))
    }
    @Test fun localMapOrganizesGeneratedNotesWithoutAnotherInference()=runBlocking{
        var calls=0
        val local=LocalAssistant(LocalTextTransport{_,_,_,_->calls++})
        val base=ResearchFixtures.state();val previous=base.copy(organization=base.organization.copy(sections=base.organization.sections+OrganizedSection("Tipos de eclipse","Un eclipse solar puede ser total o parcial.",base.lines.map{it.id})))
        val map=local.researchedMap("Eclipses solares",previous.lines,emptyList(),previous)
        assertEquals(0,calls);assertEquals(KnowledgeSummary.previousSections(previous).take(8).size,map.branches.size)
        assertTrue(map.branches.flatMap{it.points}.all{it.origin=="model"&&it.references.isEmpty()})
    }
    @Test fun switchingEnginesLeavesTheRunningAnswerIntactAndAcceptsTranscription()=runBlocking{withAssistant{vm->
        val started=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>()
        vm.local=LocalAssistant(LocalTextTransport{_,_,_,out->out("Fragmento local");started.complete(Unit);release.await();out(" completado.")})
        vm.online=object:OnlineAssistant(){override suspend fun generate(prompt:String,summary:Boolean,analysis:Boolean,map:Boolean,onText:(String)->Unit){onText("Respuesta online de prueba.")}}
        vm.setAnswerEngine("qwen3");vm.ask("¿Qué es un eclipse solar?");withTimeout(3000){started.await()}
        vm.setAnswerEngine("pollinations");vm.observe(88,"La transcripción sigue después de cambiar de motor.",true)
        vm.ask("¿Cuál es el siguiente tema?")
        await{vm.state.value.answers.last().status.startsWith("Lista")&&vm.state.value.lines.any{it.id==88L}}
        assertEquals("Fragmento local",vm.state.value.answers.first().text)
        assertTrue(vm.state.value.answers.first().status.startsWith("Respondiendo"))
        release.complete(Unit);await{vm.state.value.answers.first().status.startsWith("Lista")}
        assertEquals("Respuesta online de prueba.",vm.state.value.answers.last().text)
    }}
    @Test fun localTextOutlineKeepsSourcesAndRejectsNarrationAsAQuestion()=runBlocking{
        val outline="# **Eclipses solares**\nLa Luna se interpone entre la Tierra y el Sol.\n## Alineación orbital\nLa Luna puede proyectar su sombra sobre la Tierra.\n## Tipos de eclipses\nUn eclipse puede ser total o parcial."
        val local=LocalAssistant(LocalTextTransport{_,instruction,_,out->out(if(instruction.contains("Contesta"))"La Luna se interpone entre la Tierra y el Sol."else outline)})
        val line=SpeechLine(91,"¿Por qué ocurre un eclipse solar? La inclinación explica por qué no hay un eclipse cada mes.",true)
        val analysis=local.analyze(listOf(line),AssistantState())
        assertEquals("Eclipses solares",analysis.organization.title);assertEquals(2,analysis.organization.sections.size)
        assertEquals(1,analysis.questions.size);assertEquals(91L,analysis.questions.single().source)
        val map=local.researchedMap("Eclipses solares",listOf(line),emptyList(),AssistantState(lines=listOf(line),summary=analysis.summary,organization=analysis.organization))
        assertEquals(2,map.branches.size);assertTrue(map.branches.flatMap{it.points}.all{it.origin=="model"})
    }
}
