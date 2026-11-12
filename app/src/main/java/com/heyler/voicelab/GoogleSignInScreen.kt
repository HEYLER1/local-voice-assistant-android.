package com.heyler.voicelab

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable internal fun GoogleSignInScreen(
    configured:Boolean,
    busy:Boolean,
    error:String,
    onSignIn:(GoogleSignInEntry)->Unit
){
    Column(
        Modifier.fillMaxSize().background(VoiceBackground).verticalScroll(rememberScrollState()).systemBarsPadding().imePadding().padding(horizontal=28.dp,vertical=32.dp),
        horizontalAlignment=Alignment.CenterHorizontally,
        verticalArrangement=Arrangement.Center
    ){
        Image(painterResource(R.drawable.brand_mark),contentDescription="V · Asistente",modifier=Modifier.size(76.dp))
        Spacer(Modifier.height(20.dp))
        Text("Tu espacio de aprendizaje",fontSize=26.sp,fontWeight=FontWeight.SemiBold,textAlign=TextAlign.Center,color=MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(10.dp))
        Text(
            "Inicia sesión para mantener tus conversaciones separadas por cuenta. Las transcripciones siguen guardadas en este teléfono.",
            style=MaterialTheme.typography.bodyMedium,textAlign=TextAlign.Center,color=MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(28.dp))
        if(configured){
            Button(onClick={onSignIn(GoogleSignInEntry.GOOGLE_BUTTON)},enabled=!busy,modifier=Modifier.fillMaxWidth().height(54.dp),shape=RoundedCornerShape(16.dp),colors=ButtonDefaults.buttonColors(containerColor=Color.White,contentColor=Color(0xFF303238))){
                if(busy)CircularProgressIndicator(Modifier.size(20.dp),strokeWidth=2.dp,color=VoiceAccent)
                else Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
                    Text("G",fontSize=21.sp,fontWeight=FontWeight.Bold,color=Color(0xFF4285F4))
                    Text("Continuar con Google",fontWeight=FontWeight.Medium)
                }
            }
            TextButton(onClick={onSignIn(GoogleSignInEntry.ACCOUNT_PICKER)},enabled=!busy,modifier=Modifier.padding(top=4.dp)){Text("Elegir una cuenta de Google",color=VoiceAccent)}
            if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall,textAlign=TextAlign.Center,modifier=Modifier.padding(top=12.dp))
        }else{
            Surface(color=Color(0xFF1D2230),shape=RoundedCornerShape(20.dp),modifier=Modifier.fillMaxWidth()){
                Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){
                    Text("Falta conectar Firebase",fontWeight=FontWeight.SemiBold,color=VoiceAccent)
                    Text("Para activar el acceso real con Google, configura una vez:",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    SetupStep("1","Registra el paquete Android com.heyler.voicelab en un proyecto Firebase.")
                    SetupStep("2","Activa Google en Authentication y agrega la huella SHA-1 de la app.")
                    SetupStep("3","Descarga el google-services.json actualizado y colócalo en android/app/.")
                    Text("Al añadir el archivo, vuelve a compilar la app. No se necesitan claves privadas en el código.",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable private fun SetupStep(number:String,text:String){
    Row(horizontalArrangement=Arrangement.spacedBy(9.dp),verticalAlignment=Alignment.Top){
        Text(number,color=VoiceAccent,fontWeight=FontWeight.Bold)
        Text(text,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurface)
    }
}

@Composable internal fun GoogleSignOutDialog(onConfirm:()->Unit,onDismiss:()->Unit){
    AlertDialog(
        onDismissRequest=onDismiss,
        title={Text("Cerrar sesión")},
        text={Text("La escucha se detendrá y las conversaciones permanecerán guardadas en este teléfono. Volverán a aparecer al iniciar con la misma cuenta.")},
        confirmButton={TextButton(onClick=onConfirm){Text("Cerrar sesión")}},
        dismissButton={TextButton(onClick=onDismiss){Text("Cancelar")}}
    )
}
