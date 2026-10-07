package com.heyler.voicelab

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable fun KnowledgeMapPanel(s:AssistantState,onQuestion:(String)->Unit,onEdit:(SpeechLine)->Unit,onSave:(String,String)->Unit,onRefresh:()->Unit){
    val outline=remember(s.lines){KnowledgeOrganizer.build(s.lines)}
    Surface(color=Color(0xFF33254D),shape=RoundedCornerShape(24.dp),modifier=Modifier.fillMaxWidth()){
        Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            Text("MAPA DE TU CONVERSACIÓN",style=MaterialTheme.typography.labelSmall,color=VoiceAccent)
            Text(outline.topic,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.SemiBold)
            Text("Un esquema para repasar lo hablado",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    MapBranch("Resumen",VoiceAccent){
        Text(s.summary.ifBlank{if(s.lines.sumOf{it.text.length}<100)"Conversación breve: revisa los fragmentos organizados abajo."else "Resumen pendiente. Puedes actualizarlo con el contexto reciente."},style=MaterialTheme.typography.bodyMedium)
        Text("Síntesis del modelo · revisa su contenido",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Row{TextButton(onClick=onRefresh){Text("Actualizar")};if(s.summary.isNotBlank())TextButton(onClick={onSave(s.summary,"Resumen generado, revisar antes de guardar")}){Text("Guardar")}}
    }
    if(outline.ideas.isNotEmpty())MapBranch("Ideas del audio · ${outline.ideas.size}",VoiceGreen){
        outline.ideas.forEach{line->Text(line.text,style=MaterialTheme.typography.bodyMedium);TextButton(onClick={onEdit(line)}){Text("Ver / corregir fragmento")}}
    }
    if(s.answers.isNotEmpty())MapBranch("Preguntas y respuestas · ${s.answers.size}",Color(0xFF8DBDFF)){
        s.answers.forEach{a->TextButton(onClick={onQuestion(a.id)},contentPadding=PaddingValues(0.dp)){Text(a.question)};Text(if(a.text.isBlank())"Pendiente de respuesta"else ReadableAnswer.format(a.text),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
    }
    if(outline.figures.isNotEmpty())MapBranch("Cifras para comprobar · ${outline.figures.size}",Color(0xFFEBC189)){
        outline.figures.forEach{line->Text(line.text,style=MaterialTheme.typography.bodySmall);TextButton(onClick={onEdit(line)}){Text("Revisar en la transcripción")}}
    }
    Text("El esquema usa el contexto conservado. Las ramas literales no validan lo dicho; el resumen y las respuestas pueden contener errores.",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
}
@Composable private fun MapBranch(title:String,color:Color,content:@Composable ColumnScope.()->Unit){
    var expanded by rememberSaveable{mutableStateOf(false)}
    Row(Modifier.fillMaxWidth()){
        Canvas(Modifier.width(24.dp).height(40.dp)){drawLine(color.copy(alpha=.5f),Offset(size.width*.35f,0f),Offset(size.width*.35f,size.height*.6f),strokeWidth=2.dp.toPx());drawLine(color.copy(alpha=.5f),Offset(size.width*.35f,size.height*.6f),Offset(size.width,size.height*.6f),strokeWidth=2.dp.toPx());drawCircle(color,3.dp.toPx(),Offset(size.width*.35f,size.height*.6f))}
        Surface(color=Color(0xFF171D2A),shape=RoundedCornerShape(20.dp),modifier=Modifier.weight(1f)){Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){Row{Text(title,style=MaterialTheme.typography.titleMedium,color=color,fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f));TextButton(onClick={expanded=!expanded},contentPadding=PaddingValues(0.dp)){Text(if(expanded)"Ocultar"else "Ver",color=color)}};if(expanded)content()}}
    }
}
