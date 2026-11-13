package com.heyler.voicelab

import android.app.Application
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

private val prompts=listOf(
    "Explica brevemente qué es un eclipse solar.",
    "¿Cuál es la fórmula del área de un círculo?",
    "Explica qué es una variable en programación."
)

data class LabState(
    val busy:Boolean=false,
    val status:String="Importa Qwen3 para comenzar.",
    val answer:String="",
    val summary:String="Sin resultados medidos.",
    val resource:String="",
    val models:String=""
)

class LabViewModel(app:Application):AndroidViewModel(app){
    private val context=app.applicationContext
    private val engines=LocalEngines(context)
    val state=MutableStateFlow(LabState())
    private var job:Job?=null
    private val results=JSONArray()
    private val samples=JSONArray()
    private var currentPhase="llm"
    private var loadedLanguageMs:Double?=null
    private val isEmulator=Build.FINGERPRINT.startsWith("generic")||Build.MODEL.contains("sdk_gphone")||Build.HARDWARE.contains("ranchu")

    init{refreshModels()}
    private fun refreshModels(){state.update{it.copy(models="Qwen3: ${if(File(context.filesDir,"model.litertlm").exists())"importado"else "falta .litertlm"}")}}
    private fun launch(label:String,block:suspend()->Unit){
        if(job?.isActive==true)return
        state.update{it.copy(busy=true,status=label)}
        job=viewModelScope.launch(Dispatchers.IO){
            try{block();ensureActive();state.update{it.copy(status="Prueba terminada. Resultados locales; no se guardan consultas.")}}
            catch(e:CancellationException){state.update{it.copy(status="Detenido.")};throw e}
            catch(e:Throwable){state.update{it.copy(status="No se completó: ${e.message?:e.javaClass.simpleName}")}}
            finally{state.update{it.copy(busy=false)};refreshModels()}
        }
    }
    fun importModel(uri:Uri)=launch("Importando Qwen3 en el teléfono…"){
        engines.close()
        ModelImport.language(context,uri)
    }
    private fun snapshot(){
        val value=deviceSample(context).put("elapsed_ms",SystemClock.elapsedRealtime()).put("phase",currentPhase)
        synchronized(samples){samples.put(value)}
        state.update{it.copy(resource="RAM PSS: %.0f MB · Batería: %.0f %% · Temperatura batería: %.1f °C · térmico: %d".format(value.optDouble("pss_mb"),value.optDouble("battery_percent"),value.optDouble("battery_temperature_c"),value.optInt("thermal_status")))}
        check(value.optDouble("battery_temperature_c")<45&&value.optInt("thermal_status")<3){"Prueba detenida por temperatura o estado térmico elevado."}
    }
    private fun add(row:JSONObject){
        synchronized(results){results.put(row)}
        saveReport()
        val rows=(0 until results.length()).map{results.getJSONObject(it)}
        val p50=Metrics.percentile(rows.map{it.getDouble("first_text_ms")},.5)
        state.update{it.copy(summary="""${if(isEmulator)"EMULADOR: no representa un teléfono real"else"Dispositivo: ${Build.MODEL}"}
Qwen3: ${rows.size} consultas · primer texto p50 ${p50?.let{"%.0f ms".format(it)}?:"pendiente"}
El JSON contiene latencia, tiempo total y recursos; no evalúa veracidad.""")}
    }
    private fun saveReport(){
        val report=JSONObject().put("schema",2).put("model",Build.MODEL).put("android_sdk",Build.VERSION.SDK_INT).put("emulator",isEmulator)
            .put("llm","Modelo Qwen3 importado / LiteRT-LM 0.18.0 / CPU")
            .put("llm_load_ms",loadedLanguageMs?:JSONObject.NULL)
            .put("method","Latencia al primer texto incluye preparación de conversación; excluye carga del motor. RAM PSS muestreada; temperatura es la del sensor de batería. No se exportan audio ni prompts.")
        synchronized(results){report.put("results",JSONArray(results.toString()))}
        synchronized(samples){report.put("resources",JSONArray(samples.toString()))}
        File(context.filesDir,"benchmark.json").writeText(report.toString(2))
    }
    fun exportReport():String{saveReport();return File(context.filesDir,"benchmark.json").readText()}
    fun clear(){
        if(state.value.busy)return
        synchronized(results){while(results.length()>0)results.remove(0)}
        synchronized(samples){while(samples.length()>0)samples.remove(0)}
        File(context.filesDir,"benchmark.json").delete()
        state.update{it.copy(answer="",summary="Resultados borrados.")}
    }
    fun stop(){runCatching{engines.cancel()};job?.cancel()}
    fun benchmark(endurance:Boolean=false)=launch("Preparando prueba Qwen3…"){
        currentPhase="llm"
        loadedLanguageMs=engines.loadLanguage().takeIf{it>0}?:loadedLanguageMs
        val deadline=SystemClock.elapsedRealtime()+(if(endurance)20*60*1000L else 0L)
        var iteration=0
        do{
            currentCoroutineContext().ensureActive();iteration++
            state.update{it.copy(status="${if(endurance)"Prueba de 20 min"else"Prueba"} · consulta ${iteration}")}
            snapshot()
            for(i in prompts.indices){currentCoroutineContext().ensureActive();languageRun(prompts[i],iteration,i+1);snapshot()}
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
        currentPhase="manual";loadedLanguageMs=engines.loadLanguage().takeIf{it>0}?:loadedLanguageMs
        state.update{it.copy(answer="")}
        engines.answer(prompt){text->state.update{it.copy(answer=it.answer+text)}}
    }
    override fun onCleared(){
        stop();val previous=job
        CoroutineScope(Dispatchers.IO).launch{previous?.join();engines.close()}
        super.onCleared()
    }
}
