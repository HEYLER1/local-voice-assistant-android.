package com.heyler.voicelab

import java.text.Normalizer

data class QuestionSpan(val text:String,val start:Int)
object ConversationRules {
    fun folded(text:String)=Normalizer.normalize(text.lowercase(),Normalizer.Form.NFD).replace(Regex("\\p{M}"),"")
    fun key(text:String)=folded(text).replace(Regex("[^a-z0-9 ]")," ").replace(Regex("\\s+")," ").trim()
    private val cues=Regex("\\b(?:por\\s+que|como\\s+(?:es\\s+que|se|puede|puedo|funciona|ocurre)|que\\s+(?:causa|causan|significa|son|es)|cual\\s+(?:es|fue)|cuando\\s+(?:ocurre|sera)|que\\s+tan)\\b")
    private val narration=Regex("\\b(?:pero\\s+que\\s+raro|ok(?:ay)?|asi\\s+que|vamos\\s+camino|y\\s+tambien|bueno\\s+(?:ahora|vamos|la\\s+siguiente)|y\\s+(?:por\\s+que|como\\s+(?:es|se)|cual\\s+es))\\b")
    private val predicates=Regex("\\b(?:es|son|fue|sera|recibe|reciben|sabemos|sabia|causa|causan|ocurre|ocurren|funciona|funcionan|calcula|calcular|puede|puedo|significa|tiene|tienen|cambia|cambian|explica|explicar|hace|hacen|produce|producen|sucede|suceden|se)\\b")
    private val filler=setOf("que","por","como","cual","cuando","es","son","fue","se","el","la","los","las","un","una","de","del","en","y","a","no","esto","eso","asi","aqui","ahi","todo","algo","pero","raro","funciona","puede","puedo","calcula")
    fun questions(text:String)=spans(text).map{it.text}
    fun spans(text:String):List<QuestionSpan>{
        val found=mutableListOf<QuestionSpan>()
        fun add(raw:String,start:Int){
            val boundary=narration.find(folded(raw))?.range?.first?:raw.length
            val phrase=raw.take(boundary).trim().trim(',', ';').replace(Regex("\\s+")," ")
            val normalized=key(phrase)
            val words=normalized.split(" ")
            if(words.lastOrNull() in setOf("un","una","el","la","los","las","de","del","en","para","con","y","o","que","por","se","es","son","hay"))return
            if(Regex("^(?:que hay|que es|como es|por que es|como se)$").matches(normalized))return
            if(words.filter{it !in filler}.distinct().size<2 && words.size>5)return
            if(words.size !in 3..65||phrase.length>400||!cues.containsMatchIn(key(phrase))||!predicates.containsMatchIn(key(phrase)))return
            if(words.distinct().size<3||words.none{it !in filler}||words.distinct().size.toDouble()/words.size<.45)return
            if(Regex("^(?:que es perfecto(?: |$)|que la hay$|estas siendo|pero que|por que no$)").containsMatchIn(key(phrase)))return
            found.add(QuestionSpan("¿$phrase?",start))
        }
        Regex("¿([^¿?]+)\\?").findAll(text).forEach{add(it.groupValues[1],it.range.first)}
        cues.findAll(folded(text)).forEach{m->val tail=text.substring(m.range.first);add(tail.takeWhile{it !in "?.!¿"},m.range.first)}
        val accepted=found.distinctBy{key(it.text)}
        return accepted.filter{candidate->accepted.none{other->key(other.text)!=key(candidate.text)&&key(other.text).contains(key(candidate.text))}}.sortedBy{it.start}.takeLast(8)
    }
    fun salient(text:String):List<String> = text.split(Regex("(?<=[.!?])\\s+"))
        .map{it.trim()}.filter{key(it).split(" ").size>=8}
        .sortedByDescending{sentence->Regex("\\b(?:decidi|acord|importante|porque|significa|consiste|ocurre|objetivo|resultado|eclipse|pregunta)").findAll(key(sentence)).count()}
        .distinctBy{key(it)}.take(4)
}
