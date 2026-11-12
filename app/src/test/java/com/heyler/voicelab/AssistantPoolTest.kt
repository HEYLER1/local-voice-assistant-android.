package com.heyler.voicelab

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class AssistantPoolTest{
    @Test fun aBusyEngineIsNotInterruptedAndAnotherOneIsSelected(){
        val pool=AssistantPool();val summary=pool.tryAcquire(listOf("pollinations"))!!
        val answer=pool.tryAcquire(listOf("pollinations","horde"))!!
        assertEquals("horde",answer.engine);assertNull(pool.tryAcquire(listOf("pollinations","horde")))
        pool.release(answer);assertNull(pool.tryAcquire(listOf("pollinations")))
        pool.release(summary);assertEquals("pollinations",pool.tryAcquire(listOf("pollinations"))!!.engine)
    }
    @Test fun providerQuotaDoesNotPauseTheOtherEngines(){
        var now=0L;val pool=AssistantPool{now}
        pool.cooldown("horde",60000)
        val pollinations=pool.tryAcquire(listOf("horde","pollinations"))!!
        assertEquals("pollinations",pollinations.engine)
        now=60000;assertEquals("horde",pool.tryAcquire(listOf("horde"))!!.engine)
    }
    @Test fun repeatedLeaseReleaseCannotFreeSomeoneElsesWork(){
        val pool=AssistantPool();val first=pool.tryAcquire(listOf("qwen3"))!!;pool.release(first)
        val second=pool.tryAcquire(listOf("qwen3"))!!;pool.release(first)
        assertNull(pool.tryAcquire(listOf("qwen3")));pool.release(second)
        assertNotNull(pool.tryAcquire(listOf("qwen3")))
    }
    @Test fun cancelledWaitDoesNotTakeTheOccupiedSlot()=runBlocking{
        val pool=AssistantPool();val first=pool.tryAcquire(listOf("horde"))!!
        val waiting=launch{pool.acquire(listOf("horde")){true};fail("Should remain queued")}
        yield();waiting.cancelAndJoin();pool.release(first)
        assertNotNull(pool.tryAcquire(listOf("horde")))
    }
    @Test fun summariesPreferTheSummarizerAndNeverUseUnsupportedHordeAnalysis(){
        val pool=AssistantPool()
        assertEquals(listOf("pollinations"),pool.order(true,"auto",true))
        assertEquals(listOf("pollinations","horde"),pool.order(false,"auto",false))
        assertEquals(listOf("pollinations","horde"),pool.order(false,"qwen3",true))
    }
    @Test fun reservedSummarySkipsSoftSpacingButHonorsProviderCooldown(){
        var now=0L;val pool=AssistantPool{now}
        pool.throttle("pollinations",30_000)
        assertNull(pool.tryAcquire(listOf("pollinations")))
        pool.reserve("pollinations","summary")
        val summary=pool.tryAcquire(listOf("pollinations"),"summary")!!
        pool.release(summary)
        pool.cooldown("pollinations",10_000)
        assertNull(pool.tryAcquire(listOf("pollinations"),"summary"))
        now=10_000
        assertNotNull(pool.tryAcquire(listOf("pollinations"),"summary"))
    }
    @Test fun aRequestedSummaryReservesTheNextSlotWithoutInterruptingTheCurrentAnswer(){
        val pool=AssistantPool();val current=pool.tryAcquire(listOf("pollinations"))!!
        pool.reserve("pollinations","manual-summary")
        assertNull(pool.tryAcquire(listOf("pollinations"),"manual-summary"))
        pool.release(current)
        assertEquals("horde",pool.tryAcquire(listOf("pollinations","horde"))!!.engine)
        val summary=pool.tryAcquire(listOf("pollinations"),"manual-summary")!!
        pool.release(summary);pool.releaseReservation("obsolete-summary")
        assertNull(pool.tryAcquire(listOf("pollinations")))
        pool.releaseReservation("manual-summary");assertNotNull(pool.tryAcquire(listOf("pollinations")))
    }
    @Test fun simultaneousClaimsCannotStartTwoCallsOnTheSameProvider()=runBlocking{
        val pool=AssistantPool();val active=java.util.concurrent.atomic.AtomicInteger();val peak=java.util.concurrent.atomic.AtomicInteger()
        List(16){async(Dispatchers.Default){repeat(150){
            val lease=pool.tryAcquire(listOf("pollinations"))
            if(lease!=null)try{
                val count=active.incrementAndGet();peak.updateAndGet{maxOf(it,count)}
                Thread.yield();active.decrementAndGet()
            }finally{pool.release(lease)}
        }}}.awaitAll()
        assertEquals(1,peak.get());assertEquals(0,active.get())
    }
}
