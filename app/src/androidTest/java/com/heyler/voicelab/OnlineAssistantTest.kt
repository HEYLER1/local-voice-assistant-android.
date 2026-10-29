package com.heyler.voicelab

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.Source
import okio.Timeout
import okio.buffer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class OnlineAssistantTest{
    private fun response(request:Request,text:String,code:Int=200)=Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
        .code(code).message("Fixture").header("Content-Type","text/event-stream").body(text.toResponseBody("text/event-stream".toMediaType())).build()
    private fun stream(text:String)="data: "+JSONObject().put("choices",org.json.JSONArray().put(JSONObject().put("delta",JSONObject().put("content",text)).put("finish_reason","stop")))+"\n\ndata: [DONE]\n\n"

    @Test fun streamsOnlyAnswerAndSendsNoCredentialsOrPromptInUrl()=runBlocking{
        var sent:Request?=null
        val client=OkHttpClient.Builder().addInterceptor{chain->
            sent=chain.request()
            response(chain.request(),"data: {\"choices\":[{\"delta\":{\"reasoning\":\"Internal hidden text\"}}]}\n\n"+stream("La Luna pasa delante del Sol."))
        }.build()
        val output=StringBuilder()
        OnlineAssistant(client).generate("Pregunta ficticia sobre eclipses"){output.append(it)}
        assertEquals("La Luna pasa delante del Sol.",output.toString())
        val request=checkNotNull(sent)
        assertEquals("POST",request.method);assertEquals(OnlineAssistant.ENDPOINT,request.url.toString())
        assertNull(request.header("Authorization"));assertNull(request.url.query)
        val buffer=Buffer();checkNotNull(request.body).writeTo(buffer)
        val body=JSONObject(buffer.readUtf8())
        assertEquals("openai-fast",body.getString("model"));assertTrue(body.getBoolean("stream"))
        assertEquals(2,body.getJSONArray("messages").length())
    }
    @Test fun quotaFailureDoesNotRetryOrExposeProviderBody()=runBlocking{
        val calls=AtomicInteger()
        val client=OkHttpClient.Builder().addInterceptor{chain->calls.incrementAndGet();response(chain.request(),"do not show this raw body",429)}.build()
        val failure=runCatching{OnlineAssistant(client).generate("Ejemplo ficticio") {fail("No answer expected")}}.exceptionOrNull()
        assertNotNull(failure);assertTrue(failure!!.message.orEmpty().contains("límite"))
        assertFalse(failure.message.orEmpty().contains("raw body"));assertEquals(1,calls.get())
    }
    @Test fun truncatedStreamIsNotReportedAsCompleted()=runBlocking{
        val client=OkHttpClient.Builder().addInterceptor{chain->response(chain.request(),"data: {\"choices\":[{\"delta\":{\"content\":\"Fragmento\"}}]}\n\n")}.build()
        val output=StringBuilder()
        val error=runCatching{OnlineAssistant(client).generate("Ejemplo ficticio"){output.append(it)}}.exceptionOrNull()
        assertEquals("Fragmento",output.toString());assertNotNull(error)
    }
    @Test fun cancellationCancelsTheNetworkCall()=runBlocking{
        val active=AtomicReference<Call>()
        val reading=CompletableDeferred<Unit>()
        val client=OkHttpClient.Builder().addInterceptor{chain->
            active.set(chain.call())
            val body=object:ResponseBody(){
                override fun contentType()="text/event-stream".toMediaType()
                override fun contentLength()=-1L
                override fun source()=object:Source{
                    override fun timeout()=Timeout.NONE
                    override fun close(){}
                    override fun read(sink:Buffer,byteCount:Long):Long{
                        reading.complete(Unit)
                        while(!chain.call().isCanceled())Thread.sleep(10)
                        throw IOException("Canceled fixture")
                    }
                }.buffer()
            }
            response(chain.request(),"").newBuilder().body(body).build()
        }.build()
        val job=launch{OnlineAssistant(client).generate("Ejemplo ficticio"){fail("No late answer expected")}}
        withTimeout(3000){reading.await()};job.cancelAndJoin()
        assertTrue(active.get().isCanceled())
    }
    @Test fun activeBackgroundSessionStillAnalyzesQuestions()=runBlocking{
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val prefs=context.getSharedPreferences("assistant-options",0)
        val previous=prefs.all
        val vm=AssistantViewModel(context.applicationContext as Application)
        val store=ViewModelStore();store.put("background-online-test",vm)
        try{
            vm.temporaryConversation();vm.setOnlineProvider("pollinations");vm.setOnlineAnswers(true);vm.setAnswers(true);vm.setTranscriptionOnly(false);vm.setBackgroundListening(true)
            vm.requestSpacingMs=0
            vm.online=OnlineAssistant(OkHttpClient.Builder().addInterceptor{chain->response(chain.request(),stream(AnalysisFixtures.eclipse()))}.build())
            vm.setBackgroundServiceActive(true);vm.background()
            assertFalse(vm.state.value.appVisible)
            vm.observe(2710,"En esta clase de astronomía estudiamos los eclipses solares. ¿Por qué ocurre un eclipse solar?",true)
            withTimeout(8000){while(vm.state.value.answers.none{it.status.startsWith("Lista")})delay(50)}
            assertTrue(vm.state.value.answers.single().text.contains("Luna"))
            assertFalse(vm.state.value.languageReady)
        }finally{
            vm.setBackgroundServiceActive(false);vm.background();store.clear()
            prefs.edit().apply{for(key in listOf("online-answers","answers","transcription-only","background-listening")){(previous[key] as? Boolean)?.let{putBoolean(key,it)}?:remove(key)};(previous["online-provider"] as? String)?.let{putString("online-provider",it)}?:remove("online-provider")}.commit()
        }
    }
    @Test fun viewModelRoutesAudioQuestionsAndSummaryOnlineWithoutLoadingLocalLlm()=runBlocking{
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val preferences=context.getSharedPreferences("assistant-options",0)
        val oldProvider=preferences.getString("online-provider","pollinations");val oldOnline=preferences.getBoolean("online-answers",true);val oldAnswers=preferences.getBoolean("answers",true);val oldOnly=preferences.getBoolean("transcription-only",false)
        val vm=AssistantViewModel(context.applicationContext as Application);val store=ViewModelStore();store.put("online-test",vm)
        val calls=AtomicInteger()
        vm.requestSpacingMs=0
        vm.online=OnlineAssistant(OkHttpClient.Builder().addInterceptor{chain->calls.incrementAndGet();response(chain.request(),stream(AnalysisFixtures.eclipse()))}.build())
        suspend fun waitFor(check:()->Boolean){withTimeout(8000){while(!check())delay(30)}}
        try{
            vm.temporaryConversation();vm.setOnlineProvider("pollinations");vm.setOnlineAnswers(true);vm.setAnswers(true);vm.setTranscriptionOnly(false)
            vm.observe(2101,"La clase trata de astronomía y estudia la posición de la Luna y la Tierra respecto al Sol. ¿Por qué ocurre un eclipse solar?",true)
            waitFor{vm.state.value.answers.any{it.status.startsWith("Lista")}}
            assertEquals(1,calls.get());assertFalse(vm.state.value.languageReady)
            assertTrue(vm.state.value.answers.single().text.contains("Luna"))
            vm.requestSummary();waitFor{calls.get()==2&&!vm.state.value.generating}
            assertEquals(2,calls.get())
            val previousSummary=vm.state.value.summary
            vm.online=OnlineAssistant(OkHttpClient.Builder().addInterceptor{chain->calls.incrementAndGet();response(chain.request(),"quota fixture",429)}.build())
            vm.observe(2102,"La siguiente parte de la clase compara un eclipse parcial y un eclipse total, usando dibujos de la Tierra y de la Luna para repasar las posiciones.",true)
            waitFor{vm.state.value.lines.size==2};vm.requestSummary()
            waitFor{vm.state.value.summaryError.contains("límite")}
            assertEquals(previousSummary,vm.state.value.summary);assertEquals(3,calls.get())
            vm.setOnlineAnswers(false);vm.ask("¿Cuánto es dos más dos?");vm.requestSummary();delay(150)
            assertEquals(3,calls.get());assertTrue(vm.state.value.answers.last().status.contains("desactivadas"))
        }finally{
            vm.background();store.clear()
            preferences.edit().putString("online-provider",oldProvider).putBoolean("online-answers",oldOnline).putBoolean("answers",oldAnswers).putBoolean("transcription-only",oldOnly).apply()
        }
    }
}
