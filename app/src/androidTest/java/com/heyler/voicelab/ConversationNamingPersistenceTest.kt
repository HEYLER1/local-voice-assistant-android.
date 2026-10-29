package com.heyler.voicelab

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class ConversationNamingPersistenceTest {
    @Test fun storedAutomaticSubtitlesAreHiddenWhileManualNamesRemainComplete(){
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="short-naming-test-${System.nanoTime()}.db"
        val store=ConversationStore(context,name)
        try{
            val title="Patrones de los eclipses solares: frecuencia y diferencias entre hemisferios"
            val state=AssistantState(automaticTitle=title,lines=listOf(SpeechLine(1,"Una conversación ficticia sobre los patrones de los eclipses solares.",true)))
            store.save(1,state)
            assertEquals("Patrones de los eclipses solares",store.list().single().title)
            assertEquals("Patrones de los eclipses solares",store.load(1)!!.automaticTitle)
            store.rename(1,title)
            assertEquals(title,store.list().single().title)
        }finally{store.close();context.deleteDatabase(name)}
    }
    @Test fun pendingNamesBecomeDefinedAndSurviveReopeningWithoutOverwritingManualNames(){
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="naming-test-${System.nanoTime()}.db"
        var store=ConversationStore(context,name)
        try{
            val greeting=SpeechLine(1,"Hola, buenos días, vamos a empezar.",true)
            store.save(1,AssistantState(lines=listOf(greeting)))
            assertEquals("Desconocido",store.list().first{it.id==1L}.title)
            store.save(2,AssistantState(lines=listOf(SpeechLine(2,"Hoy vamos a estudiar métricas de software.",false))))
            assertEquals("Desconocido",ConversationNaming.display(store.load(2)!!))
            assertFalse(store.load(2)!!.lines.single().final)
            val topic=SpeechLine(2,"Hoy vamos a estudiar seguridad ciudadana.",true)
            store.save(1,store.load(1)!!.copy(lines=listOf(greeting,topic)))
            assertEquals("Seguridad ciudadana",store.list().first{it.id==1L}.title)
            store.close();store=ConversationStore(context,name)
            val saved=store.load(1)!!
            assertEquals("Seguridad ciudadana",saved.automaticTitle)
            store.save(1,saved.copy(lines=saved.lines+SpeechLine(3,"Pasamos a prevención comunitaria.",true)))
            assertEquals("Seguridad ciudadana",store.list().first{it.id==1L}.title)
            store.rename(1,"Curso de los lunes")
            store.save(1,store.load(1)!!.copy(summary="Síntesis de la conversación."))
            assertEquals("Curso de los lunes",store.list().first{it.id==1L}.title)
            assertEquals(3,store.load(1)!!.lines.size)
        }finally{store.close();context.deleteDatabase(name)}
    }
    @Test fun legacyKeywordNamesAreReplacedWithoutChangingTheirTranscript(){
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="legacy-naming-test-${System.nanoTime()}.db"
        val store=ConversationStore(context,name)
        try{
            val line=SpeechLine(1,"Hola, mucho gusto, sí, buenos días.",true)
            store.save(1,AssistantState(lines=listOf(line)))
            val db=store.writableDatabase
            val json=db.rawQuery("SELECT data FROM sessions WHERE id=1",null).use{it.moveToFirst();org.json.JSONObject(it.getString(0)).apply{remove("automaticName")}}
            db.execSQL("UPDATE sessions SET title='Mucha · Frecuencia · Solo',data=? WHERE id=1",arrayOf(json.toString()))
            assertEquals("Desconocido",store.list().first{it.id==1L}.title)
            assertEquals(line.text,store.load(1)!!.lines.single().text)
        }finally{store.close();context.deleteDatabase(name)}
    }
}
