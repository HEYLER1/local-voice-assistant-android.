package com.heyler.voicelab

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test

class LessonContextTest{
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun lessonMemoryRebuildsFromHistoryAndDoesNotMixChats(){
        val store=ConversationStore(context);val first=-22001L;val second=-22002L
        try{
            store.save(first,AssistantState(lines=listOf(SpeechLine(1,"Hoy vamos a estudiar la mitosis.",true),SpeechLine(2,"Como tarea para el lunes, preparar un dibujo de la célula.",true),SpeechLine(3,"Esto es una pregunta de examen.",true))))
            store.save(second,AssistantState(lines=listOf(SpeechLine(4,"Hoy vamos a estudiar la Revolución francesa.",true))))
            val restored=LessonMemory.build(checkNotNull(store.load(first)).lines)
            assertEquals("la mitosis",restored.topic);assertEquals(1,restored.tasks.size);assertEquals(1,restored.exams.size)
            val other=LessonMemory.build(checkNotNull(store.load(second)).lines)
            assertTrue(other.tasks.isEmpty());assertTrue(other.exams.isEmpty());assertFalse(other.topic.contains("mitosis"))
        }finally{store.delete(first);store.delete(second);store.close()}
    }
    @Test fun classQuestionUsesContextAndDoesNotUploadSavedLibrary()=runBlocking{
        val prefs=context.getSharedPreferences("assistant-options",0)
        val oldOnline=prefs.getBoolean("online-answers",true);val oldAnswers=prefs.getBoolean("answers",true)
        val vm=AssistantViewModel(context.applicationContext as Application);val holder=ViewModelStore();holder.put("lesson",vm)
        val sent=java.util.concurrent.CopyOnWriteArrayList<String>()
        vm.online=OnlineAssistant(OkHttpClient.Builder().addInterceptor{chain->
            val buffer=Buffer();chain.request().body!!.writeTo(buffer);sent.add(buffer.readUtf8())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("Fixture")
                .header("Content-Type","text/event-stream").body("data: {\"choices\":[{\"delta\":{\"content\":\"Resolver los ejercicios para el viernes.\"},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n".toResponseBody("text/event-stream".toMediaType())).build()
        }.build())
        var savedId:Long?=null
        suspend fun waitFor(check:()->Boolean){withTimeout(10000){while(!check())delay(25)}}
        try{
            vm.temporaryConversation();vm.setOnlineAnswers(true);vm.setAnswers(false)
            vm.save(null,"Recuerdo","SYNTHETIC_LIBRARY_NOT_FOR_UPLOAD_22022","","team fixture")
            savedId=vm.state.value.saved.first{it.text=="SYNTHETIC_LIBRARY_NOT_FOR_UPLOAD_22022"}.id
            vm.observe(1,"Hoy vamos a estudiar la cinemática. Como tarea para el viernes, resolver los ejercicios de velocidad de la guía.",true)
            waitFor{vm.state.value.lines.isNotEmpty()}
            vm.ask("¿Qué tareas dejó el profesor?")
            waitFor{vm.state.value.answers.any{it.status.startsWith("Lista")}}
            assertEquals(1,sent.size);assertTrue(sent.single().contains("cinemática"));assertTrue(sent.single().contains("viernes"))
            assertFalse(sent.single().contains("SYNTHETIC_LIBRARY_NOT_FOR_UPLOAD"))
            vm.ask("Muestra mis recuerdos guardados")
            assertEquals(1,sent.size);assertTrue(vm.state.value.answers.last().status.startsWith("Consulta local"))
            vm.ask("¿Puedes organizar el trabajo de la clase?")
            waitFor{vm.state.value.answers.last().status.startsWith("Lista")}
            assertEquals(2,sent.size);assertTrue(sent.last().contains("respuesta previa"))
            assertTrue(sent.none{it.contains("SYNTHETIC_LIBRARY_NOT_FOR_UPLOAD")})
        }finally{
            savedId?.let{vm.delete(it)};vm.background();holder.clear()
            prefs.edit().putBoolean("online-answers",oldOnline).putBoolean("answers",oldAnswers).apply()
        }
    }
}
