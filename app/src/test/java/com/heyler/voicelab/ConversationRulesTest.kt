package com.heyler.voicelab
import org.junit.Assert.*
import org.junit.Test
class ConversationRulesTest {
    @Test fun questionsInsideNarration(){val q=ConversationRules.questions("Estamos filmando un eclipse. ¿Por qué el hemisferio norte recibe más eclipses totales que el hemisferio sur? Luego viajamos a Burgos.");assertEquals(1,q.size);assertTrue(q[0].contains("hemisferio norte"));assertFalse(q[0].contains("Burgos"))}
    @Test fun unpunctuatedQuestion(){assertEquals(1,ConversationRules.questions("y cómo es que todavía no sabemos qué causan esas bandas de sombra en el suelo").size)}
    @Test fun noisyFragmentsAreNotQuestions(){for(text in listOf("¿Por qué el episodio norte?","¿a qué la hay?","¿estás siendo tapado?","¿Pero qué raro?","que es perfecto para la totalidad y nos reuniremos con la nasa","no sé si se explica"))assertTrue(text,ConversationRules.questions(text).isEmpty())}
    @Test fun shortMeaningfulDefinitions(){assertEquals(1,ConversationRules.questions("¿Qué es la gravedad?").size);assertEquals(1,ConversationRules.questions("¿Cómo funciona la fotosíntesis?").size);assertTrue(ConversationRules.questions("¿Qué es esto?").isEmpty())}
    @Test fun narrationAfterQuestionIsExcluded(){val q=ConversationRules.questions("cómo es que todavía no sabemos qué causan esas bandas de sombra en el suelo pero qué raro okay vamos camino a burgos");assertEquals(1,q.size);assertTrue(q[0].endsWith("suelo?"))}
    @Test fun spansKeepTheOriginOfSplitQuestions(){val text="Estamos estudiando. ¿Cómo se calcula el área de un círculo?";assertEquals(text.indexOf("¿"),ConversationRules.spans(text).single().start)}
    @Test fun statementsDoNotTriggerAnswers(){assertTrue(ConversationRules.questions("El eclipse ocurre cuando la Luna tapa al Sol.").isEmpty())}
    @Test fun extractedQuotesRemainLiteral(){val text="El eclipse solar ocurre cuando la Luna pasa delante del Sol.";assertEquals(listOf(text),ConversationRules.salient(text))}
    @Test fun incompleteNoiseQuestionsAreRejected(){for(q in listOf("¿que hay un ?","¿Qué es un?","¿Por qué es la?","¿Cómo se calcula el área de un?"))assertTrue(q,ConversationRules.questions(q).isEmpty())}
    @Test fun consecutiveCompleteQuestionsSurvive(){assertEquals(2,ConversationRules.questions("¿Qué es la gravedad? ¿Cómo funciona la fotosíntesis?").size)}
}
