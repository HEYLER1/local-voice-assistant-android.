package com.heyler.voicelab

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable fun LiveConversation(s:AssistantState,onEdit:(SpeechLine)->Unit,onSave:(String,String)->Unit,onSpeak:(String)->Unit,onSummary:()->Unit,onClear:()->Unit,onStop:()->Unit,onSettings:()->Unit,modifier:Modifier=Modifier,section:Int=0,onSection:(Int)->Unit={},preferredAnswerId:String?=null){
    val compact=LocalConfiguration.current.screenHeightDp<480
    val scroll=rememberScrollState()
    var selectedId by rememberSaveable{mutableStateOf<String?>(null)}
    LaunchedEffect(preferredAnswerId){if(preferredAnswerId!=null)selectedId=preferredAnswerId}
    LaunchedEffect(s.answers.firstOrNull()?.id){if(selectedId==null)selectedId=s.answers.firstOrNull()?.id}
    val selected=s.answers.find{it.id==selectedId}?:s.answers.firstOrNull()
    val scope=rememberCoroutineScope()
    val openAnswer:(String)->Unit={selectedId=it;onSection(1);scope.launch{scroll.animateScrollTo(0)}}
    Column(modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal=20.dp).padding(top=8.dp,bottom=24.dp),verticalArrangement=Arrangement.spacedBy(if(compact)8.dp else 18.dp)){
        if(s.generating)TextButton(onClick=onStop){Text("Detener respuesta")}
        if(s.starting)Text(s.status,color=VoiceAccent)
        if((s.speechEngine=="moonshine"&&s.models.contains("Voz: importar"))||(s.speechEngine=="soniqo"&&s.models.contains("Soniqo: importar"))||(!s.transcriptionOnly&&s.models.contains("Respuestas: importar")))Surface(color=Color.Transparent){Column(Modifier.padding(vertical=8.dp)){Text("Prepara tu asistente",fontWeight=FontWeight.SemiBold);Text(s.models,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant);TextButton(onClick=onSettings){Text("Configurar modelos")}}}
        if(s.status.startsWith("No se pudo")||s.status.startsWith("Permiso")||s.status.startsWith("Instala una voz")||s.status.startsWith("Espera a"))Text(s.status,color=MaterialTheme.colorScheme.error)
        if(section==0&&s.lines.isEmpty()&&s.answers.isEmpty())EmptyConversation()
        else if(section==0)BoxWithConstraints(Modifier.fillMaxWidth()){
            val latest=selected
            if(maxWidth>=600.dp)Row(horizontalArrangement=Arrangement.spacedBy(18.dp)){
                Column(Modifier.weight(1f)){TranscriptPanel(s,onEdit,onSave,openAnswer)}
                Column(Modifier.weight(1f)){if(latest!=null)AnswerPanel(latest,onSpeak,onSave)else WaitingForQuestion(s.autoAnswers)}
            }else Column(verticalArrangement=Arrangement.spacedBy(18.dp)){
                if(latest!=null)Column(Modifier.fillMaxWidth().clickable{openAnswer(latest.id)}.padding(vertical=8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                        Row{Text("RESPUESTA EN VIVO",style=MaterialTheme.typography.labelSmall,color=VoiceAccent,modifier=Modifier.weight(1f));Text("Abrir →",style=MaterialTheme.typography.labelSmall,color=VoiceAccent)}
                        Text(latest.question,maxLines=2,overflow=TextOverflow.Ellipsis,fontWeight=FontWeight.SemiBold)
                        Text(ReadableAnswer.format(latest.text).ifBlank{latest.status},maxLines=3,overflow=TextOverflow.Ellipsis,color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodyMedium)
                    HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant,modifier=Modifier.padding(top=8.dp))
                }
                TranscriptPanel(s,onEdit,onSave,openAnswer)
                if(latest==null)WaitingForQuestion(s.autoAnswers)
            }
        }
        if(section==1&&s.answers.isNotEmpty()){
            if(compact)Row(horizontalArrangement=Arrangement.spacedBy(16.dp)){
                Box(Modifier.weight(1f)){QuestionList(s.answers,selected?.id,openAnswer)}
                Box(Modifier.weight(1f)){selected?.let{AnswerPanel(it,onSpeak,onSave)}}
            }else{
                QuestionList(s.answers,selected?.id,openAnswer)
                selected?.let{AnswerPanel(it,onSpeak,onSave)}
            }
        }
        if(section==1&&s.answers.isEmpty())WaitingForQuestion(s.autoAnswers)
        if(section==2&&s.lines.isNotEmpty()){
            StudySummaryPanel(s,openAnswer,onEdit,onSave,onSummary)
        }
        if(section==2&&s.lines.isEmpty())Text("Graba tu clase para reunir aquí el resumen y los apuntes de estudio.",color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable private fun EmptyConversation(){
    Column(Modifier.fillMaxWidth().padding(top=72.dp,bottom=40.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(14.dp)){
        androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(R.drawable.brand_mark),contentDescription="V · voz y conocimiento",modifier=Modifier.size(64.dp))
        Text("¿Qué vamos a descubrir?",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.SemiBold)
        Text("Habla o escribe. V reúne tus ideas.",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable private fun WaitingForQuestion(enabled:Boolean){Text(if(enabled)"Las preguntas relevantes aparecerán aquí."else "Las respuestas del audio están desactivadas en Ajustes.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(vertical=8.dp))}
@Composable private fun TranscriptPanel(s:AssistantState,onEdit:(SpeechLine)->Unit,onSave:(String,String)->Unit,onAnswer:(String)->Unit){
    var editing by remember{mutableStateOf(false)}
    var follow by remember{mutableStateOf(true)}
    val textScroll=rememberScrollState()
    val literal=s.lines.joinToString(" "){it.text}
    LaunchedEffect(literal,follow){if(follow)textScroll.animateScrollTo(textScroll.maxValue)}
    Column(Modifier.fillMaxWidth().padding(vertical=8.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("Transcripción",fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f));Text(if(s.listening)"● EN VIVO"else "PAUSADA",style=MaterialTheme.typography.labelSmall,color=if(s.listening)VoiceGreen else MaterialTheme.colorScheme.onSurfaceVariant)}
        Column(Modifier.heightIn(max=420.dp).verticalScroll(textScroll)){
            if(s.voiceLabels)s.lines.forEach{line->Text(line.voice,style=MaterialTheme.typography.labelMedium,color=VoiceAccent);SelectionContainer{Text(linkedTranscript(line.text,s.answers,onAnswer),lineHeight=26.sp,fontSize=16.sp)};Spacer(Modifier.height(12.dp))}
            else SelectionContainer{Text(linkedTranscript(literal,s.answers,onAnswer),lineHeight=27.sp,fontSize=16.sp,color=Color(0xFFE1E5F0))}
        }
        if(NumericMentions.spans(literal).isNotEmpty())Text("Cifras resaltadas · compruébalas con el audio",style=MaterialTheme.typography.labelSmall,color=Color(0xFFEBC189))
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){TextButton(onClick={editing=!editing},contentPadding=PaddingValues(0.dp)){Text(if(editing)"Cerrar edición"else "Editar / guardar")};TextButton(onClick={follow=!follow},contentPadding=PaddingValues(0.dp)){Text(if(follow)"Siguiendo audio"else "Seguir audio",color=MaterialTheme.colorScheme.onSurfaceVariant)}}
        if(editing)Column(Modifier.heightIn(max=300.dp).verticalScroll(rememberScrollState())){s.lines.forEach{line->Text(line.text,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant);Row{TextButton(onClick={onEdit(line)}){Text("Editar")};TextButton(onClick={onSave(line.text,"Transcripción seleccionada por usuario")}){Text("Guardar")}}}}
    }
}
@Composable private fun AnswerPanel(a:LiveAnswer,onSpeak:(String)->Unit,onSave:(String,String)->Unit){
    val compact=LocalConfiguration.current.screenHeightDp<480
    val active=a.status=="En espera"||a.status.startsWith("Preparando")||a.status.startsWith("Respondiendo")
    Column(Modifier.fillMaxWidth().padding(vertical=8.dp),verticalArrangement=Arrangement.spacedBy(if(compact)8.dp else 12.dp)){
        Row(verticalAlignment=Alignment.CenterVertically){VoiceGlyph("spark",VoiceAccent,Modifier.size(18.dp));Spacer(Modifier.width(8.dp));Text(if(a.source==null)"TU CONSULTA"else "PREGUNTA DEL AUDIO",color=VoiceAccent,style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Bold);Spacer(Modifier.weight(1f));if(active)CircularProgressIndicator(Modifier.size(16.dp),strokeWidth=2.dp,color=VoiceAccent)}
        Text(a.question,fontWeight=FontWeight.SemiBold,fontSize=if(compact)16.sp else 18.sp,lineHeight=if(compact)22.sp else 25.sp,maxLines=if(compact)2 else 4,overflow=TextOverflow.Ellipsis)
        if(a.text.isNotBlank())SelectionContainer{Text(ReadableAnswer.format(a.text),lineHeight=if(compact)22.sp else 26.sp,fontSize=if(compact)15.sp else 16.sp,color=Color(0xFFE6E0F4))}
        else Text(if(a.status=="En espera")"Pregunta detectada. Preparando respuesta…"else a.status,color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodyMedium)
        if(a.text.isNotBlank()){
            if(active)Text("Respondiendo en vivo…",color=VoiceAccent,style=MaterialTheme.typography.labelSmall)
            else Text(if(a.status.startsWith("Lista"))"Respuesta local · sin verificación externa"else a.status,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            a.firstTextMs?.let{Text("Primer texto · %.1f s".format(it/1000),style=MaterialTheme.typography.labelSmall,color=VoiceAccent)}
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){TextButton(onClick={onSpeak(a.text)},contentPadding=PaddingValues(horizontal=0.dp,vertical=6.dp)){Text("Escuchar")};TextButton(onClick={onSave(a.text,"Respuesta local, revisar antes de guardar")}){Text("Guardar")}}
        }
    }
}

private fun linkedTranscript(text:String,answers:List<LiveAnswer>,open:(String)->Unit):AnnotatedString = buildAnnotatedString {
    append(text)
    NumericMentions.spans(text).forEach{range->addStyle(SpanStyle(background=Color(0xFF514128)),range.first,range.last+1)}
    val occupied=mutableListOf<IntRange>()
    answers.filter{it.source!=null}.forEach{answer->
        val words=answer.question.trim('¿','?').split(Regex("\\s+"))
        val pattern=words.joinToString("[\\s¿?,.!]*"){Regex.escape(it)}
        Regex(pattern,RegexOption.IGNORE_CASE).findAll(text).forEach{match->
            if(occupied.none{it.first<=match.range.last&&match.range.first<=it.last}){
                occupied.add(match.range)
                addLink(LinkAnnotation.Clickable(answer.id,TextLinkStyles(style=SpanStyle(color=VoiceAccent,textDecoration=androidx.compose.ui.text.style.TextDecoration.Underline)),linkInteractionListener={open(answer.id)}),match.range.first,match.range.last+1)
            }
        }
    }
}

@Composable private fun QuestionList(answers:List<LiveAnswer>,selected:String?,open:(String)->Unit){
    Column(Modifier.heightIn(max=190.dp).verticalScroll(rememberScrollState())){
        answers.forEachIndexed{index,a->
            Row(Modifier.fillMaxWidth().clickable{open(a.id)}.padding(vertical=10.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)){
                Text("${index+1}",style=MaterialTheme.typography.labelLarge,color=VoiceAccent)
                Column(Modifier.weight(1f)){Text(a.question,maxLines=2,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodyMedium,color=if(a.id==selected)VoiceAccent else MaterialTheme.colorScheme.onSurface);Text(a.status.substringBefore("·").trim(),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)}
            }
            HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
        }
    }
}
