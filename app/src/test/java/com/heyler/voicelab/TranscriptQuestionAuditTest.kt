package com.heyler.voicelab

import org.junit.Test
import org.junit.Assume.assumeTrue
import java.io.File

/** Optional audit against a local real transcript; keeps the transcript itself out of the repository. */
class TranscriptQuestionAuditTest {
    @Test fun auditLocallyProvidedTranscript() {
        val path = System.getenv("QUESTION_TEST_TRANSCRIPT")
        assumeTrue("Set QUESTION_TEST_TRANSCRIPT to run the local transcript audit", !path.isNullOrBlank())
        val segments = File(path!!).readLines().filter { it.isNotBlank() }
            .mapIndexed { index, text -> SpeechLine(index + 1L, text.trim(), true) }
        val found = linkedMapOf<String, AudioQuestionCandidate>()
        segments.indices.forEach { index ->
            AnalysisEvidence.audioQuestions(segments.take(index + 1).takeLast(3)).forEach {
                found.putIfAbsent(ConversationRules.key(it.question), it)
            }
        }
        println("TRANSCRIPT_QUESTION_AUDIT: segments=${segments.size} candidates=${found.size}")
        found.values.forEach { println("TRANSCRIPT_QUESTION_CANDIDATE: ${it.question} | source=${it.source.id}") }
    }
}
