package com.heyler.voicelab

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class ResearchViewModelTest {
    @Test fun explicitResearchUsesOneModelCallAndAFailurePreservesThePreviousMap()=runBlocking{
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val preferences=context.getSharedPreferences("assistant-options",0);val original=preferences.all
        val vm=AssistantViewModel(context.applicationContext as Application);val holder=ViewModelStore();holder.put("research",vm)
        val modelCalls=AtomicInteger();var quota=false
        vm.requestSpacingMs=0
        vm.online=OnlineAssistant(OkHttpClient.Builder().addInterceptor{chain->
            modelCalls.incrementAndGet()
            val content=if(quota)"quota"else AnalysisFixtures.sse(ResearchFixtures.response())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(if(quota)429 else 200).message("Fixture")
                .header("Content-Type","text/event-stream").header("Retry-After","60").body(content.toResponseBody("text/event-stream".toMediaType())).build()
        }.build())
        suspend fun await(check:()->Boolean){withTimeout(10000){while(!check())delay(30)}}
        try{
            vm.temporaryConversation();vm.setOnlineProvider("pollinations");vm.setOnlineAnswers(true);vm.setAnswers(false);vm.setSummary(false);vm.setTranscriptionOnly(false)
            vm.state.value=vm.state.value.copy(lines=ResearchFixtures.lines,organization=ResearchFixtures.state().organization.copy(map=null))
            val originalNotes=vm.state.value.organization.sections
            vm.researchSummary();await{vm.state.value.organization.map!=null&&!vm.state.value.generating}
            assertEquals(1,modelCalls.get())
            assertEquals(8,vm.state.value.organization.map!!.branches.size)
            assertEquals(originalNotes,vm.state.value.organization.sections)
            val first=vm.state.value.organization.map
            quota=true;vm.researchSummary();await{vm.state.value.summaryError.contains("límite")}
            assertEquals(first,vm.state.value.organization.map);assertEquals(2,modelCalls.get())
            vm.researchSummary();delay(150);assertEquals(2,modelCalls.get())
        }finally{
            vm.background();holder.clear()
            preferences.edit().apply{for(key in listOf("online-answers","answers","summary","transcription-only")){(original[key] as? Boolean)?.let{putBoolean(key,it)}?:remove(key)};(original["online-provider"] as? String)?.let{putString("online-provider",it)}?:remove("online-provider")}.commit()
        }
    }
}
