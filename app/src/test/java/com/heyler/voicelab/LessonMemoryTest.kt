package com.heyler.voicelab

import org.junit.Assert.*
import org.junit.Test

class LessonMemoryTest{
    @Test fun retainsOldTopicAndRetrievesRelatedMaterialBeyondRecentEightLines(){
        val lines=listOf(
            SpeechLine(1,"Hoy vamos a estudiar el trabajo mecánico y la energía.",true),
            SpeechLine(2,"El trabajo de una fuerza se calcula multiplicando fuerza por desplazamiento y por el coseno del ángulo.",true)
        )+(3L..40L).map{SpeechLine(it,"El grupo está tomando notas y dibujando en su cuaderno mientras continúa la sesión.",true)}
        val context=LessonMemory.questionContext(lines,"¿Cómo se calcula el trabajo?")
        assertTrue(context.contains("trabajo mecánico y la energía"))
        assertTrue(context.contains("coseno del ángulo"));assertTrue(context.contains("[2]"))
        assertTrue(LessonMemory.build(lines).tasks.isEmpty())
        assertTrue(LessonMemory.questionContext(lines,"¿Cómo se calcula?").contains("coseno del ángulo"))
    }
    @Test fun laterTopicDoesNotHijackAnEarlierAudioQuestion(){
        val lines=listOf(SpeechLine(1,"Hoy vamos a estudiar la fotosíntesis.",true),SpeechLine(2,"¿Cómo se produce la fotosíntesis en las plantas?",true),SpeechLine(3,"Ahora pasamos a la Revolución francesa.",true))
        val earlier=LessonMemory.questionContext(lines,"¿Cómo se produce la fotosíntesis en las plantas?",2)
        assertTrue(earlier.contains("fotosíntesis"));assertFalse(earlier.contains("Revolución"))
        assertTrue(LessonMemory.build(lines).topic.contains("Revolución francesa"))
    }
    @Test fun organizesHomeworkProjectsReadingsExercisesAndExamWithLiteralEvidence(){
        val lines=listOf(
            SpeechLine(1,"Como tarea para el viernes, resolver los problemas de fuerzas.",true),
            SpeechLine(2,"Deben presentar un trabajo grupal sobre energía renovable.",true),
            SpeechLine(3,"Lean el capítulo 4 del libro para la próxima clase.",true),
            SpeechLine(4,"Resuelvan los ejercicios 2 y 3 de la guía.",true),
            SpeechLine(5,"La fotosíntesis convierte energía luminosa en energía química.",true),
            SpeechLine(6,"Esto es una pregunta de examen.",true),
            SpeechLine(7,"¿Cómo se produce la fotosíntesis en las plantas?",true)
        )
        val memory=LessonMemory.build(lines)
        assertEquals(listOf(StudyInstructionKind.TASK,StudyInstructionKind.PROJECT,StudyInstructionKind.READING,StudyInstructionKind.EXERCISE),memory.tasks.map{it.kind})
        assertEquals("para el viernes",memory.tasks.first().timing)
        assertEquals(1,memory.exams.size)
        val exam=memory.exams.single()
        assertTrue(exam.context.any{it.sourceId==5L});assertTrue(exam.context.any{it.sourceId==7L})
        memory.instructions.forEach{assertTrue(lines.any{line->line.id==it.excerpt.sourceId&&line.text.contains(it.excerpt.text)})}
        assertEquals(1,LessonMemory.build(listOf(SpeechLine(11,"Esta pregunta es de examen.",true))).exams.size)
        assertEquals(1,LessonMemory.build(listOf(SpeechLine(12,"Esto es de examen.",true))).exams.size)
    }
    @Test fun ignoresNegatedInstructionsQuestionsAndPartialHypotheses(){
        val memory=LessonMemory.build(listOf(
            SpeechLine(1,"No hay tarea para el viernes.",true),
            SpeechLine(2,"Esto no entra en el examen.",true),
            SpeechLine(3,"Esto no es una pregunta de examen.",true),
            SpeechLine(4,"¿Qué tarea hay para el viernes?",true),
            SpeechLine(5,"Como tarea para el lunes, resolver los problemas.",false),
            SpeechLine(6,"La tarea del sistema inmunitario es proteger el organismo.",true)
        ))
        assertTrue(memory.instructions.isEmpty())
    }
    @Test fun summarySelectsEarlyTopicAndInstructionsAndStaysBounded(){
        val lines=listOf(SpeechLine(1,"Hoy vamos a estudiar la división celular.",true),SpeechLine(2,"Como tarea para el lunes, preparar dibujos de la mitosis.",true))+
            (3L..100L).map{SpeechLine(it,"La observación número $it compara detalles del material y continúa con un análisis de los dibujos ".repeat(15),true)}
        val context=LessonMemory.summaryContext(lines)
        assertTrue(context.contains("división celular"));assertTrue(context.contains("preparar dibujos de la mitosis"))
        assertTrue(context.length<=10000)
        assertTrue(LessonMemory.questionContext(lines,"¿Cómo se produce la mitosis?").length<=7000)
    }
    @Test fun noContextOrInstructionsLeakIntoNewConversation(){
        assertTrue(LessonMemory.build(emptyList()).instructions.isEmpty())
        assertEquals("Tema por confirmar",LessonMemory.build(emptyList()).topic)
        assertFalse(LessonMemory.questionContext(emptyList(),"¿Qué tarea hay?").contains("viernes"))
    }
    @Test fun distinguishesClassAssignmentsFromSavedLibraryQueries(){
        val lines=listOf(SpeechLine(1,"Como tarea para el viernes, preparar un informe sobre las células.",true))
        assertFalse(LessonMemory.asksSavedLibrary("¿Qué tareas dejó el profesor?",lines))
        assertFalse(LessonMemory.asksSavedLibrary("¿Recuerdas lo que dijo en clase?",lines))
        assertTrue(LessonMemory.asksSavedLibrary("Muestra mis tareas guardadas",lines))
        assertTrue(LessonMemory.asksSavedLibrary("¿Qué tengo pendiente de Álgebra?",emptyList()))
    }

    @Test fun shortCoherentQuestionRequiresContextAndNoiseStillRejected(){
        assertTrue(ConversationRules.spans("¿Cómo se calcula?").isEmpty())
        assertEquals(listOf("¿Cómo se calcula?"),ConversationRules.spans("¿Cómo se calcula?",allowContextual=true).map{it.text})
        assertTrue(ConversationRules.spans("¿que hay un? ¿a qué la hay?",allowContextual=true).isEmpty())
    }

    @Test fun dialogueKeepsPriorExplanationsButExcludesLibraryAndFutureAnswers(){
        val answers=listOf(
            LiveAnswer("private",null,0,"Mis recuerdos guardados","PRIVATE_LIBRARY","Consulta local de textos guardados"),
            LiveAnswer("earlier",null,0,"¿Cómo se calcula?","Multiplica 10 N por 3 m: 30 J.","Lista · online"),
            LiveAnswer("current",null,0,"Aclara el segundo paso"),
            LiveAnswer("future",null,0,"Otra materia","FUTURE_ANSWER","Lista · online"))
        val text=LessonMemory.dialogueBefore(answers,"current")
        assertTrue(text.contains("30 J"));assertFalse(text.contains("PRIVATE_LIBRARY"));assertFalse(text.contains("FUTURE_ANSWER"))
        assertFalse(LessonMemory.asksSavedLibrary("¿Recuerdas el segundo paso?",emptyList(),hasDialogue=true))
    }

}
