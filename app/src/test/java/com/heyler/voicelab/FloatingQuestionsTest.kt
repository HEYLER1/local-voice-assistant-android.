package com.heyler.voicelab

import org.junit.Assert.*
import org.junit.Test

class FloatingQuestionsTest {
    private fun q(id:String)=FloatingQuestion(id,"¿Cómo se mide el tamaño del software?","Se elige una medida según el objetivo.")
    @Test fun onlyCoherentCompletedAudioAnswersCreateMessages(){
        val answer=LiveAnswer("ready",1,1,q("1").question,q("1").answer,"Lista · IA")
        val results=FloatingQuestions.ready(listOf(answer,answer.copy(id="partial",status="Respondiendo…"),answer.copy(id="typed",source=null),answer.copy(id="suggested",suggested=true),answer.copy(id="bad",question="¿que hay un?"),answer.copy(id="empty",text="")))
        assertEquals(listOf("ready"),results.map{it.id})
    }
    @Test fun historicalQuestionsDoNotReplayOnLeavingTheApp(){
        val queue=FloatingQuestionQueue();queue.observe(listOf(q("old")),false)
        queue.observe(listOf(q("old")),true);assertNull(queue.current)
        queue.observe(listOf(q("old"),q("new")),true);assertEquals("new",queue.current?.id)
        queue.observe(listOf(q("old"),q("new")),false);assertNull(queue.current)
        queue.observe(listOf(q("old"),q("new")),true);assertNull(queue.current)
    }
    @Test fun adjacentQuestionsWaitTheirTurnAndRevisionsDoNotCreateDuplicates(){
        val queue=FloatingQuestionQueue();queue.observe(emptyList(),false)
        queue.observe(listOf(q("one"),q("two")),true);assertEquals("one",queue.current?.id)
        val revision=q("one").copy(answer="Explicación revisada.")
        queue.observe(listOf(revision,q("two")),true);assertEquals(revision,queue.current)
        queue.advance();assertEquals("two",queue.current?.id)
        queue.advance();assertNull(queue.current)
        queue.observe(listOf(revision,q("two")),true);assertNull(queue.current)
    }
    @Test fun dismissalClearsPendingMessagesAndFreshSessionsStartWithABaseline(){
        val queue=FloatingQuestionQueue();queue.observe(emptyList(),false)
        queue.observe(listOf(q("one"),q("two")),true);queue.dismiss();queue.advance();assertNull(queue.current)
        val next=FloatingQuestionQueue();next.observe(listOf(q("previous")),true);assertNull(next.current)
    }
}
