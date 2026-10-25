package com.heyler.voicelab

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

internal data class FolderMove(val conversation:Long?)
@Composable internal fun FolderPicker(folders:List<ConversationFolder>,selected:Long?,choose:(Long?)->Unit,create:()->Unit,close:()->Unit){
    AlertDialog(onDismissRequest=close,title={Text("Mover a carpeta")},text={Column(Modifier.heightIn(max=380.dp).verticalScroll(rememberScrollState())){
        TextButton(onClick={choose(null)},modifier=Modifier.fillMaxWidth()){Text(if(selected==null)"✓ Sin carpeta"else "Sin carpeta",Modifier.weight(1f))}
        folders.forEach{folder->TextButton(onClick={choose(folder.id)},modifier=Modifier.fillMaxWidth()){Text(if(selected==folder.id)"✓ ${folder.name}"else folder.name,Modifier.weight(1f))}}
        TextButton(onClick=create){Text("＋ Crear carpeta")}
    }},confirmButton={TextButton(onClick=close){Text("Cerrar")}})
}
