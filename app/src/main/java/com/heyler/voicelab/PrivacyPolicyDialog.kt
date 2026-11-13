package com.heyler.voicelab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

internal const val PRIVACY_POLICY_URL="https://heyler1.github.io/local-voice-assistant-android./privacy-policy.html"
internal const val ACCOUNT_DELETION_URL="https://heyler1.github.io/local-voice-assistant-android./delete-account.html"

@Composable internal fun PrivacyPolicyDialog(onDismiss:()->Unit,onOpenWeb:()->Unit){
    AlertDialog(
        onDismissRequest=onDismiss,
        title={Text("Privacidad")},
        text={
            Column(Modifier.fillMaxWidth().heightIn(max=480.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)){
                PolicySection("Cuenta","Al iniciar con Google, la app recibe el identificador de Firebase y el nombre y correo que Google comparte para iniciar sesión.")
                PolicySection("Transcripciones","El audio se procesa durante la escucha con el reconocedor local de Android. La app no guarda archivos de audio. Las transcripciones, respuestas, resúmenes, carpetas y textos que guardas pueden conservarse en este teléfono y sincronizarse en Firestore bajo tu cuenta.")
                PolicySection("Funciones online","Si solicitas respuestas o resúmenes online, la app envía a Pollinations o AI Horde el texto pertinente de la conversación para generar el resultado. Evita transcribir información sensible. El tratamiento posterior depende de las políticas del proveedor seleccionado.")
                PolicySection("Anuncios y edad","Google Mobile Ads y UMP pueden procesar IP, identificadores del dispositivo o cuenta, interacciones con anuncios, diagnósticos y consentimiento. La edad que eliges para el tratamiento de anuncios se guarda localmente en el teléfono; no se sincroniza a Firestore.")
                PolicySection("Conservación y borrado","Los datos locales permanecen hasta que los borres o elimines los datos de la app. Los datos sincronizados permanecen en Firestore hasta que borres conversaciones o solicites eliminar la cuenta. La opción Eliminar cuenta borra el usuario de Firebase y sus conversaciones y textos guardados de la nube y de este teléfono.")
                PolicySection("Contacto","Privacidad y soporte: soportevestudio@gmail.com. Puedes solicitar la eliminación desde la página web enlazada abajo.")
                Text("Última actualización: 13 de noviembre de 2026",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton={TextButton(onClick=onOpenWeb){Text("Versión web")}},
        dismissButton={TextButton(onClick=onDismiss){Text("Cerrar")}}
    )
}

@Composable private fun PolicySection(title:String,body:String){
    Column(verticalArrangement=Arrangement.spacedBy(3.dp)){
        Text(title,style=MaterialTheme.typography.titleSmall,color=MaterialTheme.colorScheme.primary)
        Text(body,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
