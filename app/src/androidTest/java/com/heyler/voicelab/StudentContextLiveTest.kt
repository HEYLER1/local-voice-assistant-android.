package com.heyler.voicelab

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Opt-in network test using fixed fictional classroom material, never the user's chat. */
class StudentContextLiveTest{
    @Test fun shortQuestionUsesOldPhysicsContextAndDoesNotBecomeHomeworkAdvice()=runBlocking{
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val prefs=context.getSharedPreferences("assistant-options",0)
        val oldOnline=prefs.getBoolean("online-answers",true);val oldAnswers=prefs.getBoolean("answers",true)
        val vm=AssistantViewModel(context.applicationContext as Application);val holder=ViewModelStore();holder.put("student-live",vm)
        suspend fun waitFor(check:()->Boolean){withTimeout(90000){while(!check())delay(100)}}
        try{
            vm.temporaryConversation();vm.setOnlineAnswers(true);vm.setAnswers(true)
            vm.observe(1,"Hoy vamos a estudiar el trabajo mecánico y la energía.",true)
            vm.observe(2,"El trabajo de una fuerza constante paralela al desplazamiento es W = F × d. En el ejemplo del trabajo usamos 10 newtons y 3 metros.",true)
            vm.observe(3,"Como tarea para el viernes, resolver los ejercicios 1 y 2 de la guía.",true)
            vm.observe(4,"Esto es una pregunta de examen.",true)
            for(id in 5L..22L)vm.observe(id,"El grupo continúa tomando apuntes y revisando los dibujos del cuaderno.",true)
            waitFor{vm.state.value.lines.size==22}
            vm.observe(23,"¿Cómo se calcula?",true)
            waitFor{vm.state.value.answers.any{it.question=="¿Cómo se calcula?"&&(it.status.startsWith("Lista")||it.status.startsWith("No se completó"))}}
            val answer=vm.state.value.answers.single{it.question=="¿Cómo se calcula?"}
            assertTrue(answer.status,answer.status.startsWith("Lista"))
            assertTrue(answer.text,answer.text.contains("30"))
            assertTrue(answer.text,Regex("julio|joule|\\bJ\\b",RegexOption.IGNORE_CASE).containsMatchIn(answer.text))
            val memory=LessonMemory.build(vm.state.value.lines)
            assertEquals(1,memory.tasks.size);assertEquals(1,memory.exams.size)
            val report=JSONObject().put("fixture","physics-class-fictional").put("topic",memory.topic)
                .put("question",answer.question).put("answer",answer.text).put("firstTextMs",answer.firstTextMs)
                .put("homework",memory.tasks.single().excerpt.text).put("examNotice",memory.exams.single().excerpt.text)
                .put("note","One network smoke test; not a general accuracy or latency benchmark")
            File(context.getExternalFilesDir(null),"ui-tests").apply{mkdirs()}.resolve("v-student-context-synthetic.json").writeText(report.toString(2))
        }finally{
            vm.background();holder.clear();prefs.edit().putBoolean("online-answers",oldOnline).putBoolean("answers",oldAnswers).apply()
        }
    }
}
