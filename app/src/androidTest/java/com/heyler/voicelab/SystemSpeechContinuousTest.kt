package com.heyler.voicelab

import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

class SystemSpeechContinuousTest {
    @Test fun sustainedSpanishRecognitionKeepsProducingText(){
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val results=CopyOnWriteArrayList<String>()
        val failures=CopyOnWriteArrayList<String>()
        val pipe=ParcelFileDescriptor.createPipe()
        lateinit var engine:SystemSpeech
        instrumentation.runOnMainSync{engine=SystemSpeech(context,{_,t,_->results.add(t)},{},{failures.add(it)});engine.start(pipe[0])}
        val out=ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])
        try{
            val samples=Wav16.read(context.assets.open("fixtures/5.wav").use{it.readBytes()})
            repeat(25){cycle->
                val before=results.size
                for(offset in samples.indices step 3200){
                    val n=minOf(3200,samples.size-offset);val pcm=ByteArray(n*2)
                    for(i in 0 until n){val v=(samples[offset+i]*32768).toInt().coerceIn(-32768,32767);pcm[2*i]=v.toByte();pcm[2*i+1]=(v shr 8).toByte()}
                    out.write(pcm);Thread.sleep(200)
                }
                repeat(10){out.write(ByteArray(6400));Thread.sleep(200)}
                assertTrue("Session failed at cycle $cycle: $failures",failures.isEmpty())
                assertTrue("Stopped producing text at cycle $cycle",results.size>before)
                android.util.Log.i("SpeechContinuityTest","Completed cycle $cycle")
            }
            assertEquals(1,engine.sessionStarts)
        }finally{instrumentation.runOnMainSync{engine.stop()};out.close()}
    }
    @Test fun stopThenStartTranscribesAgain()=kotlinx.coroutines.runBlocking {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        repeat(3){cycle->
            val pipe=ParcelFileDescriptor.createPipe()
            val text=CopyOnWriteArrayList<String>()
            val failures=CopyOnWriteArrayList<String>()
            lateinit var engine:SystemSpeech
            instrumentation.runOnMainSync{
                engine=SystemSpeech(context,{_,t,_->text.add(t)},{},{failures.add(it)})
                engine.start(pipe[0])
            }
            val out=ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])
            try {
                val samples=Wav16.read(context.assets.open("fixtures/5.wav").use{it.readBytes()})
                for(offset in samples.indices step 3200){
                    val n=minOf(3200,samples.size-offset);val pcm=ByteArray(n*2)
                    for(i in 0 until n){val v=(samples[offset+i]*32768).toInt().coerceIn(-32768,32767);pcm[2*i]=v.toByte();pcm[2*i+1]=(v shr 8).toByte()}
                    out.write(pcm);kotlinx.coroutines.delay(200)
                }
                repeat(15){out.write(ByteArray(6400));kotlinx.coroutines.delay(200)}
                val deadline=android.os.SystemClock.elapsedRealtime()+10000
                while(text.isEmpty() && failures.isEmpty() && android.os.SystemClock.elapsedRealtime()<deadline)kotlinx.coroutines.delay(100)
                assertTrue("Cycle $cycle failed: $failures",failures.isEmpty())
                assertTrue("Cycle $cycle lost speech: $text",ConversationRules.key(text.joinToString(" ")).contains("circulo"))
            }finally{
                instrumentation.runOnMainSync{engine.stop()}
                out.close()
                kotlinx.coroutines.withTimeout(5000){engine.awaitStopped()}
            }
        }
    }
    @Test fun twoSpanishPhrasesStayInOneSession(){
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val results=CopyOnWriteArrayList<Triple<Long,String,Boolean>>()
        val failures=CopyOnWriteArrayList<String>()
        val ready=java.util.concurrent.atomic.AtomicInteger()
        val pipe=ParcelFileDescriptor.createPipe()
        lateinit var engine:SystemSpeech
        instrumentation.runOnMainSync{
            engine=SystemSpeech(context,{id,text,final->results.add(Triple(id,text,final))},{ready.incrementAndGet()},{failures.add(it)})
            engine.start(pipe[0])
        }
        val out=ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])
        try{
            for(name in listOf("1.wav","5.wav")){
                val samples=Wav16.read(context.assets.open("fixtures/$name").use{it.readBytes()})
                for(offset in samples.indices step 3200){
                    val n=minOf(3200,samples.size-offset);val pcm=ByteArray(n*2)
                    for(i in 0 until n){val v=(samples[offset+i]*32768).toInt().coerceIn(-32768,32767);pcm[2*i]=v.toByte();pcm[2*i+1]=(v shr 8).toByte()}
                    out.write(pcm);Thread.sleep(200)
                }
                repeat(15){out.write(ByteArray(6400));Thread.sleep(200)}
            }
            val deadline=android.os.SystemClock.elapsedRealtime()+15000
            while(results.filter{it.third}.map{it.first}.distinct().size<2 && failures.isEmpty() && android.os.SystemClock.elapsedRealtime()<deadline)Thread.sleep(100)
            assertTrue("Session ended early: $failures",failures.isEmpty())
            val finals=results.filter{it.third}
            val text=ConversationRules.key(finals.joinToString(" "){it.second})
            assertTrue("First phrase missing: $text",text.contains("viernes"))
            assertTrue("Second phrase missing: $text",text.contains("circulo"))
            assertTrue("Expected separate segments: $finals",finals.map{it.first}.distinct().size>=2)
            assertEquals("No automatic microphone restarts",1,engine.sessionStarts)
            assertTrue("UI must receive ready state",ready.get()>0)
        }finally{out.close();instrumentation.runOnMainSync{engine.stop()}}
    }
}
