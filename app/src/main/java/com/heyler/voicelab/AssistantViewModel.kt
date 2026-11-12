package com.heyler.voicelab

import android.app.Application
import android.media.*
import android.os.SystemClock
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
data class AssistantState(val folderId:Long?=null,val folders:List<ConversationFolder> = emptyList(),val legacyDataAvailable:Boolean=false,val organization:ClassOrganization=ClassOrganization(),val onlineNotice:String="",val cloudStatus:String="",val summaryPending:Boolean=false,val summaryStatus:String="",val analysisRunning:Boolean=false,val researchRunning:Boolean=false,val backgroundListening:Boolean=true,val floatingControl:Boolean=false,val backgroundServiceActive:Boolean=false,val appVisible:Boolean=true,val onlineAnswers:Boolean=true,val answerEngine:String="auto",val localModelAvailable:Boolean=false,val speechEngine:String="system",val transcriptionOnly:Boolean=false,val conversationTitle:String="",val automaticTitle:String="",val conversationKey:Long=0,val listening:Boolean=false,val organizationRevision:Int=0,val audioSource:String="mic",val starting:Boolean=false,val history:List<ConversationEntry> = emptyList(),val preparing:Boolean=false,val generating:Boolean=false,val status:String="Listo. El micrófono está apagado.",val models:String="",val lines:List<SpeechLine> = emptyList(),val answers:List<LiveAnswer> = emptyList(),val summary:String="",val summaryError:String="",val highlights:List<String> = emptyList(),val autoAnswers:Boolean=true,val autoSummary:Boolean=false,val voiceLabels:Boolean=false,val saved:List<SavedItem> = emptyList(),val audioLevel:Float=0f,val languageReady:Boolean=false,val speechLanguage:String=DEFAULT_SPEECH_LANGUAGE,val localSpeechModels:LocalSpeechModelState=LocalSpeechModelState())
private data class Work(val id:String,val source:Long?,val revision:Int,val question:String,val context:String,val summary:Boolean=false,val epoch:Long,val queuedNanos:Long=SystemClock.elapsedRealtimeNanos(),val analysis:Boolean=false,val snapshot:List<SpeechLine> = emptyList(),val research:Boolean=false,val engine:String="pollinations",val token:Long=0)

class AssistantViewModel(app:Application):AndroidViewModel(app){
    private val context=app.applicationContext
    private val engines=LocalEngines(context)
    internal var online=OnlineAssistant()
    internal var remote:OnlineAssistant?=null
    private val horde by lazy{HordeAssistant(status={message->if(state.value.answerEngine in setOf("auto","horde"))state.update{it.copy(onlineNotice=message)}})}
    private val localOrganizer by lazy{LocalAssistant(LocalTextTransport{p,i,n,out->(local as LocalAssistant).transport.generate(p,i,n,out)},answerQuestions=false)}
    private var store=SavedStore(context,ownerId="legacy")
    private var conversations=ConversationStore(context,ownerId="legacy")
    @Volatile private var activeOwnerId="legacy"
    @Volatile private var rememberConversation=false
    private val cloudHistory by lazy{CloudHistorySync()}
    @Volatile private var cloudReadyOwner:String?=null
    @Volatile private var lastCloudSyncAt=0L
    private val pendingCloudChanges=java.util.concurrent.ConcurrentHashMap<Long,Boolean>()
    private val pendingSavedChanges=java.util.concurrent.ConcurrentHashMap<String,Boolean>()
    private var cloudMergeJob:Job?=null
    private var cloudWriteJob:Job?=null
    @Volatile private var sessionId=System.currentTimeMillis()
    private val historyLock=Any()
    private var systemSpeech:SystemSpeech?=null
    private var closingSystemSpeech:SystemSpeech?=null
    private var conversationSwitch:Job?=null
    private var speechSessionToken=0L
    private var speechRecovery:Job?=null
    private var speechRecoveries=0
    private val preferences=context.getSharedPreferences("assistant-options",0)
    val state=MutableStateFlow(AssistantState(backgroundListening=preferences.getBoolean("background-listening",true),floatingControl=preferences.getBoolean("floating-control",false),onlineAnswers=preferences.getBoolean("online-answers",true),speechLanguage=preferences.getString("speech-language",DEFAULT_SPEECH_LANGUAGE)?:DEFAULT_SPEECH_LANGUAGE,audioSource=preferences.getString("audio-source","mic")?:"mic",autoAnswers=preferences.getBoolean("answers",true),autoSummary=false,voiceLabels=preferences.getBoolean("labels",false)))
    private val localModels=LocalSpeechModels(context)
    private var localModelsJob:Job?=null
    private var spanishChecked=false
    private val modelLock=Mutex()
    internal var local:OnlineAssistant=LocalAssistant(LocalTextTransport{prompt,instruction,tokens,onText->
        modelLock.withLock{
            currentCoroutineContext().ensureActive()
            state.update{it.copy(status=if(it.languageReady)"Respondiendo con Qwen3 local…"else "Cargando Qwen3 local…")}
            engines.loadLanguage(LocalAssistant.CONTEXT_TOKENS)
            currentCoroutineContext().ensureActive()
            state.update{it.copy(languageReady=true)}
            engines.generate(prompt,tokens,instruction,onText)
        }
    })

    private val pool=AssistantPool()
    private val generationLock=Any()
    private val running=java.util.concurrent.ConcurrentHashMap<String,Pair<Work,Job>>()
    private val assigned=java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    @Volatile private var responseToken=0L
    @Volatile private var completedSummary=""
    private val queue=Channel<Work>(24)
    private val summaries=Channel<Work>(Channel.CONFLATED)
    private var capture:Job?=null
    private var finishJob:Job?=null
    private var generation:Job?=null
    private var warming:Job?=null
    @Volatile private var recorder:AudioRecord?=null
    @Volatile private var epoch=0L
    @Volatile private var foreground=true
    private val debounce=mutableMapOf<Long,Job>()
    private val signatures=java.util.concurrent.ConcurrentHashMap<Long,List<String>>()
    internal var requestSpacingMs=OnlineBudget.INTERVAL
    @Volatile private var lastAnalysisAttempt=""
    private var analysisDebounce:Job?=null
    @Volatile private var summaryQueued=false
    @Volatile private var summaryOwner:String?=null
    private var lastSummaryText=""
    private var streamSerial=System.currentTimeMillis()
    @Volatile private var summaryRevision=0
    init{
        preferences.edit().remove("online-provider").putString("answer-engine","auto").putString("speech-engine","system").putBoolean("transcription-only",false).putBoolean("summary",false).apply()
        refreshModels();reloadSaved()
        viewModelScope.launch(Dispatchers.IO){state.map{listOf(it.lines,it.answers,it.summary,it.organization)}.distinctUntilChanged().collect{delay(300);if(rememberConversation)persistConversation()}}
        viewModelScope.launch(Dispatchers.IO){while(isActive){
            while(running.size>=12&&isActive)delay(250)
            val work=summaries.tryReceive().getOrNull()?:queue.tryReceive().getOrNull()?:select<Work?>{queue.onReceiveCatching{it.getOrNull()};summaries.onReceiveCatching{it.getOrNull()}}?:break
            if(!valid(work)){finish(work,"Descartada");clearSummary(work);continue}
            synchronized(generationLock){
                if(generation?.isActive!=true)generation=SupervisorJob(viewModelScope.coroutineContext[Job])
                val job=viewModelScope.launch(Dispatchers.IO+checkNotNull(generation),start=CoroutineStart.LAZY){
                    try{withTimeout(if(work.summary)600000L else 120000L){runWork(work)}}
                    catch(e:TimeoutCancellationException){finish(work,"No se completó: los motores siguen ocupados. Reintenta más tarde.");if(work.summary)state.update{it.copy(summaryError="Los motores siguen ocupados. Se conserva el resumen anterior.")}}
                    finally{running.remove(work.id);assigned.remove(work.id);clearSummary(work);refreshGenerationState()}
                }
                running[work.id]=work to job;job.start()
            }
        }}
    }
    private fun clearSummary(work:Work){synchronized(generationLock){if(summaryOwner==work.id){
        summaryQueued=false;summaryOwner=null
        pool.releaseReservation(work.id)
        state.update{it.copy(summaryPending=false,summaryStatus="")}
    }}}
    private fun refreshGenerationState(){
        state.update{it.copy(generating=assigned.isNotEmpty(),analysisRunning=running.values.any{(w,_)->w.summary&&w.id in assigned},researchRunning=running.values.any{(w,_)->w.research&&w.id in assigned})}
    }
    private fun model(engine:String,summary:Boolean):OnlineAssistant=when(engine){
        "horde"->remote?:horde
        "qwen3"->if(summary&&local is LocalAssistant)localOrganizer else local
        else->online
    }
    private fun summaryKey(lines:List<SpeechLine>)=epoch.toString()+":"+lines.joinToString{it.id.toString()+":"+it.revision}
    private fun chunks(lines:List<SpeechLine>,limit:Int):List<List<SpeechLine>>{
        val result=mutableListOf<List<SpeechLine>>();var block=mutableListOf<SpeechLine>();var chars=0
        lines.forEach{line->if(block.isNotEmpty()&&(chars+line.text.length+30>limit||block.size>=40)){result.add(block);block=mutableListOf();chars=0};block.add(line);chars+=line.text.length+30}
        if(block.isNotEmpty())result.add(block)
        return result
    }
    private suspend fun runWork(work:Work){
        val attempted=mutableSetOf<String>();var received=false;var failure:Throwable?=null
        val order=pool.order(work.summary,work.engine,state.value.localModelAvailable)
        try{
            while(valid(work)&&attempted.size<order.size){
                finish(work,"En espera de un motor disponible…")
                val lease=pool.acquire(order.filter{it !in attempted},onWait={wait->
                    if(work.summary&&summaryOwner==work.id){val name="Pollinations";state.update{it.copy(summaryStatus=if(wait>0)"$name: espera mínima ${(wait+999)/1000} s…"else "Esperando a que termine la consulta de $name…")}}
                },owner=if(work.summary)work.id else null){valid(work)}?:return
                val accepted=synchronized(generationLock){if(valid(work)){assigned.add(work.id);true}else false}
                if(!accepted){pool.release(lease);return}
                attempted.add(lease.engine);refreshGenerationState()
                val provider=model(lease.engine,work.summary)
                try{
                    if(!valid(work))return
                    if(lease.engine=="pollinations"&&!work.summary)pool.throttle(lease.engine,requestSpacingMs)
                    val engineName=when(lease.engine){"horde"->"AI Horde";"qwen3"->"Qwen3 local";else->"Pollinations"}
                    finish(work,"Preparando respuesta · $engineName")
                    if(work.summary)state.update{it.copy(summaryStatus="Generando resumen · $engineName…")}
                    if(work.research){
                        val map=provider.researchedMap(work.question,work.snapshot,emptyList(),state.value)
                        if(valid(work)&&work.snapshot.all{source->state.value.lines.any{it.id==source.id&&it.revision==source.revision}})state.update{it.copy(organization=it.organization.copy(map=map),summaryError="",onlineNotice="")}
                    }else if(work.analysis){
                        // Rebuild the selected snapshot. A previous expanded map must not hide a new summary.
                        var accumulated=state.value.copy(summary="",organization=ClassOrganization())
                        val blocks=chunks(work.snapshot,if(lease.engine=="qwen3")LocalAssistant.BLOCK_CHARS else 12000)
                        blocks.forEachIndexed{index,block->
                            currentCoroutineContext().ensureActive();if(!valid(work))return
                            state.update{it.copy(summaryStatus="Generando resumen · $engineName${if(blocks.size>1)" · parte ${index+1}/${blocks.size}"else ""}…")}
                            val result=provider.analyze(block,accumulated)
                            accumulated=accumulated.copy(organization=KnowledgeSummary.merge(accumulated,result.organization),summary=result.summary.ifBlank{accumulated.summary})
                        }
                        if(valid(work)&&work.snapshot.all{source->state.value.lines.any{it.id==source.id&&it.revision==source.revision}}){
                            require(SummaryQuality.usable(accumulated.summary)){"El motor no devolvió un resumen válido. Se conservó el anterior; vuelve a intentar."}
                            applyAnalysis(AnalysisResult(accumulated.organization,accumulated.summary,emptyList()),work.snapshot)
                            completedSummary=summaryKey(work.snapshot)
                        }else if(work.epoch==epoch)state.update{it.copy(summaryError="El texto se editó mientras se resumía. Pulsa Generar resumen para incluir la corrección.")}
                    }else if(work.summary){
                        val output=StringBuilder();provider.generate(work.context,summary=true){output.append(it)}
                        if(valid(work))state.update{it.copy(summary=output.toString(),summaryError="")}
                    }else{
                        val dialogue=LessonMemory.dialogueBefore(state.value.answers,work.id).take(400)
                        val prompt="PREGUNTA ACTUAL:\n${work.question}\n\nCONTEXTO EXTRAÍDO DE LA CONVERSACIÓN (transcripción automática; puede contener errores):\n${work.context.take(4300)}\n\nINTERCAMBIO PREVIO:\n$dialogue\n\nResponde primero con lo que consta en el contexto. Si añades conocimiento general para completar, sepáralo de lo dicho en la reunión. No repitas la pregunta ni inventes. Si el audio no permite entenderla, pide una aclaración breve."
                        provider.generate(prompt){delta->if(valid(work)){
                            received=true
                            state.update{it.copy(answers=it.answers.map{a->if(a.id==work.id)a.copy(text=a.text+delta,status="Respondiendo · $engineName",firstTextMs=a.firstTextMs?:((SystemClock.elapsedRealtimeNanos()-work.queuedNanos)/1e6))else a})}
                        }}
                        finish(work,"Lista · ${if(lease.engine=="qwen3")"local"else "online"} · $engineName · sin verificación externa")
                    }
                    return
                }catch(e:CancellationException){throw e}
                catch(e:Throwable){
                    failure=e
                    pool.cooldown(lease.engine,when(e){is OnlineQuotaException->e.waitMillis;is ProviderAccessException->300000L;else->15000L})
                    // If the first online provider fails before returning text, try
                    // the next free provider. Never splice two partial answers.
                    if(received||work.engine==LocalAssistant.ID)break
                    finish(work,"${lease.engine} no disponible · buscando otro motor…")
                }finally{pool.release(lease);assigned.remove(work.id);refreshGenerationState()}
            }
            val message=SummaryIssues.display(if(failure is org.json.JSONException)SummaryIssues.INVALID_FORMAT else failure?.message?:"No hay un motor disponible para esta solicitud.")
            finish(work,if(received&&failure is OnlineOutputLimitException)"Respuesta parcial · límite de generación"else "No se completó: $message")
            if(work.summary)state.update{it.copy(summaryError=message)}else state.update{it.copy(onlineNotice=message)}
        }catch(e:CancellationException){finish(work,"Respuesta detenida");throw e}
    }
    private fun queueAudioQuestions(lines:List<SpeechLine>,completedOnly:Boolean=false){
        AnalysisEvidence.audioQuestions(lines,completedOnly).forEach{candidate->
            val line=candidate.source
            if(state.value.answers.any{AnalysisEvidence.sameQuestion(it.question,candidate.question)})return@forEach
            val key=ConversationRules.key(candidate.question)
            signatures[line.id]=(signatures[line.id].orEmpty()+key).distinct()
            enqueue(line.id,line.revision,candidate.question)
            state.update{it.copy(answers=it.answers.map{a->
                if(a.source==line.id&&AnalysisEvidence.sameQuestion(a.question,candidate.question))a.copy(evidence=candidate.evidence,supportingSources=candidate.supportingSources)
                else a
            })}
        }
    }
    private fun valid(w:Work):Boolean=state.value.onlineAnswers && (w.engine==state.value.answerEngine||w.id in assigned) && w.token==responseToken && foreground && !state.value.preparing && w.epoch==epoch && ((w.summary&&w.revision==summaryRevision) || (!w.summary&&w.source==null) || (!w.summary&&state.value.autoAnswers&&state.value.lines.any{it.id==w.source}&&signatures[w.source]?.contains(ConversationRules.key(w.question))==true))
    private fun finish(w:Work,status:String){if(w.summary&&status=="Descartada"){clearSummary(w);lastSummaryText=""};if(!w.summary)state.update{it.copy(answers=it.answers.map{a->if(a.id==w.id)a.copy(status=status)else a})}}
    private fun questionContext(question:String,source:Long?=null):String {
        val current=state.value
        val quoted=source?.let{id->current.lines.firstOrNull{it.id==id}?.text}.orEmpty()
        return "Fragmento de la pregunta:\n"+quoted.take(1400)+"\n"+
            LessonMemory.questionContext(current.lines,question,source).take(3000)
    }
    private fun enqueue(source:Long?,revision:Int,question:String,summary:Boolean=false){
        if(!state.value.onlineAnswers){
            if(!summary)state.update{it.copy(answers=it.answers+LiveAnswer(UUID.randomUUID().toString(),source,revision,question,status="Respuestas del asistente desactivadas. Actívalas en Ajustes."))}
            return
        }

        if(state.value.transcriptionOnly&&(state.value.listening||state.value.starting))return
        val work=Work(UUID.randomUUID().toString(),source,revision,question,if(summary)LessonMemory.summaryContext(state.value.lines)else questionContext(question,source),summary,epoch,engine=state.value.answerEngine,token=responseToken)
        if(!summary)state.update{it.copy(answers=(it.answers+LiveAnswer(work.id,source,revision,question)))}
        if(summary){summaryOwner=work.id;summaryQueued=true;pool.reserve("pollinations",work.id);state.update{it.copy(summaryPending=true,summaryError="")}}

        if(!(if(summary)summaries.trySend(work)else queue.trySend(work)).isSuccess){finish(work,"Omitida: hay preguntas pendientes");if(summary)clearSummary(work)}
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
            if(line.final){debounce.remove(id)?.cancel()}else if(debounce[id]?.isActive!=true){
                debounce[id]=viewModelScope.launch{
                    delay(800)
                    if(expectedEpoch==epoch&&foreground&&state.value.onlineAnswers&&state.value.autoAnswers&&!(state.value.transcriptionOnly&&(state.value.listening||state.value.starting))){
                        state.value.lines.firstOrNull{it.id==id&&!it.final}?.let{queueAudioQuestions(listOf(it),completedOnly=true)}
                    }
                    debounce.remove(id)
                }
            }
            reconcileQuestions()
        }
    }
    private fun reconcileQuestions(around:Long?=null){
        if(!foreground||(state.value.transcriptionOnly&&(state.value.listening||state.value.starting))||!state.value.onlineAnswers||!state.value.autoAnswers)return
        val lines=state.value.lines
        val window=if(around==null)lines.takeLast(3)else lines.indexOfFirst{it.id==around}.let{index->
            if(index<0)emptyList()else lines.subList((index-1).coerceAtLeast(0),(index+2).coerceAtMost(lines.size))
        }
        queueAudioQuestions(window)
    }
    private fun requestClassAnalysis(force:Boolean=false){
        if(!force)return
        synchronized(generationLock){
            val current=state.value
            fun issue(message:String){state.update{it.copy(summaryError=message)}}
            if(summaryQueued)return
            if(!current.onlineAnswers){issue("Activa el asistente en Ajustes para generar el resumen.");return}
            if(!foreground){issue("Vuelve a abrir la conversación para solicitar el resumen.");return}
            if(current.preparing){issue("Espera a que termine de prepararse el motor y vuelve a pulsar Generar resumen.");return}
            val block=current.lines.filter{it.final||it.edited}.filter{it.text.isNotBlank()}.toList()
            if(!SummaryReadiness.hasEnoughText(block.joinToString(" "){it.text})){
                issue(if(current.lines.any{!it.final&&!it.edited})"Todavía falta texto confirmado. Espera a que termine el fragmento y pulsa Generar resumen."
                    else "Todavía no hay información suficiente. Añade al menos 30 palabras sobre el tema y pulsa Generar resumen.")
                return
            }
            if(completedSummary==summaryKey(block)){state.update{it.copy(summaryError="El resumen ya incluye este texto. Añade información antes de generar otro.")};return}
            summaryQueued=true
            val work=Work(UUID.randomUUID().toString(),null,summaryRevision,"","",summary=true,epoch=epoch,analysis=true,snapshot=block,engine=current.answerEngine,token=responseToken)
            summaryOwner=work.id
            pool.reserve("pollinations",work.id)
            state.update{it.copy(summaryPending=true,summaryStatus="Resumen solicitado · Pollinations…",summaryError="",onlineNotice="")}
            if(!summaries.trySend(work).isSuccess){clearSummary(work);issue("No se pudo iniciar el resumen. Vuelve a intentar.")}
        }
    }
    private fun applyAnalysis(result:AnalysisResult,snapshot:List<SpeechLine>){
        state.update{current->
            val validIds=snapshot.filter{line->current.lines.any{it.id==line.id&&it.revision==line.revision}}.map{it.id}.toSet()
            if(validIds.size!=snapshot.size)return@update current
            val organization=result.organization.copy(map=null)
            current.copy(organization=organization,summary=result.summary.ifBlank{current.summary},summaryError="",onlineNotice="")
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
    fun setAnswers(value:Boolean){preferences.edit().putBoolean("answers",value).apply();state.update{it.copy(autoAnswers=value)};if(!value){running.values.filter{(w,_)->!w.summary&&w.source!=null}.forEach{it.second.cancel()};signatures.clear()}else reconcileQuestions()}
    fun setAnswerEngine(value:String){
        synchronized(generationLock){
        require(value in setOf("pollinations",LocalAssistant.ID,"horde","auto"))
        if(state.value.answerEngine==value)return
        preferences.edit().putString("answer-engine",value).apply()
        state.update{it.copy(answerEngine=value,languageReady=false,summaryError="",onlineNotice="",status=when(value){"horde","auto"->"Motores automáticos: AI Horde, Pollinations y Qwen local.";LocalAssistant.ID->"Qwen local seleccionado.";else->"Pollinations seleccionado."})}
        }
        if(value=="pollinations")viewModelScope.launch(Dispatchers.IO){modelLock.withLock{if(state.value.answerEngine=="pollinations")engines.unloadLanguage()}}
        refreshModels()
    }
    fun setOnlineAnswers(value:Boolean){
        stopResponse();warming?.cancel();preferences.edit().putBoolean("online-answers",value).apply()
        state.update{it.copy(onlineAnswers=value,languageReady=false,summaryError="",status=if(value)"Asistente activado."else "Respuestas del asistente desactivadas.")}
        if(!value)viewModelScope.launch(Dispatchers.IO){modelLock.withLock{if(!state.value.onlineAnswers)engines.unloadLanguage()}}
        refreshModels()
    }
    fun setSummary(value:Boolean){preferences.edit().putBoolean("summary",false).apply();state.update{it.copy(autoSummary=false)}}
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
    fun requestSummary(){requestClassAnalysis(force=true)}
    fun researchSummary(){
        val current=state.value
        if(!foreground||!current.onlineAnswers||current.preparing||summaryQueued)return

        val topic=KnowledgeSummary.title(current)
        if(topic==ConversationNaming.UNKNOWN){state.update{it.copy(summaryError="Primero organiza la conversación para identificar un tema claro.")};return}
        var chars=0
        val snapshot=current.lines.filter{it.final||it.edited}.takeWhile{chars+=it.text.length+30;chars<=(if(current.answerEngine!="pollinations")LocalAssistant.BLOCK_CHARS else 13000)}.take(80)
        if(snapshot.isEmpty())return
        summaryQueued=true
        state.update{it.copy(summaryPending=true,summaryStatus="Organizando mapa…",summaryError="",onlineNotice="")}
        val work=Work(UUID.randomUUID().toString(),null,summaryRevision,topic,"",summary=true,epoch=epoch,snapshot=snapshot,research=true,engine=current.answerEngine,token=responseToken)
        summaryOwner=work.id
        pool.reserve("pollinations",work.id)
        if(!summaries.trySend(work).isSuccess)clearSummary(work)
    }
    fun stopResponse(){
        responseToken++
        synchronized(generationLock){runCatching{engines.cancel()};generation?.cancel()}
        while(true){val w=queue.tryReceive().getOrNull()?:break;finish(w,"Respuesta detenida")}
        while(summaries.tryReceive().isSuccess){}
        summaryOwner?.let{pool.releaseReservation(it)}
        summaryQueued=false;summaryOwner=null;lastSummaryText="";lastAnalysisAttempt=""
        state.update{it.copy(summaryPending=false,summaryStatus="")}
    }
    fun setAudioSource(value:String){require(value in listOf("mic","video"));pause();preferences.edit().putString("audio-source",value).apply();state.update{it.copy(audioSource=value)}}
    fun pause(){
        speechRecovery?.cancel();speechRecovery=null;systemSpeech?.let{it.stop();closingSystemSpeech=it};systemSpeech=null;speechSessionToken++
        ListeningService.stopCapture();recorder?.let{runCatching{it.stop()}};capture?.cancel()
        state.update{it.copy(listening=false,starting=false,audioLevel=0f,organizationRevision=it.organizationRevision+1)}
    }
    fun suspendSession(){pause();finishJob?.cancel();stopResponse();warming?.cancel();debounce.values.forEach{it.cancel()};debounce.clear()}
    fun background(){state.update{it.copy(appVisible=false)};foreground=state.value.backgroundListening&&state.value.backgroundServiceActive;if(!foreground)suspendSession();if(rememberConversation)persistConversation();state.update{it.copy(history=conversations.list())}}
    fun resumeForeground(){foreground=true;state.update{it.copy(appVisible=true)};if(activeOwnerId!="legacy"&&(cloudReadyOwner!=activeOwnerId||SystemClock.elapsedRealtime()-lastCloudSyncAt>30_000L))beginCloudSync(activeOwnerId)}
    fun setBackgroundListening(value:Boolean){
        preferences.edit().putBoolean("background-listening",value).apply();state.update{it.copy(backgroundListening=value)}
        if(!value&&!state.value.appVisible)background()
    }
    fun setFloatingControl(value:Boolean){preferences.edit().putBoolean("floating-control",value).apply();state.update{it.copy(floatingControl=value)}}
    internal fun setBackgroundServiceActive(value:Boolean){state.update{it.copy(backgroundServiceActive=value)};foreground=state.value.appVisible||(value&&state.value.backgroundListening)}
    internal fun stopFromNotification(){suspendSession();if(rememberConversation)persistConversation();state.update{it.copy(status="Escucha detenida. Conversación guardada en el historial.")}}

    private fun persistConversation(){synchronized(historyLock){if(!rememberConversation)return;state.update{it.copy(automaticTitle=ConversationNaming.automatic(it))};conversations.save(sessionId,state.value);state.update{it.copy(history=conversations.list())};queueCloudSave(sessionId)}}
    private fun beginCloudSync(uid:String){
        if(uid=="legacy")return
        cloudReadyOwner=null
        state.update{it.copy(cloudStatus="Conectando al historial en la nube…")}
        cloudMergeJob?.cancel()
        cloudMergeJob=viewModelScope.launch(Dispatchers.IO){
            val targets=synchronized(historyLock){if(activeOwnerId==uid)conversations to store else null}?:return@launch
            val target=targets.first;val savedTarget=targets.second
            val currentId=sessionId
            val before=state.value
            val hadCurrentRecord=target.load(currentId)!=null
            try{
                cloudHistory.merge(uid,target,savedTarget)
                if(activeOwnerId!=uid)return@launch
                cloudReadyOwner=uid
                lastCloudSyncAt=SystemClock.elapsedRealtime()
                val restored=target.load(currentId)
                state.update{current->
                    val unchanged=current.conversationKey==currentId&&!current.listening&&!current.starting&&current.lines==before.lines&&current.answers==before.answers&&current.summary==before.summary&&current.organization==before.organization&&current.conversationTitle==before.conversationTitle
                    val withCurrent=when{
                        restored!=null&&unchanged->current.copy(folderId=restored.folderId,organization=restored.organization,conversationTitle=restored.conversationTitle,automaticTitle=restored.automaticTitle,lines=restored.lines,answers=restored.answers,summary=restored.summary,highlights=ConversationRules.salient(restored.lines.joinToString(" "){it.text}))
                        hadCurrentRecord&&restored==null&&unchanged->current.copy(folderId=null,organization=ClassOrganization(),conversationTitle="",automaticTitle="",lines=emptyList(),answers=emptyList(),summary="",highlights=emptyList())
                        else->current
                    }
                    withCurrent.copy(history=target.list(),folders=target.folders(),cloudStatus="Historial sincronizado")
                }
                flushCloudChanges(uid)
            }catch(error:CancellationException){throw error}
            catch(_:Throwable){if(activeOwnerId==uid)state.update{it.copy(cloudStatus="Pendiente: crea Cloud Firestore y publica las reglas de acceso.")}}
        }
    }
    private fun queueCloudSave(id:Long){if(activeOwnerId=="legacy")return;pendingCloudChanges[id]=false;flushCloudChanges(activeOwnerId)}
    private fun queueCloudDelete(id:Long){if(activeOwnerId=="legacy")return;pendingCloudChanges[id]=true;flushCloudChanges(activeOwnerId)}
    private fun queueSavedCloudSave(id:String){if(activeOwnerId=="legacy"||id.isBlank())return;pendingSavedChanges[id]=false;flushCloudChanges(activeOwnerId)}
    private fun queueSavedCloudDelete(id:String){if(activeOwnerId=="legacy"||id.isBlank())return;pendingSavedChanges[id]=true;flushCloudChanges(activeOwnerId)}
    private fun queueAllCloudSaves(){if(activeOwnerId=="legacy")return;conversations.cloudRecords().forEach{pendingCloudChanges[it.id]=false};store.list().forEach{pendingSavedChanges[it.cloudId]=false};flushCloudChanges(activeOwnerId)}
    private fun flushCloudChanges(uid:String){
        if(uid=="legacy"||cloudReadyOwner!=uid||cloudWriteJob?.isActive==true)return
        cloudWriteJob=viewModelScope.launch(Dispatchers.IO){
            while(activeOwnerId==uid&&cloudReadyOwner==uid&&(pendingCloudChanges.isNotEmpty()||pendingSavedChanges.isNotEmpty())){
                delay(8000)
                val operations=pendingCloudChanges.entries.associate{it.key to it.value}
                for((id,deleted) in operations){
                    if(!pendingCloudChanges.remove(id,deleted))continue
                    try{
                        val target=synchronized(historyLock){if(activeOwnerId==uid)conversations else null}?:return@launch
                        if(deleted)cloudHistory.delete(uid,id)
                        else target.cloudRecords().firstOrNull{it.id==id}?.let{cloudHistory.upload(uid,it)}
                        if(activeOwnerId==uid)state.update{it.copy(cloudStatus="Historial sincronizado")}
                    }catch(error:CancellationException){throw error}
                    catch(_:Throwable){pendingCloudChanges.putIfAbsent(id,deleted);if(activeOwnerId==uid)state.update{it.copy(cloudStatus="Sincronización pendiente; se conserva una copia en el teléfono.")};return@launch}
                }
                val savedOperations=pendingSavedChanges.entries.associate{it.key to it.value}
                for((id,deleted) in savedOperations){
                    if(!pendingSavedChanges.remove(id,deleted))continue
                    try{
                        val target=synchronized(historyLock){if(activeOwnerId==uid)store else null}?:return@launch
                        if(deleted)cloudHistory.deleteSaved(uid,id,target.cloudDeletions()[id]?:System.currentTimeMillis())
                        else target.list().firstOrNull{it.cloudId==id}?.let{cloudHistory.uploadSaved(uid,it)}
                        if(activeOwnerId==uid)state.update{it.copy(cloudStatus="Historial sincronizado")}
                    }catch(error:CancellationException){throw error}
                    catch(_:Throwable){pendingSavedChanges.putIfAbsent(id,deleted);if(activeOwnerId==uid)state.update{it.copy(cloudStatus="Sincronización pendiente; se conserva una copia en el teléfono.")};return@launch}
                }
            }
        }
    }
    private fun sessionPreferenceKey()=if(activeOwnerId=="legacy")"session-id"else "session-id:$activeOwnerId"
    private fun nextSessionId():Long{
        val next=maxOf(System.currentTimeMillis(),preferences.getLong("last-session-id",0L)+1L,conversations.maxSessionId()+1L)
        preferences.edit().putLong("last-session-id",next).apply()
        return next
    }
    /** Restores only the account's local partition. Returns true when the user can choose to import legacy local data. */
    fun restoreConversation(userId:String?=null):Boolean{
        if(state.value.listening||state.value.starting)return false
        val requestedOwner=userId?.takeIf{it.isNotBlank()}?:"legacy"
        synchronized(historyLock){
            if(rememberConversation&&activeOwnerId==requestedOwner)return false
            if(rememberConversation)persistConversation()
            if(activeOwnerId!=requestedOwner){
                cloudReadyOwner=null;pendingCloudChanges.clear();pendingSavedChanges.clear();cloudMergeJob?.cancel();cloudWriteJob?.cancel()
                rememberConversation=false
                conversations.close();store.close()
                activeOwnerId=requestedOwner
                conversations=ConversationStore(context,ownerId=requestedOwner)
                store=SavedStore(context,ownerId=requestedOwner)
                state.update{it.copy(folderId=null,folders=emptyList(),legacyDataAvailable=false,organization=ClassOrganization(),conversationTitle="",automaticTitle="",history=emptyList(),lines=emptyList(),answers=emptyList(),summary="",summaryError="",highlights=emptyList(),saved=emptyList())}
            }
            val sessionKey=sessionPreferenceKey()
            sessionId=preferences.getLong(sessionKey,0L).takeIf{it>0L}?:nextSessionId()
            preferences.edit().putLong(sessionKey,sessionId).apply()
            val saved=conversations.load(sessionId)
            if(saved!=null)state.update{it.copy(folderId=saved.folderId,organization=saved.organization,folders=conversations.folders(),conversationTitle=saved.conversationTitle,automaticTitle=saved.automaticTitle,conversationKey=sessionId,lines=saved.lines,answers=saved.answers,summary=saved.summary,summaryError="",highlights=ConversationRules.salient(saved.lines.joinToString(" "){l->l.text}))}
            else state.update{it.copy(folderId=null,organization=ClassOrganization(),folders=conversations.folders(),conversationTitle="",automaticTitle="",conversationKey=sessionId,lines=emptyList(),answers=emptyList(),summary="",summaryError="",highlights=emptyList())}
            rememberConversation=true
            reloadSaved()
            val legacyAvailable=requestedOwner!="legacy"&&(conversations.hasLegacyData()||store.hasLegacyData())
            state.update{it.copy(history=conversations.list(),legacyDataAvailable=legacyAvailable)}
            val prompt=legacyAvailable&&!preferences.getBoolean("legacy-import-dismissed:$requestedOwner",false)
            if(requestedOwner!="legacy")beginCloudSync(requestedOwner)
            if(!spanishChecked){spanishChecked=true;refreshSpeechLanguages(autoSpanish=true)}
            return prompt
        }
    }
    fun dismissLegacyImportPrompt(){if(activeOwnerId!="legacy")preferences.edit().putBoolean("legacy-import-dismissed:$activeOwnerId",true).apply()}
    fun importLegacyData(){
        if(activeOwnerId=="legacy")return
        synchronized(historyLock){
            conversations.assignLegacyDataToCurrentOwner();store.assignLegacyDataToCurrentOwner()
            preferences.edit().putBoolean("legacy-import-dismissed:$activeOwnerId",true).apply()
            state.update{it.copy(history=conversations.list(),folders=conversations.folders(),saved=store.list(),legacyDataAvailable=false)}
            queueAllCloudSaves()
        }
    }
    fun deactivateAccount(){
        if(activeOwnerId=="legacy")return
        suspendSession()
        if(rememberConversation)persistConversation()
        synchronized(historyLock){
            cloudReadyOwner=null;pendingCloudChanges.clear();pendingSavedChanges.clear();cloudMergeJob?.cancel();cloudWriteJob?.cancel()
            rememberConversation=false
            conversations.close();store.close()
            activeOwnerId="legacy"
            conversations=ConversationStore(context,ownerId="legacy")
            store=SavedStore(context,ownerId="legacy")
            state.update{it.copy(folderId=null,folders=emptyList(),legacyDataAvailable=false,organization=ClassOrganization(),conversationTitle="",automaticTitle="",conversationKey=0,history=emptyList(),lines=emptyList(),answers=emptyList(),summary="",summaryError="",highlights=emptyList(),saved=emptyList(),listening=false,starting=false,generating=false,summaryPending=false,analysisRunning=false,status="Sesión cerrada.")}
        }
    }
    internal fun temporaryConversation(){synchronized(historyLock){rememberConversation=false};clearSession(keepHistory=true)}
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
                        sessionId=nextSessionId()
                        preferences.edit().putLong(sessionPreferenceKey(),sessionId).apply()
                        state.update{it.copy(conversationKey=sessionId,folderId=newFolder,status="Nueva conversación. Pulsa el micrófono para escuchar.",history=conversations.list(),folders=conversations.folders())}
                    }else{
                        val saved=conversations.load(id)?:error("Conversación no disponible")
                        sessionId=id
                        preferences.edit().putLong(sessionPreferenceKey(),id).apply()
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
        else synchronized(historyLock){conversations.rename(id,title.trim().take(100));state.update{it.copy(history=conversations.list())};queueCloudSave(id)}
    }
    fun setConversationPinned(id:Long,pinned:Boolean){synchronized(historyLock){
        conversations.setPinned(id,pinned)
        state.update{it.copy(history=conversations.list())}
        queueCloudSave(id)
    }}
    fun createFolder(name:String):Long?=synchronized(historyLock){
        runCatching{conversations.createFolder(name).also{state.update{s->s.copy(folders=conversations.folders())}}}.getOrElse{state.update{s->s.copy(status=it.message?:"No se pudo crear la carpeta.")};null}
    }
    fun renameFolder(id:Long,name:String){synchronized(historyLock){runCatching{conversations.renameFolder(id,name)}.onFailure{e->state.update{it.copy(status=e.message?:"No se pudo cambiar el nombre.")}};state.update{it.copy(folders=conversations.folders())};queueAllCloudSaves()}}
    fun deleteFolder(id:Long){synchronized(historyLock){conversations.deleteFolder(id);state.update{it.copy(folderId=if(it.folderId==id)null else it.folderId,folders=conversations.folders(),history=conversations.list())};queueAllCloudSaves()}}
    fun moveConversation(id:Long?,folder:Long?){synchronized(historyLock){
        if(id==null||id==sessionId||id==state.value.conversationKey){state.update{it.copy(folderId=folder)};if(rememberConversation)conversations.save(sessionId,state.value);conversations.move(sessionId,folder)}else conversations.move(id,folder)
        state.update{it.copy(history=conversations.list())}
        if(id==null||id==sessionId||id==state.value.conversationKey)queueCloudSave(sessionId)else queueCloudSave(id)
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
    fun deleteConversation(id:Long){if(id==sessionId)clearSession()else synchronized(historyLock){conversations.delete(id);queueCloudDelete(id)};state.update{it.copy(history=conversations.list())}}

    fun clearSession(keepHistory:Boolean=false){val removedId=sessionId;suspendSession();epoch++;debounce.values.forEach{it.cancel()};debounce.clear();signatures.clear();state.update{it.copy(conversationTitle="",automaticTitle="",folderId=null,organization=ClassOrganization(),onlineNotice="",conversationKey=System.nanoTime(),organizationRevision=0,lines=emptyList(),answers=emptyList(),summary="",summaryError="",highlights=emptyList(),status="Sesión borrada. Los textos guardados se conservan.")};if(rememberConversation&&!keepHistory)synchronized(historyLock){conversations.delete(removedId);queueCloudDelete(removedId)};state.update{it.copy(history=conversations.list())}}
    fun setSpeechEngine(@Suppress("UNUSED_PARAMETER") value:String){pause();preferences.edit().putString("speech-engine","system").apply();state.update{it.copy(speechEngine="system")}}
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
    private suspend fun streamPlaybackToGoogle(projection:android.media.projection.MediaProjection,captureEpoch:Long,sessionToken:Long){
        val captureContext=currentCoroutineContext()
        var input:AudioRecord?=null
        var speech:SystemSpeech?=null
        var output:android.os.ParcelFileDescriptor.AutoCloseOutputStream?=null
        var pcm=ByteArray(6400)
        var shorts=ShortArray(3200)
        try{
            val minimum=AudioRecord.getMinBufferSize(16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);check(minimum>0)
            val config=AudioPlaybackCaptureConfiguration.Builder(projection).addMatchingUsage(AudioAttributes.USAGE_MEDIA).addMatchingUsage(AudioAttributes.USAGE_GAME).addMatchingUsage(AudioAttributes.USAGE_UNKNOWN).excludeUid(android.os.Process.myUid()).build()
            input=AudioRecord.Builder().setAudioPlaybackCaptureConfig(config).setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(16000).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build()).setBufferSizeInBytes(maxOf(minimum,6400)).build()
            check(input.state==AudioRecord.STATE_INITIALIZED){"No se pudo capturar el audio del teléfono."}
            val pipe=android.os.ParcelFileDescriptor.createPipe()
            speech=SystemSpeech(context,{id,text,final->if(epoch==captureEpoch&&sessionToken==speechSessionToken)observe(id,text,final,captureEpoch)},{if(sessionToken==speechSessionToken)state.update{it.copy(starting=false,listening=true,status="Escuchando · Google local")}},{message->
                if(sessionToken==speechSessionToken){state.update{it.copy(starting=false,listening=false,audioLevel=0f,status=message)};pause()}
            },{message->
                if(sessionToken==speechSessionToken){state.update{it.copy(starting=false,listening=false,audioLevel=0f,status=message)};pause()}
            })
            systemSpeech=speech
            withContext(Dispatchers.Main.immediate){speech.start(pipe[0],state.value.speechLanguage)}
            output=android.os.ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])
            recorder=input
            input.startRecording()
            var silent=0
            while(captureContext.isActive&&sessionToken==speechSessionToken){
                val count=input.read(shorts,0,shorts.size);check(count>0){"Se interrumpió la captura del teléfono."}
                var energy=0.0
                for(i in 0 until count){
                    val sample=shorts[i].toInt()
                    energy+=(sample/32768.0)*(sample/32768.0)
                    pcm[i*2]=sample.toByte();pcm[i*2+1]=(sample shr 8).toByte()
                }
                val rms=kotlin.math.sqrt(energy/count).toFloat()
                state.update{it.copy(audioLevel=(rms*5).coerceIn(0f,1f))}
                silent=if(rms<.00001f)silent+count else 0
                check(silent<16000*12){"No llega audio del teléfono. Comprueba que el video esté reproduciéndose; algunas aplicaciones bloquean la captura."}
                output.write(pcm,0,count*2)
                pcm.fill(0)
            }
        }finally{
            pcm.fill(0);shorts.fill(0);runCatching{output?.close()};runCatching{speech?.stop()}
            runCatching{input?.stop()};runCatching{input?.release()};recorder=null
            withContext(NonCancellable+Dispatchers.Main.immediate){ListeningService.stopCapture()}
            if(sessionToken==speechSessionToken)state.update{it.copy(listening=false,starting=false,audioLevel=0f,status=if(it.status.startsWith("No se pudo")||it.status.startsWith("No llega"))it.status else "Escucha pausada.")}
        }
    }
    @Suppress("MissingPermission")
    private fun startListening(projection:android.media.projection.MediaProjection?,audioSource:android.os.ParcelFileDescriptor?,recovering:Boolean=false){
        if(conversationSwitch?.isActive==true||state.value.starting||state.value.listening||capture?.isActive==true)return
        if(state.value.localSpeechModels.downloading||state.value.localSpeechModels.checking){
            state.update{it.copy(status="Espera a que termine la preparación del idioma.")}
            return
        }
        if(state.value.onlineAnswers&&state.value.autoAnswers&&!state.value.transcriptionOnly&&state.value.answerEngine!=LocalAssistant.ID&&warming?.isActive!=true){
            warming=viewModelScope.launch(Dispatchers.IO){try{online.warmConnection()}catch(e:CancellationException){throw e}catch(_:Exception){/* Audio does not depend on network availability. */}}
        }
        if(!recovering)speechRecoveries=0
        state.update{it.copy(starting=true,status="Preparando reconocimiento local de Google…")}
        val captureEpoch=epoch
        val sessionToken=++speechSessionToken
        capture=viewModelScope.launch{
            try{
                closingSystemSpeech?.awaitStopped()
                closingSystemSpeech=null
                if(projection!=null){
                    streamPlaybackToGoogle(projection,captureEpoch,sessionToken)
                    return@launch
                }
                if(state.value.transcriptionOnly){
                    stopResponse()
                    warming?.cancelAndJoin()
                    generation?.join()
                }
                systemSpeech=SystemSpeech(context,
                    {id,text,final->if(epoch==captureEpoch&&sessionToken==speechSessionToken)observe(id,text,final,captureEpoch)},
                    {if(sessionToken==speechSessionToken)state.update{it.copy(starting=false,listening=true,status="Escuchando · Google local")}},
                    {message->if(sessionToken==speechSessionToken){state.update{it.copy(starting=false,listening=false,audioLevel=0f,status=message)};pause()}},
                    {message->if(sessionToken==speechSessionToken)recoverSystemSpeech(message,sessionToken)},
                    {level->if(sessionToken==speechSessionToken)state.update{it.copy(audioLevel=level)}}
                )
                currentCoroutineContext().ensureActive()
                systemSpeech?.start(audioSource,state.value.speechLanguage)
            }catch(e:CancellationException){throw e}
            catch(e:Throwable){state.update{it.copy(starting=false,listening=false,status="No se pudo preparar el reconocimiento local: ${e.message}")}}
        }
    }
    private fun refreshModels(){val available=File(context.filesDir,"model.litertlm").exists();state.update{it.copy(localModelAvailable=available,models="Transcripción: Google local · Respuestas: ${if(!it.onlineAnswers)"desactivadas"else if(it.answerEngine!="pollinations"){if(available)"Qwen local · listo"else "importar Qwen3 .litertlm"}else "online · Pollinations Classic"}")}}
    private fun reloadSaved(){state.update{it.copy(saved=store.list())}}
    fun save(id:Long?,kind:String,text:String,scope:String,due:String)=saveWithSource(id,kind,text,scope,due,"Texto escrito por usuario")
    fun saveWithSource(id:Long?,kind:String,text:String,scope:String,due:String,source:String){val cloudId=store.saveWithSource(id,kind,text,scope,due,source);reloadSaved();queueSavedCloudSave(cloudId)}
    fun delete(id:Long){val cloudId=store.cloudId(id);store.delete(id);reloadSaved();cloudId?.let(::queueSavedCloudDelete)}
    fun done(id:Long,value:Boolean){val cloudId=store.cloudId(id);store.done(id,value);reloadSaved();cloudId?.let(::queueSavedCloudSave)}
    fun releaseModels(after:()->Unit){suspendSession();viewModelScope.launch{capture?.join();generation?.join();withContext(Dispatchers.IO){modelLock.withLock{engines.close();state.update{it.copy(languageReady=false)}}};after()}}
    override fun onCleared(){localModelsJob?.cancel();suspendSession();queue.close();summaries.close();val c=capture;val g=generation;CoroutineScope(Dispatchers.IO).launch{c?.join();g?.join();modelLock.withLock{engines.close()};store.close();synchronized(historyLock){conversations.close()}};super.onCleared()}
}
