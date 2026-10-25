package com.heyler.voicelab

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.launch
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

@Composable fun LiveConversation(s:AssistantState,onEdit:(SpeechLine)->Unit,onSave:(String,String)->Unit,onSpeak:(String)->Unit,onSummary:()->Unit,onClear:()->Unit,onStop:()->Unit,onSettings:()->Unit,modifier:Modifier=Modifier,section:Int=0,onSection:(Int)->Unit={},preferredAnswerId:String?=null){
    var selectedId by rememberSaveable{mutableStateOf<String?>(null)}
    LaunchedEffect(preferredAnswerId){if(preferredAnswerId!=null)selectedId=preferredAnswerId}
    LaunchedEffect(s.answers.firstOrNull()?.id){if(selectedId==null)selectedId=s.answers.firstOrNull()?.id}
    val selected=s.answers.find{it.id==selectedId}?:s.answers.firstOrNull()
    val openAnswer:(String)->Unit={selectedId=it;onSection(1)}
    if(section==0){
        val context=LocalContext.current
        val clipboard=remember(context){context.getSystemService(android.content.ClipboardManager::class.java)}
        TranscriptWorkspace(s,openAnswer,{text->
            val clip=android.content.ClipData.newPlainText("Transcripción",text).apply{
                description.extras=android.os.PersistableBundle().apply{putBoolean(android.content.ClipDescription.EXTRA_IS_SENSITIVE,true)}
            }
            clipboard?.setPrimaryClip(clip)
        },modifier)
        return
    }
    if(section==1){
        QuestionsWorkspace(s,selected?.id,{selectedId=it},onSpeak,onSave,modifier,preferredAnswerId){
            ConversationNotices(s,onSummary,onStop,onSettings)
        }
        return
    }
    StudySummaryPanel(s,openAnswer,onEdit,onSave,onSummary,modifier.fillMaxSize())
}
@Composable private fun ConversationNotices(s:AssistantState,onSummary:()->Unit,onStop:()->Unit,onSettings:()->Unit){
    val missingModels=(s.speechEngine=="moonshine"&&s.models.contains("Voz: importar"))||(s.speechEngine=="soniqo"&&s.models.contains("Soniqo: importar"))||(!s.transcriptionOnly&&s.models.contains("Respuestas: importar"))
    val hasError=s.status.startsWith("No se pudo")||s.status.startsWith("Permiso")||s.status.startsWith("Instala una voz")||s.status.startsWith("Espera a")
    if(!s.analysisRunning&&s.onlineNotice.isBlank()&&!s.generating&&!s.starting&&!missingModels&&!hasError)return
    Column(verticalArrangement=Arrangement.spacedBy(6.dp)){
        if(s.analysisRunning)Text("Organizando el bloque: temas, preguntas y apuntes…",color=VoiceAccent,style=MaterialTheme.typography.bodySmall)
        if(s.onlineNotice.isNotBlank())Column(verticalArrangement=Arrangement.spacedBy(4.dp)){
            Text(s.onlineNotice,color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodySmall)
            TextButton(onClick=onSummary,enabled=!s.generating){Text("Reintentar análisis")}
        }
        if(s.generating)TextButton(onClick=onStop){Text("Detener respuesta")}
        if(s.starting)Text(s.status,color=VoiceAccent)
        if(missingModels)Surface(color=Color.Transparent){Column(Modifier.padding(vertical=8.dp)){Text("Prepara tu asistente",fontWeight=FontWeight.SemiBold);Text(s.models,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant);TextButton(onClick=onSettings){Text("Configurar modelos")}}}
        if(hasError)Text(s.status,color=MaterialTheme.colorScheme.error)
    }
}
@Composable private fun WaitingForQuestion(enabled:Boolean){Text(if(enabled)"Sin preguntas todavía."else "Activa las respuestas del audio en Ajustes.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(vertical=8.dp))}
@Composable private fun AnswerPanel(a:LiveAnswer,onSpeak:(String)->Unit,onSave:(String,String)->Unit,showQuestion:Boolean=true){
    val compact=LocalConfiguration.current.screenHeightDp<480
    val active=a.status=="En espera"||a.status.startsWith("Preparando")||a.status.startsWith("Respondiendo")
    var evidenceOpen by rememberSaveable(a.id){mutableStateOf(false)}
    Column(Modifier.fillMaxWidth().padding(vertical=if(showQuestion)8.dp else 4.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Row(verticalAlignment=Alignment.CenterVertically){VoiceGlyph("spark",VoiceAccent,Modifier.size(18.dp));Spacer(Modifier.width(8.dp));Text(if(a.suggested)"SUGERIDA PARA REPASAR"else if(a.source==null)"TU CONSULTA"else if(showQuestion)"PREGUNTA DEL AUDIO · CONTEXTUALIZADA"else "DEL AUDIO",modifier=Modifier.weight(1f),color=VoiceAccent,style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Bold);if(active)CircularProgressIndicator(Modifier.size(16.dp),strokeWidth=2.dp,color=VoiceAccent)}
        if(showQuestion)Text(a.question,fontWeight=FontWeight.SemiBold,fontSize=if(compact)16.sp else 18.sp,lineHeight=if(compact)22.sp else 25.sp,maxLines=if(compact)2 else 4,overflow=TextOverflow.Ellipsis)
        if(showQuestion&&a.evidence.isNotBlank())Text("Fuente: «${a.evidence}»",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(a.text.isNotBlank())SelectionContainer{Text(ReadableAnswer.format(a.text),lineHeight=if(compact)22.sp else 26.sp,fontSize=if(compact)15.sp else 16.sp,color=Color(0xFFE6E0F4))}
        else Text(if(a.status=="En espera")"Pregunta detectada. Preparando respuesta…"else a.status,color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodyMedium)
        if(a.text.isNotBlank()){
            if(active)Text("Respondiendo en vivo…",color=VoiceAccent,style=MaterialTheme.typography.labelSmall)
            else Text(if(a.status.startsWith("Lista"))if(a.status.contains("online"))"Respuesta online · sin verificación externa"else "Respuesta guardada · sin verificación externa"else a.status,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(showQuestion)a.firstTextMs?.let{Text("Primer texto · %.1f s".format(it/1000),style=MaterialTheme.typography.labelSmall,color=VoiceAccent)}
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                TextButton(onClick={onSpeak(a.text)},contentPadding=PaddingValues(horizontal=4.dp,vertical=4.dp)){Text("Escuchar")}
                TextButton(onClick={onSave(a.text,if(a.status.contains("online"))"Respuesta online, revisar antes de guardar"else "Respuesta guardada, revisar antes de guardar")},contentPadding=PaddingValues(horizontal=4.dp,vertical=4.dp)){Text("Guardar")}
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
    preferredAnswerId:String?,
    notices:@Composable ()->Unit
){
    var suggested by rememberSaveable{mutableStateOf(s.answers.firstOrNull{it.id==selected}?.suggested?:false)}
    var collapsed by rememberSaveable{mutableStateOf<String?>(null)}
    val visible=s.answers.filter{it.suggested==suggested}
    val conversationList=rememberLazyListState(initialFirstVisibleItemIndex=s.answers.filter{!it.suggested}.indexOfFirst{it.id==selected}.let{if(it>0)it+1 else 0})
    val reviewList=rememberLazyListState(initialFirstVisibleItemIndex=s.answers.filter{it.suggested}.indexOfFirst{it.id==selected}.let{if(it>0)it+1 else 0})
    val list=if(suggested)reviewList else conversationList
    LaunchedEffect(preferredAnswerId,selected){
        if(preferredAnswerId!=null&&selected==preferredAnswerId){
            s.answers.firstOrNull{it.id==preferredAnswerId}?.let{answer->
                suggested=answer.suggested;collapsed=null
                val index=s.answers.filter{it.suggested==answer.suggested}.indexOfFirst{it.id==answer.id}
                (if(answer.suggested)reviewList else conversationList).scrollToItem(index+1)
            }
        }
    }
    Column(modifier.fillMaxSize().padding(horizontal=12.dp)){
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 40.dp){
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                TextButton(onClick={suggested=false},modifier=Modifier.weight(1f),contentPadding=PaddingValues(horizontal=4.dp,vertical=0.dp)){
                    Text("Conversación",color=if(!suggested)VoiceAccent else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick={suggested=true},modifier=Modifier.weight(1f),contentPadding=PaddingValues(horizontal=4.dp,vertical=0.dp)){
                    Text("Para repasar",color=if(suggested)VoiceAccent else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth().semantics{contentDescription="Preguntas y respuestas"},state=list,contentPadding=PaddingValues(bottom=12.dp)){
                item(key="notices"){notices()}
                if(visible.isEmpty())item(key="empty"){
                    if(s.answers.isEmpty())WaitingForQuestion(s.autoAnswers)
                    else Text(if(suggested)"No hay preguntas para repasar todavía."else "No hay preguntas en esta conversación todavía.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(vertical=8.dp))
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
                                if(!expanded&&!answer.status.startsWith("Lista"))Text(answer.status.substringBefore("·").trim(),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
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
