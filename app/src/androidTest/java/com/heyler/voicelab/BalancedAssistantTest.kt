package com.heyler.voicelab

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/** Synthetic transports only; does not send classroom text to an external provider. */
class BalancedAssistantTest{
    private val lecture="En esta clase ficticia estudiamos los eclipses solares. La Luna se sitúa entre la Tierra y el Sol y puede ocultar su luz. Distinguimos eclipses totales y parciales según la parte del disco solar que queda cubierta. La inclinación de la órbita lunar impide que se produzca un eclipse cada mes."

    private suspend fun withAssistant(block:suspend(AssistantViewModel)->Unit){
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val prefs=context.getSharedPreferences("assistant-options",0);val before=prefs.all
        val vm=AssistantViewModel(context.applicationContext as Application);val holder=ViewModelStore();holder.put("balanced",vm)
        try{
            vm.temporaryConversation();vm.setAnswerEngine("auto");vm.setOnlineAnswers(true);vm.setAnswers(true);vm.setTranscriptionOnly(false)
            vm.requestSpacingMs=0;vm.state.value=vm.state.value.copy(localModelAvailable=false)
            block(vm)
        }finally{vm.background();holder.clear();prefs.edit().clear().apply{before.forEach{(key,value)->when(value){is Boolean->putBoolean(key,value);is String->putString(key,value);is Long->putLong(key,value);is Int->putInt(key,value)}}}.commit()}
    }
    private suspend fun await(check:()->Boolean)=withTimeout(7000){while(!check())delay(25)}
    private fun result(lines:List<SpeechLine>)=AnalysisResult(ClassOrganization("Eclipses solares",sections=listOf(OrganizedSection("Alineación orbital","La Luna oculta el Sol.",lines.map{it.id})),covered=lines.associate{it.id to it.revision}),"La clase explica los eclipses solares.",emptyList())
    @Test fun questionsDoNotCancelTheSummaryAndUseTheFreeEngine()=runBlocking{withAssistant{vm->
        val summaryStarted=CompletableDeferred<Unit>();val finishSummary=CompletableDeferred<Unit>()
        vm.online=object:OnlineAssistant(){override suspend fun analyze(lines:List<SpeechLine>,previous:AssistantState):AnalysisResult{summaryStarted.complete(Unit);finishSummary.await();return result(lines)}}
        vm.remote=object:OnlineAssistant(){override suspend fun generate(prompt:String,summary:Boolean,analysis:Boolean,map:Boolean,onText:(String)->Unit){onText("La Luna oculta el Sol.")}}
        vm.importTranscript(lecture)
        vm.requestSummary();withTimeout(3000){summaryStarted.await()}
        vm.ask("¿Qué es un eclipse solar?");await{vm.state.value.answers.lastOrNull()?.status?.startsWith("Lista")==true}
        assertTrue(vm.state.value.analysisRunning);assertFalse(finishSummary.isCompleted)
        finishSummary.complete(Unit);await{vm.state.value.summary.isNotBlank()&&!vm.state.value.generating}
    }}
    @Test fun automaticModeUsesTheStreamingProviderBeforeCheckingTheCommunityQueue()=runBlocking{withAssistant{vm->
        val hordeCalls=AtomicInteger()
        vm.online=object:OnlineAssistant(){override suspend fun generate(prompt:String,summary:Boolean,analysis:Boolean,map:Boolean,onText:(String)->Unit){onText("Respuesta de prueba.")}}
        vm.remote=object:OnlineAssistant(){override suspend fun generate(prompt:String,summary:Boolean,analysis:Boolean,map:Boolean,onText:(String)->Unit){hordeCalls.incrementAndGet();throw ProviderAccessException("AI Horde")}}
        vm.ask("¿Qué es un eclipse solar?");await{vm.state.value.answers.lastOrNull()?.status?.startsWith("Lista")==true}
        assertEquals(0,hordeCalls.get());assertTrue(vm.state.value.answers.last().status.contains("Pollinations"))
    }}
    @Test fun recordingAndPausingDoNotGenerateSummariesAndRepeatedTapsAreDeduplicated()=runBlocking{withAssistant{vm->
        val calls=AtomicInteger();val release=CompletableDeferred<Unit>()
        vm.online=object:OnlineAssistant(){override suspend fun analyze(lines:List<SpeechLine>,previous:AssistantState):AnalysisResult{calls.incrementAndGet();release.await();return result(lines)}}
        vm.observe(91,lecture,true)
        await{vm.state.value.lines.isNotEmpty()};delay(2800);vm.pause();delay(450)
        assertEquals(0,calls.get())
        vm.requestSummary();await{calls.get()==1};repeat(4){vm.requestSummary()};assertEquals(1,calls.get())
        release.complete(Unit);await{vm.state.value.summary.isNotBlank()&&!vm.state.value.generating}
        vm.requestSummary();delay(300);assertEquals(1,calls.get())
    }}
    @Test fun oneQuotaDoesNotBlockFallbackAnswersOrManualSummary()=runBlocking{withAssistant{vm->
        vm.setAnswerEngine("horde")
        val hordeCalls=AtomicInteger();val pollCalls=AtomicInteger()
        vm.remote=object:OnlineAssistant(){override suspend fun generate(prompt:String,summary:Boolean,analysis:Boolean,map:Boolean,onText:(String)->Unit){hordeCalls.incrementAndGet();throw OnlineQuotaException(60000,"AI Horde")}}
        vm.online=object:OnlineAssistant(){
            override suspend fun generate(prompt:String,summary:Boolean,analysis:Boolean,map:Boolean,onText:(String)->Unit){pollCalls.incrementAndGet();onText("Explicación de prueba.")}
            override suspend fun analyze(lines:List<SpeechLine>,previous:AssistantState)=result(lines)
        }
        vm.ask("¿Qué es un eclipse solar?");await{vm.state.value.answers.lastOrNull()?.status?.startsWith("Lista")==true}
        vm.ask("¿Cómo se alinea la Luna?");await{vm.state.value.answers.lastOrNull()?.status?.startsWith("Lista")==true}
        assertEquals(1,hordeCalls.get());assertEquals(2,pollCalls.get())
        vm.importTranscript(lecture);vm.requestSummary();await{vm.state.value.summary.isNotBlank()}
    }}
    @Test fun aPartialAnswerIsNotDuplicatedOnAnotherProvider()=runBlocking{withAssistant{vm->
        vm.setAnswerEngine("horde")
        val alternate=AtomicInteger()
        vm.remote=object:OnlineAssistant(){override suspend fun generate(prompt:String,summary:Boolean,analysis:Boolean,map:Boolean,onText:(String)->Unit){onText("Fragmento recibido.");throw java.io.IOException("Conexión interrumpida")}}
        vm.online=object:OnlineAssistant(){override suspend fun generate(prompt:String,summary:Boolean,analysis:Boolean,map:Boolean,onText:(String)->Unit){alternate.incrementAndGet();onText("No debe mezclarse.")}}
        vm.ask("¿Qué es un eclipse solar?");await{vm.state.value.answers.lastOrNull()?.status?.startsWith("No se completó")==true}
        assertEquals("Fragmento recibido.",vm.state.value.answers.last().text);assertEquals(0,alternate.get())
    }}
    @Test fun insufficientOrUnconfirmedTextExplainsWhyAndNeverAutoStarts()=runBlocking{withAssistant{vm->
        val calls=AtomicInteger()
        vm.online=object:OnlineAssistant(){override suspend fun analyze(lines:List<SpeechLine>,previous:AssistantState):AnalysisResult{calls.incrementAndGet();return result(lines)}}
        vm.importTranscript("Hola, buenos días.");vm.requestSummary()
        assertTrue(vm.state.value.summaryError.contains("suficiente"));assertFalse(vm.state.value.summaryPending)
        vm.observe(701,lecture,false);await{vm.state.value.lines.any{it.id==701L}}
        vm.requestSummary();assertTrue(vm.state.value.summaryError.contains("confirmado"));assertEquals(0,calls.get())
        vm.observe(701,lecture,true);await{vm.state.value.lines.any{it.id==701L&&it.final}};delay(1000)
        assertEquals(0,calls.get())
        vm.requestSummary();await{vm.state.value.summary.isNotBlank()&&!vm.state.value.summaryPending}
        assertEquals(1,calls.get())
    }}
    @Test fun anOutputLimitLeavesTheAnswerVisibleAndDoesNotMixAnotherProvider()=runBlocking{withAssistant{vm->
        val fallback=AtomicInteger()
        vm.online=object:OnlineAssistant(){override suspend fun generate(prompt:String,summary:Boolean,analysis:Boolean,map:Boolean,onText:(String)->Unit){onText("Explicación parcial.");throw OnlineOutputLimitException()}}
        vm.remote=object:OnlineAssistant(){override suspend fun generate(prompt:String,summary:Boolean,analysis:Boolean,map:Boolean,onText:(String)->Unit){fallback.incrementAndGet()}}
        vm.ask("¿Qué es un eclipse solar?");await{vm.state.value.answers.lastOrNull()?.status?.startsWith("Respuesta parcial")==true}
        assertEquals("Explicación parcial.",vm.state.value.answers.last().text);assertEquals(0,fallback.get())
    }}
    @Test fun anOutputLimitInTheSummaryPreservesThePreviousNotesAndMap()=runBlocking{withAssistant{vm->
        vm.importTranscript(lecture)
        val old=ResearchFixtures.state()
        vm.state.value=vm.state.value.copy(summary=old.summary,organization=old.organization)
        vm.online=object:OnlineAssistant(){override suspend fun analyze(lines:List<SpeechLine>,previous:AssistantState):AnalysisResult{throw OnlineOutputLimitException()}}
        vm.requestSummary();await{!vm.state.value.summaryPending&&vm.state.value.summaryError.isNotBlank()}
        assertEquals(old.summary,vm.state.value.summary);assertEquals(old.organization,vm.state.value.organization)
    }}
    @Test fun aParserErrorCannotExposeRawClassTextOrReplaceThePreviousSummary()=runBlocking{withAssistant{vm->
        vm.importTranscript(lecture)
        val old=ResearchFixtures.state();vm.state.value=vm.state.value.copy(summary=old.summary,organization=old.organization)
        vm.online=object:OnlineAssistant(){override suspend fun analyze(lines:List<SpeechLine>,previous:AssistantState):AnalysisResult{throw org.json.JSONException("Expected ':' at character 2758 of {\"summary\":\"Contenido ficticio privado\"}")}}
        vm.requestSummary();await{!vm.state.value.summaryPending&&vm.state.value.summaryError.isNotBlank()}
        assertEquals(SummaryIssues.INVALID_FORMAT,vm.state.value.summaryError)
        assertEquals(old.summary,vm.state.value.summary);assertEquals(old.organization,vm.state.value.organization)
    }}
    @Test fun waitingSummaryStaysOnPollinationsAndShowsProgressWithoutStartingLocal()=runBlocking{withAssistant{vm->
        vm.setAnswerEngine("pollinations");vm.state.value=vm.state.value.copy(localModelAvailable=true)
        val started=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>();val summaries=AtomicInteger();val localCalls=AtomicInteger()
        vm.online=object:OnlineAssistant(){
            override suspend fun generate(prompt:String,summary:Boolean,analysis:Boolean,map:Boolean,onText:(String)->Unit){started.complete(Unit);release.await();onText("Explicación sintética.")}
            override suspend fun analyze(lines:List<SpeechLine>,previous:AssistantState):AnalysisResult{summaries.incrementAndGet();return result(lines)}
        }
        vm.local=object:OnlineAssistant(){override suspend fun analyze(lines:List<SpeechLine>,previous:AssistantState):AnalysisResult{localCalls.incrementAndGet();error("No debe cambiar a local")}}
        vm.ask("¿Qué es un eclipse solar?");withTimeout(3000){started.await()}
        vm.importTranscript(lecture);vm.requestSummary();repeat(5){vm.requestSummary()}
        await{vm.state.value.summaryPending&&vm.state.value.summaryStatus.contains("Esperando")}
        assertEquals(0,summaries.get());assertEquals(0,localCalls.get())
        release.complete(Unit);await{vm.state.value.summary.isNotBlank()&&!vm.state.value.summaryPending}
        assertEquals(1,summaries.get());assertEquals(0,localCalls.get())
    }}
    @Test fun successfulManualSummaryReplacesAnOldMapInsteadOfBeingHiddenBehindIt()=runBlocking{withAssistant{vm->
        vm.importTranscript(lecture)
        vm.state.value=vm.state.value.copy(organization=ResearchFixtures.state().organization,summary="Resumen anterior.")
        vm.online=object:OnlineAssistant(){override suspend fun analyze(lines:List<SpeechLine>,previous:AssistantState):AnalysisResult{
            assertTrue(previous.summary.isEmpty());assertTrue(previous.organization.sections.isEmpty());assertNull(previous.organization.map)
            return result(lines)
        }}
        vm.requestSummary();await{vm.state.value.summary=="La clase explica los eclipses solares."&&!vm.state.value.summaryPending}
        assertNull(vm.state.value.organization.map)
        assertEquals("Alineación orbital",vm.state.value.organization.sections.single().title)
    }}
    @Test fun closedQuestionsInPartialAudioAreAnsweredOnceWithoutWaitingForTheFinalSegment()=runBlocking{withAssistant{vm->
        vm.setAnswerEngine("horde")
        val calls=AtomicInteger()
        vm.remote=object:OnlineAssistant(){override suspend fun generate(prompt:String,summary:Boolean,analysis:Boolean,map:Boolean,onText:(String)->Unit){calls.incrementAndGet();onText("Respuesta sintética basada en el contexto.")}}
        val text="La clase trata de astronomía y el docente pregunta ¿Qué es un eclipse solar? ¿Por qué ocurre un eclipse solar? ¿Cuántos eclipses solares ocurren por año?"
        vm.observe(901,text,false)
        await{vm.state.value.answers.size==3&&vm.state.value.answers.all{it.status.startsWith("Lista")}}
        assertTrue(vm.state.value.lines.single().final==false);assertEquals(3,calls.get());assertTrue(vm.state.value.summary.isEmpty())
        vm.observe(901,text,true);delay(1000);assertEquals(3,calls.get());assertEquals(3,vm.state.value.answers.size)
    }}

}
