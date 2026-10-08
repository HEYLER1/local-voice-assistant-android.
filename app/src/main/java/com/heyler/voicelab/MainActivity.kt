package com.heyler.voicelab

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity:ComponentActivity(){
    private lateinit var assistant:AssistantViewModel
    private var tts:TextToSpeech?=null
    private var localVoiceReady=false
    private var videoRequestPending=false
    private val speechImport=registerForActivityResult(ActivityResultContracts.OpenDocument()){uri->uri?.let{assistant.importModel(it,true)}}
    private val soniqoImport=registerForActivityResult(ActivityResultContracts.OpenDocument()){uri->uri?.let{assistant.importSoniqo(it)}}
    private val languageImport=registerForActivityResult(ActivityResultContracts.OpenDocument()){uri->uri?.let{assistant.importModel(it,false)}}
    private val permission=registerForActivityResult(ActivityResultContracts.RequestPermission()){allowed->if(allowed)startSelectedAudio()else assistant.state.value=assistant.state.value.copy(status="Permiso de micrófono no concedido.")}
    private val videoConsent=registerForActivityResult(ActivityResultContracts.StartActivityForResult()){result->
        videoRequestPending=false
        if(result.resultCode==RESULT_OK&&result.data!=null){
            assistant.state.value=assistant.state.value.copy(starting=true,status="Preparando audio del video…")
            PlaybackCaptureService.onReady={projection->if(assistant.state.value.starting){assistant.resumeForeground();assistant.listen(projection)}else PlaybackCaptureService.stopCapture()}
            PlaybackCaptureService.onStopped={assistant.pause()}
            PlaybackCaptureService.onError={message->assistant.state.value=assistant.state.value.copy(starting=false,status=message)}
            runCatching{startForegroundService(Intent(this,PlaybackCaptureService::class.java).putExtra("consent",result.data))}.onFailure{assistant.state.value=assistant.state.value.copy(starting=false,status="No se pudo iniciar la captura del video: ${it.message}")}
        }else assistant.state.value=assistant.state.value.copy(status="Permiso de audio del video cancelado. Micrófono apagado.")
    }
    private fun startSelectedAudio(){if(videoRequestPending)return;if(assistant.state.value.audioSource=="video"){videoRequestPending=true;videoConsent.launch(getSystemService(android.media.projection.MediaProjectionManager::class.java).createScreenCaptureIntent())}else assistant.listen()}
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState);enableEdgeToEdge(statusBarStyle=SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),navigationBarStyle=SystemBarStyle.dark(android.graphics.Color.TRANSPARENT));assistant=ViewModelProvider(this)[AssistantViewModel::class.java];assistant.restoreConversation()
        tts=TextToSpeech(this){status->if(status==TextToSpeech.SUCCESS){val voice=tts?.voices?.firstOrNull{it.locale.language=="es"&&!it.isNetworkConnectionRequired};if(voice!=null){tts?.voice=voice;localVoiceReady=true}}}
        setContent{VoiceTheme{AssistantScreen(assistant,
            listen={tts?.stop();if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)startSelectedAudio()else permission.launch(Manifest.permission.RECORD_AUDIO)},
            importSpeech={speechImport.launch(arrayOf("application/zip","application/octet-stream"))},importLanguage={languageImport.launch(arrayOf("*/*"))},
            diagnostics={assistant.releaseModels{startActivity(Intent(this,LabActivity::class.java))}},
            speak={text->if(localVoiceReady){assistant.pause();tts?.speak(text,TextToSpeech.QUEUE_FLUSH,null,"local-answer")}else assistant.state.value=assistant.state.value.copy(status="Instala una voz española sin conexión en los ajustes de Android para escuchar respuestas.")},
            stopVoice={tts?.stop()},importSoniqo={soniqoImport.launch(arrayOf("application/zip","application/octet-stream"))})
            val s by assistant.state.collectAsState()
            DisposableEffect(s.listening||s.generating){if(s.listening||s.generating)window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);onDispose{}}
        }}
    }
    override fun onStart(){super.onStart();assistant.resumeForeground()}
    override fun onStop(){if(!isChangingConfigurations)assistant.background();tts?.stop();super.onStop()}
    override fun onDestroy(){tts?.shutdown();super.onDestroy()}
}

private data class SaveDraft(val id:Long?=null,val kind:String="Recuerdo",val text:String="",val scope:String="",val due:String="",val source:String="Texto escrito por usuario")
@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun AssistantScreen(vm:AssistantViewModel,listen:()->Unit,importSpeech:()->Unit,importLanguage:()->Unit,diagnostics:()->Unit,speak:(String)->Unit,stopVoice:()->Unit,initialTab:Int=0,initialSection:Int=0,initialDrawer:Boolean=false,importSoniqo:()->Unit={}){
    val s by vm.state.collectAsState()
    var liveSection by rememberSaveable{mutableIntStateOf(initialSection)}
    var tab by remember{mutableIntStateOf(initialTab)}
    var question by rememberSaveable(s.conversationKey){mutableStateOf("")}
    var sentAnswerId by remember(s.conversationKey){mutableStateOf<String?>(null)}
    var editing by remember{mutableStateOf<SpeechLine?>(null)}
    var draft by remember{mutableStateOf<SaveDraft?>(null)}
    var librarySection by remember{mutableIntStateOf(0)}
    var search by remember{mutableStateOf("")}
    val compact=LocalConfiguration.current.screenHeightDp<480
    val appContext=androidx.compose.ui.platform.LocalContext.current
    val keyboard=LocalSoftwareKeyboardController.current
    val drawer=rememberDrawerState(if(initialDrawer)DrawerValue.Open else DrawerValue.Closed)
    val scope=rememberCoroutineScope()
    var naming by remember{mutableStateOf(false)}
    var namingId by remember{mutableStateOf<Long?>(null)}
    var deletingId by remember{mutableStateOf<Long?>(null)}
    var choosingLanguage by remember{mutableStateOf(false)}
    var nameDraft by remember{mutableStateOf("")}
    LaunchedEffect(s.organizationRevision){if(s.organizationRevision>0)liveSection=2}
    if(naming)AlertDialog(onDismissRequest={naming=false},title={Text("Nombre del chat")},text={OutlinedTextField(nameDraft,{nameDraft=it},label={Text("Ej. Física · clase de eclipses")},singleLine=true)},confirmButton={TextButton(onClick={namingId?.let{vm.renameConversation(it,nameDraft)}?:vm.renameConversation(nameDraft);naming=false}){Text("Guardar")}},dismissButton={TextButton(onClick={naming=false}){Text("Cancelar")}})
    deletingId?.let{id->AlertDialog(onDismissRequest={deletingId=null},title={Text("¿Eliminar conversación?")},text={Text("Se borrarán su transcripción, respuestas y resumen guardados.")},confirmButton={TextButton(onClick={vm.deleteConversation(id);deletingId=null}){Text("Eliminar",color=MaterialTheme.colorScheme.error)}},dismissButton={TextButton(onClick={deletingId=null}){Text("Cancelar")}})}
    if(choosingLanguage)AlertDialog(onDismissRequest={choosingLanguage=false},title={Text("Idioma de transcripción")},text={Column(Modifier.heightIn(max=360.dp).verticalScroll(rememberScrollState())){
        s.localSpeechModels.languages().forEach{tag->TextButton(onClick={vm.setSpeechLanguage(tag);choosingLanguage=false},modifier=Modifier.fillMaxWidth()){
            Text(languageName(tag),modifier=Modifier.weight(1f),color=if(s.speechLanguage==tag)VoiceAccent else MaterialTheme.colorScheme.onSurface)
            Text(if(s.localSpeechModels.hasLanguage(tag))"Instalado"else "Descargar",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }}
    }},confirmButton={TextButton(onClick={choosingLanguage=false}){Text("Cerrar")}})
    ModalNavigationDrawer(drawerState=drawer,drawerContent={
        ChatSidebar(s,tab,{scope.launch{drawer.close()}},{vm.newConversation();tab=0;liveSection=0;scope.launch{drawer.close()}},{id->vm.openConversation(id);tab=0;liveSection=2;scope.launch{drawer.close()}},{librarySection=1;tab=1;scope.launch{drawer.close()}},{tab=2;scope.launch{drawer.close()}},onRename={id,title->namingId=id;nameDraft=title;naming=true},onDelete={deletingId=it},onRenameCurrent={namingId=null;nameDraft=s.conversationTitle.ifBlank{if(s.lines.isEmpty())"Nueva conversación"else KnowledgeOrganizer.build(s.lines).topic};naming=true})
    }){
    Scaffold(containerColor=VoiceBackground,
        topBar={
            Column(Modifier.statusBarsPadding()){
                if(tab==0)ConversationTopBar(liveSection,{liveSection=it},{keyboard?.hide();scope.launch{drawer.open()}},{vm.newConversation();liveSection=0})
                else Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
                    ChatCircleAction("Abrir historial de chats","menu"){scope.launch{drawer.open()}}
                    Spacer(Modifier.width(12.dp));VoiceHeader(s.listening,true,s.starting)
                }

            }
        },
        bottomBar={if(tab==0)ChatVoiceComposer(s.listening,s.starting,s.audioLevel,!s.preparing,{if(s.listening||s.starting){if(s.listening)liveSection=2;vm.pause()}else {liveSection=0;listen()}},question,{question=it},{if(question.isNotBlank()&&!s.preparing){vm.ask(question.trim());sentAnswerId=vm.state.value.answers.lastOrNull()?.id;question="";liveSection=0;keyboard?.hide()}})}
    ){padding->
        when(tab){
            0->Column(Modifier.fillMaxSize().padding(padding)){key(s.conversationKey){LiveConversation(s,{editing=it},{text,source->draft=SaveDraft(text=text,source=source)},speak,vm::requestSummary,{vm.clearSession();stopVoice()},{vm.stopResponse();stopVoice()},{tab=2},Modifier.weight(1f),liveSection,{liveSection=it},preferredAnswerId=sentAnswerId)}}
            else->Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(horizontal=20.dp).padding(top=16.dp,bottom=28.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
                when(tab){
                    1->{
                        Text("Tu biblioteca",style=MaterialTheme.typography.headlineSmall,fontWeight=androidx.compose.ui.text.font.FontWeight.SemiBold)
                        Text("Conversaciones e ideas, siempre a mano.",color=MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(selected=librarySection==0,onClick={librarySection=0},label={Text("Conversaciones ${s.history.size}")});FilterChip(selected=librarySection==1,onClick={librarySection=1},label={Text("Mis textos ${s.saved.size}")})}
                        if(librarySection==0){
                        Button(onClick={vm.newConversation();liveSection=0;tab=0}){Text("Nueva conversación")}
                        OutlinedTextField(search,{search=it},label={Text("Buscar una clase o conversación")},modifier=Modifier.fillMaxWidth(),singleLine=true)
                        s.history.filter{search.isBlank()||it.title.contains(search,true)}.forEach{entry->Card(onClick={vm.openConversation(entry.id);liveSection=2;tab=0},modifier=Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=androidx.compose.ui.graphics.Color(0xFF171D2A)),shape=androidx.compose.foundation.shape.RoundedCornerShape(24.dp)){Column(Modifier.padding(12.dp)){Text(entry.title,style=MaterialTheme.typography.titleMedium);Text("${entry.fragments} fragmentos · ${entry.questions} preguntas · Resumen",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant);Text(java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM,java.text.DateFormat.SHORT).format(java.util.Date(entry.updated)),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant);Row{TextButton(onClick={vm.openConversation(entry.id);liveSection=2;tab=0}){Text("Abrir")};TextButton(onClick={vm.deleteConversation(entry.id)}){Text("Borrar")}}}}}
                        if(s.history.isEmpty())DesignPanel("Tu próxima conversación"){Text("Cuando empieces a escuchar, encontrarás aquí el historial.",color=MaterialTheme.colorScheme.onSurfaceVariant)}
                        }else{
                        Text("Textos que elegiste guardar",style=MaterialTheme.typography.titleLarge)
                        Text("Almacenamiento privado de esta app. La conversación se guarda localmente; los recuerdos y tareas se eligen por separado.",style=MaterialTheme.typography.bodySmall)
                        Button(onClick={draft=SaveDraft()}){Text("Añadir recuerdo o tarea")}
                        OutlinedTextField(search,{search=it},label={Text("Buscar texto, curso o proyecto")},modifier=Modifier.fillMaxWidth())
                        s.saved.filter{search.isBlank()||"${it.text} ${it.scope} ${it.kind}".contains(search,true)}.forEach{item->Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=androidx.compose.ui.graphics.Color(0xFF171D2A)),shape=androidx.compose.foundation.shape.RoundedCornerShape(24.dp)){
                            Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                                Text("${item.kind}${if(item.done)" · completada" else ""}",style=MaterialTheme.typography.titleMedium)
                                Text(item.text)
                                Text(item.source,style=MaterialTheme.typography.bodySmall)
                                if(item.scope.isNotBlank())Text("Curso/proyecto: ${item.scope}")
                                if(item.due.isNotBlank())Text("Fecha indicada: ${item.due}")
                                Row{
                                    TextButton(onClick={draft=SaveDraft(item.id,item.kind,item.text,item.scope,item.due,item.source)}){Text("Editar")}
                                    if(item.kind=="Tarea")TextButton(onClick={vm.done(item.id,!item.done)}){Text(if(item.done)"Reabrir" else "Completar")}
                                    TextButton(onClick={vm.delete(item.id)}){Text("Borrar")}
                                }
                            }
                        }}
                        if(s.saved.isEmpty())DesignPanel("Ideas que vale la pena conservar"){Text("Guarda un fragmento, un recuerdo o una tarea desde la conversación.",color=MaterialTheme.colorScheme.onSurfaceVariant)}
                        }
                    }
                    2->{
                        Text("Ajustes",style=MaterialTheme.typography.headlineSmall,fontWeight=androidx.compose.ui.text.font.FontWeight.SemiBold)
                        DesignPanel("Idiomas · Android local"){
                            Text("Español se descarga automáticamente si está disponible. La primera descarga necesita conexión.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            val models=s.localSpeechModels
                            val managing=models.downloading||models.checking
                            TextButton(onClick={choosingLanguage=true},enabled=!managing&&!s.listening&&!s.starting,contentPadding=PaddingValues(0.dp)){
                                Text(languageName(s.speechLanguage),modifier=Modifier.weight(1f),color=MaterialTheme.colorScheme.onSurface)
                                Text("Cambiar",color=VoiceAccent)
                            }
                            Text(if(models.hasLanguage(s.speechLanguage))"Idioma listo sin conexión"else models.message,style=MaterialTheme.typography.bodySmall,color=if(models.hasLanguage(s.speechLanguage))VoiceGreen else MaterialTheme.colorScheme.onSurfaceVariant)
                            if(models.downloading){if(models.progress!=null){LinearProgressIndicator(progress={models.progress/100f},modifier=Modifier.fillMaxWidth());Text("${models.progress} %",style=MaterialTheme.typography.labelSmall)}else LinearProgressIndicator(Modifier.fillMaxWidth())}
                            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                                if(!models.hasLanguage(s.speechLanguage))TextButton(onClick=vm::downloadSpeechLanguage,enabled=models.available&&!managing&&!s.listening&&!s.starting){Text("Descargar idioma")}
                                TextButton(onClick={vm.refreshSpeechLanguages()},enabled=!managing&&!s.listening&&!s.starting){Text("Comprobar idiomas")}
                            }
                            if(!models.available&&!models.checking)TextButton(onClick={
                                val store=Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.tts"))
                                runCatching{appContext.startActivity(store)}.onFailure{vm.state.value=vm.state.value.copy(status="No se pudo abrir la tienda de aplicaciones.")}
                            }){Text("Obtener servicios de voz de Google")}
                            Text("Estos idiomas se usan con Android local. Moonshine mantiene su modelo español; Soniqo utiliza su propio modelo multilingüe.",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        DesignPanel("Fuente de audio"){
                        Row{FilterChip(selected=s.audioSource=="mic",onClick={vm.setAudioSource("mic")},label={Text("Micrófono")});Spacer(Modifier.width(8.dp));FilterChip(selected=s.audioSource=="video",onClick={vm.setAudioSource("video")},label={Text("Audio del video")})}
                        Text("Captura el video del mismo teléfono sin usar el micrófono. Mantén ambas apps en pantalla dividida. Android solicitará permiso; algunas apps bloquean la captura.",style=MaterialTheme.typography.bodySmall)
                        }
                        DesignPanel("Reconocimiento de voz"){
                        Column{FilterChip(selected=s.speechEngine=="soniqo",onClick={vm.setSpeechEngine("soniqo")},label={Text("Soniqo · Parakeet progresivo")});Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(selected=s.speechEngine=="moonshine",onClick={vm.setSpeechEngine("moonshine")},label={Text("Moonshine")});FilterChip(selected=s.speechEngine=="system",onClick={vm.setSpeechEngine("system")},label={Text("Android local")})}}
                        OutlinedButton(onClick=importSoniqo,enabled=!s.preparing&&!s.listening&&!s.starting){Text("Importar paquete Soniqo ZIP")}
                        Text("Android local es experimental: depende del servicio y del idioma instalado en tu teléfono. No es el motor privado de Transcripción instantánea. Solo usa el micrófono. Soniqo y Moonshine admiten audio directo del video.",style=MaterialTheme.typography.bodySmall)
                        Toggle("Priorizar solo transcripción",s.transcriptionOnly,vm::setTranscriptionOnly)
                        Text("Pausa respuestas y resúmenes durante la escucha. El resumen puede generarse al detenerla. Actívalo antes de grabar para descargar el modelo de respuestas de memoria.",style=MaterialTheme.typography.bodySmall)
                        }
                        DesignPanel("Durante la conversación"){
                        Toggle("Responder preguntas del audio",s.autoAnswers,vm::setAnswers)
                        Toggle("Resumen periódico",s.autoSummary,vm::setSummary)
                        Toggle("Etiquetar voces",s.voiceLabels,vm::setLabels)
                        Text("Las etiquetas se asignan al editar el texto. No hay identificación biométrica ni separación automática de personas.",style=MaterialTheme.typography.bodySmall)
                        }
                        DesignPanel("Modelos en el dispositivo"){
                        Text(s.models)
                        OutlinedButton(onClick=importSpeech,enabled=!s.listening&&!s.generating&&!s.preparing){Text("Importar modelo de voz ZIP")}
                        OutlinedButton(onClick=importLanguage,enabled=!s.listening&&!s.generating&&!s.preparing){Text("Importar modelo de respuestas")}
                        }
                        DesignPanel("Privacidad y herramientas"){
                        OutlinedButton(onClick=diagnostics,enabled=!s.preparing){Text("Abrir pruebas y métricas")}
                        Text("Funciona sin el Mac y sin permiso de Internet. Al salir se pausa el micrófono y se detienen las respuestas. La transcripción, preguntas, respuestas y resumen se guardan en el historial privado. El audio nunca se guarda. Puedes borrar cada conversación.")
                        Text("Las explicaciones del modelo pueden equivocarse. No realizan búsquedas externas ni ejecutan órdenes del audio. Las tareas y recuerdos requieren pulsar Guardar.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if(tab==2)Text("V · Android 0.20",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    }
    editing?.let{line->var text by remember(line.id){mutableStateOf(line.text)};var voice by remember(line.id){mutableStateOf(line.voice)}
        AlertDialog(onDismissRequest={editing=null},title={Text("Editar texto o voz")},text={Column{
            OutlinedTextField(text,{text=it},label={Text("Texto literal")},modifier=Modifier.fillMaxWidth())
            OutlinedTextField(voice,{voice=it},label={Text("Etiqueta de voz (manual)")},modifier=Modifier.fillMaxWidth())
        }},confirmButton={TextButton(onClick={vm.edit(line.id,text,voice);editing=null},enabled=text.isNotBlank()){Text("Aplicar")}},dismissButton={TextButton(onClick={editing=null}){Text("Cancelar")}})
    }
    draft?.let{initial->var text by remember(initial){mutableStateOf(initial.text)};var kind by remember(initial){mutableStateOf(initial.kind)};var scope by remember(initial){mutableStateOf(initial.scope)};var due by remember(initial){mutableStateOf(initial.due)}
        AlertDialog(onDismissRequest={draft=null},title={Text("Guardar texto seleccionado")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
            listOf(listOf("Recuerdo","Tarea"),listOf("Curso","Proyecto")).forEach{pair->Row{pair.forEach{k->TextButton(onClick={kind=k}){Text(if(kind==k)"• $k" else k)}}}}
            OutlinedTextField(text,{text=it},label={Text("Texto")},modifier=Modifier.fillMaxWidth())
            OutlinedTextField(scope,{scope=it},label={Text("Curso o proyecto (opcional)")},modifier=Modifier.fillMaxWidth())
            if(kind=="Tarea")OutlinedTextField(due,{due=it},label={Text("Fecha explícita (opcional)")},placeholder={Text("Ejemplo: 2026-10-09")},modifier=Modifier.fillMaxWidth())
            Text("No se deducen fechas ni responsables. Revisa el texto antes de guardarlo.",style=MaterialTheme.typography.bodySmall)
        }},confirmButton={TextButton(onClick={vm.saveWithSource(initial.id,kind,text,scope,due,initial.source);draft=null},enabled=text.isNotBlank()){Text("Guardar")}},dismissButton={TextButton(onClick={draft=null}){Text("Cancelar")}})
    }
}
@Composable private fun Toggle(label:String,value:Boolean,change:(Boolean)->Unit){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(label,Modifier.weight(1f));Switch(value,change)}}

@Composable private fun DesignPanel(title:String,content:@Composable ColumnScope.()->Unit){
    Column(Modifier.fillMaxWidth().padding(vertical=8.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        Text(title,style=MaterialTheme.typography.titleMedium,color=VoiceAccent)
        content()
        HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant,modifier=Modifier.padding(top=8.dp))
    }
}
