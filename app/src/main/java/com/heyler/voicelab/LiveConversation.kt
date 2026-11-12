package com.heyler.voicelab

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Suppress("UNUSED_PARAMETER")
@Composable fun LiveConversation(s:AssistantState,onEdit:(SpeechLine)->Unit,onSave:(String,String)->Unit,onSpeak:(String)->Unit,onSummary:()->Unit,onClear:()->Unit,onStop:()->Unit,onSettings:()->Unit,modifier:Modifier=Modifier,section:Int=0,onSection:(Int)->Unit={},preferredAnswerId:String?=null,transcriptPadding:PaddingValues=PaddingValues(0.dp),onResearch:()->Unit=onSummary){
    var selectedId by rememberSaveable{mutableStateOf<String?>(null)}
    LaunchedEffect(preferredAnswerId){if(preferredAnswerId!=null)selectedId=preferredAnswerId}
    val availableAnswers=s.answers.filterNot{it.suggested}
    LaunchedEffect(availableAnswers.firstOrNull()?.id){if(selectedId==null)selectedId=availableAnswers.firstOrNull()?.id}
    val selected=availableAnswers.find{it.id==selectedId}?:availableAnswers.firstOrNull()
    val openAnswer:(String)->Unit={selectedId=it;onSection(1)}
    if(section==0){
        val context=LocalContext.current
        val clipboard=remember(context){context.getSystemService(android.content.ClipboardManager::class.java)}
        TranscriptWorkspace(s,openAnswer,{text->
            val clip=android.content.ClipData.newPlainText("Transcripción",text).apply{
                description.extras=android.os.PersistableBundle().apply{putBoolean(android.content.ClipDescription.EXTRA_IS_SENSITIVE,true)}
            }
            clipboard?.setPrimaryClip(clip)
        },modifier,transcriptPadding)
        return
    }
    if(section==1){
        QuestionsWorkspace(s,selected?.id,{selectedId=it},onSpeak,onSave,modifier,transcriptPadding,preferredAnswerId){
            ConversationNotices(s,onStop)
        }
        return
    }
    StudySummaryPanel(s,openAnswer,onEdit,onSave,onSummary,modifier.fillMaxSize().padding(transcriptPadding),onResearch=onResearch)
}
@Composable private fun ConversationNotices(s:AssistantState,onStop:()->Unit){
    val hasError=s.status.startsWith("No se pudo")||s.status.startsWith("Permiso")||s.status.startsWith("Instala una voz")||s.status.startsWith("Espera a")
    if(s.onlineNotice.isBlank()&&!s.generating&&!hasError)return
    Column(verticalArrangement=Arrangement.spacedBy(6.dp)){
        if(s.onlineNotice.isNotBlank())Text("No se pudo generar la respuesta. Revisa la conexión e inténtalo de nuevo.",color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodySmall)
        if(s.generating)TextButton(onClick=onStop){Text("Detener")}
        if(hasError)Text(s.status,color=MaterialTheme.colorScheme.error)
    }
}
@Composable private fun WaitingForQuestion(enabled:Boolean){Text(if(enabled)"Sin preguntas todavía."else "Activa las respuestas del audio en Ajustes.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(vertical=8.dp))}
@Composable private fun AnswerPanel(a:LiveAnswer,onSpeak:(String)->Unit,onSave:(String,String)->Unit,showQuestion:Boolean=true){
    val compact=LocalConfiguration.current.screenHeightDp<480
    val active=a.status.startsWith("En espera")||a.status.startsWith("Preparando")||a.status.startsWith("Respondiendo")
    var evidenceOpen by rememberSaveable(a.id){mutableStateOf(false)}
    Column(Modifier.fillMaxWidth().padding(vertical=if(showQuestion)8.dp else 4.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        if(active)Row(verticalAlignment=Alignment.CenterVertically){CircularProgressIndicator(Modifier.size(14.dp),strokeWidth=2.dp,color=VoiceAccent);Spacer(Modifier.width(8.dp));Text("Preparando respuesta",color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.labelSmall)}
        if(showQuestion)Text(a.question,fontWeight=FontWeight.SemiBold,fontSize=if(compact)16.sp else 18.sp,lineHeight=if(compact)22.sp else 25.sp,maxLines=if(compact)2 else 4,overflow=TextOverflow.Ellipsis)
        if(a.text.isNotBlank())SelectionContainer{Text(ReadableAnswer.format(a.text),lineHeight=if(compact)22.sp else 26.sp,fontSize=if(compact)15.sp else 16.sp,color=Color(0xFFE6E0F4))}
        else if(!active){
            val publicStatus=when{
                a.status.startsWith("No se completó")->"No se pudo responder. Vuelve a intentarlo."
                a.status.startsWith("AI Horde")||a.status.contains("motor disponible",true)||a.status.startsWith("Pollinations")||a.status.startsWith("Respondiendo")->"Preparando respuesta…"
                a.status.startsWith("Lista")->""
                a.status.startsWith("Fuente modificada")->"El texto cambió; revisa la respuesta."
                else->a.status.substringBefore("·").trim()
            }
            if(publicStatus.isNotBlank())Text(publicStatus,color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodySmall)
        }
        if(a.text.isNotBlank()){
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                TextButton(onClick={onSpeak(a.text)},contentPadding=PaddingValues(horizontal=4.dp,vertical=4.dp)){Text("Escuchar")}
                TextButton(onClick={onSave(a.text,"Respuesta del asistente, revisar antes de guardar")},contentPadding=PaddingValues(horizontal=4.dp,vertical=4.dp)){Text("Guardar")}
                if(!showQuestion&&a.evidence.isNotBlank())TextButton(onClick={evidenceOpen=!evidenceOpen},contentPadding=PaddingValues(horizontal=4.dp,vertical=4.dp),modifier=Modifier.semantics{stateDescription=if(evidenceOpen)"Expandida"else "Contraída"}){Text("Fuente",color=if(evidenceOpen)VoiceAccent else MaterialTheme.colorScheme.onSurfaceVariant)}
            }
        }
        if(!showQuestion&&evidenceOpen&&a.evidence.isNotBlank())Text("Fuente: «${a.evidence}»",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

internal fun linkedTranscript(text:String,answers:List<LiveAnswer>,open:(String)->Unit):AnnotatedString = buildAnnotatedString {
    append(text)
    NumericMentions.spans(text).forEach{range->addStyle(SpanStyle(background=Color(0xFF514128)),range.first,range.last+1)}
    val occupied=mutableListOf<IntRange>()
    answers.filter{it.source!=null}.forEach{answer->
        val words=answer.evidence.ifBlank{answer.question}.trim('¿','?').split(Regex("\\s+"))
        val pattern=words.joinToString("[\\s¿?,.!]*"){Regex.escape(it)}
        Regex(pattern,RegexOption.IGNORE_CASE).findAll(text).forEach{match->
            if(occupied.none{it.first<=match.range.last&&match.range.first<=it.last}){
                occupied.add(match.range)
                addLink(LinkAnnotation.Clickable(answer.id,TextLinkStyles(style=SpanStyle(color=VoiceAccent,textDecoration=androidx.compose.ui.text.style.TextDecoration.Underline)),linkInteractionListener={open(answer.id)}),match.range.first,match.range.last+1)
            }
        }
    }
}

@Composable private fun QuestionsWorkspace(
    s:AssistantState,
    selected:String?,
    select:(String)->Unit,
    onSpeak:(String)->Unit,
    onSave:(String,String)->Unit,
    modifier:Modifier,
    contentPadding:PaddingValues,
    preferredAnswerId:String?,
    notices:@Composable ()->Unit
){
    var collapsed by rememberSaveable{mutableStateOf<String?>(null)}
    val visible=s.answers.filterNot{it.suggested}
    val list=rememberLazyListState(initialFirstVisibleItemIndex=visible.indexOfFirst{it.id==selected}.let{if(it>0)it+1 else 0})
    LaunchedEffect(preferredAnswerId,selected){
        if(preferredAnswerId!=null&&selected==preferredAnswerId){
            visible.firstOrNull{it.id==preferredAnswerId}?.let{answer->
                collapsed=null
                val index=visible.indexOfFirst{it.id==answer.id}
                if(index>=0)list.scrollToItem(index+1)
            }
        }
    }
    Column(modifier.fillMaxSize().padding(horizontal=12.dp)){
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 40.dp){
            LazyColumn(Modifier.weight(1f).fillMaxWidth().semantics{contentDescription="Preguntas y respuestas"},state=list,contentPadding=PaddingValues(top=contentPadding.calculateTopPadding(),bottom=contentPadding.calculateBottomPadding()+12.dp)){
                item(key="notices"){notices()}
                if(visible.isEmpty())item(key="empty"){
                    if(s.answers.isEmpty())WaitingForQuestion(s.autoAnswers)
                }
                itemsIndexed(visible,key={_,answer->answer.id}){index,answer->
                    val expanded=answer.id==selected&&collapsed!=answer.id
                    val number=index+1
                    Column(Modifier.fillMaxWidth()){
                        Row(Modifier.fillMaxWidth().clickable{
                            if(answer.id==selected)collapsed=if(collapsed==answer.id)null else answer.id
                            else{collapsed=null;select(answer.id)}
                        }.semantics{stateDescription=if(expanded)"Respuesta expandida"else "Respuesta contraída"}.padding(vertical=8.dp),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.Top){
                            Text(number.toString(),style=MaterialTheme.typography.labelLarge,color=VoiceAccent,modifier=Modifier.padding(top=2.dp))
                            Column(Modifier.weight(1f)){
                                Text(answer.question,maxLines=if(expanded)Int.MAX_VALUE else 2,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodyLarge,fontWeight=if(expanded)FontWeight.Medium else FontWeight.Normal,color=if(expanded)VoiceAccent else MaterialTheme.colorScheme.onSurface)
                                if(!expanded&&(answer.text.isBlank()||answer.status.startsWith("No se completó")))Text(if(answer.status.startsWith("No se completó"))"No se pudo responder"else "Preparando respuesta…",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
                            }
                            Text(if(expanded)"▾"else "▸",color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if(expanded)AnswerPanel(answer,onSpeak,onSave,showQuestion=false)
                        HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}
