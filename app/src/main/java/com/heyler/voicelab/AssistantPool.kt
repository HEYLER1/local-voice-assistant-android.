package com.heyler.voicelab

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

/** One in-flight operation per provider. Service cooldowns are isolated; queued work never interrupts. */
internal class AssistantPool(private val now:()->Long={System.nanoTime()/1_000_000}){
    data class Lease(val engine:String,val token:Long)
    private val busy=mutableMapOf<String,Long>()
    private val ready=mutableMapOf<String,Long>()
    private val spacing=mutableMapOf<String,Long>()
    private val reservations=mutableMapOf<String,String>()
    private var serial=0L
    @Synchronized fun reserve(engine:String,owner:String){reservations[engine]=owner}
    @Synchronized fun releaseReservation(owner:String){reservations.entries.removeAll{it.value==owner}}
    @Synchronized fun tryAcquire(order:List<String>,owner:String?=null):Lease?{
        val engine=order.firstOrNull{engine->
            val ownsReservation=owner!=null&&reservations[engine]==owner
            engine !in busy&&(ready[engine]?:0)<=now()&&(reservations[engine]==null||ownsReservation)&&(ownsReservation||(spacing[engine]?:0)<=now())
        }?:return null
        return Lease(engine,++serial).also{busy[engine]=it.token}
    }
    suspend fun acquire(order:List<String>,onWait:(Long)->Unit={},owner:String?=null,valid:()->Boolean):Lease?{
        while(valid()){
            currentCoroutineContext().ensureActive()
            tryAcquire(order,owner)?.let{return it}
            onWait(waitMillis(order,owner))
            delay(250)
        }
        return null
    }
    @Synchronized private fun waitMillis(order:List<String>,owner:String?):Long = order.filter{it !in busy&&(reservations[it]==null||reservations[it]==owner)}
        .minOfOrNull{engine->
            val serviceWait=((ready[engine]?:0)-now()).coerceAtLeast(0)
            val spacingWait=if(owner==null||reservations[engine]!=owner)((spacing[engine]?:0)-now()).coerceAtLeast(0)else 0
            maxOf(serviceWait,spacingWait)
        }?:0L
    /** Soft spacing limits ordinary traffic; an explicitly reserved summary may skip it. */
    @Synchronized fun throttle(engine:String,millis:Long){spacing[engine]=maxOf(spacing[engine]?:0,now()+millis.coerceAtLeast(0))}
    @Synchronized fun cooldown(engine:String,millis:Long){ready[engine]=maxOf(ready[engine]?:0,now()+millis.coerceAtLeast(0))}
    @Synchronized fun release(lease:Lease){if(busy[lease.engine]==lease.token)busy.remove(lease.engine)}
    fun order(summary:Boolean,mode:String,localAvailable:Boolean):List<String> {
        // AI Horde is configured for live Q&A only. Meeting summaries use the
        // structured Pollinations response and should not waste time on a provider
        // that cannot produce the summary format.
        if(summary)return listOf("pollinations")
        return when(mode){
        "qwen3"->listOf("pollinations","horde")
        "pollinations"->listOf("pollinations")
        "horde"->listOf("horde","pollinations")
        else->listOf("pollinations","horde")
        }
    }
}
