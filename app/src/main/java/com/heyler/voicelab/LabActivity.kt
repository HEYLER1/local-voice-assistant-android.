package com.heyler.voicelab

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

class LabActivity:ComponentActivity(){
    private lateinit var lab:LabViewModel
    private val languageImport=registerForActivityResult(ActivityResultContracts.OpenDocument()){uri->uri?.let{lab.importModel(it)}}
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
                    Text("Pruebas locales de Qwen3 · CPU",style=MaterialTheme.typography.titleMedium)
                    Text("El emulador verifica funcionamiento, no la rapidez ni el consumo de un teléfono real.")
                    Text(state.models);Text(state.status,color=MaterialTheme.colorScheme.primary)
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        OutlinedButton(onClick={languageImport.launch(arrayOf("*/*"))},enabled=!state.busy){Text("Importar Qwen3")}
                    }
                    Text("Medición del modelo de respuestas",style=MaterialTheme.typography.titleLarge)
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        Button(onClick={lab.benchmark()},enabled=!state.busy){Text("Medir")}
                        OutlinedButton(onClick={lab.benchmark(true)},enabled=!state.busy){Text("20 minutos")}
                    }
                    Button(onClick={lab.stop()},enabled=state.busy){Text("Detener")}
                    Text("Pregunta de prueba",style=MaterialTheme.typography.titleLarge)
                    OutlinedTextField(question,{question=it},label={Text("Pregunta de prueba")},modifier=Modifier.fillMaxWidth(),enabled=!state.busy)
                    Button(onClick={lab.answer(question)},enabled=!state.busy&&question.isNotBlank()){Text("Responder localmente")}
                    if(state.answer.isNotBlank())Text(state.answer)
                    HorizontalDivider();Text("Resultados medidos",style=MaterialTheme.typography.titleLarge)
                    Text(state.summary);Text(state.resource)
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        OutlinedButton(onClick={export.launch("v-android-metricas.json")},enabled=!state.busy){Text("Exportar métricas")}
                        OutlinedButton(onClick={lab.clear()},enabled=!state.busy){Text("Borrar resultados")}
                    }
                    Text("Sin conexión a Internet. Importa un modelo descargado previamente. Temperatura medida mediante el sensor de batería.",style=MaterialTheme.typography.bodySmall)
                }
            }
        }}
    }
}
