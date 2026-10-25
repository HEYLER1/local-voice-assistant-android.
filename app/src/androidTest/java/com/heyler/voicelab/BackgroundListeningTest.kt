package com.heyler.voicelab

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.os.ParcelFileDescriptor
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

/** Synthetic PCM only. Exercises the real Android recognizer and foreground-service lifecycle. */
class BackgroundListeningTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context=instrumentation.targetContext
    private suspend fun await(message:String,condition:()->Boolean){
        try{withTimeout(25000){while(!condition())delay(50)}}catch(e:TimeoutCancellationException){throw AssertionError(message,e)}
    }
    private suspend fun feed(output:java.io.OutputStream,name:String){
        val samples=Wav16.read(context.assets.open("fixtures/$name").use{it.readBytes()})
        for(offset in samples.indices step 3200){
            val n=minOf(3200,samples.size-offset);val pcm=ByteArray(n*2)
            for(i in 0 until n){val v=(samples[offset+i]*32768).toInt().coerceIn(-32768,32767);pcm[2*i]=v.toByte();pcm[2*i+1]=(v shr 8).toByte()}
            withContext(Dispatchers.IO){output.write(pcm)};delay(200)
        }
        repeat(15){withContext(Dispatchers.IO){output.write(ByteArray(6400))};delay(200)}
    }
    @Test fun recognitionSurvivesActivityDestructionAndNotificationStopsIt()=runBlocking{
        val options=context.getSharedPreferences("assistant-options",0)
        val before=options.all
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName,Manifest.permission.RECORD_AUDIO)
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName,Manifest.permission.POST_NOTIFICATIONS)
        var scenario:ActivityScenario<MainActivity>?=ActivityScenario.launch(MainActivity::class.java)
        val vm=(context.applicationContext as AssistantApplication).assistant
        val pipe=ParcelFileDescriptor.createPipe()
        val output=ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])
        try{
            scenario!!.onActivity{vm.temporaryConversation();vm.setAudioSource("mic");vm.setSpeechEngine("system");vm.setTranscriptionOnly(true);vm.setBackgroundListening(true);vm.setFloatingControl(false)}
            await("Language manager busy"){!vm.state.value.localSpeechModels.checking&&!vm.state.value.localSpeechModels.downloading}
            scenario!!.onActivity{ListeningService.startFromAudio(it,pipe[0]);pipe[0].close()}
            await("Service/recognizer did not start: ${vm.state.value.status}"){vm.state.value.backgroundServiceActive&&vm.state.value.listening}
            val notifications=context.getSystemService(NotificationManager::class.java)
            fun notificationText()=notifications.activeNotifications.firstOrNull{it.id==ListeningService.NOTICE}?.notification?.extras?.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString().orEmpty()
            await("Foreground listening has the wrong status"){notificationText()=="Transcribiendo · toca para abrir el chat"}
            assertFalse(vm.state.value.status.contains("segundo plano",true))
            feed(output,"1.wav")
            await("First phrase missing"){vm.state.value.lines.any{ConversationRules.key(it.text).contains("viernes")}}
            val oldKey=vm.state.value.conversationKey
            scenario!!.close();scenario=null
            assertFalse(vm.state.value.appVisible)
            assertTrue("Closing activity stopped the microphone",vm.state.value.listening)
            assertTrue(vm.state.value.backgroundServiceActive)
            await("Background notification did not update"){notificationText().contains("segundo plano")}
            feed(output,"5.wav")
            await("No new transcription after leaving the activity: ${vm.state.value.status}"){vm.state.value.lines.any{ConversationRules.key(it.text).contains("circulo")}}
            val notice=context.getSystemService(NotificationManager::class.java).activeNotifications.first{it.id==ListeningService.NOTICE}
            assertNotNull(notice.notification.contentIntent)
            assertTrue(notice.notification.actions.any{it.title.toString()=="Detener"})
            scenario=ActivityScenario.launch(MainActivity::class.java)
            scenario!!.onActivity{assertSame(vm,it.assistant);assertEquals(oldKey,it.assistant.state.value.conversationKey);assertTrue(it.assistant.state.value.listening)}
            await("Foreground status did not return when reopening V"){notificationText()=="Transcribiendo · toca para abrir el chat"}
            scenario!!.moveToState(Lifecycle.State.CREATED)
            notice.notification.actions.first{it.title.toString()=="Detener"}.actionIntent.send()
            await("Notification did not stop microphone"){!vm.state.value.listening&&!vm.state.value.starting&&!vm.state.value.backgroundServiceActive}
            val count=vm.state.value.lines.size
            delay(1200)
            assertEquals("Late results after Stop",count,vm.state.value.lines.size)
            assertTrue(context.getSystemService(NotificationManager::class.java).activeNotifications.none{it.id==ListeningService.NOTICE})
        }finally{
            instrumentation.runOnMainSync{vm.suspendSession()}
            runCatching{output.close()};runCatching{pipe[0].close()};scenario?.let{it.moveToState(Lifecycle.State.RESUMED);it.close()}
            instrumentation.runOnMainSync{vm.clearSession(keepHistory=true);vm.setBackgroundListening(before["background-listening"] as? Boolean?:true);vm.setTranscriptionOnly(before["transcription-only"] as? Boolean?:false);vm.setFloatingControl(before["floating-control"] as? Boolean?:false);vm.setSpeechEngine(before["speech-engine"] as? String?:"moonshine");vm.setAudioSource(before["audio-source"] as? String?:"mic")}
            options.edit().apply{for(key in listOf("background-listening","transcription-only","floating-control","speech-engine","audio-source")){when(val value=before[key]){is Boolean->putBoolean(key,value);is String->putString(key,value);null->remove(key)}}}.commit()
        }
    }
    @Test fun backgroundOptOutStopsRecordingAndService()=runBlocking{
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName,Manifest.permission.RECORD_AUDIO)
        val vm=(context.applicationContext as AssistantApplication).assistant
        val background=vm.state.value.backgroundListening
        val engine=vm.state.value.speechEngine
        val audioSource=vm.state.value.audioSource
        val only=vm.state.value.transcriptionOnly
        val pipe=ParcelFileDescriptor.createPipe()
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        try{
            scenario.onActivity{vm.temporaryConversation();vm.setAudioSource("mic");vm.setSpeechEngine("system");vm.setTranscriptionOnly(true);vm.setBackgroundListening(false)}
            await("Language busy"){!vm.state.value.localSpeechModels.checking&&!vm.state.value.localSpeechModels.downloading}
            scenario.onActivity{ListeningService.startFromAudio(it,pipe[0]);pipe[0].close()}
            await("Recording not ready: ${vm.state.value.status}"){vm.state.value.listening&&vm.state.value.backgroundServiceActive}
            scenario.moveToState(Lifecycle.State.CREATED)
            await("Opt-out left a recording alive"){!vm.state.value.listening&&!vm.state.value.starting&&!vm.state.value.backgroundServiceActive}
        }finally{
            instrumentation.runOnMainSync{vm.suspendSession();vm.setBackgroundListening(background);vm.setSpeechEngine(engine);vm.setAudioSource(audioSource);vm.setTranscriptionOnly(only)}
            runCatching{pipe[1].close()};runCatching{pipe[0].close()};scenario.moveToState(Lifecycle.State.RESUMED);scenario.close()
        }
    }
    @Test fun floatingControlsAppearOutsideChatAndStopRecording()=runBlocking{
        val originalOverlay=android.provider.Settings.canDrawOverlays(context)
        fun overlayPermission(mode:String){
            instrumentation.uiAutomation.executeShellCommand("appops set ${context.packageName} SYSTEM_ALERT_WINDOW $mode").use{android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()}
        }
        // Emulator-only test permission, restored in finally. Production always uses Android's settings screen.
        overlayPermission("allow")
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName,Manifest.permission.RECORD_AUDIO)
        val vm=(context.applicationContext as AssistantApplication).assistant
        val old=vm.state.value
        val pipe=ParcelFileDescriptor.createPipe()
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        fun bubble()=android.view.inspector.WindowInspector.getGlobalWindowViews().firstOrNull{it.contentDescription=="Controles de escucha de V"}
        try{
            scenario.onActivity{vm.temporaryConversation();vm.setAudioSource("mic");vm.setSpeechEngine("system");vm.setTranscriptionOnly(true);vm.setBackgroundListening(true);vm.setFloatingControl(true)}
            await("Language busy"){!vm.state.value.localSpeechModels.checking&&!vm.state.value.localSpeechModels.downloading}
            scenario.onActivity{ListeningService.startFromAudio(it,pipe[0]);pipe[0].close()}
            await("Recording not ready: ${vm.state.value.status}"){vm.state.value.listening&&vm.state.value.backgroundServiceActive}
            instrumentation.runOnMainSync{assertNull("Overlay should stay hidden inside the chat",bubble())}
            scenario.moveToState(Lifecycle.State.CREATED)
            var shown=false
            await("No floating control appeared"){instrumentation.runOnMainSync{shown=bubble()!=null};shown}
            instrumentation.runOnMainSync{
                val panel=bubble() as android.widget.LinearLayout
                panel.getChildAt(0).performClick()
                assertEquals(android.view.View.VISIBLE,panel.getChildAt(1).visibility)
                assertEquals("Volver a la conversación",panel.getChildAt(1).contentDescription)
                panel.getChildAt(2).performClick()
                assertNull("Stopping should remove the overlay",bubble())
            }
            await("Floating Stop left capture alive"){!vm.state.value.listening&&!vm.state.value.backgroundServiceActive}
        }finally{
            instrumentation.runOnMainSync{vm.suspendSession();vm.setBackgroundListening(old.backgroundListening);vm.setFloatingControl(old.floatingControl);vm.setSpeechEngine(old.speechEngine);vm.setAudioSource(old.audioSource);vm.setTranscriptionOnly(old.transcriptionOnly)}
            runCatching{pipe[1].close()};runCatching{pipe[0].close()};scenario.moveToState(Lifecycle.State.RESUMED);scenario.close()
            overlayPermission(if(originalOverlay)"allow"else "default")
        }
    }
    @Test fun cancellingQueuedServiceStartDoesNotReopenMicrophone()=runBlocking{
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName,Manifest.permission.RECORD_AUDIO)
        val vm=(context.applicationContext as AssistantApplication).assistant
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        try{
            scenario.onActivity{vm.temporaryConversation();ListeningService.start(it);vm.pause()}
            delay(7000)
            assertFalse(vm.state.value.listening)
            assertFalse(vm.state.value.starting)
            assertFalse(vm.state.value.backgroundServiceActive)
        }finally{instrumentation.runOnMainSync{vm.suspendSession()};scenario.moveToState(Lifecycle.State.RESUMED);scenario.close()}
    }
}
