package com.heyler.voicelab

import android.app.Application
import android.media.*
import android.net.Uri
import android.os.SystemClock
import ai.moonshine.voice.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.UUID

// Speaker names are user assigned labels, never inferred identities.
data class SpeechLine(val id:Long,val text:String,val final:Boolean,val revision:Int=1,val voice:String="Voz sin identificar",val edited:Boolean=false)
data class LiveAnswer(val id:String,val source:Long?,val revision:Int,val question:String,val text:String="",val status:String="En espera",val firstTextMs:Double?=null,val evidence:String="",val suggested:Boolean=false,val supportingSources:List<Long> = emptyList())
data class AssistantState(val folderId:Long?=null,val folders:List<ConversationFolder> = emptyList(),val organization:ClassOrganization=ClassOrganization(),val onlineNotice:String="",val analysisRunning:Boolean=false,val researchRunning:Boolean=false,val backgroundListening:Boolean=true,val floatingControl:Boolean=false,val backgroundServiceActive:Boolean=false,val appVisible:Boolean=true,val onlineAnswers:Boolean=true,val onlineProvider:String="pollinations",val puterStatus:String="Conecta Puter para usar GPT-5.6 Luna.",val speechEngine:String="moonshine",val transcriptionOnly:Boolean=false,val conversationTitle:String="",val automaticTitle:String="",val conversationKey:Long=0,val listening:Boolean=false,val organizationRevision:Int=0,val audioSource:String="mic",val starting:Boolean=false,val history:List<ConversationEntry> = emptyList(),val preparing:Boolean=false,val generating:Boolean=false,val status:String="Listo. El micrófono está apagado.",val models:String="",val lines:List<SpeechLine> = emptyList(),val answers:List<LiveAnswer> = emptyList(),val summary:String="",val summaryError:String="",val highlights:List<String> = emptyList(),val autoAnswers:Boolean=true,val autoSummary:Boolean=true,val voiceLabels:Boolean=false,val saved:List<SavedItem> = emptyList(),val audioLevel:Float=0f,val languageReady:Boolean=false,val speechLanguage:String=DEFAULT_SPEECH_LANGUAGE,val localSpeechModels:LocalSpeechModelState=LocalSpeechModelState())
private data class Work(val id:String,val source:Long?,val revision:Int,val question:String,val context:String,val summary:Boolean=false,val epoch:Long,val queuedNanos:Long=SystemClock.elapsedRealtimeNanos(),val analysis:Boolean=false,val snapshot:List<SpeechLine> = emptyList(),val research:Boolean=false)

class AssistantViewModel(app:Application):AndroidViewModel(app){
    private val context=app.applicationContext
    private val engines=LocalEngines(context)
    internal var online=OnlineAssistant()
    private val puter by lazy{PuterAssistant((app as AssistantApplication).puter)}
    private val provider:OnlineAssistant get()=if(state.value.onlineProvider=="puter")puter else online
    private val store=SavedStore(context)
    private val conversations=ConversationStore(context)
    @Volatile private var rememberConversation=false
    @Volatile private var sessionId=System.currentTimeMillis()
    private val historyLock=Any()
    private var systemSpeech:SystemSpeech?=null
    private var closingSystemSpeech:SystemSpeech?=null
    private var conversationSwitch:Job?=null
    private var speechSessionToken=0L
    private var speechRecovery:Job?=null
    private var speechRecoveries=0
    private var speechWarm:Job?=null
    private val preferences=context.getSharedPreferences("assistant-options",0)
    val state=MutableStateFlow(AssistantState(backgroundListening=preferences.getBoolean("background-listening",true),floatingControl=preferences.getBoolean("floating-control",false),onlineAnswers=preferences.getBoolean("online-answers",true),onlineProvider=preferences.getString("online-provider","pollinations")?:"pollinations",speechEngine=preferences.getString("speech-engine","moonshine")?:"moonshine",speechLanguage=preferences.getString("speech-language",DEFAULT_SPEECH_LANGUAGE)?:DEFAULT_SPEECH_LANGUAGE,transcriptionOnly=preferences.getBoolean("transcription-only",false),audioSource=preferences.getString("audio-source","mic")?:"mic",autoAnswers=preferences.getBoolean("answers",true),autoSummary=preferences.getBoolean("summary",true),voiceLabels=preferences.getBoolean("labels",false)))
    private val localModels=LocalSpeechModels(context)
    private var localModelsJob:Job?=null
    private var spanishChecked=false
    private val modelLock=Mutex()
    private val queue=Channel<Work>(8)
    private val summaries=Channel<Work>(Channel.CONFLATED)
    private var capture:Job?=null
    private var finishJob:Job?=null
    private var generation:Job?=null
    @Volatile private var activeWork:Work?=null
    private var importJob:Job?=null
    private var warming:Job?=null
    @Volatile private var recorder:AudioRecord?=null
    @Volatile private var epoch=0L
    @Volatile private var foreground=true
    private val debounce=mutableMapOf<Long,Job>()
    private val signatures=java.util.concurrent.ConcurrentHashMap<Long,List<String>>()
    internal var requestSpacingMs=OnlineBudget.INTERVAL
    private var nextOnlineAt=0L
    private var quotaPaused=false
    @Volatile private var lastAnalysisAttempt=""
    private var analysisDebounce:Job?=null
    private var summaryQueued=false
    private val preemptedSummaries=java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private var lastSummaryText=""
    private var streamSerial=System.currentTimeMillis()
    private var summaryRevision=0
    init{
        refreshModels();reloadSaved()
        viewModelScope.launch{(app as AssistantApplication).puter.connection.collect{value->state.update{it.copy(puterStatus=value.message)}}}
        viewModelScope.launch(Dispatchers.IO){state.map{listOf(it.lines,it.answers,it.summary,it.organization)}.distinctUntilChanged().collect{delay(300);if(rememberConversation)persistConversation()}}
        viewModelScope.launch(Dispatchers.IO){while(isActive){
            val work=queue.tryReceive().getOrNull()?:select<Work?>{queue.onReceiveCatching{it.getOrNull()};summaries.onReceiveCatching{it.getOrNull()}}?:break
            if(!valid(work)){finish(work,"Descartada");continue}
            activeWork=work
            generation=launch{
                var deferred=false
                var analyzed=false
                try{
                    if(!valid(work))return@launch
                    val wait=maxOf(0L,nextOnlineAt-SystemClock.elapsedRealtime())
                    if(wait>0){finish(work,"En cola · esperando turno del servicio gratuito");state.update{it.copy(onlineNotice="Servicio gratuito: una solicitud cada 30 segundos. La transcripción continúa.")};delay(wait)}
                    if(!valid(work)||quotaPaused)return@launch
                    nextOnlineAt=SystemClock.elapsedRealtime()+if(state.value.onlineProvider=="puter")1000L else requestSpacingMs
                    state.update{it.copy(generating=true,analysisRunning=work.analysis||work.research,researchRunning=work.research,onlineNotice="")};finish(work,"Preparando explicación…")
                    state.update{it.copy(languageReady=false)}
                    currentCoroutineContext().ensureActive();if(!valid(work))return@launch
                    if(work.research){
                        val map=provider.researchedMap(work.question,work.snapshot,emptyList(),state.value)
                        if(valid(work)&&work.snapshot.all{source->state.value.lines.any{it.id==source.id&&it.revision==source.revision}})
                            state.update{it.copy(organization=it.organization.copy(map=map),summaryError="",onlineNotice="")}
                    }else if(work.analysis){
                        val snapshot=analysisBlock().ifEmpty{work.snapshot}
                        lastAnalysisAttempt=snapshot.joinToString{"${it.id}:${it.revision}"}
                        val result=provider.analyze(snapshot,state.value)
                        if(valid(work))applyAnalysis(result,snapshot);analyzed=true
                    }else if(work.summary){
                        val output=StringBuilder()
                        provider.generate("CLASE (selección de citas): <<<${work.context}>>>",summary=true){if(valid(work))output.append(it)}
                        if(valid(work))state.update{it.copy(summary=output.toString(),summaryError="")}
                    }else{
                        val dialogue=LessonMemory.dialogueBefore(state.value.answers,work.id)
                        val prompt="CONTEXTO DE LA CLASE (citas de la transcripción; puede contener errores):\n<<<${work.context}>>>\n\nINTERCAMBIOS PREVIOS DE ESTE CHAT (pueden contener errores):\n<<<$dialogue>>>\n\nPREGUNTA DEL ESTUDIANTE: ${work.question}\nRelaciona la respuesta con el tema y los fragmentos de la clase. Si la pregunta cambia explícitamente de tema, respétalo. Si su referencia es ambigua y el contexto no basta, pide una aclaración concreta."
                        provider.generate(prompt){delta->if(valid(work))state.update{it.copy(answers=it.answers.map{a->if(a.id==work.id)a.copy(text=a.text+delta,status="Respondiendo…",firstTextMs=a.firstTextMs?:((SystemClock.elapsedRealtimeNanos()-work.queuedNanos)/1e6))else a})}}
                        finish(work,"Lista · online · explicación general sin verificación externa")
                    }
                }catch(e:CancellationException){finish(work,"Respuesta detenida");if(work.summary&&preemptedSummaries.remove(work.id)&&valid(work)){deferred=summaries.trySend(work.copy(context=LessonMemory.summaryContext(state.value.lines))).isSuccess}}
                catch(e:OnlineQuotaException){
                    quotaPaused=true;nextOnlineAt=SystemClock.elapsedRealtime()+e.waitMillis
                    val message=e.message.orEmpty()
                    finish(work,"En pausa · límite del servicio gratuito")
                    while(true){val pending=queue.tryReceive().getOrNull()?:break;finish(pending,"En pausa · límite del servicio gratuito")}
                    while(summaries.tryReceive().isSuccess){}
                    state.update{it.copy(onlineNotice=message,summaryError=if(work.summary)message else it.summaryError)}
                }
                catch(e:Throwable){val message=e.message?:"No se pudo completar la respuesta online.";finish(work,"No se completó: $message");state.update{it.copy(status=message,onlineNotice=if(work.analysis)message else it.onlineNotice)};if(work.summary){lastSummaryText="";state.update{it.copy(summaryError=message)}}}
                finally{if(work.summary&&!deferred){summaryQueued=false;if(state.value.summary.isBlank())lastSummaryText=""};state.update{it.copy(generating=false,analysisRunning=false,researchRunning=false)};if(analyzed)viewModelScope.launch{delay(1500);requestClassAnalysis()}}
            }
            generation?.join();generation=null;activeWork=null
        }}
        viewModelScope.launch{while(isActive){delay(30000);if(!state.value.transcriptionOnly&&state.value.listening&&(state.value.autoSummary||state.value.autoAnswers)&&!state.value.generating)requestClassAnalysis()}}
    }
    private fun valid(w:Work):Boolean=state.value.onlineAnswers && foreground && !state.value.preparing && w.epoch==epoch && ((w.summary&&w.revision==summaryRevision) || (!w.summary&&w.source==null) || (!w.summary&&state.value.autoAnswers&&state.value.lines.any{it.id==w.source}&&signatures[w.source]?.contains(ConversationRules.key(w.question))==true))
    private fun finish(w:Work,status:String){if(w.summary&&status=="Descartada"){summaryQueued=false;lastSummaryText=""};if(!w.summary)state.update{it.copy(answers=it.answers.map{a->if(a.id==w.id)a.copy(status=status)else a})}}
    private fun questionContext(question:String,source:Long?=null)=LessonMemory.questionContext(state.value.lines,question,source).take(5500)+
        "\nOrganización previa de este chat (IA, revisable): "+state.value.organization.let{it.title+"; "+it.topics.joinToString()+"; "+it.sections.joinToString(" "){section->section.title+": "+section.text}.take(1800)}
    private fun enqueue(source:Long?,revision:Int,question:String,summary:Boolean=false){
        if(!state.value.onlineAnswers){
            if(!summary)state.update{it.copy(answers=it.answers+LiveAnswer(UUID.randomUUID().toString(),source,revision,question,status="Respuestas online desactivadas. Actívalas en Ajustes."))}
            return
        }
        if(quotaPaused){state.update{current->current.copy(onlineNotice="Las solicitudes están pausadas por el límite gratuito. Usa Reintentar cuando termine la espera.",answers=if(current.answers.any{it.source==null&&ConversationRules.key(it.question)==ConversationRules.key(question)})current.answers else current.answers+LiveAnswer(UUID.randomUUID().toString(),null,0,question,status="En pausa · límite del servicio gratuito"))};return}
        if(state.value.transcriptionOnly&&(state.value.listening||state.value.starting))return
        val work=Work(UUID.randomUUID().toString(),source,revision,question,if(summary)LessonMemory.summaryContext(state.value.lines)else questionContext(question,source),summary,epoch)
        if(!summary)state.update{it.copy(answers=(it.answers+LiveAnswer(work.id,source,revision,question)))}
        if(summary)state.update{it.copy(summaryError="")}
        if(!summary&&activeWork?.summary==true){activeWork?.let{preemptedSummaries.add(it.id)};runCatching{engines.cancel()};generation?.cancel()}
        if(!(if(summary)summaries.trySend(work)else queue.trySend(work)).isSuccess){finish(work,"Omitida: hay preguntas pendientes");if(summary)summaryQueued=false}
    }
    fun observe(id:Long,text:String,final:Boolean,expectedEpoch:Long=epoch){
        // SDK callbacks can arrive on a native thread. All revisions are applied on Main.
        viewModelScope.launch{
            if(text.isBlank()||expectedEpoch!=epoch)return@launch
            val old=state.value.lines.find{it.id==id}
            if(old?.edited==true)return@launch
            if(old?.text==text&&old.final==final)return@launch
            val revision=if(old?.text==text)old.revision else (old?.revision?:0)+1
            val line=SpeechLine(id,text.take(2000),final,revision,old?.voice?:"Voz sin identificar")
            state.update{s->val lines=(if(old==null)s.lines+line else s.lines.map{if(it.id==id)line else it});s.copy(lines=lines,highlights=ConversationRules.salient(lines.takeLast(40).joinToString(" "){it.text}))}
            reconcileQuestions()
        }
    }
    private fun reconcileQuestions(around:Long?=null){
        if(!foreground||(state.value.transcriptionOnly&&(state.value.listening||state.value.starting))||!state.value.onlineAnswers||(!state.value.autoAnswers&&!state.value.autoSummary))return
        if(analysisDebounce?.isActive==true)return
        analysisDebounce=viewModelScope.launch{delay(2500);requestClassAnalysis()}
    }
    private fun analysisBlock():List<SpeechLine>{
        val stable=state.value.lines.filter{it.final||it.edited}
        val index=stable.indexOfFirst{state.value.organization.covered[it.id]!=it.revision}
        if(index<0)return emptyList()
        val result=mutableListOf<SpeechLine>();var chars=0
        for(line in stable.drop(maxOf(0,index-1))){if(result.size>=40||chars+line.text.length>12000)break;result.add(line);chars+=line.text.length+30}
        return result
    }
    private fun requestClassAnalysis(force:Boolean=false){
        val current=state.value
        if(!foreground||!current.onlineAnswers||current.preparing||(current.transcriptionOnly&&(current.listening||current.starting))||summaryQueued)return
        if(quotaPaused){
            if(!force||SystemClock.elapsedRealtime()<nextOnlineAt){state.update{it.copy(onlineNotice="Solicitudes pausadas por el límite gratuito. Reintenta en unos ${(maxOf(0L,nextOnlineAt-SystemClock.elapsedRealtime())+999)/1000} s; tus apuntes se conservan.")};return}
            quotaPaused=false
        }
        var block=analysisBlock()
        if(block.isEmpty()&&force)block=current.lines.filter{it.final||it.edited}.takeLast(12)
        var length=0
        block=block.takeWhile{line->length+=line.text.length+30;length<=12000}.take(40)
        if(block.isEmpty())return
        if(!force&&block.sumOf{it.text.length}<350&&block.none{AnalysisEvidence.earlyQuestion(it.text)})return
        val fingerprint=block.joinToString{"${it.id}:${it.revision}"}
        if(!force&&fingerprint==lastAnalysisAttempt)return
        if(!force&&!current.autoSummary&&(!current.autoAnswers||block.none{AnalysisEvidence.hasQuestion(it.text)}))return
        lastAnalysisAttempt=fingerprint;summaryQueued=true
        val work=Work(UUID.randomUUID().toString(),null,summaryRevision,"","",summary=true,epoch=epoch,analysis=true,snapshot=block)
        state.update{it.copy(summaryError="",onlineNotice=if(current.generating)"El bloque se analizará al terminar la solicitud actual."else it.onlineNotice)}
        if(!summaries.trySend(work).isSuccess)summaryQueued=false
    }
    private fun applyAnalysis(result:AnalysisResult,snapshot:List<SpeechLine>){
        state.update{current->
            val validIds=snapshot.filter{line->current.lines.any{it.id==line.id&&it.revision==line.revision}}.map{it.id}.toSet()
            if(validIds.size!=snapshot.size)return@update current
            val answers=current.answers.toMutableList()
            if(current.autoAnswers)result.questions.forEach{q->
                val existing=answers.indexOfFirst{it.source!=null&&AnalysisEvidence.sameQuestion(it.question,q.question)}
                if(existing>=0&&!answers[existing].suggested&&q.suggested)return@forEach
                val answer=LiveAnswer(if(existing>=0)answers[existing].id else UUID.randomUUID().toString(),q.source,snapshot.first{it.id==q.source}.revision,q.question,q.answer,if(q.suggested)"Lista · sugerida para repasar · IA"else "Lista · pregunta contextualizada · IA",evidence=q.evidence,suggested=q.suggested,supportingSources=q.sources)
                if(existing>=0)answers[existing]=answer else answers.add(answer)
            }
            val organization=KnowledgeSummary.merge(current,result.organization)
            current.copy(organization=organization,answers=answers,summary=result.summary.ifBlank{current.summary},summaryError="",onlineNotice="")
        }
    }
    fun importTranscript(text:String){
        if(text.isBlank())return
        val words=text.trim().take(60000).split(Regex("\\s+"));val lines=mutableListOf<SpeechLine>();var part=StringBuilder();var id=System.nanoTime()
        words.forEach{word->if(part.length+word.length>900){lines.add(SpeechLine(id++,part.toString().trim(),true));part=StringBuilder()};part.append(word).append(' ')}
        if(part.isNotBlank())lines.add(SpeechLine(id,part.toString().trim(),true))
        state.update{it.copy(lines=it.lines+lines,status="Texto añadido a esta conversación. Puedes corregirlo antes de analizarlo.")}
        reconcileQuestions()
    }
    fun edit(id:Long,text:String,voice:String){
        val old=state.value.lines.find{it.id==id}?:return
        val line=old.copy(text=text.trim().take(2000),voice=voice.trim().ifBlank{"Voz sin identificar"},revision=old.revision+1,edited=true,final=true)
        state.update{it.copy(lines=it.lines.map{l->if(l.id==id)line else l},answers=it.answers.map{answer->if((answer.source==id||id in answer.supportingSources)&&answer.evidence.isNotBlank()&&AnalysisEvidence.literal(it.lines.map{item->if(item.id==id)line else item}.filter{item->item.id==answer.source||item.id in answer.supportingSources}.joinToString(" "){item->item.text},answer.evidence)==null)answer.copy(status="Fuente modificada · revisa esta respuesta")else answer})};signatures.remove(id);reconcileQuestions(id)
        summaryRevision++;state.update{it.copy(highlights=ConversationRules.salient(it.lines.joinToString(" "){l->l.text}),summary="")};lastSummaryText=""
    }
    fun setAnswers(value:Boolean){preferences.edit().putBoolean("answers",value).apply();state.update{it.copy(autoAnswers=value)};if(!value){stopResponse();debounce.values.forEach{it.cancel()};signatures.clear()}else reconcileQuestions()}
    fun setOnlineAnswers(value:Boolean){
        stopResponse();warming?.cancel();preferences.edit().putBoolean("online-answers",value).apply()
        state.update{it.copy(onlineAnswers=value,languageReady=false,summaryError="",status=if(value)"Respuestas online activadas · Pollinations Classic"else "Respuestas online desactivadas.")}
        viewModelScope.launch(Dispatchers.IO){modelLock.withLock{engines.unloadLanguage()}}
        refreshModels()
    }
    fun setSummary(value:Boolean){preferences.edit().putBoolean("summary",value).apply();state.update{it.copy(autoSummary=value)}}
    fun setLabels(value:Boolean){preferences.edit().putBoolean("labels",value).apply();state.update{it.copy(voiceLabels=value)}}
    fun ask(question:String){
        if(question.isBlank())return
        val normalized=ConversationRules.key(question)
        if(LessonMemory.asksSavedLibrary(question,state.value.lines,state.value.answers.any{it.status.startsWith("Lista")})){
            val filters=normalized.split(" ").filter{it.length>2&&it !in setOf("que","tengo","hay","mis","los","las","del","para","este","esta","recuerdas","recuerdo","recuerdos","pendiente","pendientes","tareas","guardado","guardados","mostrar","muestra","dime")}
            val rows=state.value.saved.filter{item->(!normalized.contains("pendiente")||item.kind=="Tarea"&&!item.done)&&filters.all{token->ConversationRules.key("${item.text} ${item.scope}").contains(token)}}
            val answer=if(rows.isEmpty())"No encontré textos guardados para esa consulta."else rows.take(20).joinToString("\n"){"${it.kind}: ${it.text}${if(it.scope.isNotBlank())" · ${it.scope}"else ""}${if(it.due.isNotBlank())" · ${it.due}"else ""}"}
            state.update{it.copy(answers=(it.answers+LiveAnswer(UUID.randomUUID().toString(),null,0,question.trim(),answer,"Consulta local de textos guardados")))}
        }else enqueue(null,0,question.trim().take(600))
    }
    fun setOnlineProvider(value:String){
        require(value in setOf("pollinations","puter"));if(state.value.onlineProvider==value)return
        stopResponse();quotaPaused=false;nextOnlineAt=0L
        preferences.edit().putString("online-provider",value).apply()
        state.update{it.copy(onlineProvider=value,summaryError="",onlineNotice="")}
    }
    fun requestSummary(){requestClassAnalysis(force=true)}
    fun researchSummary(){
        val current=state.value
        if(!foreground||!current.onlineAnswers||current.generating||current.preparing||summaryQueued)return
        if(quotaPaused){if(SystemClock.elapsedRealtime()<nextOnlineAt){state.update{it.copy(summaryError="La investigación está en pausa por el límite del servicio. Vuelve a intentar más tarde.")};return};quotaPaused=false}
        val topic=KnowledgeSummary.title(current)
        if(topic==ConversationNaming.UNKNOWN){state.update{it.copy(summaryError="Primero organiza la conversación para identificar un tema claro.")};return}
        var chars=0
        val snapshot=current.lines.filter{it.final||it.edited}.takeWhile{chars+=it.text.length+30;chars<=13000}.take(80)
        if(snapshot.isEmpty())return
        summaryQueued=true
        state.update{it.copy(summaryError="",onlineNotice="")}
        val work=Work(UUID.randomUUID().toString(),null,summaryRevision,topic,"",summary=true,epoch=epoch,snapshot=snapshot,research=true)
        if(!summaries.trySend(work).isSuccess)summaryQueued=false
    }
    fun stopResponse(){analysisDebounce?.cancel();runCatching{engines.cancel()};generation?.cancel();while(true){val w=queue.tryReceive().getOrNull()?:break;finish(w,"Respuesta detenida")};while(summaries.tryReceive().isSuccess){};preemptedSummaries.clear();summaryQueued=false;lastSummaryText="";lastAnalysisAttempt=""}
    fun setAudioSource(value:String){require(value in listOf("mic","video"));pause();preferences.edit().putString("audio-source",value).apply();state.update{it.copy(audioSource=value)}}
    fun pause(){speechRecovery?.cancel();speechRecovery=null;systemSpeech?.let{it.stop();closingSystemSpeech=it};systemSpeech=null;speechSessionToken++;val wasListening=state.value.listening;val stopped=capture;ListeningService.stopCapture();recorder?.let{runCatching{it.stop()}};stopped?.cancel();state.update{it.copy(listening=false,starting=false,audioLevel=0f)};if(wasListening&&foreground){finishJob?.cancel();finishJob=viewModelScope.launch{stopped?.join();delay(350);if(foreground&&!state.value.listening&&!state.value.starting){state.update{it.copy(organizationRevision=it.organizationRevision+1)};while(summaryQueued&&isActive)delay(100);if(foreground&&!state.value.listening&&!state.value.starting)requestSummary()}}}}
    fun suspendSession(){pause();finishJob?.cancel();stopResponse();warming?.cancel();debounce.values.forEach{it.cancel()};debounce.clear()}
    fun background(){state.update{it.copy(appVisible=false)};foreground=state.value.backgroundListening&&state.value.backgroundServiceActive;if(!foreground)suspendSession();if(rememberConversation)persistConversation();state.update{it.copy(history=conversations.list())}}
    fun resumeForeground(){foreground=true;state.update{it.copy(appVisible=true)}}
    fun setBackgroundListening(value:Boolean){
        preferences.edit().putBoolean("background-listening",value).apply();state.update{it.copy(backgroundListening=value)}
        if(!value&&!state.value.appVisible)background()
    }
    fun setFloatingControl(value:Boolean){preferences.edit().putBoolean("floating-control",value).apply();state.update{it.copy(floatingControl=value)}}
    internal fun setBackgroundServiceActive(value:Boolean){state.update{it.copy(backgroundServiceActive=value)};foreground=state.value.appVisible||(value&&state.value.backgroundListening)}
    internal fun stopFromNotification(){suspendSession();if(rememberConversation)persistConversation();state.update{it.copy(status="Escucha detenida. Conversación guardada en el historial.")}}

    private fun persistConversation(){synchronized(historyLock){if(!rememberConversation)return;state.update{it.copy(automaticTitle=ConversationNaming.automatic(it))};conversations.save(sessionId,state.value);state.update{it.copy(history=conversations.list())}}}
    fun restoreConversation(){
        if(rememberConversation||state.value.listening||state.value.starting)return
        synchronized(historyLock){sessionId=preferences.getLong("session-id",System.currentTimeMillis());preferences.edit().putLong("session-id",sessionId).apply();val saved=conversations.load(sessionId);if(saved!=null)state.update{it.copy(folderId=saved.folderId,organization=saved.organization,folders=conversations.folders(),conversationTitle=saved.conversationTitle,automaticTitle=saved.automaticTitle,conversationKey=sessionId,lines=saved.lines,answers=saved.answers,summary=saved.summary,summaryError="",highlights=ConversationRules.salient(saved.lines.joinToString(" "){l->l.text}))}else state.update{it.copy(folders=conversations.folders(),lines=emptyList(),answers=emptyList(),summary="",summaryError="",highlights=emptyList())};rememberConversation=true;state.update{it.copy(history=conversations.list())}}
        prepareSpeech()
        if(!spanishChecked){spanishChecked=true;refreshSpeechLanguages(autoSpanish=true)}
    }
    internal fun temporaryConversation(){synchronized(historyLock){rememberConversation=false};clearSession(keepHistory=true)}
    fun prepareSpeech(){if(state.value.speechEngine!="moonshine")return;if(speechWarm?.isActive==true||engines.speech!=null)return;speechWarm=viewModelScope.launch(Dispatchers.IO){try{modelLock.withLock{engines.loadSpeech()}}catch(_:Throwable){}}}
    private fun switchConversation(id:Long?,newFolder:Long?=null){
        if(conversationSwitch?.isActive==true)return
        suspendSession()
        if(rememberConversation)persistConversation()
        epoch++
        state.update{it.copy(starting=true,status="Cerrando la escucha anterior…")}
        val previousCapture=capture
        val previousSpeech=closingSystemSpeech
        conversationSwitch=viewModelScope.launch{
            try{
                previousCapture?.join()
                previousSpeech?.awaitStopped()
                closingSystemSpeech=null
                synchronized(historyLock){
                    signatures.clear()
                    if(id==null){
                        clearSession(keepHistory=true)
                        sessionId=System.currentTimeMillis()
                        preferences.edit().putLong("session-id",sessionId).apply()
                        state.update{it.copy(conversationKey=sessionId,folderId=newFolder,status="Nueva conversación. Pulsa el micrófono para escuchar.",history=conversations.list(),folders=conversations.folders())}
                    }else{
                        val saved=conversations.load(id)?:error("Conversación no disponible")
                        sessionId=id
                        preferences.edit().putLong("session-id",id).apply()
                        state.update{it.copy(folderId=saved.folderId,organization=saved.organization,folders=conversations.folders(),conversationTitle=saved.conversationTitle,automaticTitle=saved.automaticTitle,conversationKey=sessionId,lines=saved.lines,answers=saved.answers,summary=saved.summary,summaryError="",highlights=ConversationRules.salient(saved.lines.joinToString(" "){l->l.text}),organizationRevision=it.organizationRevision+1,history=conversations.list(),status="Conversación abierta. Pulsa el micrófono para continuar.")}
                    }
                }
            }catch(e:CancellationException){throw e}
            catch(e:Throwable){state.update{it.copy(status="No se pudo cambiar de conversación: ${e.message}")}}
            finally{state.update{it.copy(starting=false,listening=false)}}
        }
    }
    fun newConversation(folder:Long?=null){switchConversation(null,folder)}
    fun openConversation(id:Long){switchConversation(id)}
    fun renameConversation(title:String){state.update{it.copy(conversationTitle=title.trim().take(100))};if(rememberConversation)persistConversation()}
    fun renameConversation(id:Long,title:String){
        if(title.isBlank())return
        if(id==sessionId||id==state.value.conversationKey)renameConversation(title)
        else synchronized(historyLock){conversations.rename(id,title.trim().take(100));state.update{it.copy(history=conversations.list())}}
    }
    fun createFolder(name:String):Long?=synchronized(historyLock){
        runCatching{conversations.createFolder(name).also{state.update{s->s.copy(folders=conversations.folders())}}}.getOrElse{state.update{s->s.copy(status=it.message?:"No se pudo crear la carpeta.")};null}
    }
    fun renameFolder(id:Long,name:String){synchronized(historyLock){runCatching{conversations.renameFolder(id,name)}.onFailure{e->state.update{it.copy(status=e.message?:"No se pudo cambiar el nombre.")}};state.update{it.copy(folders=conversations.folders())}}}
    fun deleteFolder(id:Long){synchronized(historyLock){conversations.deleteFolder(id);state.update{it.copy(folderId=if(it.folderId==id)null else it.folderId,folders=conversations.folders(),history=conversations.list())}}}
    fun moveConversation(id:Long?,folder:Long?){synchronized(historyLock){
        if(id==null||id==sessionId||id==state.value.conversationKey){state.update{it.copy(folderId=folder)};if(rememberConversation)conversations.save(sessionId,state.value);conversations.move(sessionId,folder)}else conversations.move(id,folder)
        state.update{it.copy(history=conversations.list())}
    }}
    fun setSpeechLanguage(language:String){
        require(language in state.value.localSpeechModels.languages())
        pause();preferences.edit().putString("speech-language",language).apply();state.update{it.copy(speechLanguage=language)}
        refreshSpeechLanguages()
    }
    fun refreshSpeechLanguages(autoSpanish:Boolean=false){
        if(localModelsJob?.isActive==true||state.value.listening||state.value.starting)return
        localModelsJob=viewModelScope.launch{
            state.update{it.copy(localSpeechModels=it.localSpeechModels.copy(checking=true,message="Comprobando idiomas…"))}
            try{
                val report=localModels.inspect(if(autoSpanish)DEFAULT_SPEECH_LANGUAGE else state.value.speechLanguage)
                state.update{it.copy(localSpeechModels=report)}
                if(autoSpanish){
                    val spanish=report.spanishLanguage()
                    if(spanish!=null&&state.value.speechLanguage==DEFAULT_SPEECH_LANGUAGE&&spanish!=DEFAULT_SPEECH_LANGUAGE){preferences.edit().putString("speech-language",spanish).apply();state.update{it.copy(speechLanguage=spanish)}}
                    if(report.needsSpanish()&&spanish!=null)downloadLocalLanguage(spanish)
                }
            }catch(e:CancellationException){throw e}catch(e:Throwable){state.update{it.copy(localSpeechModels=it.localSpeechModels.copy(checking=false,downloading=false,message="No se pudo comprobar el servicio local."))}}
        }
    }
    fun downloadSpeechLanguage(){
        if(localModelsJob?.isActive==true||state.value.listening||state.value.starting)return
        localModelsJob=viewModelScope.launch{downloadLocalLanguage(state.value.speechLanguage)}
    }
    private suspend fun downloadLocalLanguage(language:String){
        if(state.value.localSpeechModels.hasLanguage(language))return
        state.update{it.copy(localSpeechModels=it.localSpeechModels.copy(checking=false,downloading=true,progress=null,message="Descargando ${languageName(language)}…"))}
        try{
            val message=localModels.download(language){progress->state.update{it.copy(localSpeechModels=it.localSpeechModels.copy(progress=progress))}}
            val report=localModels.inspect(state.value.speechLanguage)
            state.update{it.copy(localSpeechModels=report.copy(message=if(report.hasLanguage(language))"${languageName(language)} listo sin conexión"else message))}
        }catch(e:CancellationException){throw e}catch(e:Throwable){state.update{it.copy(localSpeechModels=it.localSpeechModels.copy(downloading=false,progress=null,message="No se pudo descargar el idioma. Reintenta con conexión."))}}
    }
    fun deleteConversation(id:Long){if(id==sessionId)clearSession()else synchronized(historyLock){conversations.delete(id)};state.update{it.copy(history=conversations.list())}}

    fun clearSession(keepHistory:Boolean=false){suspendSession();epoch++;debounce.values.forEach{it.cancel()};debounce.clear();signatures.clear();state.update{it.copy(conversationTitle="",automaticTitle="",folderId=null,organization=ClassOrganization(),onlineNotice="",conversationKey=System.nanoTime(),organizationRevision=0,lines=emptyList(),answers=emptyList(),summary="",summaryError="",highlights=emptyList(),status="Sesión borrada. Los textos guardados se conservan.")};if(rememberConversation&&!keepHistory)synchronized(historyLock){conversations.delete(sessionId)};state.update{it.copy(history=conversations.list())}}
    fun setSpeechEngine(value:String){require(value in listOf("moonshine","system","soniqo"));pause();preferences.edit().putString("speech-engine",value).apply();state.update{it.copy(speechEngine=value)};if(value=="moonshine")prepareSpeech()}
    fun setTranscriptionOnly(value:Boolean){preferences.edit().putBoolean("transcription-only",value).apply();state.update{it.copy(transcriptionOnly=value)};if(value){stopResponse();debounce.values.forEach{it.cancel()};debounce.clear()}else{signatures.clear();reconcileQuestions()}}
    internal fun recoverSystemSpeech(message:String,token:Long=speechSessionToken){
        if(token!=speechSessionToken||!foreground)return
        val old=systemSpeech
        old?.stop()
        systemSpeech=null
        closingSystemSpeech=old
        speechRecoveries++
        if(speechRecoveries>3){state.update{it.copy(starting=false,listening=false,audioLevel=0f,status="$message. No se pudo recuperar tras tres intentos. Revisa el micrófono o selecciona otro motor.")};return}
        state.update{it.copy(starting=true,listening=false,audioLevel=0f,status="$message. Reconectando reconocimiento local ($speechRecoveries/3)…")}
        speechRecovery=viewModelScope.launch{
            old?.awaitStopped()
            delay(500L*speechRecoveries)
            if(token==speechSessionToken && foreground && conversationSwitch?.isActive!=true){
                closingSystemSpeech=null
                state.update{it.copy(starting=false)}
                startListening(null,null,true)
            }
        }
    }
    @Suppress("MissingPermission")
    fun listen(projection:android.media.projection.MediaProjection?=null){startListening(projection,null)}
    internal fun listenFromAudio(source:android.os.ParcelFileDescriptor){startListening(null,source)}
    @Suppress("MissingPermission")
    private fun startListening(projection:android.media.projection.MediaProjection?,audioSource:android.os.ParcelFileDescriptor?,recovering:Boolean=false){
        if(conversationSwitch?.isActive==true||state.value.starting||state.value.listening||capture?.isActive==true||importJob?.isActive==true)return
        if(projection==null&&state.value.speechEngine=="system"){
            if(state.value.localSpeechModels.downloading||state.value.localSpeechModels.checking){state.update{it.copy(status="Espera a que termine la preparación del idioma.")};return}
            if(!recovering)speechRecoveries=0
            state.update{it.copy(starting=true,status="Preparando reconocimiento local de Android…")}
            val captureEpoch=epoch
            val sessionToken=++speechSessionToken
            capture=viewModelScope.launch{try{closingSystemSpeech?.awaitStopped();closingSystemSpeech=null;if(state.value.transcriptionOnly){stopResponse();warming?.cancelAndJoin();generation?.join();modelLock.withLock{engines.unloadLanguage();state.update{it.copy(languageReady=false)}}};speechWarm?.join();modelLock.withLock{engines.unloadSpeech()}
                systemSpeech=SystemSpeech(context,{id,text,final->if(epoch==captureEpoch && sessionToken==speechSessionToken)observe(id,text,final,captureEpoch)},{if(sessionToken==speechSessionToken)state.update{it.copy(starting=false,listening=true,status="Escuchando · Android local continuo")}},{message->if(sessionToken==speechSessionToken)state.update{it.copy(starting=false,listening=false,audioLevel=0f,status=message)}},{message->recoverSystemSpeech(message,sessionToken)},{level->if(sessionToken==speechSessionToken)state.update{it.copy(audioLevel=level)}})
                currentCoroutineContext().ensureActive();systemSpeech?.start(audioSource,state.value.speechLanguage)
            }catch(e:CancellationException){throw e}catch(e:Throwable){state.update{it.copy(starting=false,listening=false,status="No se pudo preparar el reconocimiento local: ${e.message}")}}};return
        }
        state.update{it.copy(starting=true,status="Preparando modelo de voz; aún no se está grabando…")}
        val previousCapture=capture
        capture=viewModelScope.launch(Dispatchers.IO){
            previousCapture?.join();currentCoroutineContext().ensureActive();state.update{it.copy(starting=true)}
            var input:AudioRecord?=null
            var soniqo:SoniqoSpeech?=null
            var listener:java.util.function.Consumer<TranscriptEvent>?=null
            var started=false
            var noise:android.media.audiofx.NoiseSuppressor?=null
            try{
                if(state.value.transcriptionOnly){stopResponse();warming?.cancelAndJoin();generation?.join();modelLock.withLock{engines.unloadLanguage();state.update{it.copy(languageReady=false)}}}
                val useSoniqo=state.value.speechEngine=="soniqo"
                val captureEpoch=epoch
                speechWarm?.join()
                if(useSoniqo){
                    modelLock.withLock{engines.unloadSpeech()}
                    soniqo=SoniqoSpeech(File(context.filesDir,"soniqo"),this,{id,text,final->if(epoch==captureEpoch)observe(id,text,final,captureEpoch)},{message->state.update{it.copy(status="Error del motor Soniqo: $message")}})
                }else{
                    if(engines.speech==null)modelLock.withLock{engines.loadSpeech()}
                    val streamBase=++streamSerial*1000000L
                    val visitor=object:TranscriptEventListener(){
                        fun update(line:TranscriptLine,final:Boolean){if(epoch==captureEpoch)observe(streamBase+line.id,line.text,final,captureEpoch);line.audioData=null}
                        override fun onLineTextChanged(e:TranscriptEvent.LineTextChanged){update(e.line,false)}
                        override fun onLineCompleted(e:TranscriptEvent.LineCompleted){update(e.line,true)}
                    }
                    listener=java.util.function.Consumer{it.accept(visitor)};engines.speech?.addListener(listener)
                }
                currentCoroutineContext().ensureActive()
                val min=AudioRecord.getMinBufferSize(16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);check(min>0)
                input=if(projection==null)AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,maxOf(min,6400))else{
                    val config=AudioPlaybackCaptureConfiguration.Builder(projection).addMatchingUsage(AudioAttributes.USAGE_MEDIA).addMatchingUsage(AudioAttributes.USAGE_GAME).addMatchingUsage(AudioAttributes.USAGE_UNKNOWN).excludeUid(android.os.Process.myUid()).build()
                    AudioRecord.Builder().setAudioPlaybackCaptureConfig(config).setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(16000).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build()).setBufferSizeInBytes(maxOf(min,6400)).build()
                };check(input.state==AudioRecord.STATE_INITIALIZED){"No se pudo abrir el micrófono."}
                noise=runCatching{if(projection==null&&android.media.audiofx.NoiseSuppressor.isAvailable())android.media.audiofx.NoiseSuppressor.create(input.audioSessionId)?.apply{enabled=true}else null}.getOrNull()
                recorder=input;if(soniqo!=null)soniqo.start()else engines.speech?.start();started=true;input.startRecording();state.update{it.copy(listening=true,starting=false,status=if(projection==null)"Escuchando micrófono · audio solo en memoria"else "Audio directo del video · sin micrófono ni imágenes")}

                var emptyPlaybackFrames=0
                val shorts=ShortArray(3200)
                try{while(isActive){val n=input.read(shorts,0,shorts.size);check(n>0){"Captura detenida."};val audio=FloatArray(n){shorts[it]/32768f};val rms=kotlin.math.sqrt(audio.sumOf{(it*it).toDouble()}/n).toFloat();state.update{it.copy(audioLevel=(rms*5).coerceIn(0f,1f))};if(projection!=null){emptyPlaybackFrames=if(rms<.00001f)emptyPlaybackFrames+n else 0;if(emptyPlaybackFrames>=16000*12){audio.fill(0f);error("No llega audio del video. Comprueba que el video esté reproduciéndose; algunas aplicaciones bloquean la captura. No se cambiará al micrófono.")}};try{if(soniqo!=null)soniqo.addAudio(audio)else engines.speech?.addAudio(audio,16000)}finally{audio.fill(0f);shorts.fill(0)}}}finally{shorts.fill(0)}
            }catch(e:CancellationException){}catch(e:Throwable){if(isActive)state.update{it.copy(status="No se pudo escuchar: ${e.message}")}}
            finally{runCatching{soniqo?.close()};runCatching{input?.stop()};noise?.release();input?.release();recorder=null;if(projection!=null)withContext(NonCancellable+Dispatchers.Main.immediate){ListeningService.stopCapture()};if(started)runCatching{engines.speech?.stop()};listener?.let{runCatching{engines.speech?.removeListener(it)}};state.update{it.copy(listening=false,starting=false,audioLevel=0f,status=if(it.status.startsWith("No se pudo"))it.status else "Escucha pausada.")}}
        }
    }
    fun importSoniqo(uri:Uri){
        if(state.value.preparing)return
        suspendSession();state.update{it.copy(preparing=true,status="Importando modelos de Soniqo…")}
        importJob=viewModelScope.launch(Dispatchers.IO){try{capture?.join();ModelImport.soniqo(context,uri);preferences.edit().putString("speech-engine","soniqo").apply();state.update{it.copy(speechEngine="soniqo",status="Soniqo listo. Pulsa el micrófono.")}}catch(e:Throwable){state.update{it.copy(status="No se pudo importar Soniqo: ${e.message}")}}finally{state.update{it.copy(preparing=false)};refreshModels()}}
    }
    fun importModel(uri:Uri,speech:Boolean){
        if(state.value.preparing)return
        suspendSession();state.update{it.copy(preparing=true,status="Importando modelo…")}
        importJob=viewModelScope.launch(Dispatchers.IO){try{capture?.join();generation?.join();modelLock.withLock{engines.close();state.update{it.copy(languageReady=false)};if(speech)ModelImport.speech(context,uri)else ModelImport.language(context,uri)};state.update{it.copy(status="Modelo importado.")}}catch(e:Throwable){state.update{it.copy(status="No se pudo importar: ${e.message}")}}finally{state.update{it.copy(preparing=false)};refreshModels()}}
    }
    private fun refreshModels(){state.update{it.copy(models="Soniqo: ${if(File(context.filesDir,"soniqo/parakeet-encoder-int8.onnx").exists())"listo"else "importar ZIP"} · Voz: ${if(File(context.filesDir,"speech/streaming_config.json").exists())"lista" else "importar ZIP"} · Respuestas: ${if(it.onlineAnswers)"online · Pollinations Classic"else "desactivadas"}")}}
    private fun reloadSaved(){state.update{it.copy(saved=store.list())}}
    fun save(id:Long?,kind:String,text:String,scope:String,due:String)=saveWithSource(id,kind,text,scope,due,"Texto escrito por usuario")
    fun saveWithSource(id:Long?,kind:String,text:String,scope:String,due:String,source:String){store.saveWithSource(id,kind,text,scope,due,source);reloadSaved()}
    fun delete(id:Long){store.delete(id);reloadSaved()}
    fun done(id:Long,value:Boolean){store.done(id,value);reloadSaved()}
    fun releaseModels(after:()->Unit){suspendSession();viewModelScope.launch{capture?.join();generation?.join();withContext(Dispatchers.IO){modelLock.withLock{engines.close();state.update{it.copy(languageReady=false)}}};after()}}
    override fun onCleared(){localModelsJob?.cancel();suspendSession();queue.close();summaries.close();val c=capture;val g=generation;CoroutineScope(Dispatchers.IO).launch{c?.join();g?.join();modelLock.withLock{engines.close()};store.close();synchronized(historyLock){conversations.close()}};super.onCleared()}
}
