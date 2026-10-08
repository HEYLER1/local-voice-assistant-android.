package com.heyler.voicelab
import org.junit.Test
import org.junit.Assert.*
class SpeechHealthTest{
    @Test fun longSilenceDoesNotRestart(){assertNull(SpeechHealth.stalled(120000,120000,120000,0,0))}
    @Test fun healthySpeechDoesNotRestart(){assertNull(SpeechHealth.stalled(120000,120000,120000,119000,9000))}
    @Test fun blockedConsumerIsDetected(){assertNotNull(SpeechHealth.stalled(20000,19000,0,19000,0))}
    @Test fun speechWithoutResultsIsDetected(){assertNotNull(SpeechHealth.stalled(50000,50000,50000,0,9000));assertNull(SpeechHealth.stalled(50000,50000,50000,0,1000))}
}
