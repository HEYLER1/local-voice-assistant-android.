package com.heyler.voicelab

import android.net.Uri
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class SoniqoIntegrationTest{
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun modelImportSelectsSoniqo()=runBlocking {
        val store=androidx.lifecycle.ViewModelStore()
        lateinit var vm:AssistantViewModel
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            vm=AssistantViewModel(context.applicationContext as android.app.Application)
            store.put("test",vm)
            vm.importSoniqo(Uri.fromFile(File(context.getExternalFilesDir(null),"soniqo-test.zip")))
        }
        try {
            val deadline=SystemClock.elapsedRealtime()+60000
            while(vm.state.value.preparing && SystemClock.elapsedRealtime()<deadline)delay(100)
            assertFalse("Import timed out",vm.state.value.preparing)
            assertEquals("soniqo",vm.state.value.speechEngine)
            assertTrue(vm.state.value.status,vm.state.value.status.contains("listo"))
        }finally{InstrumentationRegistry.getInstrumentation().runOnMainSync{store.clear()}}
    }
    @Test fun importedModelsTranscribeSpanishAudioProgressively()=runBlocking{
        val zip=File(context.getExternalFilesDir(null),"soniqo-test.zip")
        if(!File(context.filesDir,"soniqo/parakeet-encoder-int8.onnx").exists()){assertTrue("Push the prepared model ZIP to the app external files directory",zip.exists());ModelImport.soniqo(context,Uri.fromFile(zip))}
        val outputs=java.util.concurrent.CopyOnWriteArrayList<Pair<String,Boolean>>()
        val errors=java.util.concurrent.CopyOnWriteArrayList<String>()
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
        val engine=SoniqoSpeech(File(context.filesDir,"soniqo"),scope,{_,text,final->outputs.add(text to final)},{errors.add(it)})
        try{
            engine.start();val audio=Wav16.read(context.assets.open("fixtures/5.wav").use{it.readBytes()})
            for(chunk in audio.toList().chunked(3200)){engine.addAudio(chunk.toFloatArray());delay(200)}
            repeat(12){engine.addAudio(FloatArray(3200));delay(200)}
            val deadline=SystemClock.elapsedRealtime()+30000
            while(outputs.none{it.second}&&errors.isEmpty()&&SystemClock.elapsedRealtime()<deadline)delay(100)
            assertTrue("Native SDK errors: $errors",errors.isEmpty())
            assertTrue("No transcription: $outputs",outputs.isNotEmpty())
            val text=outputs.filter{it.second}.joinToString(" "){it.first}
            assertTrue("Expected Spanish circle question, got: $text",ConversationRules.key(text).contains("circulo"))
            assertTrue("Question missing: $text",ConversationRules.questions(text).isNotEmpty())
            assertTrue("Expected progressive partials",outputs.any{!it.second})
        }finally{engine.close();scope.cancel()}
    }
}
