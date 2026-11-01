package com.heyler.voicelab

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

/** One in-flight operation per provider. Cooldowns are isolated; no cancellation on new work. */
internal class AssistantPool(private val now:()->Long={System.nanoTime()/1_000_000}){
    data class Lease(val engine:String,val token:Long)
    private val busy=mutableMapOf<String,Long>()
    private val ready=mutableMapOf<String,Long>()
    private val reservations=mutableMapOf<String,String>()
    private var serial=0L
    @Synchronized fun reserve(engine:String,owner:String){reservations[engine]=owner}
    @Synchronized fun releaseReservation(owner:String){reservations.entries.removeAll{it.value==owner}}
    @Synchronized fun tryAcquire(order:List<String>,owner:String?=null):Lease?{
        val engine=order.firstOrNull{it !in busy&&(ready[it]?:0)<=now()&&(reservations[it]==null||reservations[it]==owner)}?:return null
        return Lease(engine,++serial).also{busy[engine]=it.token}
    }
    suspend fun acquire(order:List<String>,onWait:(Long)->Unit={},owner:String?=null,valid:()->Boolean):Lease?{
        while(valid()){
            currentCoroutineContext().ensureActive()
            tryAcquire(order,owner)?.let{return it}
            onWait(waitMillis(order))
            delay(250)
        }
        return null
    }
    @Synchronized private fun waitMillis(order:List<String>):Long = order.filter{it !in busy}
        .minOfOrNull{((ready[it]?:0)-now()).coerceAtLeast(0)}?:0L
    @Synchronized fun cooldown(engine:String,millis:Long){ready[engine]=maxOf(ready[engine]?:0,now()+millis.coerceAtLeast(0))}
    @Synchronized fun release(lease:Lease){if(busy[lease.engine]==lease.token)busy.remove(lease.engine)}
    fun order(summary:Boolean,mode:String,localAvailable:Boolean):List<String> = when(mode){
        "qwen3"->listOf("qwen3")
        "pollinations"->listOf("pollinations")
        "horde"->(if(summary)listOf("pollinations")else listOf("horde","pollinations","qwen3")).filter{it!="qwen3"||localAvailable}
        else->(if(summary)listOf("pollinations")else listOf("pollinations","horde","qwen3"))
            .filter{it!="qwen3"||localAvailable}
    }
}
