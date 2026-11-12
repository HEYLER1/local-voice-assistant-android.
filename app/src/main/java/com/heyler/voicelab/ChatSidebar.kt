package com.heyler.voicelab

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.rotate
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
@Composable internal fun ChatSidebar(s:AssistantState,tab:Int,onClose:()->Unit,onNew:()->Unit,onOpen:(Long)->Unit,onSettings:()->Unit,onRename:(Long,String)->Unit={_,_->},onDelete:(Long)->Unit={},onCreateFolder:()->Unit={},onRenameFolder:(Long,String)->Unit={_,_->},onDeleteFolder:(Long)->Unit={},onMove:(Long?)->Unit={},onNewInFolder:(Long?)->Unit={onNew()},account:GoogleAccountInfo?=null,onAccount:()->Unit={},onSignOut:()->Unit={},onPin:(Long,Boolean)->Unit={_,_->}){
    var searching by rememberSaveable{mutableStateOf(false)}
    var search by rememberSaveable{mutableStateOf("")}
    var expanded by rememberSaveable{mutableStateOf(s.folders.map{it.id})}
    var projectsOpen by rememberSaveable{mutableStateOf(true)}
    var knownProjects by remember{mutableStateOf(s.folders.map{it.id})}
    var initializedProjects by remember{mutableStateOf(false)}
    val focus=remember{FocusRequester()}
    val focusManager=LocalFocusManager.current
    val keyboard=LocalSoftwareKeyboardController.current
    val query=if(searching)search.trim() else ""
    var accountExpanded by rememberSaveable{mutableStateOf(false)}
    val accountInitials=remember(account){
        val words=account?.displayName.orEmpty().trim().split(Regex("\\s+")).filter{it.isNotBlank()}
        when{
            words.size>=2->"${words.first().first()}${words.last().first()}"
            words.isNotEmpty()->words.first().take(2)
            account?.email.orEmpty().isNotBlank()->account?.email.orEmpty().substringBefore('@').filter{it.isLetterOrDigit()}.take(2).ifBlank{"V"}
            else->"V"
        }.uppercase()
    }
    val matching=s.history.filter{query.isBlank()||it.title.contains(query,true)}
    val projectIds=s.folders.map{it.id}.toSet()
    val pinned=matching.filter{it.pinned}
    val standalone=matching.filter{it.folderId !in projectIds&&!it.pinned}
    val projects=s.folders.filter{folder->query.isBlank()||folder.name.contains(query,true)||matching.any{it.folderId==folder.id}}
    LaunchedEffect(s.folderId,s.conversationKey){s.folderId?.let{if(it !in expanded)expanded=expanded+it}}
    LaunchedEffect(s.folders){
        val ids=s.folders.map{it.id}
        val toExpand=if(initializedProjects)ids.filter{it !in knownProjects}else ids
        expanded=(expanded+toExpand).distinct().filter{it in projectIds}
        knownProjects=ids
        initializedProjects=true
    }
    LaunchedEffect(searching){if(searching){focus.requestFocus();keyboard?.show()}}
    val width=minOf(340.dp,LocalConfiguration.current.screenWidthDp.dp*.88f)
    ModalDrawerSheet(modifier=Modifier.width(width),drawerContainerColor=Color(0xFF10131D),drawerContentColor=Color(0xFFECEEF5),drawerShape=RoundedCornerShape(topEnd=24.dp,bottomEnd=24.dp)){
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 40.dp){
        Column(Modifier.fillMaxSize().imePadding().padding(horizontal=12.dp,vertical=4.dp)){
            Row(Modifier.fillMaxWidth().padding(start=8.dp),verticalAlignment=Alignment.CenterVertically){
                Image(painterResource(R.drawable.brand_mark),"V",Modifier.size(26.dp))
                Spacer(Modifier.weight(1f))
                IconButton(onClick={searching=!searching;if(!searching){search="";focusManager.clearFocus();keyboard?.hide()}},modifier=Modifier.size(40.dp).semantics{contentDescription=if(searching)"Cerrar búsqueda"else "Buscar conversaciones"}){VoiceGlyph(if(searching)"close"else "search",Color(0xFF9BA4B7),Modifier.size(20.dp))}
                IconButton(onClick=onClose,modifier=Modifier.size(40.dp).semantics{contentDescription="Cerrar menú"}){VoiceGlyph("close",Color(0xFF9BA4B7),Modifier.size(19.dp))}
            }
            AnimatedVisibility(searching,enter=expandVertically(expandFrom=Alignment.Top)+fadeIn(),exit=shrinkVertically(shrinkTowards=Alignment.Top)+fadeOut()){
                OutlinedTextField(search,{search=it},placeholder={Text("Buscar chats o carpetas",style=MaterialTheme.typography.bodyMedium)},singleLine=true,shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth().padding(vertical=6.dp).focusRequester(focus).semantics{contentDescription="Texto de búsqueda"})
            }
            Box(Modifier.weight(1f).fillMaxWidth()){
            LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(bottom=82.dp)){
                stickyHeader(key="projects-header"){
                    ProjectsHeader(projectsOpen,{projectsOpen=!projectsOpen},onCreateFolder)
                }
                if(projectsOpen){
                projects.forEach{project->
                    val open=project.id in expanded||query.isNotBlank()
                    val chats=if(query.isNotBlank()&&project.name.contains(query,true))s.history.filter{it.folderId==project.id}else matching.filter{it.folderId==project.id}
                    item(key="project-${project.id}"){
                        Row(Modifier.fillMaxWidth().padding(start=4.dp).heightIn(min=46.dp).clickable{expanded=if(project.id in expanded)expanded-project.id else expanded+project.id}.padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically){
                            VoiceGlyph("folder",if(s.folderId==project.id)VoiceAccent else Color(0xFF9BA4B7),Modifier.size(20.dp))
                            Text(project.name,Modifier.weight(1f).padding(start=12.dp),style=MaterialTheme.typography.bodyMedium,fontWeight=FontWeight.Normal,maxLines=1,overflow=TextOverflow.Ellipsis,color=if(s.folderId==project.id)VoiceAccent else Color(0xFFECEEF5))
                            VoiceGlyph("chevron",Color(0xFF8993A7),Modifier.padding(horizontal=8.dp).size(15.dp).rotate(if(open)0f else -90f))
                            var menu by remember{mutableStateOf(false)}
                            Box{IconButton(onClick={menu=true},modifier=Modifier.size(36.dp).semantics{contentDescription="Opciones de carpeta ${project.name}"}){Text("⋯",color=Color(0xFF9BA4B7))}
                                DropdownMenu(menu,{menu=false}){
                                    DropdownMenuItem(text={Text("Nueva conversación")},onClick={menu=false;onNewInFolder(project.id)})
                                    DropdownMenuItem(text={Text("Renombrar carpeta")},onClick={menu=false;onRenameFolder(project.id,project.name)})
                                    DropdownMenuItem(text={Text("Eliminar carpeta")},onClick={menu=false;onDeleteFolder(project.id)})
                                }
                            }
                        }
                    }
                    if(open){
                        items(chats,key={"chat-${it.id}"}){entry->ConversationHistoryRow(entry,s.conversationKey==entry.id&&tab==0,true,onOpen,onRename,onMove,onDelete,onPin=onPin)}
                        if(chats.isEmpty()&&query.isBlank())item(key="empty-${project.id}"){
                            TextButton(onClick={onNewInFolder(project.id)},modifier=Modifier.padding(start=48.dp)){
                                VoiceGlyph("chat",Color(0xFF8993A7),Modifier.size(17.dp));Spacer(Modifier.width(9.dp));Text("Nueva conversación",color=Color(0xFF9BA4B7),style=MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
                }
                if(pinned.isNotEmpty()){
                    stickyHeader(key="pinned-header"){
                        Text("Fijados",Modifier.fillMaxWidth().background(Color(0xFF10131D)).padding(start=12.dp,top=18.dp,bottom=8.dp),style=MaterialTheme.typography.bodyMedium,fontWeight=FontWeight.Normal,color=Color(0xFF9BA4B7))
                    }
                    items(pinned,key={"pinned-${it.id}"}){entry->ConversationHistoryRow(entry,s.conversationKey==entry.id&&tab==0,false,onOpen,onRename,onMove,onDelete,showIcon=false,onPin=onPin)}
                }
                stickyHeader(key="recent-header"){
                    Text("recientes",Modifier.fillMaxWidth().background(Color(0xFF10131D)).padding(start=12.dp,top=18.dp,bottom=8.dp),style=MaterialTheme.typography.bodyMedium,fontWeight=FontWeight.Normal,color=Color(0xFF9BA4B7))
                }
                items(standalone,key={"recent-${it.id}"}){entry->ConversationHistoryRow(entry,s.conversationKey==entry.id&&tab==0,false,onOpen,onRename,onMove,onDelete,showIcon=false,onPin=onPin)}
                if(projects.isEmpty()&&standalone.isEmpty()&&query.isNotBlank())item(key="no-results"){Text("Sin resultados",style=MaterialTheme.typography.bodySmall,color=Color(0xFF8993A7),modifier=Modifier.padding(12.dp))}
            }
            FloatingActionButton(onClick=onNew,modifier=Modifier.align(Alignment.BottomEnd).padding(end=8.dp,bottom=12.dp).size(54.dp).semantics{contentDescription="Nueva conversación"},shape=CircleShape,containerColor=VoiceAccent,contentColor=Color(0xFF201337)){
                VoiceGlyph("new-chat",Color(0xFF201337),Modifier.size(23.dp))
            }
            }
            HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
            Column{
                AnimatedVisibility(accountExpanded,enter=expandVertically(expandFrom=Alignment.Bottom)+fadeIn(),exit=shrinkVertically(shrinkTowards=Alignment.Bottom)+fadeOut()){
                    Column(Modifier.fillMaxWidth().padding(bottom=8.dp).background(Color(0xFF202534),RoundedCornerShape(16.dp)).padding(6.dp)){
                        AccountMenuItem("account","Mi cuenta",account?.email?.ifBlank{"Cuenta conectada"}?:"Sin sesión"){accountExpanded=false;onAccount()}
                        AccountMenuItem("settings","Configuración"){accountExpanded=false;onSettings()}
                        AccountMenuItem("logout","Cerrar sesión",danger=true){accountExpanded=false;onSignOut()}
                    }
                }
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                    Surface(color=Color(0xFF965BB5),shape=CircleShape,modifier=Modifier.size(44.dp).semantics{
                        contentDescription="Abrir menú de cuenta"
                        stateDescription=if(accountExpanded)"Menú abierto"else"Menú cerrado"
                    }.clickable{accountExpanded=!accountExpanded}){
                        Box(contentAlignment=Alignment.Center){Text(accountInitials,color=Color.White,style=MaterialTheme.typography.labelLarge,fontWeight=FontWeight.SemiBold)}
                    }
                }
            }
        }
        }
    }
}
@Composable private fun AccountMenuItem(icon:String,label:String,subtitle:String?=null,danger:Boolean=false,onClick:()->Unit){
    Row(Modifier.fillMaxWidth().heightIn(min=if(subtitle==null)48.dp else 60.dp).semantics{contentDescription=label}.clickable(onClick=onClick).padding(horizontal=10.dp),verticalAlignment=Alignment.CenterVertically){
        Box(Modifier.size(36.dp).background(if(danger)MaterialTheme.colorScheme.error.copy(alpha=.12f) else Color(0xFF302642),RoundedCornerShape(11.dp)),contentAlignment=Alignment.Center){
            VoiceGlyph(icon,if(danger)MaterialTheme.colorScheme.error else VoiceAccent,Modifier.size(19.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.Center){
            Text(label,color=if(danger)MaterialTheme.colorScheme.error else Color(0xFFECEEF5),style=MaterialTheme.typography.bodyLarge,maxLines=1)
            if(subtitle!=null)Text(subtitle,color=Color(0xFF9BA4B7),style=MaterialTheme.typography.labelSmall,maxLines=1,overflow=TextOverflow.Ellipsis)
        }
    }
}
@Composable private fun ProjectsHeader(expanded:Boolean,onToggle:()->Unit,onCreate:()->Unit){
    Row(Modifier.fillMaxWidth().background(Color(0xFF10131D)).heightIn(min=42.dp).clickable(onClick=onToggle).semantics{heading();contentDescription="Proyectos";stateDescription=if(expanded)"Desplegados"else"Contraídos"}.padding(start=12.dp),verticalAlignment=Alignment.CenterVertically){
        Text("Proyectos",Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium,fontWeight=FontWeight.Normal,color=Color(0xFF9BA4B7))
        IconButton(onClick=onCreate,modifier=Modifier.size(38.dp).semantics{contentDescription="Crear proyecto"}){VoiceGlyph("add",Color(0xFF9BA4B7),Modifier.size(19.dp))}
        VoiceGlyph("chevron",Color(0xFF9BA4B7),Modifier.padding(horizontal=10.dp).size(16.dp).rotate(if(expanded)0f else -90f))
    }
}
@Composable internal fun ConversationHistoryRow(entry:ConversationEntry,active:Boolean,nested:Boolean,onOpen:(Long)->Unit,onRename:(Long,String)->Unit,onMove:(Long?)->Unit,onDelete:(Long)->Unit,showIcon:Boolean=true,onPin:(Long,Boolean)->Unit={_,_->}){
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 40.dp){
    Row(Modifier.fillMaxWidth().padding(start=if(nested)48.dp else 0.dp).background(if(active)Color(0xFF252A38) else Color.Transparent,RoundedCornerShape(14.dp)).heightIn(min=48.dp).clickable{onOpen(entry.id)},verticalAlignment=Alignment.CenterVertically){
        if(showIcon)VoiceGlyph("chat",if(active)VoiceAccent else Color(0xFF8993A7),Modifier.padding(start=if(nested)8.dp else 14.dp).size(17.dp))
        Text(entry.title,color=if(active)Color(0xFFF2EFFF) else Color(0xFFCDD2DE),style=MaterialTheme.typography.bodyMedium,fontWeight=FontWeight.Normal,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.weight(1f).padding(start=if(showIcon)8.dp else 16.dp,end=4.dp))
        var menu by remember{mutableStateOf(false)}
        Box{
            IconButton(onClick={menu=true},modifier=Modifier.size(40.dp).semantics{contentDescription="Opciones de ${entry.title}"}){Text("⋯",color=Color(0xFF8993A7),style=MaterialTheme.typography.titleLarge)}
            DropdownMenu(menu,{menu=false}){
                DropdownMenuItem(text={Text(if(entry.pinned)"Quitar de fijados" else "Fijar chat")},onClick={menu=false;onPin(entry.id,!entry.pinned)})
                DropdownMenuItem(text={Text("Renombrar")},onClick={menu=false;onRename(entry.id,entry.title)})
                DropdownMenuItem(text={Text("Mover a carpeta")},onClick={menu=false;onMove(entry.id)})
                DropdownMenuItem(text={Text("Eliminar",color=MaterialTheme.colorScheme.error)},onClick={menu=false;onDelete(entry.id)})
            }
        }
    }
    }
}
