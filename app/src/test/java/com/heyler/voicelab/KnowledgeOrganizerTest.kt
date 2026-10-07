package com.heyler.voicelab
import org.junit.Assert.*
import org.junit.Test
class KnowledgeOrganizerTest {
    @Test fun preservesSourceIdsAndLiteralQuantities(){val line=SpeechLine(8,"El eclipse solar duró cinco minutos y se observó en España.",true);val map=KnowledgeOrganizer.build(listOf(line));assertTrue(map.ideas.contains(line));assertEquals(listOf(line),map.figures);assertTrue(map.topic.isNotBlank());assertEquals("El eclipse solar duró cinco minutos y se observó en España.",map.figures.single().text)}
    @Test fun emptyTranscriptInventsNoKnowledge(){val map=KnowledgeOrganizer.build(emptyList());assertTrue(map.ideas.isEmpty());assertTrue(map.figures.isEmpty());assertEquals("Tu conversación",map.topic)}
}
