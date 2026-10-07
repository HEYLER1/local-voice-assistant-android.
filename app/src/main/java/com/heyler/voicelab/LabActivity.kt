package com.heyler.voicelab

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LabActivity:ComponentActivity(){
    private lateinit var lab:LabViewModel
    private val speechImport=registerForActivityResult(ActivityResultContracts.OpenDocument()){uri->uri?.let{lab.importModel(it,true)}}
    private val languageImport=registerForActivityResult(ActivityResultContracts.OpenDocument()){uri->uri?.let{lab.importModel(it,false)}}
    private val permission=registerForActivityResult(ActivityResultContracts.RequestPermission()){allowed->if(allowed)lab.microphone()}
    private val export=registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")){uri->
        uri?.let{lifecycleScope.launch(Dispatchers.IO){val report=lab.exportReport();contentResolver.openOutputStream(it)?.use{output->output.write(report.toByteArray())}}}
    }
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState);lab=ViewModelProvider(this)[LabViewModel::class.java]
        setContent { MaterialTheme {
            val state by lab.state.collectAsState();var question by remember{mutableStateOf("¿Qué es un eclipse solar?")}
            DisposableEffect(state.busy){if(state.busy)window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);onDispose{}}
            Surface(modifier=Modifier.fillMaxSize()){
                Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(20.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
                    Text("V · Laboratorio Android",style=MaterialTheme.typography.headlineSmall)
                    Text("Kotlin nativo · CPU · sin conexión al Mac",style=MaterialTheme.typography.titleMedium)
                    Text("Prueba en el A54 real. Un emulador verifica funcionamiento, no rapidez, batería ni temperatura del teléfono.")
                    Text(state.models);Text(state.status,color=MaterialTheme.colorScheme.primary)
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        OutlinedButton(onClick={speechImport.launch(arrayOf("application/zip","application/octet-stream"))},enabled=!state.busy){Text("Importar voz ZIP")}
                        OutlinedButton(onClick={languageImport.launch(arrayOf("*/*"))},enabled=!state.busy){Text("Importar LLM")}
                    }
                    Text("Motores por separado y comparación",style=MaterialTheme.typography.titleLarge)
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        Button(onClick={lab.benchmark("asr")},enabled=!state.busy){Text("Voz sola")}
                        Button(onClick={lab.benchmark("llm")},enabled=!state.busy){Text("LLM solo")}
                    }
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        Button(onClick={lab.benchmark("combined")},enabled=!state.busy){Text("Integrados")}
                        OutlinedButton(onClick={lab.benchmark("combined",true)},enabled=!state.busy){Text("20 minutos")}
                    }
                    Button(onClick={lab.stop()},enabled=state.busy){Text("Detener")}
                    Text("Audio y preguntas manuales",style=MaterialTheme.typography.titleLarge)
                    OutlinedButton(onClick={if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)lab.microphone()else permission.launch(Manifest.permission.RECORD_AUDIO)},enabled=!state.busy){Text("Escuchar micrófono")}
                    if(state.transcript.isNotBlank())Text(state.transcript)
                    OutlinedTextField(question,{question=it},label={Text("Pregunta de prueba")},modifier=Modifier.fillMaxWidth(),enabled=!state.busy)
                    Button(onClick={lab.answer(question)},enabled=!state.busy&&question.isNotBlank()){Text("Responder localmente")}
                    if(state.answer.isNotBlank())Text(state.answer)
                    HorizontalDivider();Text("Resultados medidos",style=MaterialTheme.typography.titleLarge)
                    Text(state.summary);Text(state.resource)
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        OutlinedButton(onClick={export.launch("v-android-metricas.json")},enabled=!state.busy){Text("Exportar métricas")}
                        OutlinedButton(onClick={lab.clear()},enabled=!state.busy){Text("Borrar resultados")}
                    }
                    Text("Audio del micrófono y consultas manuales: solo en memoria. Sin permiso de Internet. Importa modelos descargados previamente. Temperatura = sensor de batería, no temperatura del procesador.",style=MaterialTheme.typography.bodySmall)
                }
            }
        }}
    }
    override fun onStop(){lab.stop();super.onStop()}
}
