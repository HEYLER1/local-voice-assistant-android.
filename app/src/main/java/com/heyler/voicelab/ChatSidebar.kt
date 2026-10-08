package com.heyler.voicelab

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable internal fun ChatSidebar(s:AssistantState,tab:Int,onClose:()->Unit,onNew:()->Unit,onOpen:(Long)->Unit,onSaved:()->Unit,onSettings:()->Unit,onRename:(Long,String)->Unit={_,_->},onDelete:(Long)->Unit={},onRenameCurrent:()->Unit={}){
    var search by remember{mutableStateOf("")}
    val today=LocalDate.now()
    val sections=s.history.filter{it.title.contains(search,true)}.groupBy{
        val date=Instant.ofEpochMilli(it.updated).atZone(ZoneId.systemDefault()).toLocalDate()
        when {date==today->"Hoy";date==today.minusDays(1)->"Ayer";date>=today.minusDays(7)->"Últimos 7 días";else->"Anteriores"}
    }
    val width=minOf(340.dp,LocalConfiguration.current.screenWidthDp.dp*.86f)
    ModalDrawerSheet(modifier=Modifier.width(width),drawerContainerColor=Color(0xFF10131D),drawerContentColor=Color(0xFFECEEF5),drawerShape=RoundedCornerShape(topEnd=24.dp,bottomEnd=24.dp)){
        Column(Modifier.fillMaxSize().imePadding().padding(horizontal=16.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                Image(painterResource(R.drawable.brand_mark),"V",Modifier.size(26.dp))
                Text("Historial",Modifier.weight(1f).padding(start=8.dp),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold,maxLines=1)
                IconButton(onClick=onNew,modifier=Modifier.size(40.dp).semantics{contentDescription="Nueva conversación"}){VoiceGlyph("new-chat",VoiceAccent,Modifier.size(20.dp))}
                var currentMenu by remember{mutableStateOf(false)}
                Box{
                    IconButton(onClick={currentMenu=true},modifier=Modifier.size(40.dp).semantics{contentDescription="Opciones de conversación"}){Text("⋯",style=MaterialTheme.typography.titleLarge)}
                    DropdownMenu(currentMenu,{currentMenu=false}){DropdownMenuItem(text={Text("Renombrar conversación actual")},onClick={currentMenu=false;onRenameCurrent()})}
                }
                IconButton(onClick=onClose,modifier=Modifier.size(40.dp).semantics{contentDescription="Cerrar menú"}){VoiceGlyph("close",Color(0xFF9BA4B7),Modifier.size(19.dp))}
            }
            TextField(search,{search=it},placeholder={Text("Buscar chats",style=MaterialTheme.typography.bodyMedium)},leadingIcon={VoiceGlyph("search",Color(0xFF8993A7),Modifier.size(18.dp))},trailingIcon={if(search.isNotEmpty())IconButton(onClick={search=""},modifier=Modifier.semantics{contentDescription="Limpiar búsqueda"}){VoiceGlyph("close",Color(0xFF8993A7),Modifier.size(18.dp))}},singleLine=true,colors=TextFieldDefaults.colors(focusedContainerColor=Color.Transparent,unfocusedContainerColor=Color.Transparent,focusedIndicatorColor=VoiceAccent,unfocusedIndicatorColor=MaterialTheme.colorScheme.outlineVariant),modifier=Modifier.fillMaxWidth().semantics{contentDescription="Buscar conversaciones"})
            LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(bottom=8.dp)){
                sections.forEach{(label,chats)->
                    item(key="section-$label"){Text(label,style=MaterialTheme.typography.labelSmall,color=Color(0xFF808B9F),modifier=Modifier.padding(start=12.dp,top=16.dp,bottom=4.dp))}
                    items(chats,key={it.id}){entry->
                        val active=s.conversationKey==entry.id&&tab==0
                        Row(Modifier.fillMaxWidth().heightIn(min=48.dp).clickable{onOpen(entry.id)},verticalAlignment=Alignment.CenterVertically){
                            Box(Modifier.width(3.dp).height(20.dp).background(if(active)VoiceAccent else Color.Transparent,RoundedCornerShape(2.dp)))
                            Text(entry.title,color=if(active)VoiceAccent else Color(0xFFECEEF5),style=MaterialTheme.typography.bodyMedium,fontWeight=if(active)FontWeight.SemiBold else FontWeight.Normal,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.weight(1f).padding(start=9.dp,end=4.dp))
                            var menu by remember{mutableStateOf(false)}
                            Box{
                                IconButton(onClick={menu=true},modifier=Modifier.size(48.dp).semantics{contentDescription="Opciones de ${entry.title}"}){Text("⋯",color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.titleLarge)}
                                DropdownMenu(menu,{menu=false}){
                                    DropdownMenuItem(text={Text("Renombrar")},onClick={menu=false;onRename(entry.id,entry.title)})
                                    DropdownMenuItem(text={Text("Eliminar",color=MaterialTheme.colorScheme.error)},onClick={menu=false;onDelete(entry.id)})
                                }
                            }
                        }
                    }
                }
                if(sections.isEmpty())item{Text(if(search.isBlank())"Tus conversaciones aparecerán aquí."else "No hay conversaciones con ese nombre.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(horizontal=12.dp,vertical=20.dp))}
            }
            HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
            Column{
                SidebarLink("Textos guardados","save",tab==1,onSaved)
                SidebarLink("Ajustes","settings",tab==2,onSettings)
            }
        }
    }
}
@Composable private fun SidebarLink(label:String,icon:String,active:Boolean,onClick:()->Unit){
    Row(Modifier.fillMaxWidth().heightIn(min=48.dp).clickable(onClick=onClick).padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
        VoiceGlyph(icon,if(active)VoiceAccent else Color(0xFF9BA4B7),Modifier.size(18.dp))
        Text(label,style=MaterialTheme.typography.bodyMedium,color=if(active)VoiceAccent else Color(0xFFCDD2DE))
    }
}
