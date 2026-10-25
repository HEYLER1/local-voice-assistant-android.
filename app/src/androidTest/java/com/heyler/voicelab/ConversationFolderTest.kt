package com.heyler.voicelab

import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class ConversationFolderTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun folderMovesSurviveAutosaveAndDeletingFolderKeepsConversation(){
        val name="folder-test-${System.nanoTime()}.db";val store=ConversationStore(context,name)
        try{
            val first=store.createFolder("Seguridad ciudadana");val second=store.createFolder("Entrevistas")
            assertEquals(first,store.createFolder("SEGURIDAD CIUDADANA"));assertEquals(store.createFolder("Álgebra"),store.createFolder("álgebra"))
            val organization=ClassOrganization("Prevención comunitaria",listOf("Prevención"),listOf(OrganizedSection("Objetivos","Se describen objetivos de la clase.",listOf(1))),mapOf(1L to 1))
            val state=AssistantState(folderId=first,organization=organization,lines=listOf(SpeechLine(1,"Una clase ficticia de prevención comunitaria.",true)),summary="Una síntesis guardada.",answers=listOf(LiveAnswer("q",1,1,"¿Cómo se evalúa la prevención comunitaria?","Se definen indicadores.","Lista",evidence="Una clase ficticia",suggested=true)))
            store.save(100,state);store.move(100,second);store.save(100,state.copy(summary="Resumen actualizado."))
            val saved=store.load(100)!!;assertEquals(second,saved.folderId);assertEquals(organization,saved.organization);assertEquals(state.answers,saved.answers)
            store.renameFolder(second,"Entrevistas de trabajo");assertEquals("Entrevistas de trabajo",store.folders().first{it.id==second}.name)
            store.deleteFolder(second);assertNull(store.load(100)!!.folderId);assertEquals("Resumen actualizado.",store.load(100)!!.summary);assertEquals(1,store.list().size)
        }finally{store.close();context.deleteDatabase(name)}
    }
    @Test fun upgradesOldDatabaseWithoutLosingTranscript(){
        val name="migration-test-${System.nanoTime()}.db";val path=context.getDatabasePath(name);path.parentFile!!.mkdirs()
        val old=SQLiteDatabase.openOrCreateDatabase(path,null)
        try{
            old.execSQL("CREATE TABLE sessions(id INTEGER PRIMARY KEY,title TEXT NOT NULL,updated INTEGER NOT NULL,data TEXT NOT NULL)")
            old.execSQL("INSERT INTO sessions VALUES(7,'Clase anterior',1,?)",arrayOf("""{"summary":"Resumen anterior","name":"Clase anterior","lines":[{"id":1,"text":"Contenido anterior.","revision":1,"voice":"Docente"}],"answers":[]}"""))
            old.version=1
        }finally{old.close()}
        val store=ConversationStore(context,name)
        try{assertEquals("Contenido anterior.",store.load(7)!!.lines.single().text);assertEquals(1,store.list().single().fragments);assertEquals("Resumen anterior",store.load(7)!!.summary);assertNull(store.load(7)!!.folderId)
            val folder=store.createFolder("Clases");store.move(7,folder);assertEquals(folder,store.load(7)!!.folderId)
        }finally{store.close();context.deleteDatabase(name)}
    }
}
