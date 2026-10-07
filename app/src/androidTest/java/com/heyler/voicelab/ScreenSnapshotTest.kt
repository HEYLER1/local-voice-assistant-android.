package com.heyler.voicelab

import android.app.Activity
import androidx.activity.compose.setContent
import androidx.compose.runtime.key
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.PixelCopy
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

// Render only this app's window, populated with synthetic test text and a real local answer.
// No desktop screenshot, user screen, personal transcript, or real microphone is captured.
class ScreenSnapshotTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context=instrumentation.targetContext
    private fun current():Activity?{var activity:Activity?=null;instrumentation.runOnMainSync{activity=ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).firstOrNull{it is MainActivity}};return activity}
    private fun waitFor(condition:()->Boolean){val end=SystemClock.elapsedRealtime()+45000;while(!condition()&&SystemClock.elapsedRealtime()<end)SystemClock.sleep(100);assertTrue("Timed out waiting for local answer/layout",condition())}
    private fun render(activity:Activity,name:String):File{
        instrumentation.waitForIdleSync();SystemClock.sleep(500)
        var bitmap:Bitmap?=null
        instrumentation.runOnMainSync{bitmap=Bitmap.createBitmap(activity.window.decorView.width,activity.window.decorView.height,Bitmap.Config.ARGB_8888)}
        val result=java.util.concurrent.atomic.AtomicInteger(-1);val latch=CountDownLatch(1)
        PixelCopy.request(activity.window,checkNotNull(bitmap),{value->result.set(value);latch.countDown()},Handler(Looper.getMainLooper()))
        assertTrue(latch.await(5,TimeUnit.SECONDS));assertEquals(PixelCopy.SUCCESS,result.get())
        val file=File(context.getExternalFilesDir("ui-tests"),name)
        file.outputStream().use{checkNotNull(bitmap).compress(Bitmap.CompressFormat.PNG,100,it)};bitmap?.recycle()
        assertTrue(file.length()>1000);return file
    }
    @Test fun appPortraitAndLandscapeWithSyntheticContent(){
        instrumentation.startActivitySync(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        var activity=checkNotNull(current());var vm:AssistantViewModel?=null;var previous=true
        try{
            instrumentation.runOnMainSync{vm=ViewModelProvider(activity as MainActivity)[AssistantViewModel::class.java];previous=checkNotNull(vm).state.value.autoAnswers;checkNotNull(vm).temporaryConversation();checkNotNull(vm).state.value=checkNotNull(vm).state.value.copy(saved=emptyList(),history=listOf(ConversationEntry(-1,"Geometría: el área de un círculo",0),ConversationEntry(-2,"Ideas para estudiar astronomía",0)));checkNotNull(vm).setAnswers(true)}
            val assistant=checkNotNull(vm)
            assistant.observe(9501,"Un eclipse solar ocurre cuando la Luna pasa entre la Tierra y el Sol. Estamos estudiando astronomía y geometría.",true)
            val speech=LocalEngines(context)
            val text=try{speech.loadSpeech();val bytes=context.assets.open("fixtures/5.wav").use{it.readBytes()};val audio=Wav16.read(bytes);val value=speech.transcribe(audio);audio.fill(0f);bytes.fill(0);value}finally{speech.close()}
            assistant.observe(9502,text,true)
            waitFor{assistant.state.value.answers.any{it.status.startsWith("Lista")&&it.text.isNotBlank()}}
            render(activity,"v-android-portrait-synthetic.png")
            fun page(tab:Int,section:Int,name:String){instrumentation.runOnMainSync{(activity as MainActivity).setContent{VoiceTheme{key(tab,section){AssistantScreen(assistant,{}, {}, {}, {}, {}, {},tab,section)}}}};render(activity,name)}
            page(0,1,"v-android-questions-synthetic.png")
            assistant.requestSummary();waitFor{assistant.state.value.summary.isNotBlank()}
            page(0,2,"v-android-map-synthetic.png")
            page(1,0,"v-android-library-synthetic.png")
            page(2,0,"v-android-settings-synthetic.png")
            page(0,0,"v-android-portrait-synthetic.png")
            instrumentation.runOnMainSync{(activity as MainActivity).setContent{VoiceTheme{key("drawer"){AssistantScreen(assistant,{}, {}, {}, {}, {}, {},initialDrawer=true)}}}}
            render(activity,"v-android-sidebar-synthetic.png")
            page(0,0,"v-android-portrait-synthetic.png")
            instrumentation.runOnMainSync{activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE}
            waitFor{val candidate=current();candidate!=null&&candidate.resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE}
            activity=checkNotNull(current());render(activity,"v-android-landscape-synthetic.png")
            assertFalse(assistant.state.value.listening)
        }finally{vm?.setAnswers(previous);vm?.restoreConversation();current()?.let{last->instrumentation.runOnMainSync{last.finish()}}}
    }
}
