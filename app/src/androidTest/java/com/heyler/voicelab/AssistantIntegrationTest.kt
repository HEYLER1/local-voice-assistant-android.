package com.heyler.voicelab

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class AssistantIntegrationTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private fun waitFor(timeout:Long=45000,condition:()->Boolean){val deadline=SystemClock.elapsedRealtime()+timeout;while(!condition()&&SystemClock.elapsedRealtime()<deadline)SystemClock.sleep(100);assertTrue("Timed out",condition())}
    private fun withAssistant(block:(AssistantViewModel)->Unit){val vm=AssistantViewModel(context.applicationContext as Application);val store=ViewModelStore();store.put("assistant",vm);try{block(vm)}finally{vm.background();store.clear();SystemClock.sleep(500)}}
    @Test fun realAudioToLiveAnswerAndSummary()=withAssistant{vm->
        val speech=LocalEngines(context)
        val before=context.filesDir.listFiles()?.map{it.name}?.toSet()?:emptySet()
        try{
            speech.loadSpeech()
            val bytes=context.assets.open("fixtures/5.wav").use{it.readBytes()};val audio=Wav16.read(bytes)
            val text=speech.transcribe(audio);audio.fill(0f);bytes.fill(0)
            assertTrue("Real synthetic ASR must preserve a question",ConversationRules.questions(text).isNotEmpty())
            vm.observe(51,text,true)
            waitFor{vm.state.value.answers.any{it.status.startsWith("Lista")&&it.text.isNotBlank()}}
            assertEquals(1,vm.state.value.answers.size)
            assertEquals(text,vm.state.value.lines.single().text)
            // Revising only surrounding narration must not repeat the same question.
            vm.observe(51,"Estamos estudiando geometría. $text",true)
            SystemClock.sleep(1200);assertEquals(1,vm.state.value.answers.size)
            vm.observe(52,"El eclipse solar ocurre cuando la Luna pasa delante del Sol y bloquea su luz durante un tiempo breve.",true)
            waitFor{vm.state.value.lines.size==2}
            vm.requestSummary();waitFor{vm.state.value.summary.isNotBlank()}
            assertTrue(vm.state.value.highlights.isNotEmpty())
            vm.edit(51,"El área de un círculo se calcula con pi por el radio al cuadrado.","Persona A")
            waitFor{vm.state.value.answers.isEmpty()}
            assertEquals("Persona A",vm.state.value.lines.first().voice)
            assertTrue(vm.state.value.summary.isBlank())
            vm.clearSession();assertTrue(vm.state.value.lines.isEmpty());assertTrue(vm.state.value.answers.isEmpty())
            val newFiles=(context.filesDir.listFiles()?.map{it.name}?.toSet()?:emptySet())-before
            assertFalse(newFiles.any{it.endsWith(".wav")||it.endsWith(".pcm")||it.contains("transcript")})
        }finally{speech.close()}
    }
    @Test fun stableQuestionSurvivesRapidPartialRevisions()=withAssistant{vm->
        vm.setAnswers(true)
        val question="¿Cómo se calcula el área de un círculo?"
        for(i in 1..5){vm.observe(701,"$question Estamos estudiando geometría y continuamos $i",false);SystemClock.sleep(150)}
        waitFor{vm.state.value.answers.any{it.text.isNotBlank()&&it.status.startsWith("Lista")}}
        assertEquals(1,vm.state.value.answers.size)
        assertEquals(question,vm.state.value.answers.single().question)
    }
    @Test fun questionAcrossTwoAudioFragments()=withAssistant{vm->
        vm.setAnswers(true)
        vm.observe(801,"Estamos estudiando geometría. ¿Cómo se calcula",false)
        SystemClock.sleep(100)
        vm.observe(802,"el área de un círculo?",true)
        waitFor{vm.state.value.answers.any{it.text.isNotBlank()&&it.status.startsWith("Lista")}}
        assertEquals(1,vm.state.value.answers.size)
        assertTrue(vm.state.value.answers.single().question.contains("área de un círculo"))
        vm.edit(802,"La frase anterior fue un error del audio.","Voz sin identificar")
        waitFor{vm.state.value.answers.none{it.status.startsWith("Lista")}}
    }
    @Test fun selectedMemorySurvivesReopenAndDeletion(){
        val store=SavedStore(context);val marker="Prueba sintética ${System.nanoTime()}"
        var id=0L
        try{
            store.save(null,"Tarea",marker,"Cálculo","2026-10-09");id=store.list().single{it.text==marker}.id
            store.close()
            val reopened=SavedStore(context)
            try{assertEquals("2026-10-09",reopened.list().single{it.id==id}.due);reopened.save(id,"Tarea",marker+" corregida","Cálculo","2026-10-10");reopened.done(id,true);assertTrue(reopened.list().single{it.id==id}.done);reopened.delete(id);assertFalse(reopened.list().any{it.id==id})}finally{reopened.close()}
        }finally{val cleanup=SavedStore(context);if(id!=0L)cleanup.delete(id);cleanup.close()}
    }
    @Test fun noAutoPersistenceOrBackgroundAnswer()=withAssistant{vm->
        val saved=vm.state.value.saved.size
        vm.setAnswers(false);vm.observe(99,"¿Cómo se calcula el área de un círculo?",true)
        waitFor{vm.state.value.lines.isNotEmpty()};SystemClock.sleep(1000)
        assertTrue(vm.state.value.answers.isEmpty());assertEquals(saved,vm.state.value.saved.size)
        vm.background();vm.setAnswers(true);vm.observe(100,"¿Por qué el hemisferio norte recibe más eclipses totales?",true)
        SystemClock.sleep(1400);assertTrue(vm.state.value.answers.isEmpty())
    }
    @Test fun manualSavedRetrievalDoesNotUseTheModel()=withAssistant{vm->
        val marker="Ejercicio sintético ${System.nanoTime()}";vm.save(null,"Tarea",marker,"Álgebra","2026-10-09")
        val id=vm.state.value.saved.single{it.text==marker}.id
        try{vm.ask("¿Qué tengo pendiente de Álgebra?");assertTrue(vm.state.value.answers.last().text.contains(marker));assertTrue(vm.state.value.answers.last().status.startsWith("Consulta local"));vm.done(id,true);vm.ask("¿Qué tengo pendiente de Álgebra?");assertFalse(vm.state.value.answers.last().text.contains(marker))}finally{vm.delete(id)}
    }
    @Test fun addingSecondQuestionKeepsFirstAnswer()=withAssistant{vm->
        vm.setAnswers(true)
        vm.observe(990,"¿Qué es la gravedad?",true)
        waitFor{vm.state.value.answers.any{it.status.startsWith("Lista")}}
        val original=vm.state.value.answers.single()
        vm.observe(990,"¿Qué es la gravedad? ¿Cómo funciona la fotosíntesis?",true)
        waitFor{vm.state.value.answers.size==2&&vm.state.value.answers.all{it.status.startsWith("Lista")}}
        assertEquals(original.id,vm.state.value.answers.first().id)
        assertEquals(original.text,vm.state.value.answers.first().text)
        vm.observe(991,"¿que hay un ?",true);SystemClock.sleep(1800)
        assertEquals(2,vm.state.value.answers.size)
    }
    @Test fun stoppingListeningOrganizesTheConversation()=withAssistant{vm->
        vm.setAnswers(false)
        val text="Un eclipse solar ocurre cuando la Luna pasa entre la Tierra y el Sol. Durante la totalidad se estudian la corona del Sol y las bandas de sombra."
        vm.observe(998,text,true);waitFor{vm.state.value.lines.isNotEmpty()}
        vm.state.value=vm.state.value.copy(listening=true)
        vm.pause();waitFor{vm.state.value.organizationRevision>0&&vm.state.value.summary.isNotBlank()}
        assertFalse(vm.state.value.listening);assertEquals(text,vm.state.value.lines.single().text)
        assertTrue(KnowledgeOrganizer.build(vm.state.value.lines).ideas.isNotEmpty())
    }
}
