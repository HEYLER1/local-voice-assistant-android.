package com.heyler.voicelab

internal data class FloatingQuestion(val id:String,val question:String,val answer:String)

/** Only completed audio answers become previews. No extra model requests or notifications. */
internal object FloatingQuestions {
    fun ready(answers:List<LiveAnswer>)=answers.filter{
        it.source!=null&&!it.suggested&&it.status.startsWith("Lista")&&it.text.isNotBlank()&&AnalysisEvidence.coherent(it.question)
    }.map{FloatingQuestion(it.id,it.question,it.text)}
}

internal class FloatingQuestionQueue {
    private val seen=mutableSetOf<String>()
    private val pending=ArrayDeque<FloatingQuestion>()
    private var initialized=false
    var current:FloatingQuestion?=null;private set
    var latest:FloatingQuestion?=null;private set
    fun observe(items:List<FloatingQuestion>,visible:Boolean){
        latest=items.lastOrNull()
        if(!initialized||!visible){
            initialized=true;seen.addAll(items.map{it.id});pending.clear();current=null
            return
        }
        items.forEach{item->
            if(seen.add(item.id)){if(pending.size<8)pending.addLast(item)}
            else if(current?.id==item.id)current=item
        }
        if(current==null)current=pending.removeFirstOrNull()
    }
    fun advance(){current=pending.removeFirstOrNull()}
    fun dismiss(){pending.clear();current=null}
}
