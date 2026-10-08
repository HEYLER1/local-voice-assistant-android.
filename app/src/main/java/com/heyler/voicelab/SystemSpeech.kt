package com.heyler.voicelab

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.*
import android.speech.*
import java.util.concurrent.Executors
import java.util.concurrent.Future
import kotlinx.coroutines.*

/** One on-device session fed through a pipe; never restarts the microphone between segments. */
internal class SystemSpeech(private val context:Context,private val onText:(Long,String,Boolean)->Unit,private val onReady:()->Unit,private val onFailure:(String)->Unit,private val onInterrupted:((String)->Unit)?=null,private val onAudioLevel:(Float)->Unit={}){
    private val handler=Handler(Looper.getMainLooper())
    private var recognizer:SpeechRecognizer?=null
    private var running=false
    @Volatile private var lastFrame=0L
    @Volatile private var lastWrite=0L
    @Volatile private var voiceMillis=0L
    private var lastResult=0L
    private var monitorEnabled=false
    private val healthCheck=object:Runnable{override fun run(){if(!running||!monitorEnabled)return;val reason=SpeechHealth.stalled(SystemClock.elapsedRealtime(),lastFrame,lastWrite,lastResult,voiceMillis);if(reason!=null)interrupt(reason)else handler.postDelayed(this,1000)}}
    private var stopped=CompletableDeferred<Unit>().apply{complete(Unit)}
    private var captureFinished:java.util.concurrent.CountDownLatch?=null
    private var lineId=System.nanoTime()
    private var lastText=""
    private var readEnd:ParcelFileDescriptor?=null
    private var writeEnd:ParcelFileDescriptor?=null
    private var recorder:AudioRecord?=null
    private var worker:java.util.concurrent.ExecutorService?=null
    private var capture:Future<*>?=null
    internal var sessionStarts=0;private set
    @Suppress("MissingPermission")
    fun start(audioSource:ParcelFileDescriptor?=null){
        check(Looper.myLooper()==Looper.getMainLooper())
        check(stopped.isCompleted){"La captura anterior todavía se está cerrando"}
        if(Build.VERSION.SDK_INT<33){onFailure("La escucha continua de Android local requiere Android 13 o superior. Selecciona Moonshine.");return}
        if(!SpeechRecognizer.isOnDeviceRecognitionAvailable(context)){onFailure("Este dispositivo no ofrece reconocimiento local. Selecciona Moonshine.");return}
        lastFrame=SystemClock.elapsedRealtime();lastWrite=lastFrame;lastResult=lastFrame;voiceMillis=0;monitorEnabled=audioSource==null
        stopped=CompletableDeferred();running=true;lineId=System.nanoTime();lastText="";sessionStarts=0
        try{
            readEnd=audioSource ?: ParcelFileDescriptor.createPipe().let{readEnd=it[0];writeEnd=it[1];it[0]}
            recognizer=SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            recognizer!!.setRecognitionListener(object:RecognitionListener{
                override fun onReadyForSpeech(params:Bundle?){if(running)onReady()}
                override fun onBeginningOfSpeech(){}
                override fun onRmsChanged(rmsdB:Float){}
                override fun onBufferReceived(buffer:ByteArray?){}
                override fun onEndOfSpeech(){}
                override fun onEvent(eventType:Int,params:Bundle?){}
                override fun onPartialResults(results:Bundle?){emit(results,false)}
                override fun onSegmentResults(results:Bundle){if(!running)return;emit(results,true);lastText="";lineId++}
                override fun onEndOfSegmentedSession(){if(running)interrupt("El servicio terminó la sesión continua")}
                override fun onResults(results:Bundle?){if(!running)return;emit(results,true);lastText="";fail("El servicio no mantuvo la sesión segmentada. Selecciona Moonshine para escucha continua.")}
                override fun onError(error:Int){if(!running)return
                    if(error in listOf(SpeechRecognizer.ERROR_SERVER,SpeechRecognizer.ERROR_SERVER_DISCONNECTED,SpeechRecognizer.ERROR_RECOGNIZER_BUSY,SpeechRecognizer.ERROR_SPEECH_TIMEOUT,SpeechRecognizer.ERROR_NO_MATCH)){interrupt("El servicio local interrumpió la sesión (código $error)");return}
                    fail(when(error){
                    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE->"El servicio local no tiene español instalado. Descarga el idioma antes de escuchar."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS->"Revisa el permiso del micrófono."
                    else->"Reconocimiento local detenido (código $error). No se reiniciará automáticamente ni se usará la nube."
                })}
            })
            sessionStarts++
            recognizer!!.startListening(sessionIntent(checkNotNull(readEnd)))
            if(audioSource==null){
                val minimum=AudioRecord.getMinBufferSize(16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT)
                check(minimum>0){"Formato de micrófono no disponible"}
                val input=AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,maxOf(minimum,6400))
                recorder=input;check(input.state==AudioRecord.STATE_INITIALIZED){"No se pudo abrir el micrófono"}
                input.startRecording()
                val output=ParcelFileDescriptor.AutoCloseOutputStream(checkNotNull(writeEnd))
                worker=Executors.newSingleThreadExecutor()
                val finished=java.util.concurrent.CountDownLatch(1);captureFinished=finished
                capture=worker!!.submit{
                    val buffer=ByteArray(3200)
                    var lastLevel=0L
                    try{output.use{while(!Thread.currentThread().isInterrupted){val count=input.read(buffer,0,buffer.size);check(count>0){"Captura interrumpida"};lastFrame=SystemClock.elapsedRealtime()
                        var energy=0.0
                        for(i in 0 until count-1 step 2){val sample=((buffer[i].toInt() and 255) or (buffer[i+1].toInt() shl 8)).toShort().toDouble()/32768;energy+=sample*sample}
                        val rms=if(count>1)kotlin.math.sqrt(energy/(count/2)).toFloat()else 0f
                        if(rms>.003f)voiceMillis+=count*1000L/32000
                        if(lastFrame-lastLevel>=200){lastLevel=lastFrame;handler.post{if(running)onAudioLevel((rms*5).coerceIn(0f,1f))}}
                        it.write(buffer,0,count);lastWrite=SystemClock.elapsedRealtime()}}}
                    catch(e:Exception){handler.post{if(running)interrupt("Se interrumpió el canal de audio local: ${e.message}")}}
                    finally{buffer.fill(0);runCatching{input.release()};finished.countDown()}
                }
                handler.postDelayed(healthCheck,1000)
            }
        }catch(e:Exception){fail("No se pudo iniciar la sesión continua: ${e.message}")}
    }
    private fun emit(bundle:Bundle?,final:Boolean){if(!running)return;val text=bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty();if(text.isNotBlank()){lastResult=SystemClock.elapsedRealtime();voiceMillis=0;lastText=text;onText(lineId,text,final)}}
    private fun interrupt(message:String){android.util.Log.w("LocalSpeechHealth",message);if(onInterrupted!=null && monitorEnabled){stop();onInterrupted.invoke(message)}else fail(message)}
    private fun fail(message:String){stop();onFailure(message)}
    suspend fun awaitStopped(){stopped.await()}
    fun stop(){
        if(!running && recognizer==null && recorder==null)return
        val completion=stopped
        handler.removeCallbacks(healthCheck)
        val pending=if(running)lastText else "";running=false;lastText=""
        runCatching{writeEnd?.close()};writeEnd=null
        val input=recorder;recorder=null
        runCatching{input?.stop()}
        val executor=worker;worker=null
        executor?.shutdown();capture=null
        val finished=captureFinished;captureFinished=null
        val old=recognizer;recognizer=null
        runCatching{old?.cancel()}
        runCatching{readEnd?.close()};readEnd=null
        // Let SpeechRecognizer dispatch cancel before destroy clears its pending queue.
        handler.post{
            runCatching{old?.destroy()}
            if(finished==null){runCatching{input?.release()};completion.complete(Unit)}
            else Thread{
                finished.await()
                completion.complete(Unit)
            }.apply{isDaemon=true;start()}
        }
        if(pending.isNotBlank())onText(lineId,pending,true)
    }
    companion object{
        internal fun sessionIntent(source:ParcelFileDescriptor)=Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE,"es-ES")
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true)
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true)
            .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE,source)
            .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT,1)
            .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING,AudioFormat.ENCODING_PCM_16BIT)
            .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE,16000)
            .putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION,RecognizerIntent.EXTRA_AUDIO_SOURCE)
    }
}
