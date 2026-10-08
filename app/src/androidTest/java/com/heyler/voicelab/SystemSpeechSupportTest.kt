package com.heyler.voicelab

import android.content.Intent
import android.speech.*
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.json.JSONObject

class SystemSpeechSupportTest {
    @Test fun downloadSpanishModel() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val result=JSONObject()
        val done=CountDownLatch(1)
        var recognizer:SpeechRecognizer?=null
        instrumentation.runOnMainSync {
            check(SpeechRecognizer.isOnDeviceRecognitionAvailable(context))
            recognizer=SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            recognizer!!.triggerModelDownload(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE,"es-ES")
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true),context.mainExecutor,
                object:ModelDownloadListener {
                    override fun onProgress(completedPercent:Int){android.util.Log.i("SpanishModelDownload","Progress: $completedPercent")}
                    override fun onSuccess(){result.put("status","downloaded");done.countDown()}
                    override fun onScheduled(){result.put("status","scheduled");done.countDown()}
                    override fun onError(error:Int){result.put("status","error").put("error",error);done.countDown()}
                })
        }
        if(!done.await(10,TimeUnit.MINUTES))result.put("status","timeout")
        instrumentation.runOnMainSync{recognizer?.destroy()}
        context.getExternalFilesDir(null)!!.resolve("system-speech-download.json").writeText(result.toString(2))
        println("SYSTEM_SPEECH_DOWNLOAD: $result")
    }
    @Test fun reportLocalLanguageSupport() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val result=JSONObject()
        val done=CountDownLatch(1)
        var recognizer:SpeechRecognizer?=null
        instrumentation.runOnMainSync {
            val available=SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
            result.put("onDeviceAvailable",available)
            if(!available)done.countDown() else {
                recognizer=SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                recognizer!!.checkRecognitionSupport(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE,"es-ES")
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true),context.mainExecutor,
                    object:RecognitionSupportCallback {
                        override fun onSupportResult(s:RecognitionSupport){
                            result.put("installed",org.json.JSONArray(s.installedOnDeviceLanguages))
                            result.put("supported",org.json.JSONArray(s.supportedOnDeviceLanguages))
                            result.put("pending",org.json.JSONArray(s.pendingOnDeviceLanguages))
                            done.countDown()
                        }
                        override fun onError(error:Int){result.put("error",error);done.countDown()}
                    })
            }
        }
        if(!done.await(20,TimeUnit.SECONDS))result.put("timeout",true)
        instrumentation.runOnMainSync{recognizer?.destroy()}
        context.getExternalFilesDir(null)!!.resolve("system-speech-support.json").writeText(result.toString(2))
        println("SYSTEM_SPEECH_SUPPORT: $result")
    }
}
