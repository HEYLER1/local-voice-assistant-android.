package com.heyler.voicelab

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*

/** Holds Android's consent token. Only AudioRecord is used; no display or image capture. */
class PlaybackCaptureService:Service(){
    private var projection:MediaProjection?=null
    override fun onBind(intent:Intent?)=null
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int{
        if(intent?.action=="STOP"){stop();return START_NOT_STICKY}
        try{
            val manager=getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel("video-audio","Audio del video",NotificationManager.IMPORTANCE_LOW))
            val stop=PendingIntent.getService(this,1,Intent(this,PlaybackCaptureService::class.java).setAction("STOP"),PendingIntent.FLAG_IMMUTABLE)
            val open=PendingIntent.getActivity(this,2,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE)
            val notice=Notification.Builder(this,"video-audio").setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle("V · Audio del video activo").setContentText("Solo audio; detener al salir del asistente.").setContentIntent(open).setOngoing(true).addAction(Notification.Action.Builder(null,"Detener",stop).build()).build()
            startForeground(51,notice,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            @Suppress("DEPRECATION")
            val data=checkNotNull(if(Build.VERSION.SDK_INT>=33)intent?.getParcelableExtra("consent",Intent::class.java)else intent?.getParcelableExtra<Intent>("consent"))
            val p=checkNotNull(getSystemService(MediaProjectionManager::class.java).getMediaProjection(Activity.RESULT_OK,data))
            projection=p;instance=this
            p.registerCallback(object:MediaProjection.Callback(){override fun onStop(){stop()}},Handler(Looper.getMainLooper()))
            onReady?.invoke(p)?:stop()
        }catch(e:Throwable){onError?.invoke("No se pudo capturar el video: ${e.message}");stop()}
        return START_NOT_STICKY
    }
    private fun stop(){val p=projection;projection=null;instance=null;runCatching{p?.stop()};onStopped?.invoke();stopForeground(STOP_FOREGROUND_REMOVE);stopSelf()}
    override fun onDestroy(){if(instance===this){projection?.let{runCatching{it.stop()}};projection=null;instance=null;onStopped?.invoke()};super.onDestroy()}
    companion object{
        private var instance:PlaybackCaptureService?=null
        var onReady:((MediaProjection)->Unit)?=null
        var onStopped:(()->Unit)?=null
        var onError:((String)->Unit)?=null
        fun stopCapture(){instance?.stop()}
    }
}
