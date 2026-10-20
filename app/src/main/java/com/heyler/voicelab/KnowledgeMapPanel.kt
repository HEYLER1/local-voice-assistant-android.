package com.heyler.voicelab

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable fun StudySummaryPanel(s:AssistantState,onQuestion:(String)->Unit,onEdit:(SpeechLine)->Unit,onSave:(String,String)->Unit,onRefresh:()->Unit){
    val guide=remember(s.lines){StudyGuide.build(s.lines)}
    val lesson=remember(s.lines){LessonMemory.build(s.lines)}
    var section by rememberSaveable{mutableIntStateOf(0)}
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
        Text("Resumen",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f))
        TextButton(onClick=onRefresh,enabled=s.onlineAnswers&&!s.generating&&s.lines.sumOf{it.text.length}>=100&&!(s.transcriptionOnly&&(s.listening||s.starting))){Text("Actualizar")}
    }
    if(s.lines.isNotEmpty())Text("${if(lesson.topics.isEmpty())"Contexto orientativo"else "Tema"} · ${lesson.topic}",style=MaterialTheme.typography.bodySmall,color=VoiceGreen)
    if(s.summary.isNotBlank()){
        SelectionContainer{Text(ReadableAnswer.format(s.summary),style=MaterialTheme.typography.bodyLarge,lineHeight=27.sp)}
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
            Text("Síntesis de fragmentos seleccionados de la clase",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.weight(1f))
            TextButton(onClick={onSave(s.summary,"Resumen generado, revisar antes de guardar")},contentPadding=PaddingValues(0.dp)){Text("Guardar")}
        }
    }else Text(if(s.generating)"Preparando la síntesis…"else if(!s.onlineAnswers)"La síntesis online está desactivada. Puedes repasar las ideas extraídas abajo."else "Toca Actualizar para sintetizar lo hablado. Ya puedes repasar las ideas extraídas abajo.",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
    if(s.summaryError.isNotBlank())Text(s.summaryError,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)
    HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(22.dp)){
        listOf(0 to "Ideas",4 to "Tareas ${lesson.tasks.size}",5 to "Examen ${lesson.exams.size}",1 to "Conceptos",2 to "Repaso",3 to "Cifras").forEach{(index,label)->
            Column(Modifier.clickable{section=index}.semantics{selected=section==index;role=Role.Tab}.padding(top=6.dp),horizontalAlignment=Alignment.CenterHorizontally){
                Text(label,color=if(section==index)VoiceAccent else MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.labelLarge,modifier=Modifier.padding(bottom=10.dp))
                Box(Modifier.width(28.dp).height(2.dp).background(if(section==index)VoiceAccent else MaterialTheme.colorScheme.background))
            }
        }
    }
    when(section){
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
    Text("Extractos del audio · revisa posibles errores de transcripción y del modelo.",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
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
