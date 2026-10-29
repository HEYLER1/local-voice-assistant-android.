package com.heyler.voicelab

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Started from the visible activity, before opening audio. Never restarts a microphone after process death. */
class ListeningService:Service(){
    private val assistant get()=(application as AssistantApplication).assistant
    private var projection:MediaProjection?=null
    private var wakeLock:PowerManager.WakeLock?=null
    private var scope:CoroutineScope?=null
    private var bubble:FloatingVoiceControl?=null
    override fun onBind(intent:Intent?)=null
    override fun onConfigurationChanged(newConfig:android.content.res.Configuration){super.onConfigurationChanged(newConfig);bubble?.onBoundsChanged()}
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int{
        if(intent?.action==STOP){assistant.stopFromNotification();finish();return START_NOT_STICKY}
        if(instance===this)return START_NOT_STICKY
        if(intent==null){stopSelf(startId);return START_NOT_STICKY}
        try{
            val manager=getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL,"Escucha activa",NotificationManager.IMPORTANCE_LOW).apply{description="Controles de la grabación activa de V";setSound(null,null)})
            val consent=parcelable(intent,"consent",Intent::class.java)
            startForeground(NOTICE,notification("Preparando transcripción…"),if(consent==null)ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            // Acknowledge every foreground start, including a start cancelled before delivery.
            // No audio is opened for a cancelled request.
            if(intent.getLongExtra("request",-1)!=request||!requested){stopForeground(STOP_FOREGROUND_REMOVE);stopSelf(startId);return START_NOT_STICKY}
            instance=this
            assistant.setBackgroundServiceActive(true)
            acquireWakeLock()
            if(consent!=null){
                projection=checkNotNull(getSystemService(MediaProjectionManager::class.java).getMediaProjection(Activity.RESULT_OK,consent)).also{p->
                    p.registerCallback(object:MediaProjection.Callback(){override fun onStop(){if(instance===this@ListeningService)assistant.stopFromNotification()}},Handler(Looper.getMainLooper()))
                }
            }
            // Clear only our preparation flag before letting the engine own the capture state.
            assistant.state.value=assistant.state.value.copy(starting=false)
            val source=pendingAudio.also{pendingAudio=null}
            if(source!=null)assistant.listenFromAudio(source)else assistant.listen(projection)
            bubble=FloatingVoiceControl(this,{id->openConversation(id)},{assistant.stopFromNotification()})
            scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
            scope?.launch{
                var previousAnswers:List<LiveAnswer>?=null
                var previews=emptyList<FloatingQuestion>()
                assistant.state.map{
                    if(it.answers!==previousAnswers){previousAnswers=it.answers;previews=FloatingQuestions.ready(it.answers)}
                    SessionUi(it.listening,it.starting,it.appVisible,it.floatingControl,it.backgroundListening,it.conversationKey,previews)
                }.distinctUntilChanged().collect{s->
                    if(!s.listening&&!s.starting){finish();return@collect}
                    if(!s.visible&&!s.background){assistant.background();return@collect}
                    manager.notify(NOTICE,notification(if(!s.listening)"Preparando transcripción…"else if(s.visible)"Transcribiendo · toca para abrir el chat"else "Transcribiendo en segundo plano · toca para volver"))
                    bubble?.show(!s.visible&&s.background&&s.floating,s.starting,s.questions,s.conversation)
                }
            }
        }catch(e:Throwable){
            assistant.stopFromNotification()
            assistant.state.value=assistant.state.value.copy(status="No se pudo iniciar la escucha: ${e.message}")
            finish()
        }
        return START_NOT_STICKY
    }
    @Suppress("DEPRECATION")
    private fun <T:Parcelable> parcelable(intent:Intent,name:String,type:Class<T>):T?=
        if(Build.VERSION.SDK_INT>=33)intent.getParcelableExtra(name,type)else intent.getParcelableExtra(name)
    @SuppressLint("WakelockTimeout")
    private fun acquireWakeLock(){wakeLock=getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"V:activeTranscription").apply{setReferenceCounted(false);acquire()}}
    private fun notification(message:String):Notification{
        val stop=PendingIntent.getService(this,31,Intent(this,ListeningService::class.java).setAction(STOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val open=PendingIntent.getActivity(this,32,Intent(this,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle("V · Escucha activa").setContentText(message).setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).setCategory(Notification.CATEGORY_SERVICE).setVisibility(Notification.VISIBILITY_PRIVATE).addAction(Notification.Action.Builder(null,"Abrir chat",open).build()).addAction(Notification.Action.Builder(null,"Detener",stop).build()).build()
    }
    private fun openConversation(answerId:String?=null){startActivity(Intent(this,MainActivity::class.java).putExtra(MainActivity.FLOATING_ANSWER_ID,answerId).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))}
    private fun finish(){
        if(instance===this)instance=null
        scope?.cancel();scope=null
        bubble?.close();bubble=null
        val previous=projection;projection=null;runCatching{previous?.stop()}
        wakeLock?.let{if(it.isHeld)it.release()};wakeLock=null
        assistant.setBackgroundServiceActive(false)
        stopForeground(STOP_FOREGROUND_REMOVE);stopSelf()
    }
    override fun onDestroy(){if(instance===this){instance=null;assistant.stopFromNotification()};finish();super.onDestroy()}
    private data class SessionUi(val listening:Boolean,val starting:Boolean,val visible:Boolean,val floating:Boolean,val background:Boolean,val conversation:Long,val questions:List<FloatingQuestion>)
    companion object{
        private const val CHANNEL="v-listening"
        internal const val NOTICE=52
        internal const val STOP="com.heyler.voicelab.STOP_LISTENING"
        private var instance:ListeningService?=null
        private var requested=false
        private var request=0L
        private var pendingAudio:ParcelFileDescriptor?=null
        fun start(context:Context,consent:Intent?=null){startRequest(context,consent,null)}
        internal fun startFromAudio(context:Context,source:ParcelFileDescriptor){startRequest(context,null,source)}
        private fun startRequest(context:Context,consent:Intent?,source:ParcelFileDescriptor?){
            val vm=(context.applicationContext as AssistantApplication).assistant
            if(requested&&(instance!=null||vm.state.value.starting))return
            if(vm.state.value.listening||vm.state.value.starting||vm.state.value.preparing)return
            if(vm.state.value.audioSource=="video"&&consent==null){vm.state.value=vm.state.value.copy(status="Falta el permiso de captura del video. No se activó el micrófono.");return}
            requested=true;request++;pendingAudio=source?.let{ParcelFileDescriptor.dup(it.fileDescriptor)}
            vm.state.value=vm.state.value.copy(starting=true,status=if(vm.state.value.audioSource=="video")"Preparando audio del video…"else "Iniciando micrófono…")
            try{context.startForegroundService(Intent(context,ListeningService::class.java).putExtra("request",request).putExtra("audio-source",vm.state.value.audioSource).putExtra("consent",consent))}
            catch(e:Throwable){requested=false;runCatching{pendingAudio?.close()};pendingAudio=null;vm.state.value=vm.state.value.copy(starting=false,status="No se pudo iniciar la escucha: ${e.message}")}
        }
        fun stopCapture(){
            requested=false;request++;runCatching{pendingAudio?.close()};pendingAudio=null
            // A queued foreground start must be acknowledged by onStartCommand before stopping.
            instance?.finish()
        }
    }
}
