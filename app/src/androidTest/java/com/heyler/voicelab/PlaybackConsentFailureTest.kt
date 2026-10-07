package com.heyler.voicelab
import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
class PlaybackConsentFailureTest {
    @Test fun missingConsentNeverFallsBackToMicrophone(){
        val instrumentation=InstrumentationRegistry.getInstrumentation();val context=instrumentation.targetContext
        val activity=instrumentation.startActivitySync(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val oldReady=PlaybackCaptureService.onReady;val oldStop=PlaybackCaptureService.onStopped;val oldError=PlaybackCaptureService.onError
        val ready=AtomicBoolean(false);val failed=CountDownLatch(1)
        try{instrumentation.runOnMainSync{PlaybackCaptureService.onReady={ready.set(true)};PlaybackCaptureService.onStopped={};PlaybackCaptureService.onError={failed.countDown()};context.startForegroundService(Intent(context,PlaybackCaptureService::class.java))}
            assertTrue("Missing consent must report failure",failed.await(10,TimeUnit.SECONDS));assertFalse("Never start capture without consent",ready.get())
        }finally{instrumentation.runOnMainSync{PlaybackCaptureService.stopCapture();PlaybackCaptureService.onReady=oldReady;PlaybackCaptureService.onStopped=oldStop;PlaybackCaptureService.onError=oldError;activity.finish()}}
    }
}
