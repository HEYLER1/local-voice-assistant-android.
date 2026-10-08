package com.heyler.voicelab

import android.app.Application
import android.os.ParcelFileDescriptor
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class ConversationSwitchListeningTest {
    @Test fun stoppingCancelsPendingAutomaticRecovery()=runBlocking{
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val options=context.getSharedPreferences("assistant-options",0)
        val oldOnly=options.getBoolean("transcription-only",false)
        val store=ViewModelStore()
        lateinit var vm:AssistantViewModel
        val pipe=ParcelFileDescriptor.createPipe()
        try{
            instrumentation.runOnMainSync{vm=AssistantViewModel(context.applicationContext as Application);store.put("test",vm);vm.temporaryConversation();vm.setSpeechEngine("system");vm.setTranscriptionOnly(true);vm.listenFromAudio(pipe[0])}
            withTimeout(20000){while(!vm.state.value.listening)delay(50)}
            instrumentation.runOnMainSync{
                vm.recoverSystemSpeech("Interrupción de prueba")
                assertTrue(vm.state.value.starting)
                vm.pause()
            }
            delay(2000)
            assertFalse("Recovery reopened after manual stop",vm.state.value.listening)
            assertFalse(vm.state.value.starting)
        }finally{pipe[1].close();instrumentation.runOnMainSync{store.clear()};options.edit().putBoolean("transcription-only",oldOnly).commit()}
    }
    @Test fun newChatClosesLiveSessionAndCanTranscribeAgain()=runBlocking{
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val options=context.getSharedPreferences("assistant-options",0)
        val oldSession=options.getLong("session-id",System.currentTimeMillis())
        val oldOnly=options.getBoolean("transcription-only",false)
        val store=ViewModelStore()
        lateinit var vm:AssistantViewModel
        instrumentation.runOnMainSync{vm=AssistantViewModel(context.applicationContext as Application);store.put("test",vm);vm.temporaryConversation();vm.setSpeechEngine("system");vm.setTranscriptionOnly(true)}
        suspend fun waitFor(condition:()->Boolean){withTimeout(20000){while(!condition())delay(50)}}
        suspend fun feed(out:java.io.OutputStream,name:String){
            val samples=Wav16.read(context.assets.open("fixtures/$name").use{it.readBytes()})
            for(offset in samples.indices step 3200){val n=minOf(3200,samples.size-offset);val pcm=ByteArray(n*2)
                for(i in 0 until n){val v=(samples[offset+i]*32768).toInt().coerceIn(-32768,32767);pcm[2*i]=v.toByte();pcm[2*i+1]=(v shr 8).toByte()}
                out.write(pcm);delay(200)
            }
            repeat(15){out.write(ByteArray(6400));delay(200)}
        }
        try{
            val first=ParcelFileDescriptor.createPipe()
            val oldOutput=ParcelFileDescriptor.AutoCloseOutputStream(first[1])
            instrumentation.runOnMainSync{vm.listenFromAudio(first[0])}
            waitFor{vm.state.value.listening}
            feed(oldOutput,"1.wav")
            waitFor{vm.state.value.lines.any{ConversationRules.key(it.text).contains("viernes")}}
            assertTrue(vm.state.value.listening)
            val oldKey=vm.state.value.conversationKey
            instrumentation.runOnMainSync{vm.newConversation()}
            waitFor{!vm.state.value.starting && vm.state.value.conversationKey!=oldKey}
            oldOutput.close()
            assertFalse(vm.state.value.listening)
            assertTrue(vm.state.value.lines.isEmpty())
            val second=ParcelFileDescriptor.createPipe()
            val newOutput=ParcelFileDescriptor.AutoCloseOutputStream(second[1])
            instrumentation.runOnMainSync{vm.listenFromAudio(second[0])}
            waitFor{vm.state.value.listening}
            feed(newOutput,"5.wav")
            waitFor{vm.state.value.lines.any{ConversationRules.key(it.text).contains("circulo")}}
            assertFalse("Old chat text leaked",vm.state.value.lines.any{ConversationRules.key(it.text).contains("viernes")})
            instrumentation.runOnMainSync{vm.pause()}
            newOutput.close()
        }finally{instrumentation.runOnMainSync{store.clear()};options.edit().putLong("session-id",oldSession).putBoolean("transcription-only",oldOnly).commit()}
    }
}
