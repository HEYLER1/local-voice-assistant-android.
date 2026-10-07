package com.heyler.voicelab
import android.Manifest
import android.content.pm.PackageManager
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import android.app.Application
import android.content.Intent
import android.os.SystemClock
import androidx.lifecycle.ViewModelStore
import org.json.JSONObject

class EngineSmokeTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun nativeScreen(){
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val activity=instrumentation.startActivitySync(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        instrumentation.waitForIdleSync()
        assertTrue(activity.window.decorView.isShown)
        instrumentation.runOnMainSync{activity.finish()}
    }
    @Test fun comparisonProtocol(){
        val lab=LabViewModel(context.applicationContext as Application)
        val store=ViewModelStore();store.put("lab",lab)
        try{
            lab.clear()
            for(phase in listOf("asr","llm","combined")){
                lab.benchmark(phase)
                val deadline=SystemClock.elapsedRealtime()+180000
                while(lab.state.value.busy && SystemClock.elapsedRealtime()<deadline)SystemClock.sleep(100)
                assertFalse("Benchmark timed out",lab.state.value.busy)
                assertTrue(lab.state.value.status,lab.state.value.status.startsWith("Prueba terminada"))
            }
            val report=JSONObject(lab.exportReport());val rows=report.getJSONArray("results")
            assertEquals(60,rows.length())
            assertTrue(report.has("emulator"))
            assertFalse(report.toString().contains("transcript"))
        }finally{lab.stop();store.clear()}
    }
    @Test fun streamingNativeSpeech(){
        val engines=LocalEngines(context)
        val output=java.util.concurrent.ConcurrentHashMap<Long,String>()
        try{
            engines.loadSpeech();val speech=checkNotNull(engines.speech)
            val visitor=object:ai.moonshine.voice.TranscriptEventListener(){
                override fun onLineTextChanged(event:ai.moonshine.voice.TranscriptEvent.LineTextChanged){output[event.line.id]=event.line.text;event.line.audioData=null}
                override fun onLineCompleted(event:ai.moonshine.voice.TranscriptEvent.LineCompleted){output[event.line.id]=event.line.text;event.line.audioData=null}
            }
            val listener=java.util.function.Consumer<ai.moonshine.voice.TranscriptEvent>{it.accept(visitor)}
            speech.addListener(listener);speech.start()
            val bytes=context.assets.open("fixtures/1.wav").use{it.readBytes()};val audio=Wav16.read(bytes);bytes.fill(0)
            try{for(offset in audio.indices step 3200){val chunk=audio.copyOfRange(offset,minOf(offset+3200,audio.size));speech.addAudio(chunk,16000);chunk.fill(0f)};speech.stop()}finally{audio.fill(0f);speech.removeListener(listener)}
            assertTrue("Native streaming must produce the synthetic fixture",output.values.joinToString(" ").lowercase().contains("viernes"))
        }finally{engines.close()}
    }
    @Test fun noNetworkPermission(){assertEquals(PackageManager.PERMISSION_DENIED,context.checkSelfPermission(Manifest.permission.INTERNET))}
    @Test fun realSpeechModel(){
        val engines=LocalEngines(context)
        try {
            engines.loadSpeech()
            val bytes=context.assets.open("fixtures/1.wav").use{it.readBytes()};val audio=Wav16.read(bytes)
            val text=engines.transcribe(audio);audio.fill(0f);bytes.fill(0)
            assertTrue("Native speech engine must produce text",text.isNotBlank())
            assertTrue(text.lowercase().contains("viernes"))
        } finally {engines.close()}
    }
    @Test fun directAnswerContainsTheCircleCalculation()=runBlocking{
        val engines=LocalEngines(context)
        try{engines.loadLanguage();val text=StringBuilder();engines.answerBrief("¿Cómo se calcula el área de un círculo con un radio de cinco metros? Responde con la fórmula y el resultado."){text.append(it)};assertTrue("Expected a formula/calculation, received: $text",Regex("\\bpi\\b|π|r[²^]|78[.,]5|25",RegexOption.IGNORE_CASE).containsMatchIn(text.toString()))}
        finally{engines.close()}
    }
    @Test fun realLanguageModel()=runBlocking{
        val engines=LocalEngines(context)
        try{engines.loadLanguage();val text=StringBuilder();val times=engines.answer("Escribe hola en español."){text.append(it)};assertTrue(text.isNotBlank());assertTrue(times.first>0 && times.second>=times.first)}
        finally{engines.close()}
    }
}
