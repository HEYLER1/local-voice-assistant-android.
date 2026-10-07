package com.heyler.voicelab
import org.junit.Assert.*
import org.junit.Test
class MetricsTest {
    @Test fun substitutionInsertionDeletion() {
        assertEquals(3, Metrics.errors("uno dos tres cuatro", "uno nuevo cinco"))
        assertEquals(0, Metrics.errors("¿Cómo está?", "como esta"))
        assertEquals(2, Metrics.errors("", "dos palabras"))
    }
    @Test fun percentileNearestRank() {
        assertEquals(3.0, Metrics.percentile(listOf(5.0,1.0,3.0),.5)!!,0.0)
        assertEquals(5.0, Metrics.percentile(listOf(5.0,1.0,3.0),.95)!!,0.0)
        assertNull(Metrics.percentile(emptyList(),.5))
    }
}
