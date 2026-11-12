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
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

class MainActivity:ComponentActivity(){
    internal lateinit var assistant:AssistantViewModel
        private set
    private lateinit var googleAuth:GoogleAuthRepository
    private var requestedAnswerId by mutableStateOf<String?>(null)
    private var tts:TextToSpeech?=null
    private var localVoiceReady=false
    private var videoRequestPending=false
    private val permission=registerForActivityResult(ActivityResultContracts.RequestPermission()){allowed->if(allowed)requestListening()else assistant.state.value=assistant.state.value.copy(status="Permiso de micrófono no concedido.")}
    private val videoConsent=registerForActivityResult(ActivityResultContracts.StartActivityForResult()){result->
        videoRequestPending=false
        if(result.resultCode==RESULT_OK&&result.data!=null){
            ListeningService.start(this,result.data)

        }else assistant.state.value=assistant.state.value.copy(status="Permiso de audio del video cancelado. Micrófono apagado.")
    }
    private fun startSelectedAudio(){if(videoRequestPending)return;if(assistant.state.value.audioSource=="video"){videoRequestPending=true;videoConsent.launch(getSystemService(android.media.projection.MediaProjectionManager::class.java).createScreenCaptureIntent())}else ListeningService.start(this)}
    private val notifications=registerForActivityResult(ActivityResultContracts.RequestPermission()){startSelectedAudio()}
    private fun requestListening(){
        if(android.os.Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED&&!getPreferences(0).getBoolean("notice-requested",false)){
            getPreferences(0).edit().putBoolean("notice-requested",true).apply();notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }else startSelectedAudio()
    }
    private val overlayPermission=registerForActivityResult(ActivityResultContracts.StartActivityForResult()){
        val granted=android.provider.Settings.canDrawOverlays(this)
        assistant.setFloatingControl(granted)
        if(!granted)assistant.state.value=assistant.state.value.copy(status="Botón flotante desactivado. Puedes usar la notificación para volver o detener la escucha.")
    }
    private fun floatingControl(enabled:Boolean){
        if(enabled)assistant.setBackgroundListening(true)
        if(!enabled||android.provider.Settings.canDrawOverlays(this))assistant.setFloatingControl(enabled)
        else runCatching{overlayPermission.launch(Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,android.net.Uri.parse("package:$packageName")))}.onFailure{assistant.state.value=assistant.state.value.copy(status="No se pudo abrir el permiso de mostrar sobre otras aplicaciones.")}
    }
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState);enableEdgeToEdge(statusBarStyle=SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),navigationBarStyle=SystemBarStyle.dark(android.graphics.Color.TRANSPARENT));assistant=(application as AssistantApplication).assistant;googleAuth=GoogleAuthRepository(this)
        tts=TextToSpeech(this){status->if(status==TextToSpeech.SUCCESS){val voice=tts?.voices?.firstOrNull{it.locale.language=="es"&&!it.isNetworkConnectionRequired};if(voice!=null){tts?.voice=voice;localVoiceReady=true}}}
        requestedAnswerId=intent?.getStringExtra(FLOATING_ANSWER_ID)
        setContent{VoiceTheme{
            val auth by googleAuth.state.collectAsState()
            val scope=rememberCoroutineScope()
            var signingIn by remember{mutableStateOf(false)}
            var signInError by remember{mutableStateOf("")}
            var restoredUid by remember{mutableStateOf<String?>(null)}
            var legacyPrompt by remember{mutableStateOf(false)}
            when(val current=auth){
                GoogleAuthState.ConfigurationRequired->GoogleSignInScreen(configured=false,busy=false,error=""){ }
                GoogleAuthState.SignedOut->GoogleSignInScreen(configured=true,busy=signingIn,error=signInError){entry->
                    scope.launch{
                        signingIn=true
                        try{googleAuth.signIn(this@MainActivity,entry);signInError=""}
                        catch(error:CancellationException){signInError=""}
                        catch(error:Throwable){signInError=googleAuth.message(error)}
                        finally{signingIn=false}
                    }
                }
                is GoogleAuthState.SignedIn->{
                    LaunchedEffect(current.account.uid){restoredUid=null;legacyPrompt=assistant.restoreConversation(current.account.uid);restoredUid=current.account.uid}
                    if(restoredUid!=current.account.uid)Box(Modifier.fillMaxSize().systemBarsPadding(),contentAlignment=androidx.compose.ui.Alignment.Center){CircularProgressIndicator(color=VoiceAccent)}
                    else{
                        val s by assistant.state.collectAsState()
                        AssistantScreen(assistant,
                            listen={tts?.stop();if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)requestListening()else permission.launch(Manifest.permission.RECORD_AUDIO)},
                            speak={text->if(localVoiceReady){assistant.pause();tts?.speak(text,TextToSpeech.QUEUE_FLUSH,null,"local-answer")}else assistant.state.value=assistant.state.value.copy(status="Instala una voz española sin conexión en los ajustes de Android para escuchar respuestas.")},
                            stopVoice={tts?.stop()},floatingControl=::floatingControl,requestedAnswerId=requestedAnswerId,onAnswerRequestHandled={requestedAnswerId=null},account=current.account,
                            onSignOut={scope.launch{assistant.deactivateAccount();googleAuth.signOut()}},onImportLegacy=assistant::importLegacyData)
                        DisposableEffect(s.listening||s.generating){if(s.listening||s.generating)window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);onDispose{}}
                        if(legacyPrompt&&s.legacyDataAvailable)AlertDialog(onDismissRequest={assistant.dismissLegacyImportPrompt();legacyPrompt=false},title={Text("Conversaciones anteriores")},text={Text("Encontré conversaciones y textos guardados antes de iniciar sesión. Puedes asociarlos a esta cuenta; se sincronizarán cuando Firestore esté configurado.")},confirmButton={TextButton(onClick={assistant.importLegacyData();legacyPrompt=false}){Text("Asociar a esta cuenta")}},dismissButton={TextButton(onClick={assistant.dismissLegacyImportPrompt();legacyPrompt=false}){Text("Dejar aparte")}})
                    }
                }
            }
        }}
    }
    override fun onNewIntent(intent:Intent){super.onNewIntent(intent);setIntent(intent);requestedAnswerId=intent.getStringExtra(FLOATING_ANSWER_ID)}
    companion object{internal const val FLOATING_ANSWER_ID="floating-answer-id"}
    override fun onStart(){super.onStart();assistant.resumeForeground()}
    override fun onStop(){if(!isChangingConfigurations)assistant.background();tts?.stop();super.onStop()}
    override fun onDestroy(){if(::googleAuth.isInitialized)googleAuth.close();tts?.shutdown();super.onDestroy()}
}

private data class SaveDraft(val id:Long?=null,val kind:String="Recuerdo",val text:String="",val scope:String="",val due:String="",val source:String="Texto escrito por usuario")
@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun AssistantScreen(vm:AssistantViewModel,listen:()->Unit,speak:(String)->Unit,stopVoice:()->Unit,initialTab:Int=0,initialSection:Int=0,initialDrawer:Boolean=false,floatingControl:(Boolean)->Unit=vm::setFloatingControl,requestedAnswerId:String?=null,onAnswerRequestHandled:()->Unit={},account:GoogleAccountInfo?=null,onSignOut:()->Unit={},onImportLegacy:()->Unit={}){
    val s by vm.state.collectAsState()
    var liveSection by rememberSaveable{mutableIntStateOf(initialSection)}
    var tab by remember{mutableIntStateOf(initialTab)}
    var question by rememberSaveable(s.conversationKey){mutableStateOf("")}
    var sentAnswerId by remember(s.conversationKey){mutableStateOf<String?>(null)}
    var editing by remember{mutableStateOf<SpeechLine?>(null)}
    var draft by remember{mutableStateOf<SaveDraft?>(null)}
    var librarySection by remember{mutableIntStateOf(0)}
    var search by remember{mutableStateOf("")}
    var librarySearching by rememberSaveable{mutableStateOf(false)}
    val librarySearchFocus=remember{FocusRequester()}
    val compact=LocalConfiguration.current.screenHeightDp<480
    val keyboard=LocalSoftwareKeyboardController.current
    val drawer=rememberDrawerState(if(initialDrawer)DrawerValue.Open else DrawerValue.Closed)
    val scope=rememberCoroutineScope()
    LaunchedEffect(requestedAnswerId){
        if(requestedAnswerId!=null){sentAnswerId=requestedAnswerId;liveSection=1;tab=0;drawer.close();onAnswerRequestHandled()}
    }
    var naming by remember{mutableStateOf(false)}
    var namingId by remember{mutableStateOf<Long?>(null)}
    var deletingId by remember{mutableStateOf<Long?>(null)}
    var choosingLanguage by remember{mutableStateOf(false)}
    var nameDraft by remember{mutableStateOf("")}
    var moving by remember{mutableStateOf<FolderMove?>(null)}
    var folderName by remember{mutableStateOf<String?>(null)}
    var folderEdit by remember{mutableStateOf<Long?>(null)}
    var folderDelete by remember{mutableStateOf<Long?>(null)}
    var confirmSignOut by remember{mutableStateOf(false)}
    var showAccountDetails by remember{mutableStateOf(false)}
    val accountInitials=remember(account){
        val words=account?.displayName.orEmpty().trim().split(Regex("\\s+")).filter{it.isNotBlank()}
        when{words.size>=2->"${words.first().first()}${words.last().first()}";words.isNotEmpty()->words.first().take(2);else->account?.email.orEmpty().substringBefore('@').filter{it.isLetterOrDigit()}.take(2).ifBlank{"V"}}.uppercase()
    }
    if(confirmSignOut)GoogleSignOutDialog({confirmSignOut=false;onSignOut()},{confirmSignOut=false})
    if(showAccountDetails){
        val email=account?.email.orEmpty()
        val handle=email.substringBefore('@').takeIf{it.isNotBlank()}?.let{"@$it"}?:"Cuenta local"
        val displayName=account?.displayName?.ifBlank{"Usuario"}?:"Usuario"
        val connected=account!=null
        androidx.compose.ui.window.Dialog(onDismissRequest={showAccountDetails=false}){
            Surface(modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(28.dp),color=Color(0xFF1D2335),shadowElevation=18.dp){
                Column(Modifier.padding(horizontal=22.dp,vertical=18.dp),verticalArrangement=Arrangement.spacedBy(20.dp),horizontalAlignment=androidx.compose.ui.Alignment.CenterHorizontally){
                    Box(Modifier.fillMaxWidth()){
                        IconButton(onClick={showAccountDetails=false},modifier=Modifier.align(androidx.compose.ui.Alignment.TopEnd).size(38.dp)){VoiceGlyph("close",Color(0xFF9BA4B7),Modifier.size(18.dp))}
                        Column(Modifier.fillMaxWidth().padding(top=20.dp),horizontalAlignment=androidx.compose.ui.Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)){
                            Box(Modifier.size(92.dp).background(Color(0xFF965BB5),androidx.compose.foundation.shape.CircleShape),contentAlignment=androidx.compose.ui.Alignment.Center){
                                Text(accountInitials,color=Color.White,style=MaterialTheme.typography.headlineMedium,fontWeight=androidx.compose.ui.text.font.FontWeight.Normal)
                            }
                            Text(displayName,style=MaterialTheme.typography.headlineSmall,fontWeight=androidx.compose.ui.text.font.FontWeight.Normal,color=Color(0xFFF3F2F9),textAlign=androidx.compose.ui.text.style.TextAlign.Center,maxLines=2)
                            Text(handle,style=MaterialTheme.typography.bodyLarge,color=Color(0xFFA6AABD),textAlign=androidx.compose.ui.text.style.TextAlign.Center,maxLines=1)
                        }
                    }
                    HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                    Column(horizontalAlignment=androidx.compose.ui.Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)){
                        Row(Modifier.background(if(connected)VoiceGreen.copy(alpha=.12f)else Color(0xFF2A3040),RoundedCornerShape(50)).padding(horizontal=12.dp,vertical=7.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
                            Box(Modifier.size(7.dp).background(if(connected)VoiceGreen else Color(0xFF9BA4B7),androidx.compose.foundation.shape.CircleShape))
                            Spacer(Modifier.width(7.dp));Text(if(connected)"Cuenta conectada"else"Sin sesión",style=MaterialTheme.typography.labelMedium,color=if(connected)VoiceGreen else Color(0xFFB7BECC))
                        }
                        if(email.isNotBlank())Text(email,style=MaterialTheme.typography.bodyMedium,color=Color(0xFFCDD2DE),textAlign=androidx.compose.ui.text.style.TextAlign.Center,maxLines=2)
                    }
                    Button(onClick={showAccountDetails=false},modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=VoiceAccent,contentColor=Color(0xFF201337))){Text("Listo",fontWeight=androidx.compose.ui.text.font.FontWeight.Medium)}
                }
            }
        }
    }
    moving?.let{target->FolderPicker(s.folders,if(target.conversation==null)s.folderId else s.history.firstOrNull{it.id==target.conversation}?.folderId,{folder->vm.moveConversation(target.conversation,folder);moving=null},{folderEdit=null;folderName=""},{moving=null})}
    folderName?.let{initial->var name by remember(initial,folderEdit){mutableStateOf(initial)}
        AlertDialog(onDismissRequest={folderName=null},title={Text(if(folderEdit==null)"Nueva carpeta"else "Renombrar carpeta")},text={OutlinedTextField(name,{name=it.take(80)},singleLine=true,label={Text("Nombre")})},confirmButton={TextButton(onClick={
            if(folderEdit==null){val id=vm.createFolder(name);if(id!=null){moving?.let{vm.moveConversation(it.conversation,id);moving=null};folderName=null}}
            else{vm.renameFolder(checkNotNull(folderEdit),name);folderName=null}
        },enabled=name.isNotBlank()){Text("Guardar")}},dismissButton={TextButton(onClick={folderName=null}){Text("Cancelar")}})
    }
    folderDelete?.let{id->AlertDialog(onDismissRequest={folderDelete=null},title={Text("¿Eliminar carpeta?")},text={Text("Las conversaciones se conservarán en el historial.")},confirmButton={TextButton(onClick={vm.deleteFolder(id);folderDelete=null}){Text("Eliminar carpeta")}},dismissButton={TextButton(onClick={folderDelete=null}){Text("Cancelar")}})}
    LaunchedEffect(s.organizationRevision){if(s.organizationRevision>0)liveSection=2}
    if(naming)AlertDialog(onDismissRequest={naming=false},title={Text("Nombre del chat")},text={OutlinedTextField(nameDraft,{nameDraft=it},label={Text("Nombre")},singleLine=true)},confirmButton={TextButton(onClick={namingId?.let{vm.renameConversation(it,nameDraft)}?:vm.renameConversation(nameDraft);naming=false}){Text("Guardar")}},dismissButton={TextButton(onClick={naming=false}){Text("Cancelar")}})
    deletingId?.let{id->AlertDialog(onDismissRequest={deletingId=null},title={Text("¿Eliminar conversación?")},text={Text("Se borrarán su transcripción, respuestas y resumen guardados.")},confirmButton={TextButton(onClick={vm.deleteConversation(id);deletingId=null}){Text("Eliminar",color=MaterialTheme.colorScheme.error)}},dismissButton={TextButton(onClick={deletingId=null}){Text("Cancelar")}})}
    if(choosingLanguage)AlertDialog(onDismissRequest={choosingLanguage=false},title={Text("Idioma de transcripción")},text={Column(Modifier.heightIn(max=360.dp).verticalScroll(rememberScrollState())){
        s.localSpeechModels.languages().forEach{tag->TextButton(onClick={vm.setSpeechLanguage(tag);choosingLanguage=false},modifier=Modifier.fillMaxWidth()){
            Text(languageName(tag),modifier=Modifier.weight(1f),color=if(s.speechLanguage==tag)VoiceAccent else MaterialTheme.colorScheme.onSurface)
            Text(if(s.localSpeechModels.hasLanguage(tag))"Instalado"else "Descargar",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }}
    }},confirmButton={TextButton(onClick={choosingLanguage=false}){Text("Cerrar")}})
    ModalNavigationDrawer(drawerState=drawer,drawerContent={
        ChatSidebar(s,tab,{scope.launch{drawer.close()}},{vm.newConversation();tab=0;liveSection=0;scope.launch{drawer.close()}},{id->vm.openConversation(id);tab=0;liveSection=0;scope.launch{drawer.close()}},{tab=2;scope.launch{drawer.close()}},onRename={id,title->namingId=id;nameDraft=title;naming=true},onDelete={deletingId=it},onCreateFolder={folderEdit=null;folderName=""},onRenameFolder={id,name->folderEdit=id;folderName=name},onDeleteFolder={folderDelete=it},onMove={moving=FolderMove(it)},onNewInFolder={folder->vm.newConversation(folder);tab=0;liveSection=0;scope.launch{drawer.close()}},account=account,onAccount={showAccountDetails=true},onSignOut={confirmSignOut=true},onPin=vm::setConversationPinned)
    }){
    Scaffold(containerColor=VoiceBackground,contentWindowInsets=if(tab==0)WindowInsets(0,0,0,0)else ScaffoldDefaults.contentWindowInsets,
        topBar={if(tab!=0)
            Column(Modifier.statusBarsPadding()){
                Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
                    ChatCircleAction("Abrir historial de chats","menu"){scope.launch{drawer.open()}}
                    Spacer(Modifier.width(12.dp));VoiceHeader(s.listening,true,s.starting)
                }

            }
        }
    ){padding->
        when(tab){
            0->ConversationStage(
                header={ConversationTopBar(liveSection,{liveSection=it},{keyboard?.hide();scope.launch{drawer.open()}},{vm.newConversation();liveSection=0})},
                composer={ChatVoiceComposer(s.listening,s.starting,s.audioLevel,!s.preparing,{if(s.listening||s.starting){if(s.listening)liveSection=2;vm.pause()}else {liveSection=0;listen()}},question,{question=it},{if(question.isNotBlank()&&!s.preparing){vm.ask(question.trim());sentAnswerId=vm.state.value.answers.lastOrNull()?.id;question="";liveSection=1;keyboard?.hide()}})}
            ){readingInsets->key(s.conversationKey){LiveConversation(s,{editing=it},{text,source->draft=SaveDraft(kind=if(source.startsWith("Tarea de clase"))"Tarea"else "Recuerdo",text=text,source=source)},speak,vm::requestSummary,{vm.clearSession();stopVoice()},{vm.stopResponse();stopVoice()},{tab=2},Modifier.fillMaxSize(),liveSection,{liveSection=it},preferredAnswerId=sentAnswerId,transcriptPadding=readingInsets,onResearch=vm::researchSummary)}}
            else->Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(horizontal=20.dp).padding(top=16.dp,bottom=28.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
                when(tab){
                    1->{
                        Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){
                            Text("Tu biblioteca",Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
                            IconButton(onClick={librarySearching=!librarySearching;if(!librarySearching){search="";keyboard?.hide()}},modifier=Modifier.semantics{contentDescription=if(librarySearching)"Cerrar búsqueda de biblioteca"else "Buscar en biblioteca"}){VoiceGlyph("search",VoiceAccent,Modifier.size(20.dp))}
                        }
                        if(librarySearching){
                            OutlinedTextField(search,{search=it},placeholder={Text("Buscar en biblioteca")},modifier=Modifier.fillMaxWidth().focusRequester(librarySearchFocus),singleLine=true)
                            LaunchedEffect(Unit){librarySearchFocus.requestFocus();keyboard?.show()}
                        }
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(selected=librarySection==0,onClick={librarySection=0},label={Text("Conversaciones ${s.history.size}")});FilterChip(selected=librarySection==1,onClick={librarySection=1},label={Text("Mis textos ${s.saved.size}")})}
                        if(librarySection==0){
                        Button(onClick={vm.newConversation();liveSection=0;tab=0}){Text("Nueva conversación")}
                        s.history.filter{search.isBlank()||it.title.contains(search,true)}.forEach{entry->
                            key(entry.id){ConversationHistoryRow(entry,false,false,{id->vm.openConversation(id);liveSection=0;tab=0},{id,title->namingId=id;nameDraft=title;naming=true},{moving=FolderMove(it)},{deletingId=it},onPin=vm::setConversationPinned)}
                        }
                        if(s.history.isEmpty())DesignPanel("Tu próxima conversación"){Text("Cuando empieces a escuchar, encontrarás aquí el historial.",color=MaterialTheme.colorScheme.onSurfaceVariant)}
                        }else{
                        Text("Textos que elegiste guardar",style=MaterialTheme.typography.titleLarge)
                        Text("Almacenamiento privado de esta app. La conversación se guarda localmente; los recuerdos y tareas se eligen por separado.",style=MaterialTheme.typography.bodySmall)
                        Button(onClick={draft=SaveDraft()}){Text("Añadir recuerdo o tarea")}
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
                        SettingsSection("Escucha","phone"){
                            SettingsToggle("phone","Continuar al salir","La transcripción sigue en segundo plano",s.backgroundListening,vm::setBackgroundListening)
                            SettingsToggle("expand","Botón flotante","Acceso rápido sobre otras apps",s.floatingControl,floatingControl)
                        }
                        SettingsSection("Preguntas","spark"){
                            SettingsToggle("spark","Respuestas online","Usa Internet para responder",s.onlineAnswers,vm::setOnlineAnswers)
                            SettingsToggle("mic","Detectar durante la escucha","Responder cuando aparezca una pregunta clara",s.autoAnswers,vm::setAnswers)
                        }
                        SettingsSection("Entrada de audio","wave"){
                            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                                FilterChip(selected=s.audioSource=="mic",onClick={vm.setAudioSource("mic")},label={Text("Micrófono")},leadingIcon={VoiceGlyph("mic",if(s.audioSource=="mic")VoiceAccent else MaterialTheme.colorScheme.onSurfaceVariant,Modifier.size(18.dp))})
                                FilterChip(selected=s.audioSource=="video",onClick={vm.setAudioSource("video")},label={Text("Teléfono")},leadingIcon={VoiceGlyph("phone",if(s.audioSource=="video")VoiceAccent else MaterialTheme.colorScheme.onSurfaceVariant,Modifier.size(18.dp))})
                            }
                        }
                        SettingsSection("Transcripción local","language"){
                            val models=s.localSpeechModels
                            val managing=models.downloading||models.checking
                            Text(languageName(s.speechLanguage),style=MaterialTheme.typography.titleMedium,fontWeight=androidx.compose.ui.text.font.FontWeight.Medium)
                            Text(if(models.hasLanguage(s.speechLanguage))"Disponible sin conexión"else models.message,style=MaterialTheme.typography.bodySmall,color=if(models.hasLanguage(s.speechLanguage))VoiceGreen else MaterialTheme.colorScheme.onSurfaceVariant)
                            if(models.downloading){if(models.progress!=null){LinearProgressIndicator(progress={models.progress/100f},modifier=Modifier.fillMaxWidth());Text("${models.progress} %",style=MaterialTheme.typography.labelSmall)}else LinearProgressIndicator(Modifier.fillMaxWidth())}
                            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                                TextButton(onClick={choosingLanguage=true},enabled=!managing&&!s.listening&&!s.starting){Text("Idioma")}
                                if(models.available&&!models.hasLanguage(s.speechLanguage))TextButton(onClick=vm::downloadSpeechLanguage,enabled=!managing&&!s.listening&&!s.starting){Text("Descargar")}
                                if(!models.checking)TextButton(onClick={vm.refreshSpeechLanguages()},enabled=!managing&&!s.listening&&!s.starting){Text("Comprobar")}
                            }
                        }
                        SettingsSection("Cuenta","account"){
                            if(s.cloudStatus.isNotBlank())Text(s.cloudStatus,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            if(s.legacyDataAvailable)TextButton(onClick=onImportLegacy,contentPadding=PaddingValues(0.dp)){Text("Importar historial anterior",color=VoiceAccent)}
                            Text("El audio no se guarda.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
    }
    editing?.let{line->var text by remember(line.id){mutableStateOf(line.text)}
        AlertDialog(onDismissRequest={editing=null},title={Text("Editar texto")},text={Column{
            OutlinedTextField(text,{text=it},label={Text("Texto literal")},modifier=Modifier.fillMaxWidth())
        }},confirmButton={TextButton(onClick={vm.edit(line.id,text,line.voice);editing=null},enabled=text.isNotBlank()){Text("Aplicar")}},dismissButton={TextButton(onClick={editing=null}){Text("Cancelar")}})
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
@Composable private fun SettingsSection(title:String,icon:String,content:@Composable ColumnScope.()->Unit){
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(10.dp)){
        Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
            androidx.compose.foundation.layout.Box(Modifier.size(34.dp).background(Color(0xFF302642),RoundedCornerShape(11.dp)),contentAlignment=androidx.compose.ui.Alignment.Center){VoiceGlyph(icon,VoiceAccent,Modifier.size(18.dp))}
            Text(title,style=MaterialTheme.typography.titleMedium,fontWeight=androidx.compose.ui.text.font.FontWeight.SemiBold)
        }
        Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp),content=content)
        HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant,modifier=Modifier.padding(top=4.dp))
    }
}

@Composable private fun SettingsToggle(icon:String,title:String,description:String,value:Boolean,change:(Boolean)->Unit){
    Row(Modifier.fillMaxWidth().background(Color(0xFF141827),RoundedCornerShape(16.dp)).padding(horizontal=12.dp,vertical=10.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
        VoiceGlyph(icon,if(value)VoiceAccent else MaterialTheme.colorScheme.onSurfaceVariant,Modifier.size(20.dp))
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)){
            Text(title,style=MaterialTheme.typography.bodyLarge)
            Text(description,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,lineHeight=17.sp)
        }
        Switch(checked=value,onCheckedChange=change)
    }
}

@Composable private fun DesignPanel(title:String,content:@Composable ColumnScope.()->Unit){
    Column(Modifier.fillMaxWidth().padding(vertical=8.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        Text(title,style=MaterialTheme.typography.titleMedium,color=VoiceAccent)
        content()
        HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant,modifier=Modifier.padding(top=8.dp))
    }
}
