package com.heyler.voicelab

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class ClassOrganizationTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private val lecture="En esta clase ficticia estudiamos métricas de software. cómo se mide el tamaño de un sistema de software se pueden comparar medidas de tamaño y complejidad. Las métricas ayudan a evaluar sistemas. También hablamos de auditoría de sistemas."
    @Test fun sourceValidationAndDedupKeepOnlySupportedQuestions(){
        val lines=listOf(SpeechLine(98765432123456789L,lecture,true))
        val result=AnalysisProtocol.parse(AnalysisFixtures.software(),lines)
        assertEquals(2,result.questions.size)
        assertEquals(1,result.questions.count{it.suggested})
        assertTrue(result.questions.all{it.source==lines.single().id&&lines.single().text.contains(it.evidence)})
        assertFalse(result.questions.any{it.question.contains("COCOMO")||it.question=="¿Cómo se mide?"})
        assertEquals(lines.single().id,result.organization.sections.single().sources.single())
    }
    @Test fun evidenceCanCrossAdjacentTranscriptChunks(){
        val lines=listOf(SpeechLine(701,"En clase preguntamos cómo se",true),SpeechLine(702,"mide el tamaño de un sistema de software y luego comparamos medidas. Las métricas ayudan a evaluar sistemas.",true))
        val result=AnalysisProtocol.parse(AnalysisFixtures.software(),lines)
        val heard=result.questions.first{!it.suggested}
        assertEquals(701L,heard.source);assertEquals(listOf(701L,702L),heard.sources)
        assertTrue(heard.evidence.contains("cómo se mide"))
    }
    @Test fun oneRequestOrganizesWholeBlockAndQuotaDoesNotFloodCards()=runBlocking{
        val prefs=context.getSharedPreferences("assistant-options",0);val before=prefs.all
        val vm=AssistantViewModel(context.applicationContext as Application);val holder=ViewModelStore();holder.put("analysis",vm)
        val calls=AtomicInteger();var quota=false
        vm.requestSpacingMs=0
        vm.online=OnlineAssistant(OkHttpClient.Builder().addInterceptor{chain->calls.incrementAndGet();Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(if(quota)429 else 200).message("Fixture").header("Content-Type","text/event-stream").header("Retry-After","60").body((if(quota)"fixture quota"else AnalysisFixtures.sse(AnalysisFixtures.software())).toResponseBody("text/event-stream".toMediaType())).build()}.build())
        suspend fun await(check:()->Boolean){withTimeout(10000){while(!check())delay(30)}}
        try{
            vm.temporaryConversation();vm.setOnlineAnswers(true);vm.setAnswers(true);vm.setSummary(true);vm.setTranscriptionOnly(false);vm.renameConversation("Mi clase elegida")
            vm.importTranscript(lecture)
            await{vm.state.value.answers.size==2&&!vm.state.value.generating}
            assertEquals(1,calls.get());assertEquals("Mi clase elegida",vm.state.value.conversationTitle)
            assertEquals("Métricas de software y auditoría",vm.state.value.organization.title)
            assertTrue(vm.state.value.summary.isNotBlank())
            val originalId=vm.state.value.answers.first().id
            vm.requestSummary();await{calls.get()==2&&!vm.state.value.generating}
            assertEquals(2,vm.state.value.answers.size);assertEquals(originalId,vm.state.value.answers.first().id)
            val previous=vm.state.value.summary;quota=true;vm.requestSummary()
            await{vm.state.value.onlineNotice.contains("límite")}
            val failedCalls=calls.get();repeat(3){vm.requestSummary()};delay(500)
            assertEquals(failedCalls,calls.get());assertEquals(previous,vm.state.value.summary);assertEquals(2,vm.state.value.answers.size)
            vm.ask("¿Cómo se calcula el tamaño funcional?");assertEquals(3,vm.state.value.answers.size);assertTrue(vm.state.value.answers.last().status.startsWith("En pausa"));assertEquals(failedCalls,calls.get())
        }finally{
            vm.background();holder.clear()
            prefs.edit().apply{for(key in listOf("online-answers","answers","summary","transcription-only")){(before[key] as? Boolean)?.let{putBoolean(key,it)}?:remove(key)}}.commit()
        }
    }
    @Test fun importingLongTextKeepsEveryWord(){
        val vm=AssistantViewModel(context.applicationContext as Application);val holder=ViewModelStore();holder.put("import",vm)
        val prefs=context.getSharedPreferences("assistant-options",0);val before=prefs.getBoolean("online-answers",true)
        try{vm.temporaryConversation();vm.setOnlineAnswers(false);val text=(1..1800).joinToString(" "){"término$it"};vm.importTranscript(text)
            assertTrue(vm.state.value.lines.size>10);assertEquals(text,vm.state.value.lines.joinToString(" "){it.text});assertTrue(vm.state.value.lines.all{it.text.length<=900})
        }finally{vm.background();holder.clear();prefs.edit().putBoolean("online-answers",before).commit()}
    }
}
