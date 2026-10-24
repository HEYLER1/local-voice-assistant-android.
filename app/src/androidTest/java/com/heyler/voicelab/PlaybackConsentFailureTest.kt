package com.heyler.voicelab

import android.app.NotificationManager
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class PlaybackConsentFailureTest {
    @Test fun missingConsentNeverFallsBackToMicrophone()=runBlocking{
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val vm=(context.applicationContext as AssistantApplication).assistant
        val previous=vm.state.value.audioSource
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        try{
            scenario.onActivity{vm.setAudioSource("video");ListeningService.start(it)}
            withTimeout(10000){while(!vm.state.value.status.contains("Falta el permiso"))delay(50)}
            assertFalse(vm.state.value.listening)
            assertFalse(vm.state.value.starting)
            assertFalse(vm.state.value.backgroundServiceActive)
            assertTrue(context.getSystemService(NotificationManager::class.java).activeNotifications.none{it.id==ListeningService.NOTICE})
        }finally{scenario.onActivity{vm.setAudioSource(previous)};scenario.close()}
    }
}
