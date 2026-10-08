package com.heyler.voicelab

import android.content.Context
import android.content.Intent
import android.os.Build
import android.speech.*
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import java.util.Locale

internal const val DEFAULT_SPEECH_LANGUAGE="es-ES"
data class LocalSpeechModelState(
    val available:Boolean=false,val checking:Boolean=false,val downloading:Boolean=false,
    val installed:List<String> = emptyList(),val supported:List<String> = emptyList(),val pending:List<String> = emptyList(),
    val message:String="Comprobando idiomas…",val progress:Int?=null
){
    fun hasLanguage(tag:String)=installed.any{it.equals(tag,true)}
    fun spanishLanguage():String?{
        val choices=installed+pending+supported
        return choices.firstOrNull{it.equals(DEFAULT_SPEECH_LANGUAGE,true)}?:choices.firstOrNull{it.startsWith("es-",true)||it.equals("es",true)}?:if(supported.isEmpty())DEFAULT_SPEECH_LANGUAGE else null
    }
    fun needsSpanish():Boolean{val language=spanishLanguage()?:return false;return available&&!hasLanguage(language)&&pending.none{it.equals(language,true)}}
    fun languages()=(installed+supported+pending+DEFAULT_SPEECH_LANGUAGE).distinct().sortedBy{languageName(it)}
}
internal fun languageName(tag:String)=Locale.forLanguageTag(tag).getDisplayName(Locale.forLanguageTag("es")).replaceFirstChar{it.uppercase()}
internal class LocalSpeechModels(private val context:Context){
    companion object{
        fun intent(language:String)=Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE,language)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true)
    }
    suspend fun inspect(language:String):LocalSpeechModelState=withContext(Dispatchers.Main.immediate){
        if(Build.VERSION.SDK_INT<33)return@withContext LocalSpeechModelState(message="La descarga de idiomas requiere Android 13 o superior.")
        if(!SpeechRecognizer.isOnDeviceRecognitionAvailable(context))return@withContext LocalSpeechModelState(message="No hay un servicio local de reconocimiento disponible en este dispositivo.")
        val recognizer=SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        try{
            withTimeoutOrNull(15000){suspendCancellableCoroutine{continuation->
                recognizer.checkRecognitionSupport(intent(language),context.mainExecutor,object:RecognitionSupportCallback{
                    override fun onSupportResult(result:RecognitionSupport){if(continuation.isActive)continuation.resume(LocalSpeechModelState(available=true,installed=result.installedOnDeviceLanguages,supported=result.supportedOnDeviceLanguages,pending=result.pendingOnDeviceLanguages,message="Idiomas comprobados"))}
                    override fun onError(error:Int){if(continuation.isActive)continuation.resume(LocalSpeechModelState(available=true,message=errorMessage(error)))}
                })
            }}?:LocalSpeechModelState(available=true,message="Android no respondió. Pulsa Comprobar idiomas para reintentar.")
        }finally{recognizer.destroy()}
    }
    /** Download is delegated to the installed service. No audio is captured or sent. */
    suspend fun download(language:String,onProgress:(Int)->Unit):String=withContext(Dispatchers.Main.immediate){
        if(Build.VERSION.SDK_INT<33||!SpeechRecognizer.isOnDeviceRecognitionAvailable(context))return@withContext "Descarga local no disponible en este dispositivo."
        if(inspect(language).hasLanguage(language))return@withContext "Descarga completada"
        val recognizer=SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        try{
            if(Build.VERSION.SDK_INT<34){recognizer.triggerModelDownload(intent(language));delay(300);return@withContext "Descarga solicitada a Android. Pulsa Comprobar idiomas para consultar su estado."}
            withTimeoutOrNull(120000){suspendCancellableCoroutine{continuation->
                recognizer.triggerModelDownload(intent(language),context.mainExecutor,object:ModelDownloadListener{
                    override fun onProgress(completedPercent:Int){if(continuation.isActive)onProgress(completedPercent.coerceIn(0,100))}
                    override fun onSuccess(){if(continuation.isActive)continuation.resume("Descarga completada")}
                    override fun onScheduled(){if(continuation.isActive)continuation.resume("Android dejó la descarga pendiente. Comprueba su estado más tarde.")}
                    override fun onError(error:Int){if(continuation.isActive){
                        if(error==SpeechRecognizer.ERROR_CANNOT_LISTEN_TO_DOWNLOAD_EVENTS){recognizer.triggerModelDownload(intent(language));continuation.resume("Descarga solicitada. Este servicio no muestra progreso; comprueba su estado más tarde.")}
                        else continuation.resume(errorMessage(error))
                    }}
                })
            }}?:"La descarga no se confirmó a tiempo. Comprueba su estado más tarde."
        }finally{recognizer.destroy()}
    }
    private fun errorMessage(code:Int)=when(code){
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED->"El servicio no admite este idioma sin conexión."
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE->"El idioma todavía no está instalado."
        SpeechRecognizer.ERROR_NETWORK,SpeechRecognizer.ERROR_NETWORK_TIMEOUT->"Conecta el teléfono a Internet para descargar el idioma."
        SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT->"El servicio no permite consultar sus idiomas."
        SpeechRecognizer.ERROR_CANNOT_LISTEN_TO_DOWNLOAD_EVENTS->"El servicio no informa del progreso de descarga."
        SpeechRecognizer.ERROR_TOO_MANY_REQUESTS,SpeechRecognizer.ERROR_RECOGNIZER_BUSY->"El servicio está ocupado. Reintenta más tarde."
        else->"No se pudo gestionar el idioma (código $code). Reintenta más tarde."
    }
}
