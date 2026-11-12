package com.heyler.voicelab

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** One real Pollinations request, using a fixed fictional lesson and a split ASR question. */
class OnlineQuestionLiveTest {
    @Test fun splitClassQuestionGetsAContextualAnswerFromPollinations()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val prefs=context.getSharedPreferences("assistant-options",0)
        val before=prefs.all
        val vm=AssistantViewModel(context.applicationContext as Application)
        val holder=ViewModelStore().also{it.put("pollinations-question-live",vm)}
        try{
            vm.temporaryConversation()
            vm.setOnlineAnswers(true);vm.setAnswers(true);vm.setAnswerEngine("pollinations")
            vm.requestSpacingMs=0
            val started=SystemClock.elapsedRealtime()
            vm.observe(901,"Clase ficticia de física: el trabajo mecánico se calcula con W = F × d. Si una fuerza de 10 newtons desplaza un objeto 3 metros, el trabajo es 30 joules.",true)
            vm.observe(902,"¿Cómo se calcula",false)
            vm.observe(903,"el trabajo para una fuerza de 10 newtons y un desplazamiento de 3 metros?",true)

            withTimeout(5000){while(vm.state.value.answers.isEmpty())delay(25)}
            withTimeout(90000){while(vm.state.value.answers.none{it.status.startsWith("Lista")||it.status.startsWith("No se completó")})delay(100)}

            val answer=vm.state.value.answers.single()
            val totalMs=SystemClock.elapsedRealtime()-started
            val report=JSONObject().put("provider","Pollinations · online")
                .put("syntheticFixture",true).put("question",answer.question)
                .put("status",answer.status).put("firstTextMs",answer.firstTextMs?:JSONObject.NULL)
                .put("totalMs",totalMs).put("answer",answer.text)
            android.util.Log.i("LiveQuestionProbe",report.toString())
            println("LIVE_QUESTION_PROBE: $report")
            assertEquals("¿Cómo se calcula el trabajo para una fuerza de 10 newtons y un desplazamiento de 3 metros?",answer.question)
            assertTrue("Pollinations status: ${answer.status}",answer.status.startsWith("Lista"))
            assertTrue("Contextual calculation missing: ${answer.text}",Regex("\\b30\\b").containsMatchIn(answer.text))
            assertTrue("Unit missing: ${answer.text}",Regex("(?i)\\b(?:j|julios?|joules?|newton.?metros?)\\b").containsMatchIn(answer.text))
        }finally{
            vm.background();holder.clear()
            val editor=prefs.edit().clear()
            before.forEach{(key,value)->when(value){
                is String->editor.putString(key,value)
                is Boolean->editor.putBoolean(key,value)
                is Long->editor.putLong(key,value)
                is Int->editor.putInt(key,value)
            }}
            editor.commit()
        }
    }
}
