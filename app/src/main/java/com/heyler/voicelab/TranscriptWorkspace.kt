package com.heyler.voicelab

import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Transcript reading and copy have no dependency on answer generation or storage actions. */
@Composable internal fun TranscriptWorkspace(
    s:AssistantState,
    onAnswer:(String)->Unit,
    onCopy:(String)->Unit,
    modifier:Modifier=Modifier,
    contentPadding:PaddingValues=PaddingValues(0.dp)
){
    val literal=s.lines.joinToString(" "){it.text}
    val copyText=if(s.voiceLabels)s.lines.joinToString("\n\n"){"${it.voice}\n${it.text}"}else literal
    var copyFeedback by remember{mutableIntStateOf(0)}
    var copied by remember{mutableStateOf(false)}
    LaunchedEffect(copyFeedback){if(copyFeedback>0){copied=true;delay(1200);copied=false}}
    val scroll=rememberScrollState()
    val dragged by scroll.interactionSource.collectIsDraggedAsState()
    var following by remember{mutableStateOf(true)}
    val tolerance=with(LocalDensity.current){48.dp.toPx()}
    val nearEnd by remember(tolerance){derivedStateOf{scroll.maxValue-scroll.value<=tolerance}}
    LaunchedEffect(dragged,nearEnd){if(dragged)following=false else if(nearEnd)following=true}
    LaunchedEffect(literal,s.listening,following){
        if(s.listening&&following&&!dragged){withFrameNanos{};scroll.animateScrollTo(scroll.maxValue)}
    }
    Column(modifier.fillMaxSize().verticalScroll(scroll).semantics{contentDescription="Texto de la conversación"}.padding(contentPadding).padding(horizontal=20.dp).padding(top=4.dp,bottom=12.dp)){
        if(s.status.startsWith("No se pudo")||s.status.startsWith("Permiso")||s.status.startsWith("Error")||s.status.startsWith("Instala una voz")||s.status.startsWith("Espera a")){
            Text(s.status,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(bottom=8.dp))
        }
        Column(Modifier.fillMaxWidth()){
            if(s.voiceLabels)s.lines.forEach{line->
                Text(line.voice,style=MaterialTheme.typography.labelMedium,color=VoiceAccent)
                SelectionContainer{Text(linkedTranscript(line.text,s.answers,onAnswer),lineHeight=26.sp,fontSize=16.sp,color=Color(0xFFE1E5F0))}
                Spacer(Modifier.height(12.dp))
            }else if(literal.isNotBlank())SelectionContainer{Text(linkedTranscript(literal,s.answers,onAnswer),lineHeight=26.sp,fontSize=16.sp,color=Color(0xFFE1E5F0))}
        }
        if(literal.isNotBlank())Row(Modifier.fillMaxWidth().padding(top=8.dp),horizontalArrangement=Arrangement.End,verticalAlignment=Alignment.CenterVertically){
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 40.dp){
                IconButton(onClick={onCopy(copyText);copyFeedback++},modifier=Modifier.size(40.dp).semantics{contentDescription="Copiar transcripción"}){
                    VoiceGlyph(if(copied)"check"else "copy",if(copied)VoiceGreen else MaterialTheme.colorScheme.onSurfaceVariant,Modifier.size(19.dp))
                }
            }
        }
    }
}
