package com.heyler.voicelab

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class AccountDataIsolationTest{
    private val context=InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun conversationsFoldersAndSavedTextsAreScopedAndLegacyImportIsExplicit(){
        val database="account-isolation-${System.nanoTime()}.db"
        val savedDatabase="account-isolation-saved-${System.nanoTime()}.db"
        val legacy=ConversationStore(context,database,"legacy")
        val first=ConversationStore(context,database,"firebase-user-a")
        val second=ConversationStore(context,database,"firebase-user-b")
        val savedLegacy=SavedStore(context,savedDatabase,"legacy")
        val savedFirst=SavedStore(context,savedDatabase,"firebase-user-a")
        val savedSecond=SavedStore(context,savedDatabase,"firebase-user-b")
        val legacyId=System.nanoTime();val firstId=legacyId+1;val secondId=legacyId+2
        try{
            val legacyFolder=legacy.createFolder("Clases")
            val firstFolder=first.createFolder("Clases")
            val secondFolder=second.createFolder("Clases")
            assertNotEquals(firstFolder,secondFolder)
            legacy.save(legacyId,AssistantState(folderId=legacyFolder,lines=listOf(SpeechLine(1,"Contenido previo.",true))))
            first.save(firstId,AssistantState(folderId=firstFolder,lines=listOf(SpeechLine(2,"Contenido de la cuenta A.",true))))
            second.save(secondId,AssistantState(folderId=secondFolder,lines=listOf(SpeechLine(3,"Contenido de la cuenta B.",true))))
            savedLegacy.save(null,"Recuerdo","Texto local anterior",""," ")
            savedFirst.save(null,"Recuerdo","Texto privado de A",""," ")
            savedSecond.save(null,"Recuerdo","Texto privado de B",""," ")

            assertNull(first.load(legacyId));assertNull(second.load(legacyId))
            assertEquals("Contenido de la cuenta A.",first.list().single().let{first.load(it.id)!!.lines.single().text})
            assertEquals("Contenido de la cuenta B.",second.list().single().let{second.load(it.id)!!.lines.single().text})
            assertEquals(listOf("Texto privado de A"),savedFirst.list().map{it.text})
            assertEquals(listOf("Texto privado de B"),savedSecond.list().map{it.text})

            first.assignLegacyDataToCurrentOwner()
            savedFirst.assignLegacyDataToCurrentOwner()
            assertNull(legacy.load(legacyId))
            assertEquals(setOf("Contenido previo.","Contenido de la cuenta A."),first.list().map{first.load(it.id)!!.lines.single().text}.toSet())
            assertEquals(setOf("Texto local anterior","Texto privado de A"),savedFirst.list().map{it.text}.toSet())
            assertNull(second.load(legacyId));assertEquals(listOf("Texto privado de B"),savedSecond.list().map{it.text})
        }finally{
            legacy.close();first.close();second.close();savedLegacy.close();savedFirst.close();savedSecond.close()
            context.deleteDatabase(database)
            context.deleteDatabase(savedDatabase)
        }
    }
}
