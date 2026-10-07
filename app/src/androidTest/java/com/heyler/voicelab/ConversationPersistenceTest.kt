package com.heyler.voicelab
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
class ConversationPersistenceTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun completeConversationSurvivesReopening(){
        val id=System.nanoTime();val db=ConversationStore(context)
        val state=AssistantState(lines=listOf(SpeechLine(11,"¿Qué es la gravedad?",true,2,"Ana")),answers=listOf(LiveAnswer("a",11,2,"¿Qué es la gravedad?","Atracción entre cuerpos.","Lista")),summary="Hablamos de gravedad.")
        try{db.save(id,state);db.close();val reopened=ConversationStore(context);try{val restored=checkNotNull(reopened.load(id));assertEquals(state.lines.single().text,restored.lines.single().text);assertEquals("Ana",restored.lines.single().voice);assertEquals(state.answers,restored.answers);assertEquals(state.summary,restored.summary);assertEquals(KnowledgeOrganizer.build(state.lines),KnowledgeOrganizer.build(restored.lines));val entry=reopened.list().first{it.id==id};assertEquals(1,entry.fragments);assertEquals(1,entry.questions);assertFalse(restored.listening);reopened.delete(id);assertNull(reopened.load(id))}finally{reopened.close()}}finally{val cleanup=ConversationStore(context);cleanup.delete(id);cleanup.close()}
    }
    @Test fun namedClassCanContinueWithoutLosingEarlierLessons(){
        val id=System.nanoTime();val other=id+1;val db=ConversationStore(context)
        try{
            val first=SpeechLine(501,"El eclipse solar permite estudiar la corona del Sol.",true)
            db.save(id,AssistantState(conversationTitle="Física · semestre 1",lines=listOf(first),summary="Primera clase."))
            db.save(other,AssistantState(conversationTitle="Historia",lines=listOf(SpeechLine(700,"Otra materia.",true))))
            val reopened=checkNotNull(db.load(id))
            val second=SpeechLine(502,"La Luna proyecta su sombra sobre la Tierra.",true)
            db.save(id,reopened.copy(lines=reopened.lines+second))
            val continued=checkNotNull(db.load(id))
            assertEquals("Física · semestre 1",continued.conversationTitle)
            assertEquals(listOf(first.text,second.text),continued.lines.map{it.text})
            assertEquals(continued.lines,KnowledgeOrganizer.topics(continued.lines).flatMap{it.lines})
            assertEquals("Otra materia.",db.load(other)!!.lines.single().text)
            assertEquals("Física · semestre 1",db.list().first{it.id==id}.title)
        }finally{db.delete(id);db.delete(other);db.close()}
    }
    @Test fun interruptedAnswersAreNotRestoredAsGenerating(){val id=System.nanoTime();val db=ConversationStore(context);try{db.save(id,AssistantState(answers=listOf(LiveAnswer("b",null,0,"Una consulta","Texto parcial","Respondiendo…"))));val restored=checkNotNull(db.load(id));assertEquals("Texto parcial",restored.answers.single().text);assertTrue(restored.answers.single().status.startsWith("Interrumpida"));assertFalse(restored.generating)}finally{db.delete(id);db.close()}}
}
