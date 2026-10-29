package com.heyler.voicelab

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal object ResearchedMapDocument {
    fun text(map:ResearchedMap)=buildString{
        append(map.title+"\n\n"+map.overview+"\n\n")
        map.branches.forEach{branch->append(branch.title+"\n");branch.points.forEach{point->
            append("${point.subtitle}: ${point.text}\n")
            append(if(point.origin=="recording")"(Grabación)\n"else if(point.origin=="model")"(Ampliación de IA, sin verificación externa)\n"else "(Ampliación: ${point.references.joinToString()})\n")
        };append('\n')}
        if(map.references.isNotEmpty())append("Fuentes consultadas\n")
        map.references.forEach{append("[${it.id}] ${it.title} · ${it.publisher}\n${it.url}\n${it.license}\n")}
    }
}
@Composable internal fun ResearchedMapReading(map:ResearchedMap,state:AssistantState,onEdit:(SpeechLine)->Unit){
    SelectionContainer{Text(map.overview,lineHeight=25.sp)}
    map.branches.forEach{branch->
        Text(branch.title,color=VoiceAccent,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(top=12.dp))
        ResearchedBranchReading(branch,map,state,onEdit)
    }
}
@Composable internal fun ResearchedBranchReading(branch:MapBranch,map:ResearchedMap,state:AssistantState,onEdit:(SpeechLine)->Unit){
    val uri=LocalUriHandler.current
    branch.points.forEach{point->
        Text(point.subtitle,fontWeight=FontWeight.SemiBold)
        SelectionContainer{Text(point.text,lineHeight=25.sp)}
        if(point.origin=="recording"){
            Text("De la grabación",color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.labelSmall)
            point.recording.forEach{id->state.lines.firstOrNull{it.id==id}?.let{line->TextButton(onClick={onEdit(line)},contentPadding=PaddingValues(0.dp)){Text("Ver en la transcripción")}}}
        }else if(point.origin=="model"){
            Text("Ampliación de IA · sin verificación externa",color=VoiceGreen,style=MaterialTheme.typography.labelSmall)
        }else{
            Text("Ampliación con fuentes",color=VoiceGreen,style=MaterialTheme.typography.labelSmall)
            point.references.forEach{id->map.references.firstOrNull{it.id==id}?.let{source->
                TextButton(onClick={if(TopicResearch.validUrl(source.url))uri.openUri(source.url)},contentPadding=PaddingValues(0.dp)){Text("${source.title} ↗")}
                Text("${source.publisher} · ${source.license}",color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.labelSmall)
            }}
        }
    }
}
