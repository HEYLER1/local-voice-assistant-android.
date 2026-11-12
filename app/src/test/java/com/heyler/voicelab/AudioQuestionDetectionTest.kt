package com.heyler.voicelab

import org.junit.Assert.*
import org.junit.Test
import kotlin.system.measureNanoTime

class AudioQuestionDetectionTest {
    @Test fun questionSplitAcrossRecognitionSegmentsIsReconstructedImmediately(){
        val segments=listOf(
            SpeechLine(801,"Estamos estudiando geometría. ¿Cómo se calcula",false),
            SpeechLine(802,"el área de un círculo?",true)
        )

        val found=AnalysisEvidence.audioQuestions(segments)

        assertEquals(1,found.size)
        assertEquals("¿Cómo se calcula el área de un círculo?",found.single().question)
        assertEquals(802L,found.single().source.id)
        assertEquals(listOf(801L,802L),found.single().supportingSources)
    }

    @Test fun incompleteOrIncoherentCandidatesAreRejected(){
        val junk=listOf(
            SpeechLine(1,"¿Qué hay un?",true),
            SpeechLine(2,"¿Cómo se calcula",false),
            SpeechLine(3,"¿Por qué una porque ya los lenguajes se auto generan incluye librerías y nos vemos?",true)
        )
        assertTrue(AnalysisEvidence.audioQuestions(junk).isEmpty())
    }

    @Test fun statementWithAnEmbeddedWhatIsNotQueued(){
        val statement=listOf(SpeechLine(20,"Hemos definido qué es un sistema de gestión y dijimos que sus instrumentos son voluntarios.",true))
        assertTrue(AnalysisEvidence.audioQuestions(statement).isEmpty())
    }

    @Test fun liveModeKeepsAnInformalActionQuestionInsideAStatement(){
        val segment=SpeechLine(21,"La matriz ayuda a evaluar riesgos qué controles voy a proponer para este peligro",true)
        val found=AnalysisEvidence.audioQuestions(listOf(segment))
        assertEquals(1,found.size)
        assertEquals("¿qué controles voy a proponer para este peligro?",found.single().question)
    }

    @Test fun liveModeSplitsTwoInformalQuestionsWithoutTakingTheAnswerAsPartOfEither(){
        val segment=SpeechLine(22,"Correcto, qué podríamos plantear y qué actividades les dejo a ustedes",true)
        val found=AnalysisEvidence.audioQuestions(listOf(segment))
        assertEquals(2,found.size)
        assertTrue(found[0].question.startsWith("¿qué podríamos plantear"))
        assertTrue(found[1].question.startsWith("¿qué actividades"))
    }

    @Test fun liveModeStillRejectsQuestionWordsInsideDefinitions(){
        val segment=SpeechLine(23,"La contribución que es materia legal y los instrumentos que son voluntarios",true)
        assertTrue(AnalysisEvidence.audioQuestions(listOf(segment)).isEmpty())
    }

    @Test fun liveModeDoesNotTreatASubordinateLoQueAsAQuestion(){
        val segments=listOf(
            SpeechLine(24,"que vamos a trabajar justamente en este momento okay entonces les voy a",true),
            SpeechLine(25,"entregar tomamos primero grupos de cuatro se cuatro jóvenes esta",true),
            SpeechLine(26,"comunión vamos entonces vean ustedes el hecho de lo que vamos a trabajar",true)
        )
        assertTrue(AnalysisEvidence.audioQuestions(segments).isEmpty())
    }

    @Test fun liveModeRecognizesAnIndirectQuestionWithoutQuestionIntonationWords(){
        val segment=SpeechLine(26,"No sé si ustedes lo han pensado, pero pasa con frecuencia",true)
        val found=AnalysisEvidence.audioQuestions(listOf(segment))
        assertEquals(1,found.size)
        assertEquals("¿No sé si ustedes lo han pensado?",found.single().question)
    }

    @Test fun questionFollowedByItsExplanationKeepsOnlyTheQuestionSpan(){
        val segments=listOf(
            SpeechLine(30,"¿Por qué la empresa tiene equipos en movimiento?",true),
            SpeechLine(31,"Porque hay accidentes y deben revisarse los controles. Luego se documentan las medidas.",true)
        )
        val found=AnalysisEvidence.audioQuestions(segments)
        assertEquals(1,found.size)
        assertEquals("¿Por qué la empresa tiene equipos en movimiento?",found.single().question)
        assertEquals("¿Por qué la empresa tiene equipos en movimiento?",found.single().evidence)
    }

    @Test fun lowInformationInterjectionsDoNotReachTheAnswerQueue(){
        val segments=listOf(SpeechLine(40,"¿Qué significa esto?",true))
        assertTrue(AnalysisEvidence.audioQuestions(segments).isEmpty())
    }

    @Test fun contextualClarificationIsKeptAndNotJoinedToTheAnswer(){
        val segments=listOf(
            SpeechLine(50,"En esta parte tenemos un incremento",true),
            SpeechLine(51,"qué significa esto que no solamente ojo no solamente",true),
            SpeechLine(52,"el hecho real de cuántos accidentes ocurren sino también cuántos se reportan.",true)
        )
        val found=AnalysisEvidence.audioQuestions(segments)
        assertEquals(1,found.size)
        assertEquals("¿qué significa esto?",found.single().question)
        assertEquals(51L,found.single().source.id)
    }

    @Test fun twoConsecutiveQuestionsRemainSeparateCandidates(){
        val segments=listOf(
            SpeechLine(10,"¿Qué diferencia hay entre una órbita elíptica y una circular?",true),
            SpeechLine(11,"¿Por qué la Luna no causa un eclipse cada mes?",true)
        )
        val found=AnalysisEvidence.audioQuestions(segments)
        assertEquals(2,found.size)
        assertTrue(found[0].question.contains("órbita elíptica"))
        assertTrue(found[1].question.contains("Luna"))
    }

    @Test fun partialRecognitionOnlyQueuesAnExplicitClosedQuestion(){
        val complete=SpeechLine(1,"¿Por qué la Luna bloquea la luz del Sol?",false)
        val incomplete=SpeechLine(2,"¿Por qué la Luna bloquea",false)
        assertEquals(1,AnalysisEvidence.audioQuestions(listOf(complete),completedOnly=true).size)
        assertTrue(AnalysisEvidence.audioQuestions(listOf(incomplete),completedOnly=true).isEmpty())
    }

    @Test fun detectorAddsNoVisibleWaitAndStaysBelowTwoMillisecondsPerWindow(){
        val sample=listOf(
            SpeechLine(1,"Hoy estudiamos eclipses solares y la geometría de las órbitas.",true),
            SpeechLine(2,"¿Por qué la Luna no bloquea el Sol cada mes?",true),
            SpeechLine(3,"La órbita lunar está inclinada unos cinco grados.",true)
        )
        repeat(100){AnalysisEvidence.audioQuestions(sample)}
        val iterations=1_000
        val elapsed=measureNanoTime{repeat(iterations){AnalysisEvidence.audioQuestions(sample)}}
        val averageMs=elapsed.toDouble()/iterations/1_000_000.0
        println("AUDIO_QUESTION_DETECTION_BENCHMARK: iterations=$iterations averageMs=$averageMs")
        assertTrue("Local question filter took $averageMs ms per window",averageMs<2.0)
    }
}
