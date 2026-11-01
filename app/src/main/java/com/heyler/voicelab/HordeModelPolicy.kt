package com.heyler.voicelab

internal data class HordeModel(val name:String,val workers:Int,val speed:Double,val queued:Double){
    val estimatedSeconds:Double get()=(queued+180.0)/(speed*workers.coerceAtLeast(1))
}
internal object HordeModelPolicy{
    fun choose(models:List<HordeModel>)=models.filter{
        it.workers>0&&it.speed.isFinite()&&it.speed>0&&it.queued.isFinite()&&it.queued>=0&&
            Regex("qwen[-_/ ]?3[._-]?5",RegexOption.IGNORE_CASE).containsMatchIn(it.name)
    }.minWithOrNull(compareBy<HordeModel>{it.estimatedSeconds}.thenBy{it.name})
}
