package com.heyler.voicelab

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun StudySummaryPanel(s:AssistantState,onQuestion:(String)->Unit,onEdit:(SpeechLine)->Unit,onSave:(String,String)->Unit,onRefresh:()->Unit,modifier:Modifier=Modifier,onCopy:((String)->Unit)?=null,onResearch:()->Unit=onRefresh){
    val guide=remember(s.lines){StudyGuide.build(s.lines)}
    val lesson=remember(s.lines){LessonMemory.build(s.lines)}
    val map=s.organization.map
    val baseTopic=remember(s.organization,s.lines,s.automaticTitle){KnowledgeSummary.title(s)}
    val topic=map?.title?:baseTopic
    val document=remember(topic,s.summary,s.organization){map?.let{ResearchedMapDocument.text(it)}?:KnowledgeSummary.document(s)}
    val context=androidx.compose.ui.platform.LocalContext.current
    var copied by remember{mutableStateOf(false)}
    LaunchedEffect(copied){if(copied){kotlinx.coroutines.delay(1200);copied=false}}
    var section by rememberSaveable{mutableIntStateOf(if(s.organization.sections.isNotEmpty())6 else 0)}
    var inspected by rememberSaveable(s.conversationKey){mutableStateOf<String?>(null)}
    val nodes=remember(s.organization,guide,lesson,s.answers.size){
        if(map!=null)map.branches.mapIndexed{index,branch->SummaryCanvasNode("branch:$index",branch.title,"",branch=branch)}else buildList{
            val reserved=listOf(lesson.tasks.isNotEmpty(),lesson.exams.isNotEmpty(),s.answers.isNotEmpty()).count{it}
            if(s.organization.sections.isNotEmpty())s.organization.sections.take(6-reserved).forEach{note->add(SummaryCanvasNode("note:${note.title}",note.title,"Apuntes organizados",note=note))}
            else if(guide.concepts.isNotEmpty())guide.concepts.take(3).forEach{concept->add(SummaryCanvasNode("concept:${concept.excerpt.sourceId}:${concept.term}",concept.term,"Concepto del audio",note=OrganizedSection(concept.term,concept.excerpt.text,listOf(concept.excerpt.sourceId))))}
            else guide.ideas.take(3).forEachIndexed{index,idea->val title=idea.text.split(Regex("\\s+")).take(6).joinToString(" ");add(SummaryCanvasNode("idea:$index",title,"Extracto del audio",note=OrganizedSection(title,idea.text,listOf(idea.sourceId))))}
            if(lesson.tasks.isNotEmpty())add(SummaryCanvasNode("tasks","Por preparar","${lesson.tasks.size} ${if(lesson.tasks.size==1)"indicación"else "indicaciones"}",category=4))
            if(lesson.exams.isNotEmpty())add(SummaryCanvasNode("exam","Examen","${lesson.exams.size} ${if(lesson.exams.size==1)"aviso"else "avisos"}",category=5))
            if(s.answers.isNotEmpty())add(SummaryCanvasNode("review","Preguntas","${s.answers.size} ${if(s.answers.size==1)"pregunta"else "preguntas"}",category=2))
            if(size<6&&guide.figures.isNotEmpty())add(SummaryCanvasNode("figures","Cifras","${guide.figures.size} ${if(guide.figures.size==1)"extracto"else "extractos"}",category=3))
        }.take(6)
    }
    val canRefresh=s.onlineAnswers&&!s.generating&&s.lines.sumOf{it.text.length}>=100&&!(s.transcriptionOnly&&(s.listening||s.starting))
    val issue=s.summaryError.ifBlank{s.onlineNotice}
    val tools:@Composable ()->Unit={
        IconButton(onClick={inspected="browse"},modifier=Modifier.size(40.dp).semantics{contentDescription="Explorar apuntes"}){VoiceGlyph("notes",MaterialTheme.colorScheme.onSurfaceVariant,Modifier.size(20.dp))}
        IconButton(onClick=onResearch,enabled=canRefresh&&KnowledgeSummary.title(s)!=ConversationNaming.UNKNOWN,modifier=Modifier.size(40.dp).semantics{contentDescription=if(s.researchRunning)"Ampliando el tema"else "Ampliar y organizar mapa"}){if(s.researchRunning)CircularProgressIndicator(Modifier.size(18.dp),strokeWidth=2.dp,color=VoiceGreen)else VoiceGlyph("search",if(canRefresh)VoiceGreen else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=.4f),Modifier.size(20.dp))}
        IconButton(onClick=onRefresh,enabled=canRefresh,modifier=Modifier.size(40.dp).semantics{contentDescription=if(s.analysisRunning)"Actualizando resumen"else "Actualizar resumen"}){
            if(s.analysisRunning&&!s.researchRunning)CircularProgressIndicator(Modifier.size(18.dp),strokeWidth=2.dp,color=VoiceAccent)
            else VoiceGlyph("refresh",if(canRefresh)VoiceAccent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=.4f),Modifier.size(20.dp))
        }
        if(issue.isNotBlank())IconButton(onClick={inspected="status"},modifier=Modifier.size(40.dp).semantics{contentDescription="Ver aviso del resumen"}){Text("!",color=VoiceAccent,fontWeight=FontWeight.Bold)}
    }
    BoxWithConstraints(modifier.fillMaxWidth()){
        val viewport=if(constraints.hasBoundedHeight)Modifier.fillMaxSize()else Modifier.fillMaxWidth().height(560.dp)
        if(s.lines.isEmpty()&&s.summary.isBlank())Box(viewport){
            Row(Modifier.align(Alignment.TopStart).padding(start=8.dp)){tools()}
            VoiceGlyph("spark",MaterialTheme.colorScheme.outline,Modifier.align(Alignment.Center).size(36.dp).semantics{contentDescription="Resumen sin contenido"})
        }else {
            SummaryCanvas(topic,nodes,{inspected=it?.id?:"summary"},viewport,tools,ReadableAnswer.format(map?.overview?:s.summary))
            if(map==null&&canRefresh&&KnowledgeSummary.title(s)!=ConversationNaming.UNKNOWN)TextButton(onClick=onResearch,modifier=Modifier.align(Alignment.BottomStart).padding(8.dp)){Text("Ampliar y organizar mapa",color=VoiceGreen)}
        }
    }
    val node=nodes.firstOrNull{it.id==inspected}
    if(inspected=="summary"||inspected=="browse"||inspected=="status"||node!=null)ModalBottomSheet(onDismissRequest={inspected=null},sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),containerColor=androidx.compose.ui.graphics.Color(0xFF141827)){
        val detailSize=if(inspected=="summary"||inspected=="browse")Modifier.fillMaxHeight(.9f)else Modifier.heightIn(max=600.dp)
        Column(Modifier.fillMaxWidth().then(detailSize).navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal=20.dp).padding(bottom=20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.Top){
                Text(if(inspected=="browse")"Contenido"else if(inspected=="status")"Aviso"else node?.title?:topic,modifier=Modifier.weight(1f),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold)
                if(inspected=="summary"&&(map!=null||s.summary.isNotBlank()||s.organization.sections.isNotEmpty()))IconButton(onClick={
                    if(onCopy!=null)onCopy(document)else context.getSystemService(android.content.ClipboardManager::class.java)?.setPrimaryClip(
                        android.content.ClipData.newPlainText("Resumen",document).apply{description.extras=android.os.PersistableBundle().apply{putBoolean(android.content.ClipDescription.EXTRA_IS_SENSITIVE,true)}})
                    copied=true
                },modifier=Modifier.size(36.dp).semantics{contentDescription=if(copied)"Resumen copiado"else "Copiar resumen"}){VoiceGlyph(if(copied)"check"else "copy",if(copied)VoiceGreen else MaterialTheme.colorScheme.onSurfaceVariant,Modifier.size(18.dp))}
                IconButton(onClick={inspected=null},modifier=Modifier.size(36.dp).semantics{contentDescription="Cerrar detalle del resumen"}){VoiceGlyph("close",MaterialTheme.colorScheme.onSurfaceVariant,Modifier.size(18.dp))}
            }
            if(inspected=="browse"){
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(16.dp)){
                    val categories=buildList{if(s.organization.sections.isNotEmpty())add(6 to "Apuntes");addAll(listOf(0 to "Ideas",4 to "Tareas ${lesson.tasks.size}",5 to "Examen ${lesson.exams.size}",1 to "Conceptos",2 to "Repaso",3 to "Cifras"))}
                    categories.forEach{(index,label)->
                        Column(Modifier.clickable{section=index}.semantics{selected=section==index;role=Role.Tab}.padding(vertical=6.dp),horizontalAlignment=Alignment.CenterHorizontally){
                            Text(label,color=if(section==index)VoiceAccent else MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.labelLarge,modifier=Modifier.padding(bottom=8.dp))
                            Box(Modifier.width(24.dp).height(2.dp).background(if(section==index)VoiceAccent else MaterialTheme.colorScheme.background))
                        }
                    }
                }
                StudyContent(section,s,guide,lesson,{inspected=null;onQuestion(it)},{inspected=null;onEdit(it)},{text,source->inspected=null;onSave(text,source)})
            }else if(inspected=="status"){
                Text(issue,style=MaterialTheme.typography.bodyMedium)
                TextButton(onClick={inspected=null;onRefresh()},enabled=canRefresh){Text("Reintentar")}
            }else if(inspected=="summary"){
                if(map!=null){
                    ResearchedMapReading(map,s,onEdit={inspected=null;onEdit(it)})
                    TextButton(onClick={inspected=null;onSave(document,"Mapa ampliado por IA, revisar antes de guardar")}){Text("Guardar resumen")}
                }else{
                if(s.summary.isNotBlank()){
                    SelectionContainer{Text(ReadableAnswer.format(s.summary),lineHeight=26.sp)}
                }
                s.organization.sections.forEach{note->
                    Row(Modifier.fillMaxWidth().padding(top=8.dp),verticalAlignment=Alignment.CenterVertically){
                        Text(note.title,color=VoiceAccent,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f))
                        note.sources.take(3).forEachIndexed{index,id->s.lines.firstOrNull{it.id==id}?.let{line->
                            IconButton(onClick={inspected=null;onEdit(line)},modifier=Modifier.size(32.dp).semantics{contentDescription="Ver fuente de ${note.title} (${index+1})"}){VoiceGlyph("notes",MaterialTheme.colorScheme.onSurfaceVariant,Modifier.size(17.dp))}
                        }}
                    }
                    SelectionContainer{Text(note.text,lineHeight=25.sp)}
                }
                if(s.summary.isNotBlank()||s.organization.sections.isNotEmpty())TextButton(onClick={inspected=null;onSave(document,"Resumen generado, revisar antes de guardar")}){Text("Guardar resumen")}
                else StudyEmpty("La síntesis aún no está disponible. Puedes explorar los extractos del lienzo.")
                }
            }else if(node?.branch!=null&&map!=null)ResearchedBranchReading(node.branch,map,s,onEdit={inspected=null;onEdit(it)})
            else if(node?.note!=null)OrganizedNoteBody(node.note,s,{inspected=null;onEdit(it)})
            else node?.category?.let{category->StudyContent(category,s,guide,lesson,{inspected=null;onQuestion(it)},{inspected=null;onEdit(it)},{text,source->inspected=null;onSave(text,source)})}
        }
    }
}
@Composable private fun OrganizedNote(note:OrganizedSection,s:AssistantState,onEdit:(SpeechLine)->Unit){
    var expanded by rememberSaveable(note.title){mutableStateOf(false)}
    Column(Modifier.fillMaxWidth().animateContentSize(),verticalArrangement=Arrangement.spacedBy(6.dp)){
        TextButton(onClick={expanded=!expanded},contentPadding=PaddingValues(0.dp)){Text((if(expanded)"▾ "else "▸ ")+note.title,style=MaterialTheme.typography.titleMedium,color=VoiceAccent)}
        if(expanded)OrganizedNoteBody(note,s,onEdit)
    }
}
@Composable private fun OrganizedNoteBody(note:OrganizedSection,s:AssistantState,onEdit:(SpeechLine)->Unit){
    SelectionContainer{Text(note.text,lineHeight=25.sp)}
    note.sources.take(3).forEach{id->s.lines.firstOrNull{it.id==id}?.let{line->TextButton(onClick={onEdit(line)},contentPadding=PaddingValues(0.dp)){Text("Ver fuente en la transcripción ↗",style=MaterialTheme.typography.labelSmall)}}}
}
@Composable private fun StudyContent(section:Int,s:AssistantState,guide:StudyGuide,lesson:LessonMemory,onQuestion:(String)->Unit,onEdit:(SpeechLine)->Unit,onSave:(String,String)->Unit){
    when(section){
        6->{
            if(s.organization.sections.isEmpty())StudyEmpty("Los apuntes organizados aparecerán al analizar la conversación.")
            s.organization.sections.forEach{note->key(note.title){OrganizedNote(note,s,onEdit)}}
        }
        0->{
            Text("Lo esencial",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
            if(guide.ideas.isEmpty())StudyEmpty("Añade más contenido para reunir las ideas de la clase.")
            guide.ideas.forEachIndexed{index,excerpt->
                Row(horizontalArrangement=Arrangement.spacedBy(14.dp)){
                    Text("%02d".format(index+1),color=VoiceGreen,style=MaterialTheme.typography.labelLarge,modifier=Modifier.padding(top=4.dp))
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){
                        SelectionContainer{Text(excerpt.text,lineHeight=25.sp)}
                        StudySource(excerpt,s,onEdit)
                    }
                }
            }
        }
        1->{
            Text("Conceptos en tus apuntes",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
            if(guide.concepts.isEmpty())StudyEmpty("Todavía no hay definiciones explícitas. Puedes revisar las ideas o seguir grabando la clase.")
            guide.concepts.forEach{concept->
                Column(verticalArrangement=Arrangement.spacedBy(6.dp)){
                    Text(concept.term,color=VoiceGreen,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
                    SelectionContainer{Text(concept.excerpt.text,lineHeight=25.sp)}
                    StudySource(concept.excerpt,s,onEdit)
                }
            }
        }
        2->{
            Text("Ponte a prueba",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
            StudyEmpty("Intenta responder antes de mostrar la explicación.")
            if(s.answers.isEmpty())StudyEmpty("Las preguntas de esta conversación aparecerán aquí para repasarlas.")
            s.answers.forEach{answer->key(answer.id){StudyQuestion(answer,onQuestion)}}
        }
        3->{
            Text("Cifras que conviene revisar",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
            if(guide.figures.isEmpty())StudyEmpty("Aún no hay cifras en los extractos de la conversación.")
            guide.figures.forEach{excerpt->
                Column(verticalArrangement=Arrangement.spacedBy(4.dp)){
                    SelectionContainer{Text(excerpt.text,lineHeight=25.sp)}
                    StudySource(excerpt,s,onEdit)
                }
            }
        }
        4->{
            Text("Qué hay que preparar",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
            if(lesson.tasks.isEmpty())StudyEmpty("Las tareas, trabajos, lecturas y ejercicios indicados en la clase aparecerán aquí.")
            lesson.tasks.forEach{item->StudyInstructionPanel(item,s,onEdit,onSave)}
        }
        5->{
            Text("Avisos de evaluación",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
            if(lesson.exams.isEmpty())StudyEmpty("Aquí se reúnen las menciones explícitas a preguntas de examen, parciales y evaluaciones.")
            lesson.exams.forEach{item->StudyInstructionPanel(item,s,onEdit,onSave)}
        }
    }
}
@Composable private fun StudyInstructionPanel(item:StudyInstruction,s:AssistantState,onEdit:(SpeechLine)->Unit,onSave:(String,String)->Unit){
    var expanded by rememberSaveable(item.id){mutableStateOf(false)}
    Column(Modifier.fillMaxWidth().animateContentSize(),verticalArrangement=Arrangement.spacedBy(6.dp)){
        Text(item.kind.label,color=VoiceGreen,style=MaterialTheme.typography.labelLarge)
        SelectionContainer{Text(item.excerpt.text,lineHeight=25.sp)}
        if(item.timing.isNotBlank())Text("Plazo citado · ${item.timing}",style=MaterialTheme.typography.bodySmall,color=VoiceAccent)
        if(item.context.any{it!=item.excerpt}){
            TextButton(onClick={expanded=!expanded},contentPadding=PaddingValues(0.dp)){Text(if(expanded)"Ocultar contexto"else "Ver contexto de la indicación")}
            AnimatedVisibility(expanded){Column(verticalArrangement=Arrangement.spacedBy(6.dp)){
                item.context.filter{it!=item.excerpt}.forEach{excerpt->Text(excerpt.text,style=MaterialTheme.typography.bodySmall);StudySource(excerpt,s,onEdit)}
            }}
        }
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){
            StudySource(item.excerpt,s,onEdit)
            if(item.kind!=StudyInstructionKind.EXAM)TextButton(onClick={onSave(item.context.joinToString(" "){it.text},"Tarea de clase · ${item.kind.label}; indicación literal para revisar")},contentPadding=PaddingValues(0.dp)){Text("Guardar como tarea")}
        }
        HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
    }
}
@Composable private fun StudySource(excerpt:StudyExcerpt,s:AssistantState,onEdit:(SpeechLine)->Unit){
    s.lines.firstOrNull{it.id==excerpt.sourceId}?.let{line->TextButton(onClick={onEdit(line)},contentPadding=PaddingValues(0.dp)){Text("Ver en el texto ↗",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
}
@Composable private fun StudyEmpty(text:String){Text(text,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
@Composable private fun StudyQuestion(answer:LiveAnswer,onQuestion:(String)->Unit){
    var revealed by rememberSaveable{mutableStateOf(false)}
    Column(Modifier.fillMaxWidth().animateContentSize(),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Text(answer.question,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold,lineHeight=25.sp)
        TextButton(onClick={revealed=!revealed},contentPadding=PaddingValues(0.dp)){Text(if(revealed)"Ocultar explicación"else "Mostrar explicación")}
        AnimatedVisibility(revealed){Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
            SelectionContainer{Text(ReadableAnswer.format(answer.text).ifBlank{answer.status},lineHeight=25.sp)}
            TextButton(onClick={onQuestion(answer.id)},contentPadding=PaddingValues(0.dp)){Text("Abrir respuesta ↗")}
        }}
        HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
    }
}
