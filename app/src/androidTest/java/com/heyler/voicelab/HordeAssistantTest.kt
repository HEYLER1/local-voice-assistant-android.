package com.heyler.voicelab

import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

/** Compile-only until the user authorizes packaging; all traffic is intercepted. */
class HordeAssistantTest{
    private fun reply(request:Request,text:String)=Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("Fixture")
        .body(text.toResponseBody("application/json".toMediaType())).build()
    @Test fun anonymousRequestUsesOnlyTheSelectedQwen35()=runBlocking{
        val requests=mutableListOf<Request>()
        val client=OkHttpClient.Builder().addInterceptor{chain->val r=chain.request();requests.add(r)
            reply(r,when(r.url.encodedPath){
                "/api/v2/status/models"->"""[{"name":"Qwen3.5-4B","count":2,"performance":30,"queued":0}]"""
                "/api/v2/generate/text/async"->"""{"id":"00000000-0000-4000-8000-000000000001"}"""
                else->"""{"done":true,"generations":[{"model":"Qwen3.5-4B","text":"La Luna oculta el Sol."}]}"""
            })}.build()
        var answer="";HordeAssistant(client,pollMs=1).generate("¿Qué es un eclipse solar?"){answer+=it}
        assertEquals("La Luna oculta el Sol.",answer);assertEquals(3,requests.size)
        assertEquals("0000000000",requests[1].header("apikey"));assertNull(requests[1].header("Authorization"));assertNull(requests[1].url.query)
    }
    @Test fun missingQwenDoesNotSubmitToAnotherModel()=runBlocking{
        var count=0
        val client=OkHttpClient.Builder().addInterceptor{chain->count++;reply(chain.request(),"""[{"name":"OtherModel","count":3,"performance":90,"queued":0}]""")}.build()
        val error=runCatching{HordeAssistant(client).generate("Ejemplo sintético"){fail("No answer")}}.exceptionOrNull()
        assertNotNull(error);assertTrue(error!!.message.orEmpty().contains("Qwen 3.5"));assertEquals(1,count)
    }
    @Test fun aStalledQueueIsCancelledSoAnotherProviderCanAnswer()=runBlocking{
        var removed=false
        val client=OkHttpClient.Builder().addInterceptor{chain->val r=chain.request()
            reply(r,when{
                r.method=="DELETE"->{removed=true;"{}"}
                r.url.encodedPath.endsWith("models")->"""[{"name":"Qwen3.5-4B","count":1,"performance":30,"queued":0}]"""
                r.url.encodedPath.endsWith("async")->"""{"id":"00000000-0000-4000-8000-000000000001"}"""
                else->"""{"done":false,"processing":0,"queue_position":10}"""
            })}.build()
        val error=runCatching{HordeAssistant(client,pollMs=1,queueWaitMs=0).generate("Pregunta ficticia"){fail("No answer expected")}}.exceptionOrNull()
        assertNotNull(error);assertTrue(error!!.message.orEmpty().contains("trabajador libre"));assertTrue(removed)
    }
    @Test fun copiedLocalInstructionsCannotReplaceNotes()=runBlocking{
        val local=LocalAssistant(LocalTextTransport{_,_,_,out->out("## TEMA PREVIO: #### SISTEMAS DE ECLIPSES SOLARES")})
        val error=runCatching{local.analyze(ResearchFixtures.lines,ResearchFixtures.state())}.exceptionOrNull()
        assertNotNull(error);assertTrue(error!!.message.orEmpty().contains("instrucciones"))
    }
}
