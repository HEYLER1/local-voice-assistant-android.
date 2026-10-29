package com.heyler.voicelab

import android.webkit.WebView
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Loads the official SDK only. Does not sign in, create an account or request AI output. */
class PuterSdkTest {
    @Test fun connectionLoadsOnALocalOriginWithoutGivingTheSdkFileAccess(){
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val app=instrumentation.targetContext.applicationContext as AssistantApplication
        val scenario=ActivityScenario.launch(PuterConnectionActivity::class.java)
        val deadline=android.os.SystemClock.elapsedRealtime()+30000
        try{
            while(!app.puter.connection.value.loaded&&android.os.SystemClock.elapsedRealtime()<deadline)Thread.sleep(100)
            assertTrue(app.puter.connection.value.message,app.puter.connection.value.loaded)
            assertFalse("No sign-in should be performed by the test",app.puter.connection.value.connected)
            assertTrue(app.puter.connection.value.message,app.puter.connection.value.message.contains("Conecta"))
            scenario.onActivity{activity->
                fun web(group:ViewGroup):WebView?{for(i in 0 until group.childCount){val child=group.getChildAt(i);if(child is WebView)return child;if(child is ViewGroup)web(child)?.let{return it}};return null}
                val view=checkNotNull(web(activity.findViewById(android.R.id.content)))
                assertTrue(view.url!!.startsWith("http://localhost:"));assertFalse(view.settings.allowFileAccess);assertFalse(view.settings.allowContentAccess)
            }
            val report=JSONObject().put("sdk_loaded",true).put("origin","http://localhost:<private-app-port>").put("signed_in",false)
                .put("ai_requests",0).put("personal_data_sent",false).put("authentication_and_live_ai_verified",false)
            java.io.File(app.getExternalFilesDir(null),"ui-tests").apply{mkdirs()}.resolve("v-puter-sdk-verification.json").writeText(report.toString(2))
        }finally{scenario.close()}
    }
}
