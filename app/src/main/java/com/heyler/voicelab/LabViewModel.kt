package com.heyler.voicelab

import android.app.Application
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

private val references=listOf(
    "Mañana entrego los ejercicios de cálculo. No, el viernes.",
    "Un eclipse solar ocurre cuando la Luna se interpone entre la Tierra y el Sol.",
    "El equipo decidió usar Python para organizar las tareas del proyecto.",
    "La integración por partes permite transformar una integral en otra más sencilla.",
    "¿Cómo se calcula el área de un círculo con un radio de cinco metros?"
)
private val prompts=listOf("Explica brevemente qué es un eclipse solar.","¿Cuál es la fórmula del área de un círculo?","Explica qué es una variable en programación.")

data class LabState(val busy:Boolean=false,val status:String="Importa los modelos para comenzar.",val transcript:String="",val answer:String="",val summary:String="Sin resultados medidos.",val resource:String="",val models:String="")

class LabViewModel(app:Application):AndroidViewModel(app){
    private val context=app.applicationContext
    private val engines=LocalEngines(context)
    val state=MutableStateFlow(LabState())
    private var job:Job?=null
    @Volatile private var recorder:AudioRecord?=null
    private val results=JSONArray()
    private val samples=JSONArray()
    private var currentPhase=""
    private var loadedSpeechMs:Double?=null
    private var loadedLanguageMs:Double?=null
    private val isEmulator=Build.FINGERPRINT.startsWith("generic") || Build.MODEL.contains("sdk_gphone") || Build.HARDWARE.contains("ranchu")
    init { refreshModels() }
    private fun refreshModels(){state.update{it.copy(models="Voz: ${if(File(context.filesDir,"speech/streaming_config.json").exists()) "importada" else "falta ZIP"} · Respuestas: ${if(File(context.filesDir,"model.litertlm").exists()) "importado" else "falta .litertlm"}")}}
    private fun launch(label:String,block:suspend()->Unit){
        if(job?.isActive==true)return
        state.update{it.copy(busy=true,status=label)}
        job=viewModelScope.launch(Dispatchers.IO){
            try{block();ensureActive();state.update{it.copy(status="Prueba terminada. Resultados locales, sin audio guardado.")}}
            catch(e:CancellationException){state.update{it.copy(status="Detenido.")};throw e}
            catch(e:Throwable){state.update{it.copy(status="No se completó: ${e.message?:e.javaClass.simpleName}")}}
            finally{state.update{it.copy(busy=false)};refreshModels()}
        }
    }
    fun importModel(uri:Uri,speech:Boolean)=launch("Importando modelo en el teléfono…"){
        engines.close()
        if(speech)ModelImport.speech(context,uri) else ModelImport.language(context,uri)
    }
    private fun snapshot(){
        val value=deviceSample(context).put("elapsed_ms",SystemClock.elapsedRealtime()).put("phase",currentPhase)
        synchronized(samples){samples.put(value)}
        state.update{it.copy(resource="RAM PSS: %.0f MB · Batería: %.0f %% · Temperatura batería: %.1f °C · térmico: %d".format(value.optDouble("pss_mb"),value.optDouble("battery_percent"),value.optDouble("battery_temperature_c"),value.optInt("thermal_status")))}
        check(value.optDouble("battery_temperature_c")<45 && value.optInt("thermal_status")<3){"Sesión detenida por temperatura/estado térmico elevado."}
    }
    private fun add(row:JSONObject){
        synchronized(results){results.put(row)}
        saveReport()
        val rows=(0 until results.length()).map{results.getJSONObject(it)}
        val asr=rows.filter{it.optString("kind")=="asr"}
        val llm=rows.filter{it.optString("kind")=="llm"}
        val errors=asr.sumOf{it.getInt("word_errors")};val words=asr.sumOf{it.getInt("reference_words")}
        val p50=Metrics.percentile(llm.map{it.getDouble("first_text_ms")},.5)
        state.update{it.copy(summary="${if(isEmulator) "EMULADOR: no representa el A54" else "Dispositivo real: ${Build.MODEL}"}\nASR: ${asr.size} casos · WER ${if(words>0) "%.2f %%".format(100.0*errors/words) else "pendiente"}\nLLM: ${llm.size} consultas · primer texto p50 ${p50?.let { "%.0f ms".format(it) }?:"pendiente"}\n${rows.size} mediciones. El JSON separa motores y fases; no comprueba veracidad de respuestas.")}
    }
    private fun saveReport(){
        val report=JSONObject().put("schema",1).put("model",Build.MODEL).put("android_sdk",Build.VERSION.SDK_INT).put("emulator",isEmulator)
            .put("asr","Moonshine Small Streaming ES 0.1.5 / arch 4").put("llm","Modelo importado / LiteRT-LM 0.18.0 / CPU")
            .put("speech_load_ms",loadedSpeechMs?:JSONObject.NULL).put("llm_load_ms",loadedLanguageMs?:JSONObject.NULL)
            .put("method","WER normalized words; first-text latency includes conversation setup, excludes engine loading; temperature is battery sensor, PSS sampled before/after runs; no audio or prompt export")
        synchronized(results){report.put("results",JSONArray(results.toString()))};synchronized(samples){report.put("resources",JSONArray(samples.toString()))}
        File(context.filesDir,"benchmark.json").writeText(report.toString(2))
    }
    fun exportReport():String {saveReport();return File(context.filesDir,"benchmark.json").readText()}
    fun clear(){if(state.value.busy)return;synchronized(results){while(results.length()>0)results.remove(0)};synchronized(samples){while(samples.length()>0)samples.remove(0)};File(context.filesDir,"benchmark.json").delete();state.update{it.copy(transcript="",answer="",summary="Resultados y texto borrados.")}}
    fun stop(){ recorder?.let{runCatching{it.stop()}};runCatching{engines.cancel()};job?.cancel() }
    fun benchmark(phase:String,endurance:Boolean=false)=launch("Preparando motores…"){
        currentPhase=phase
        if(phase=="asr"){engines.unloadLanguage();loadedSpeechMs=engines.loadSpeech().takeIf{it>0}?:loadedSpeechMs}
        if(phase=="llm"){engines.unloadSpeech();loadedLanguageMs=engines.loadLanguage().takeIf{it>0}?:loadedLanguageMs}
        if(phase=="combined"){loadedSpeechMs=engines.loadSpeech().takeIf{it>0}?:loadedSpeechMs;loadedLanguageMs=engines.loadLanguage().takeIf{it>0}?:loadedLanguageMs}
        val deadline=SystemClock.elapsedRealtime()+(if(endurance)20*60*1000L else 0L)
        var iteration=0
        do {
            currentCoroutineContext().ensureActive();iteration++
            state.update{it.copy(status="${if(endurance) "Sesión 20 min" else "Comparación"} · $phase · vuelta $iteration")};snapshot()
            if(phase!="llm")for(i in references.indices){
                currentCoroutineContext().ensureActive()
                val bytes=context.assets.open("fixtures/${i+1}.wav").use{it.readBytes()}
                val audio=Wav16.read(bytes);bytes.fill(0)
                val start=SystemClock.elapsedRealtimeNanos()
                val text=try{engines.transcribe(audio)}finally{audio.fill(0f)}
                val elapsed=(SystemClock.elapsedRealtimeNanos()-start)/1e6
                state.update{it.copy(transcript=text)}
                add(JSONObject().put("kind","asr").put("phase",phase).put("iteration",iteration).put("case",i+1)
                    .put("asr_ms",elapsed).put("audio_seconds",audio.size/16000.0).put("rtf",elapsed/(audio.size/16.0))
                    .put("word_errors",Metrics.errors(references[i],text)).put("reference_words",Metrics.words(references[i]).size))
                if(phase=="combined")languageRun(prompts[i % prompts.size],iteration,i+1)
                snapshot()
            }
            if(phase=="llm")for(i in references.indices){currentCoroutineContext().ensureActive();languageRun(prompts[i % prompts.size],iteration,i+1);snapshot()}
            if(endurance){saveReport();delay(1000)}
        }while(if(endurance)SystemClock.elapsedRealtime()<deadline else iteration<3)
        saveReport()
    }
    private suspend fun languageRun(prompt:String,iteration:Int,case:Int){
        state.update{it.copy(answer="")}
        val(first,total)=engines.answer(prompt){text->state.update{it.copy(answer=it.answer+text)}}
        add(JSONObject().put("kind","llm").put("phase",currentPhase).put("iteration",iteration).put("case",case).put("first_text_ms",first).put("total_ms",total))
    }
    fun answer(prompt:String)=launch("Cargando modelo local…"){
        currentPhase="manual";engines.loadLanguage();state.update{it.copy(answer="")};engines.answer(prompt){t->state.update{it.copy(answer=it.answer+t)}}
    }
    @Suppress("MissingPermission")
    fun microphone()=launch("Escucha local. Audio solo en memoria."){
        engines.loadSpeech();val t=checkNotNull(engines.speech)
        val min=AudioRecord.getMinBufferSize(16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT)
        require(min>0){"El dispositivo no permite audio mono a 16 kHz."}
        val input=AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,maxOf(min,6400))
        check(input.state==AudioRecord.STATE_INITIALIZED){"No se pudo abrir el micrófono."}
        recorder=input;val listener=engines.liveListener{text->state.update{it.copy(transcript=text)}}
        t.addListener(listener);t.start();val shorts=ShortArray(3200)
        try{input.startRecording();while(currentCoroutineContext().isActive){val n=input.read(shorts,0,shorts.size);check(n>0){"Captura detenida."};val floats=FloatArray(n){shorts[it]/32768f};t.addAudio(floats,16000);floats.fill(0f);shorts.fill(0)}}
        finally{runCatching{input.stop()};input.release();recorder=null;shorts.fill(0);t.stop();t.removeListener(listener)}
    }
    override fun onCleared(){stop();val previous=job;CoroutineScope(Dispatchers.IO).launch{previous?.join();engines.close()};super.onCleared()}
}
