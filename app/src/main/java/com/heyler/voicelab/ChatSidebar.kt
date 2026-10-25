package com.heyler.voicelab

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable internal fun ChatSidebar(s:AssistantState,tab:Int,onClose:()->Unit,onNew:()->Unit,onOpen:(Long)->Unit,onSaved:()->Unit,onSettings:()->Unit,onRename:(Long,String)->Unit={_,_->},onDelete:(Long)->Unit={},onCreateFolder:()->Unit={},onRenameFolder:(Long,String)->Unit={_,_->},onDeleteFolder:(Long)->Unit={},onMove:(Long?)->Unit={},onNewInFolder:(Long?)->Unit={onNew()}){
    var searching by rememberSaveable{mutableStateOf(false)}
    var search by rememberSaveable{mutableStateOf("")}
    var expanded by rememberSaveable{mutableStateOf(listOf<Long>())}
    var knownProjects by remember{mutableStateOf(s.folders.map{it.id})}
    val focus=remember{FocusRequester()}
    val focusManager=LocalFocusManager.current
    val keyboard=LocalSoftwareKeyboardController.current
    val query=if(searching)search.trim() else ""
    var foldedSections by rememberSaveable{mutableStateOf(listOf<String>())}
    var searchFoldedSections by remember(query){mutableStateOf(listOf<String>())}
    val folded=if(query.isBlank())foldedSections else searchFoldedSections
    val foldersOpen="folders" !in folded
    val conversationsOpen="chats" !in folded
    fun toggleSection(section:String){
        val updated=if(section in folded)folded-section else folded+section
        if(query.isBlank())foldedSections=updated else searchFoldedSections=updated
    }
    val matching=s.history.filter{query.isBlank()||it.title.contains(query,true)}
    val projectIds=s.folders.map{it.id}.toSet()
    val standalone=matching.filter{it.folderId !in projectIds}
    val projects=s.folders.filter{folder->query.isBlank()||folder.name.contains(query,true)||matching.any{it.folderId==folder.id}}
    LaunchedEffect(s.folderId,s.conversationKey){s.folderId?.let{if(it !in expanded)expanded=expanded+it}}
    LaunchedEffect(s.folders){
        expanded=(expanded+s.folders.map{it.id}.filter{it !in knownProjects}).distinct().filter{it in projectIds}
        knownProjects=s.folders.map{it.id}
    }
    LaunchedEffect(searching){if(searching){focus.requestFocus();keyboard?.show()}}
    val width=minOf(340.dp,LocalConfiguration.current.screenWidthDp.dp*.88f)
    ModalDrawerSheet(modifier=Modifier.width(width),drawerContainerColor=Color(0xFF10131D),drawerContentColor=Color(0xFFECEEF5),drawerShape=RoundedCornerShape(topEnd=24.dp,bottomEnd=24.dp)){
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 40.dp){
        Column(Modifier.fillMaxSize().imePadding().padding(horizontal=12.dp,vertical=4.dp)){
            Row(Modifier.fillMaxWidth().padding(start=8.dp),verticalAlignment=Alignment.CenterVertically){
                Image(painterResource(R.drawable.brand_mark),"V",Modifier.size(26.dp))
                Spacer(Modifier.weight(1f))
                IconButton(onClick={searching=!searching;if(!searching){search="";focusManager.clearFocus();keyboard?.hide()}},modifier=Modifier.size(40.dp).semantics{contentDescription=if(searching)"Cerrar búsqueda"else "Buscar conversaciones"}){VoiceGlyph("search",Color(0xFF9BA4B7),Modifier.size(20.dp))}
                IconButton(onClick=onNew,modifier=Modifier.size(40.dp).semantics{contentDescription="Nueva conversación"}){VoiceGlyph("new-chat",VoiceAccent,Modifier.size(20.dp))}
                IconButton(onClick=onClose,modifier=Modifier.size(40.dp).semantics{contentDescription="Cerrar menú"}){VoiceGlyph("close",Color(0xFF9BA4B7),Modifier.size(19.dp))}
            }
            if(searching)OutlinedTextField(search,{search=it},placeholder={Text("Buscar conversaciones y carpetas",style=MaterialTheme.typography.bodyMedium)},singleLine=true,shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth().padding(vertical=6.dp).focusRequester(focus).semantics{contentDescription="Texto de búsqueda"})
            LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(bottom=8.dp)){
                stickyHeader(key="folders-header"){
                    SidebarSectionHeader("Carpetas",foldersOpen,{toggleSection("folders")},onCreateFolder)
                }
                if(foldersOpen){
                projects.forEach{project->
                    val open=project.id in expanded||query.isNotBlank()
                    val chats=if(query.isNotBlank()&&project.name.contains(query,true))s.history.filter{it.folderId==project.id}else matching.filter{it.folderId==project.id}
                    item(key="project-${project.id}"){
                        Row(Modifier.fillMaxWidth().padding(start=16.dp).heightIn(min=40.dp).clickable{expanded=if(project.id in expanded)expanded-project.id else expanded+project.id}.padding(start=12.dp),verticalAlignment=Alignment.CenterVertically){
                            Text(if(open)"▾"else "▸",color=Color(0xFF8993A7))
                            Text(project.name,Modifier.weight(1f).padding(start=8.dp),style=MaterialTheme.typography.bodyMedium,fontWeight=FontWeight.Medium,maxLines=1,overflow=TextOverflow.Ellipsis,color=if(s.folderId==project.id)VoiceAccent else Color(0xFFECEEF5))
                            IconButton(onClick={onNewInFolder(project.id)},modifier=Modifier.size(40.dp).semantics{contentDescription="Nueva conversación en ${project.name}"}){VoiceGlyph("new-chat",Color(0xFF9BA4B7),Modifier.size(18.dp))}
                            var menu by remember{mutableStateOf(false)}
                            Box{IconButton(onClick={menu=true},modifier=Modifier.size(40.dp).semantics{contentDescription="Opciones de carpeta ${project.name}"}){Text("⋯",color=Color(0xFF9BA4B7))}
                                DropdownMenu(menu,{menu=false}){
                                    DropdownMenuItem(text={Text("Nueva conversación")},onClick={menu=false;onNewInFolder(project.id)})
                                    DropdownMenuItem(text={Text("Renombrar carpeta")},onClick={menu=false;onRenameFolder(project.id,project.name)})
                                    DropdownMenuItem(text={Text("Eliminar carpeta")},onClick={menu=false;onDeleteFolder(project.id)})
                                }
                            }
                        }
                    }
                    if(open){
                        items(chats,key={"chat-${it.id}"}){entry->ConversationHistoryRow(entry,s.conversationKey==entry.id&&tab==0,true,onOpen,onRename,onMove,onDelete)}
                        if(chats.isEmpty()&&query.isBlank())item(key="empty-${project.id}"){
                            TextButton(onClick={onNewInFolder(project.id)},modifier=Modifier.padding(start=48.dp)){Text("＋ Nueva conversación",color=Color(0xFF9BA4B7),style=MaterialTheme.typography.bodyMedium)}
                        }
                    }
                }
                }
                if(standalone.isNotEmpty())stickyHeader(key="chats-header"){
                    SidebarSectionHeader("Conversaciones",conversationsOpen,{toggleSection("chats")})
                }
                if(conversationsOpen)items(standalone,key={"chat-${it.id}"}){entry->ConversationHistoryRow(entry,s.conversationKey==entry.id&&tab==0,false,onOpen,onRename,onMove,onDelete)}
                if(projects.isEmpty()&&standalone.isEmpty()&&query.isNotBlank())item(key="no-results"){Text("Sin resultados",style=MaterialTheme.typography.bodySmall,color=Color(0xFF8993A7),modifier=Modifier.padding(12.dp))}
            }
            HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
            SidebarLink("Textos guardados","save",tab==1,onSaved)
            SidebarLink("Ajustes","settings",tab==2,onSettings)
        }
        }
    }
}
@Composable private fun SidebarSectionHeader(title:String,expanded:Boolean,onToggle:()->Unit,onCreate:(()->Unit)?=null){
    Row(Modifier.fillMaxWidth().background(Color(0xFF10131D)).heightIn(min=40.dp).clickable(onClick=onToggle).semantics{heading();stateDescription=if(expanded)"Expandido"else "Contraído"}.padding(start=12.dp),verticalAlignment=Alignment.CenterVertically){
        Text(if(expanded)"▾"else "▸",color=Color(0xFF8993A7))
        Text(title,Modifier.weight(1f).padding(start=8.dp),style=MaterialTheme.typography.labelMedium,color=Color(0xFF8993A7))
        if(onCreate!=null)IconButton(onClick=onCreate,modifier=Modifier.size(40.dp).semantics{contentDescription="Crear carpeta"}){Text("＋",color=VoiceAccent,style=MaterialTheme.typography.titleLarge)}
    }
}
@Composable internal fun ConversationHistoryRow(entry:ConversationEntry,active:Boolean,nested:Boolean,onOpen:(Long)->Unit,onRename:(Long,String)->Unit,onMove:(Long?)->Unit,onDelete:(Long)->Unit){
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 40.dp){
    Row(Modifier.fillMaxWidth().padding(start=if(nested)48.dp else 0.dp).background(if(active)VoiceAccent.copy(alpha=.10f) else Color.Transparent,RoundedCornerShape(12.dp)).heightIn(min=40.dp).clickable{onOpen(entry.id)},verticalAlignment=Alignment.CenterVertically){
        Text(entry.title,color=if(active)VoiceAccent else Color(0xFFCDD2DE),style=MaterialTheme.typography.bodyMedium,fontWeight=if(active)FontWeight.Medium else FontWeight.Normal,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.weight(1f).padding(start=12.dp,end=4.dp))
        var menu by remember{mutableStateOf(false)}
        Box{
            IconButton(onClick={menu=true},modifier=Modifier.size(40.dp).semantics{contentDescription="Opciones de ${entry.title}"}){Text("⋯",color=Color(0xFF8993A7),style=MaterialTheme.typography.titleLarge)}
            DropdownMenu(menu,{menu=false}){
                DropdownMenuItem(text={Text("Renombrar")},onClick={menu=false;onRename(entry.id,entry.title)})
                DropdownMenuItem(text={Text("Mover a carpeta")},onClick={menu=false;onMove(entry.id)})
                DropdownMenuItem(text={Text("Eliminar",color=MaterialTheme.colorScheme.error)},onClick={menu=false;onDelete(entry.id)})
            }
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
