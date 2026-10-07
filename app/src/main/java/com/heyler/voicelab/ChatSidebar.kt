package com.heyler.voicelab

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable internal fun ChatSidebar(s:AssistantState,tab:Int,onClose:()->Unit,onNew:()->Unit,onOpen:(Long)->Unit,onSaved:()->Unit,onSettings:()->Unit){
    var search by remember{mutableStateOf("")}
    val today=LocalDate.now()
    val sections=s.history.filter{it.title.contains(search,true)}.groupBy{
        val date=Instant.ofEpochMilli(it.updated).atZone(ZoneId.systemDefault()).toLocalDate()
        when {date==today->"Hoy";date==today.minusDays(1)->"Ayer";date>=today.minusDays(7)->"Últimos 7 días";else->"Anteriores"}
    }
    ModalDrawerSheet(modifier=Modifier.widthIn(max=340.dp).fillMaxWidth(.88f),drawerContainerColor=Color(0xFF10131D),drawerContentColor=Color(0xFFECEEF5),drawerShape=RoundedCornerShape(topEnd=28.dp,bottomEnd=28.dp)){
        Column(Modifier.fillMaxSize().padding(horizontal=18.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
            Row(Modifier.fillMaxWidth().padding(top=12.dp),verticalAlignment=Alignment.CenterVertically){
                Image(painterResource(R.drawable.brand_mark),"V",Modifier.size(36.dp))
                Column(Modifier.weight(1f).padding(start=10.dp)){Text("Tu espacio",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold);Text("V · voz y conocimiento",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                IconButton(onClick=onClose,modifier=Modifier.semantics{contentDescription="Cerrar menú"}){VoiceGlyph("close",Color(0xFF9BA4B7),Modifier.size(20.dp))}
            }
            Surface(onClick=onNew,shape=RoundedCornerShape(16.dp),color=Color(0xFFBDAAFF),modifier=Modifier.fillMaxWidth()){
                Row(Modifier.padding(horizontal=16.dp,vertical=15.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){VoiceGlyph("add",Color(0xFF211B31),Modifier.size(20.dp));Text("Nueva conversación",color=Color(0xFF211B31),style=MaterialTheme.typography.labelLarge,fontWeight=FontWeight.SemiBold)}
            }
            TextField(search,{search=it},placeholder={Text("Buscar en tus chats",style=MaterialTheme.typography.bodyMedium)},leadingIcon={VoiceGlyph("search",Color(0xFF8993A7),Modifier.size(19.dp))},trailingIcon={if(search.isNotEmpty())IconButton(onClick={search=""},modifier=Modifier.semantics{contentDescription="Limpiar búsqueda"}){VoiceGlyph("close",Color(0xFF8993A7),Modifier.size(18.dp))}},singleLine=true,shape=RoundedCornerShape(16.dp),colors=TextFieldDefaults.colors(focusedContainerColor=Color(0xFF1B202D),unfocusedContainerColor=Color(0xFF1B202D),focusedIndicatorColor=Color.Transparent,unfocusedIndicatorColor=Color.Transparent),modifier=Modifier.fillMaxWidth())
            LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp),contentPadding=PaddingValues(bottom=12.dp)){
                sections.forEach{(label,chats)->
                    item(key="section-$label"){Text(label,style=MaterialTheme.typography.labelSmall,color=Color(0xFF808B9F),modifier=Modifier.padding(start=10.dp,top=10.dp,bottom=6.dp))}
                    items(chats,key={it.id}){entry->
                        val active=s.conversationKey==entry.id&&tab==0
                        Surface(onClick={onOpen(entry.id)},shape=RoundedCornerShape(15.dp),color=if(active)Color(0xFF30283F)else Color.Transparent,modifier=Modifier.fillMaxWidth()){
                            Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(11.dp)){
                                Box(Modifier.size(32.dp).background(if(active)Color(0xFF46385E)else Color(0xFF202633),CircleShape),contentAlignment=Alignment.Center){VoiceGlyph("chat",if(active)VoiceAccent else Color(0xFF8993A7),Modifier.size(16.dp))}
                                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){
                                    Text(entry.title,color=Color(0xFFECEEF5),style=MaterialTheme.typography.bodyMedium,fontWeight=if(active)FontWeight.SemiBold else FontWeight.Normal,maxLines=1,overflow=TextOverflow.Ellipsis)
                                    Text("${entry.fragments} fragmentos · ${entry.questions} preguntas",style=MaterialTheme.typography.labelSmall,color=Color(0xFF8993A7))
                                }
                                if(active)Box(Modifier.size(5.dp).background(VoiceAccent,CircleShape))
                            }
                        }
                    }
                }
                if(sections.isEmpty())item{Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text(if(search.isBlank())"Empieza algo nuevo"else "Sin resultados",style=MaterialTheme.typography.titleSmall);Text(if(search.isBlank())"Tus clases e ideas tendrán su propio espacio aquí."else "Prueba con otro nombre.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
            }
            Surface(color=Color(0xFF191E2A),shape=RoundedCornerShape(18.dp),modifier=Modifier.fillMaxWidth()){
                Column(Modifier.padding(4.dp)){
                    SidebarLink("Textos guardados","save",tab==1,onSaved)
                    SidebarLink("Ajustes","settings",tab==2,onSettings)
                }
            }
            Row(Modifier.fillMaxWidth().padding(bottom=12.dp),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(5.dp).background(VoiceGreen,CircleShape));Text("  Privado · en tu dispositivo",style=MaterialTheme.typography.labelSmall,color=Color(0xFF808B9F))}
        }
    }
}
@Composable private fun SidebarLink(label:String,icon:String,active:Boolean,onClick:()->Unit){
    Surface(onClick=onClick,color=if(active)Color(0xFF30283F)else Color.Transparent,shape=RoundedCornerShape(14.dp),modifier=Modifier.fillMaxWidth()){
        Row(Modifier.padding(horizontal=12.dp,vertical=13.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){VoiceGlyph(icon,if(active)VoiceAccent else Color(0xFF9BA4B7),Modifier.size(18.dp));Text(label,style=MaterialTheme.typography.bodyMedium,color=if(active)VoiceAccent else Color(0xFFCDD2DE))}
    }
}
