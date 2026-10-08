package com.heyler.voicelab
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
class LocalSpeechModelsTest{
    @Test fun installedSpanishAndSelectedLanguage(){runBlocking{
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val manager=LocalSpeechModels(context)
        val report=manager.inspect("es-ES")
        assertTrue(report.available)
        assertTrue("Spanish was downloaded in the emulator setup",report.hasLanguage("es-ES"))
        val result=manager.download("es-ES"){}
        assertEquals("Descarga completada",result)
        assertTrue(manager.inspect("es-ES").hasLanguage("es-ES"))
        val pipe=android.os.ParcelFileDescriptor.createPipe()
        try{assertEquals("en-US",SystemSpeech.sessionIntent(pipe[0],"en-US").getStringExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE))}finally{pipe.forEach{it.close()}}
    }}
    @Test fun renamingKeepsTranscriptAndOtherChats(){
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val store=ConversationStore(context)
        val first=-System.nanoTime();val second=first-1
        try{
            store.save(first,AssistantState(lines=listOf(SpeechLine(1,"Clase de astronomía de ejemplo",true))))
            store.save(second,AssistantState(conversationTitle="Otra clase",lines=listOf(SpeechLine(2,"Ejemplo de geometría",true))))
            store.rename(first,"Astronomía · parte 1")
            assertEquals("Astronomía · parte 1",store.load(first)?.conversationTitle)
            assertEquals("Clase de astronomía de ejemplo",store.load(first)?.lines?.first()?.text)
            assertEquals("Otra clase",store.load(second)?.conversationTitle)
            store.delete(first)
            assertNull(store.load(first));assertNotNull(store.load(second))
        }finally{store.delete(first);store.delete(second);store.close()}
    }
}
